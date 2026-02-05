/**
 * Context Domain - context selection and task text.
 *
 * Commands:
 * - context/SELECT - select context (template), reloads sections, mode-sets, tag-sets
 * - context/SET_TASK - update task description text
 * - context/LOADED - store loaded contexts list and validate current selection
 *
 * When context changes, all dependent catalogs are reloaded from CLI.
 * If review mode is active for the new context, branches are also loaded.
 *
 * Ported from VS Code Extension's state-lg/domains/context.ts
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionsListSchema
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

data class SelectContextPayload(val template: String)
val SelectContext = command("context/SELECT").payload<SelectContextPayload>()

data class SetTaskPayload(val text: String)
val SetTask = command("context/SET_TASK").payload<SetTaskPayload>()

data class ContextsLoadedPayload(val contexts: List<String>)
val ContextsLoaded = command("context/LOADED").payload<ContextsLoadedPayload>()

// ============================================
// Temporary Command Stubs
// Will be moved to respective domains in step 11.
// ============================================

// Adaptive domain (step 11)
data class ModeSetsLoadedPayload(val modeSets: ModeSetsListSchema?)
val ModeSetsLoaded = command("adaptive/MODE_SETS_LOADED").payload<ModeSetsLoadedPayload>()

data class TagSetsLoadedPayload(val tagSets: TagSetsListSchema?)
val TagSetsLoaded = command("adaptive/TAG_SETS_LOADED").payload<TagSetsLoadedPayload>()

data class BranchesLoadedPayload(val branches: List<String>)
val BranchesLoaded = command("adaptive/BRANCHES_LOADED").payload<BranchesLoadedPayload>()

// ============================================
// Rule Registration
// ============================================

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Register context domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerContextRules(project: Project) {

    // When contexts loaded, validate current template selection
    rule.invoke(ContextsLoaded, RuleConfig(
        condition = { _: PCEState, _: ContextsLoadedPayload -> true },
        apply = { state: PCEState, payload: ContextsLoadedPayload ->
            val contexts = payload.contexts
            val currentTemplate = state.persistent.template

            val isValid = currentTemplate.isNotBlank() && currentTemplate in contexts

            if (isValid) {
                lgResult(configMutations = mapOf("contexts" to contexts))
            } else {
                val newTemplate = contexts.firstOrNull() ?: ""
                lgResult(
                    configMutations = mapOf("contexts" to contexts),
                    followUp = if (newTemplate.isNotBlank()) {
                        listOf(SelectContext.create(SelectContextPayload(newTemplate)))
                    } else null
                )
            }
        }
    ))

    // When context changes, reload sections, mode-sets and tag-sets
    rule.invoke(SelectContext, RuleConfig(
        condition = { state: PCEState, payload: SelectContextPayload ->
            payload.template.isNotBlank() && payload.template != state.persistent.template
        },
        apply = { state: PCEState, payload: SelectContextPayload ->
            val template = payload.template
            val providerId = state.persistent.providerId

            val asyncOps = mutableListOf<AsyncOperation>()

            // Reload sections
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliExecutor = project.service<CliExecutor>()
                    val sections = try {
                        val stdout = cliExecutor.execute(
                            args = listOf("list", "sections"),
                            timeoutMs = 30_000
                        ).getOrThrow()
                        json.decodeFromString<SectionsListSchema>(stdout).sections
                    } catch (_: Exception) {
                        emptyList()
                    }
                    return SectionsLoaded.create(SectionsLoadedPayload(sections))
                }
            })

            // Reload mode-sets (depend on both provider AND template)
            if (providerId.isNotBlank()) {
                asyncOps.add(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val cliExecutor = project.service<CliExecutor>()
                        val modeSets = try {
                            val stdout = cliExecutor.execute(
                                args = listOf("list", "mode-sets", "--context", template, "--provider", providerId),
                                timeoutMs = 30_000
                            ).getOrThrow()
                            json.decodeFromString<ModeSetsListSchema>(stdout)
                        } catch (_: Exception) {
                            null
                        }
                        return ModeSetsLoaded.create(ModeSetsLoadedPayload(modeSets))
                    }
                })
            }

            // Reload tag-sets (depend on template)
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliExecutor = project.service<CliExecutor>()
                    val tagSets = try {
                        val stdout = cliExecutor.execute(
                            args = listOf("list", "tag-sets", "--context", template),
                            timeoutMs = 30_000
                        ).getOrThrow()
                        json.decodeFromString<TagSetsListSchema>(stdout)
                    } catch (_: Exception) {
                        null
                    }
                    return TagSetsLoaded.create(TagSetsLoadedPayload(tagSets))
                }
            })

            // Load branches if review mode is active for the new context
            val newModes = state.persistent.modesByContextProvider[template]?.get(providerId) ?: emptyMap()
            val isReviewActive = newModes.values.any { it == "review" }

            if (isReviewActive) {
                asyncOps.add(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val gitService = LgGitService.getInstance(project)
                        val branches = gitService.getBranches()
                        return BranchesLoaded.create(BranchesLoadedPayload(branches))
                    }
                })
            }

            lgResult(
                mutations = mapOf("template" to template),
                asyncOps = asyncOps
            )
        }
    ))

    // When task text is set, update persistent state
    rule.invoke(SetTask, RuleConfig(
        condition = { _: PCEState, _: SetTaskPayload -> true },
        apply = { _: PCEState, payload: SetTaskPayload ->
            lgResult(mutations = mapOf("taskText" to payload.text))
        }
    ))
}
