/**
 * Adaptive Domain - modes, tags, and target branch (review mode).
 *
 * Commands:
 * - adaptive/SELECT_MODE - select mode within a mode-set
 * - adaptive/TOGGLE_TAG - toggle tag selection
 * - adaptive/SELECT_BRANCH - select target branch (review mode)
 * - adaptive/MODE_SETS_LOADED - store loaded mode-sets, actualize selections
 * - adaptive/TAG_SETS_LOADED - store loaded tag-sets, remove invalid tags
 * - adaptive/BRANCHES_LOADED - store loaded branches, validate selection
 *
 * This is the most complex domain:
 * - Manages nested structures (modesByContextProvider, tagsByContext)
 * - Handles deep copy for immutable updates of nested maps
 * - Conditionally loads branches when entering review mode
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.project.Project
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.TagSetsListSchema
import lg.intellij.services.git.LgGitService
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.PersistentState
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule
import lg.intellij.statepce.withModes
import lg.intellij.statepce.withContextTags
import lg.intellij.statepce.withMode

// ============================================
// Commands
// ============================================

data class SelectModePayload(val modeSetId: String, val modeId: String)
val SelectMode = command("adaptive/SELECT_MODE").payload<SelectModePayload>()

data class ToggleTagPayload(val tagSetId: String, val tagId: String)
val ToggleTag = command("adaptive/TOGGLE_TAG").payload<ToggleTagPayload>()

val SetTags = command("adaptive/SET_TAGS").payload<Map<String, Set<String>>>()
val SelectBranch = command("adaptive/SELECT_BRANCH").payload<String>()
val ModeSetsLoaded = command("adaptive/MODE_SETS_LOADED").payload<ModeSetsListSchema>()
val TagSetsLoaded = command("adaptive/TAG_SETS_LOADED").payload<TagSetsListSchema>()
val BranchesLoaded = command("adaptive/BRANCHES_LOADED").payload<List<String>>()

// ============================================
// Rule Registration
// ============================================

/**
 * Register adaptive domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerAdaptiveRules(project: Project) {

    // When mode-sets are loaded, ensure all mode-sets have valid selection
    rule.invoke(ModeSetsLoaded, RuleConfig(
        condition = { _: PCEState, _: ModeSetsListSchema -> true },
        apply = { state: PCEState, modeSets: ModeSetsListSchema ->
            val ctx = state.persistent.template
            val provider = state.persistent.providerId
            val savedModes = state.persistent.modesByContextProvider[ctx]?.get(provider) ?: emptyMap()

            val actualizedModes = mutableMapOf<String, String>()

            for (modeSet in modeSets.modeSets) {
                val savedModeId = savedModes[modeSet.id]
                val modeExists = modeSet.modes.any { it.id == savedModeId }

                if (savedModeId != null && modeExists) {
                    actualizedModes[modeSet.id] = savedModeId
                } else {
                    // For ai-interaction mode-set, prefer "agent" mode as default
                    var defaultMode = modeSet.modes.firstOrNull()
                    if (modeSet.id == "ai-interaction") {
                        val agentMode = modeSet.modes.find { it.id == "agent" }
                        if (agentMode != null) {
                            defaultMode = agentMode
                        }
                    }
                    if (defaultMode != null) {
                        actualizedModes[modeSet.id] = defaultMode.id
                    }
                }
            }

            lgResult(
                config = { c -> c.copy(modeSets = modeSets) },
                persistent = { s -> s.withModes(ctx, provider, actualizedModes) }
            )
        }
    ))

    // When tag-sets are loaded, remove invalid saved tags
    rule.invoke(TagSetsLoaded, RuleConfig(
        condition = { _: PCEState, _: TagSetsListSchema -> true },
        apply = { state: PCEState, tagSets: TagSetsListSchema ->
            val ctx = state.persistent.template
            val savedTags = state.persistent.tagsByContext[ctx] ?: emptyMap()

            // Build valid pairs: tagSetId -> Set<tagId>
            val validPairs = mutableMapOf<String, Set<String>>()
            for (tagSet in tagSets.tagSets) {
                validPairs[tagSet.id] = tagSet.tags.map { it.id }.toSet()
            }

            // Filter saved tags against valid pairs
            val validatedTags = mutableMapOf<String, MutableSet<String>>()
            for ((setId, tagIds) in savedTags) {
                val validTagsInSet = validPairs[setId] ?: continue
                val filteredTags = tagIds.filter { it in validTagsInSet }.toMutableSet()
                if (filteredTags.isNotEmpty()) {
                    validatedTags[setId] = filteredTags
                }
            }

            val hasChanges = savedTags.mapValues { it.value.toSet() } != validatedTags.mapValues { it.value.toSet() }

            if (hasChanges) {
                lgResult(
                    config = { c -> c.copy(tagSets = tagSets) },
                    persistent = { s ->
                        s.withContextTags(ctx, validatedTags.mapValues { it.value.toSet() })
                    }
                )
            } else {
                lgResult(
                    config = { c -> c.copy(tagSets = tagSets) }
                )
            }
        }
    ))

    // When mode changes, update persistent state and load branches if entering review mode
    rule.invoke(SelectMode, RuleConfig(
        condition = { state: PCEState, payload: SelectModePayload ->
            val ctx = state.persistent.template
            val provider = state.persistent.providerId
            val currentModeId = state.persistent.modesByContextProvider[ctx]?.get(provider)?.get(payload.modeSetId)
            payload.modeId != currentModeId
        },
        apply = { state: PCEState, payload: SelectModePayload ->
            val ctx = state.persistent.template
            val provider = state.persistent.providerId

            // Load branches when switching to review mode
            val asyncOps = if (payload.modeId == "review") {
                listOf(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val gitService = LgGitService.getInstance(project)
                        val branches = gitService.getBranches()
                        return BranchesLoaded.create(branches)
                    }
                })
            } else {
                null
            }

            lgResult(
                persistent = { s -> s.withMode(ctx, provider, payload.modeSetId, payload.modeId) },
                asyncOps = asyncOps
            )
        }
    ))

    // When tag is toggled, update persistent state
    rule.invoke(ToggleTag, RuleConfig(
        condition = { _: PCEState, _: ToggleTagPayload -> true },
        apply = { state: PCEState, payload: ToggleTagPayload ->
            val ctx = state.persistent.template

            lgResult(persistent = { s ->
                val currentTags = s.tagsByContext[ctx] ?: emptyMap()
                val currentSet = currentTags[payload.tagSetId] ?: emptySet()

                val updatedSet = if (payload.tagId in currentSet) {
                    currentSet - payload.tagId
                } else {
                    currentSet + payload.tagId
                }

                val newTags = if (updatedSet.isNotEmpty()) {
                    currentTags + (payload.tagSetId to updatedSet)
                } else {
                    currentTags - payload.tagSetId
                }

                s.withContextTags(ctx, newTags)
            })
        }
    ))

    // When branches are loaded, validate target branch selection
    rule.invoke(BranchesLoaded, RuleConfig(
        condition = { _: PCEState, _: List<String> -> true },
        apply = { state: PCEState, branches: List<String> ->
            val currentBranch = state.persistent.targetBranch

            // Validate: try current, then main/master, then first
            val branchSet = branches.toSet()
            val candidates = listOf(currentBranch, "main", "master", "origin/main", "origin/master")
            val newBranch = candidates.firstOrNull { it.isNotEmpty() && it in branchSet }
                ?: branches.firstOrNull()
                ?: ""

            lgResult(
                env = { e -> e.copy(branches = branches) },
                persistent = if (newBranch != currentBranch) {
                    { s: PersistentState -> s.copy(targetBranch = newBranch) }
                } else null
            )
        }
    ))

    // Batch set all tags for current context (from tags dialog)
    rule.invoke(SetTags, RuleConfig(
        condition = { _: PCEState, _: Map<String, Set<String>> -> true },
        apply = { state: PCEState, tags: Map<String, Set<String>> ->
            val ctx = state.persistent.template

            lgResult(persistent = { s -> s.withContextTags(ctx, tags) })
        }
    ))

    // When target branch changes, update persistent state
    rule.invoke(SelectBranch, RuleConfig(
        condition = { state: PCEState, branch: String ->
            branch != state.persistent.targetBranch
        },
        apply = { _: PCEState, branch: String ->
            lgResult(persistent = { s -> s.copy(targetBranch = branch) })
        }
    ))
}
