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
 *
 * Ported from VS Code Extension's state-lg/domains/adaptive.ts
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
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Commands
// ============================================

data class SelectModePayload(val modeSetId: String, val modeId: String)
val SelectMode = command("adaptive/SELECT_MODE").payload<SelectModePayload>()

data class ToggleTagPayload(val tagSetId: String, val tagId: String)
val ToggleTag = command("adaptive/TOGGLE_TAG").payload<ToggleTagPayload>()

data class SetTagsPayload(val tags: Map<String, Set<String>>)
val SetTags = command("adaptive/SET_TAGS").payload<SetTagsPayload>()

data class SelectBranchPayload(val branch: String)
val SelectBranch = command("adaptive/SELECT_BRANCH").payload<SelectBranchPayload>()

data class ModeSetsLoadedPayload(val modeSets: ModeSetsListSchema?)
val ModeSetsLoaded = command("adaptive/MODE_SETS_LOADED").payload<ModeSetsLoadedPayload>()

data class TagSetsLoadedPayload(val tagSets: TagSetsListSchema?)
val TagSetsLoaded = command("adaptive/TAG_SETS_LOADED").payload<TagSetsLoadedPayload>()

data class BranchesLoadedPayload(val branches: List<String>)
val BranchesLoaded = command("adaptive/BRANCHES_LOADED").payload<BranchesLoadedPayload>()

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
        condition = { _: PCEState, _: ModeSetsLoadedPayload -> true },
        apply = { state: PCEState, payload: ModeSetsLoadedPayload ->
            val modeSets = payload.modeSets ?: ModeSetsListSchema(emptyList())
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

            // Deep copy for nested structure
            val newModesByCtxProvider = state.persistent.modesByContextProvider.toMutableMap()
            val contextModes = (newModesByCtxProvider[ctx]?.toMutableMap() ?: mutableMapOf())
            contextModes[provider] = actualizedModes.toMutableMap()
            newModesByCtxProvider[ctx] = contextModes

            lgResult(
                configMutations = mapOf("modeSets" to modeSets),
                mutations = mapOf("modesByContextProvider" to newModesByCtxProvider)
            )
        }
    ))

    // When tag-sets are loaded, remove invalid saved tags
    rule.invoke(TagSetsLoaded, RuleConfig(
        condition = { _: PCEState, _: TagSetsLoadedPayload -> true },
        apply = { state: PCEState, payload: TagSetsLoadedPayload ->
            val tagSets = payload.tagSets ?: TagSetsListSchema(emptyList())
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
                // Deep copy
                val newTagsByContext = state.persistent.tagsByContext.toMutableMap()
                newTagsByContext[ctx] = validatedTags

                lgResult(
                    configMutations = mapOf("tagSets" to tagSets),
                    mutations = mapOf("tagsByContext" to newTagsByContext)
                )
            } else {
                lgResult(
                    configMutations = mapOf("tagSets" to tagSets)
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

            // Deep copy for nested structure
            val newModesByCtxProvider = state.persistent.modesByContextProvider.toMutableMap()
            val contextModes = (newModesByCtxProvider[ctx]?.toMutableMap() ?: mutableMapOf())
            val providerModes = (contextModes[provider]?.toMutableMap() ?: mutableMapOf())
            providerModes[payload.modeSetId] = payload.modeId
            contextModes[provider] = providerModes
            newModesByCtxProvider[ctx] = contextModes

            // Load branches when switching to review mode
            val asyncOps = if (payload.modeId == "review") {
                listOf(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val gitService = LgGitService.getInstance(project)
                        val branches = gitService.getBranches()
                        return BranchesLoaded.create(BranchesLoadedPayload(branches))
                    }
                })
            } else {
                null
            }

            lgResult(
                mutations = mapOf("modesByContextProvider" to newModesByCtxProvider),
                asyncOps = asyncOps
            )
        }
    ))

    // When tag is toggled, update persistent state
    rule.invoke(ToggleTag, RuleConfig(
        condition = { _: PCEState, _: ToggleTagPayload -> true },
        apply = { state: PCEState, payload: ToggleTagPayload ->
            val ctx = state.persistent.template
            val currentTags = state.persistent.tagsByContext[ctx] ?: emptyMap()
            val tagsInSet = currentTags[payload.tagSetId]?.toMutableSet() ?: mutableSetOf()

            val isCurrentlySelected = payload.tagId in tagsInSet
            if (isCurrentlySelected) {
                tagsInSet.remove(payload.tagId)
            } else {
                tagsInSet.add(payload.tagId)
            }

            // Deep copy
            val newTagsByContext = state.persistent.tagsByContext.toMutableMap()
            val newTags = currentTags.toMutableMap()

            if (tagsInSet.isNotEmpty()) {
                newTags[payload.tagSetId] = tagsInSet
            } else {
                newTags.remove(payload.tagSetId)
            }
            newTagsByContext[ctx] = newTags

            lgResult(
                mutations = mapOf("tagsByContext" to newTagsByContext)
            )
        }
    ))

    // When branches are loaded, validate target branch selection
    rule.invoke(BranchesLoaded, RuleConfig(
        condition = { _: PCEState, _: BranchesLoadedPayload -> true },
        apply = { state: PCEState, payload: BranchesLoadedPayload ->
            val branches = payload.branches
            val currentBranch = state.persistent.targetBranch

            // Validate: try current, then main/master, then first
            val branchSet = branches.toSet()
            val candidates = listOf(currentBranch, "main", "master", "origin/main", "origin/master")
            val newBranch = candidates.firstOrNull { it.isNotEmpty() && it in branchSet }
                ?: branches.firstOrNull()
                ?: ""

            val mutations = if (newBranch != currentBranch) {
                mapOf("targetBranch" to newBranch)
            } else {
                null
            }

            lgResult(
                envMutations = mapOf("branches" to branches),
                mutations = mutations
            )
        }
    ))

    // Batch set all tags for current context (from tags dialog)
    rule.invoke(SetTags, RuleConfig(
        condition = { _: PCEState, _: SetTagsPayload -> true },
        apply = { state: PCEState, payload: SetTagsPayload ->
            val ctx = state.persistent.template

            // Deep copy
            val newTagsByContext = state.persistent.tagsByContext.toMutableMap()
            newTagsByContext[ctx] = payload.tags.mapValues { it.value.toMutableSet() }.toMutableMap()

            lgResult(
                mutations = mapOf("tagsByContext" to newTagsByContext)
            )
        }
    ))

    // When target branch changes, update persistent state
    rule.invoke(SelectBranch, RuleConfig(
        condition = { state: PCEState, payload: SelectBranchPayload ->
            payload.branch != state.persistent.targetBranch
        },
        apply = { _: PCEState, payload: SelectBranchPayload ->
            lgResult(
                mutations = mapOf("targetBranch" to payload.branch)
            )
        }
    ))
}
