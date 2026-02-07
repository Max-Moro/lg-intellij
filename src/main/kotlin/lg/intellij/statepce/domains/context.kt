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
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import lg.intellij.cli.CliClient
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

val SelectContext = command("context/SELECT").payload<String>()
val SetTask = command("context/SET_TASK").payload<String>()
val ContextsLoaded = command("context/LOADED").payload<List<String>>()

// ============================================
// Rule Registration
// ============================================

/**
 * Register context domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerContextRules(project: Project) {

    // When contexts loaded, validate current template selection
    rule.invoke(ContextsLoaded, RuleConfig(
        condition = { _: PCEState, _: List<String> -> true },
        apply = { state: PCEState, contexts: List<String> ->
            val currentTemplate = state.persistent.template

            val isValid = currentTemplate.isNotBlank() && currentTemplate in contexts

            if (isValid) {
                lgResult(configMutations = mapOf("contexts" to contexts))
            } else {
                val newTemplate = contexts.firstOrNull() ?: ""
                lgResult(
                    configMutations = mapOf("contexts" to contexts),
                    followUp = if (newTemplate.isNotBlank()) {
                        listOf(SelectContext.create(newTemplate))
                    } else null
                )
            }
        }
    ))

    // When context changes, reload sections, mode-sets and tag-sets
    rule.invoke(SelectContext, RuleConfig(
        condition = { state: PCEState, template: String ->
            template.isNotBlank() && template != state.persistent.template
        },
        apply = { state: PCEState, template: String ->
            val providerId = state.persistent.providerId

            val asyncOps = mutableListOf<AsyncOperation>()

            // Reload sections
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliClient = project.service<CliClient>()
                    val sections = cliClient.listSections()
                    return SectionsLoaded.create(sections)
                }
            })

            // Reload mode-sets (depend on both provider AND template)
            if (providerId.isNotBlank()) {
                asyncOps.add(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val cliClient = project.service<CliClient>()
                        val modeSets = cliClient.listModeSets(template, providerId)
                        return ModeSetsLoaded.create(modeSets)
                    }
                })
            }

            // Reload tag-sets (depend on template)
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliClient = project.service<CliClient>()
                    val tagSets = cliClient.listTagSets(template)
                    return TagSetsLoaded.create(tagSets)
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
                        return BranchesLoaded.create(branches)
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
        condition = { _: PCEState, _: String -> true },
        apply = { _: PCEState, text: String ->
            lgResult(mutations = mapOf("taskText" to text))
        }
    ))
}
