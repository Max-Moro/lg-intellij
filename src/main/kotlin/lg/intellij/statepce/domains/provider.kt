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
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.models.ContextsListSchema
import lg.intellij.models.ModeSetsListSchema
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

data class SelectProviderPayload(val providerId: String)
val SelectProvider = command("provider/SELECT").payload<SelectProviderPayload>()

data class ProvidersDetectedPayload(val providers: List<ProviderInfo>)
val ProvidersDetected = command("provider/DETECTED").payload<ProvidersDetectedPayload>()

data class SetCliScopePayload(val scope: String)
val SetCliScope = command("provider/SET_CLI_SCOPE").payload<SetCliScopePayload>()

data class SelectCliShellPayload(val shell: ShellType)
val SelectCliShell = command("provider/SELECT_CLI_SHELL").payload<SelectCliShellPayload>()

// ============================================
// Rule Registration
// ============================================

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Register provider domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerProviderRules(project: Project) {

    // When providers detected, store in environment and select best available
    rule.invoke(ProvidersDetected, RuleConfig(
        condition = { _: PCEState, _: ProvidersDetectedPayload -> true },
        apply = { state: PCEState, payload: ProvidersDetectedPayload ->
            val providers = payload.providers
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
                    SelectProvider.create(SelectProviderPayload(effectiveProvider))
                )
            )
        }
    ))

    // When provider changes, reload contexts and mode-sets
    rule.invoke(SelectProvider, RuleConfig(
        condition = { state: PCEState, payload: SelectProviderPayload ->
            payload.providerId != state.persistent.providerId
        },
        apply = { state: PCEState, payload: SelectProviderPayload ->
            val providerId = payload.providerId
            val template = state.persistent.template

            val asyncOps = mutableListOf<AsyncOperation>()

            // Reload contexts (always when provider changes)
            asyncOps.add(object : AsyncOperation {
                override suspend fun execute(): BaseCommand {
                    val cliExecutor = project.service<CliExecutor>()
                    val contexts = try {
                        val stdout = cliExecutor.execute(
                            args = listOf("list", "contexts", "--provider", providerId),
                            timeoutMs = 30_000
                        ).getOrThrow()
                        json.decodeFromString<ContextsListSchema>(stdout).contexts
                    } catch (_: Exception) {
                        emptyList()
                    }
                    return ContextsLoaded.create(ContextsLoadedPayload(contexts))
                }
            })

            // Mode-sets depend on both provider AND template
            // Reload them if template exists (tags don't depend on provider, so skip them)
            if (template.isNotBlank()) {
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

            lgResult(
                mutations = mapOf("providerId" to providerId),
                asyncOps = asyncOps
            )
        }
    ))

    // When CLI scope is set, update persistent state
    rule.invoke(SetCliScope, RuleConfig(
        condition = { _: PCEState, _: SetCliScopePayload -> true },
        apply = { _: PCEState, payload: SetCliScopePayload ->
            lgResult(mutations = mapOf("cliScope" to payload.scope))
        }
    ))

    // When CLI shell is selected, update persistent state
    rule.invoke(SelectCliShell, RuleConfig(
        condition = { _: PCEState, _: SelectCliShellPayload -> true },
        apply = { _: PCEState, payload: SelectCliShellPayload ->
            lgResult(mutations = mapOf("cliShell" to payload.shell))
        }
    ))
}
