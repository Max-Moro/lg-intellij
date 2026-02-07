/**
 * Provider Domain - AI provider selection and CLI settings.
 *
 * Commands:
 * - provider/SELECT - select AI provider (reloads contexts and mode-sets)
 * - provider/DETECTED - store detected providers and select best available
 * - provider/SET_CLI_SCOPE - update CLI working directory scope
 * - provider/SELECT_CLI_SHELL - update shell type for CLI execution
 *
 * When provider changes, dependent catalogs (contexts, mode-sets) are reloaded
 * because they may vary by provider.
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import lg.intellij.cli.CliClient
import lg.intellij.models.ShellType
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.RuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.ProviderInfo
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Commands
// ============================================

val SelectProvider = command("provider/SELECT").payload<String>()
val ProvidersDetected = command("provider/DETECTED").payload<List<ProviderInfo>>()
val SetCliScope = command("provider/SET_CLI_SCOPE").payload<String>()
val SelectCliShell = command("provider/SELECT_CLI_SHELL").payload<ShellType>()

// ============================================
// Rule Registration
// ============================================

/**
 * Register provider domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerProviderRules(project: Project) {

    // When providers detected, store in environment and select best available
    rule.invoke(ProvidersDetected, RuleConfig(
        condition = { _: PCEState, _: List<ProviderInfo> -> true },
        apply = { state: PCEState, providers: List<ProviderInfo> ->
            val savedProvider = state.persistent.providerId

            val savedExists = providers.any { it.id == savedProvider }
            val effectiveProvider = if (savedExists) {
                savedProvider
            } else {
                providers.firstOrNull()?.id ?: "clipboard"
            }

            lgResult(
                envMutations = mapOf("providers" to providers),
                followUp = listOf(
                    SelectProvider.create(effectiveProvider)
                )
            )
        }
    ))

    // When provider changes, reload contexts and mode-sets
    rule.invoke(SelectProvider, RuleConfig(
        condition = { state: PCEState, providerId: String ->
            providerId != state.persistent.providerId
        },
        apply = { state: PCEState, providerId: String ->
            val template = state.persistent.template

            val asyncOps = mutableListOf<AsyncOperation>()

            // Reload contexts (always when provider changes)
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliClient = project.service<CliClient>()
                    val contexts = cliClient.listContexts(providerId)
                    return ContextsLoaded.create(contexts)
                }
            })

            // Mode-sets depend on both provider AND template
            // Reload them if template exists (tags don't depend on provider, so skip them)
            if (template.isNotBlank()) {
                asyncOps.add(object : AsyncOperation {
                    override suspend fun execute(): BaseCommand {
                        val cliClient = project.service<CliClient>()
                        val modeSets = cliClient.listModeSets(template, providerId)
                        return ModeSetsLoaded.create(modeSets)
                    }
                })
            }

            lgResult(
                mutations = mapOf("providerId" to providerId),
                asyncOps = asyncOps
            )
        }
    ))

    // When CLI scope is set, update persistent state
    rule.invoke(SetCliScope, RuleConfig(
        condition = { _: PCEState, _: String -> true },
        apply = { _: PCEState, cliScope: String ->
            lgResult(mutations = mapOf("cliScope" to cliScope))
        }
    ))

    // When CLI shell is selected, update persistent state
    rule.invoke(SelectCliShell, RuleConfig(
        condition = { _: PCEState, _: ShellType -> true },
        apply = { _: PCEState, shell: ShellType ->
            lgResult(mutations = mapOf("cliShell" to shell))
        }
    ))
}
