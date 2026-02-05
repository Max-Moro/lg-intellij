/**
 * Lifecycle Domain - initialization and refresh.
 *
 * Commands:
 * - lifecycle/INITIALIZE - initial bootstrap (includes provider detection)
 * - lifecycle/REFRESH - refresh all catalogs (without provider detection)
 *
 * Both commands load ALL available catalogs from CLI.
 * The only difference: INITIALIZE includes provider detection, REFRESH does not.
 *
 * Catalogs are loaded conditionally based on persistent state dependencies:
 * - tokenizer-libs: always (no dependencies)
 * - sections: if template exists
 * - contexts: if providerId exists
 * - mode-sets: if providerId AND template exist
 * - tag-sets: if template exists
 *
 * Ported from VS Code Extension's state-lg/domains/lifecycle.ts
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.models.ContextsListSchema
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionsListSchema
import lg.intellij.models.TagSetsListSchema
import lg.intellij.models.TokenizerLibsListSchema
import lg.intellij.services.ai.AiIntegrationService
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.NoPayloadRuleConfig
import lg.intellij.stateengine.command
import lg.intellij.statepce.PCEState
import lg.intellij.statepce.ProviderInfo
import lg.intellij.statepce.lgResult
import lg.intellij.statepce.rule

// ============================================
// Commands
// ============================================

val Initialize = command("lifecycle/INITIALIZE").noPayload()
val Refresh = command("lifecycle/REFRESH").noPayload()

// ============================================
// Shared Catalog Loading
// ============================================

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Build async operations for loading all available catalogs.
 *
 * Each catalog is loaded conditionally based on state dependencies:
 * - tokenizer-libs: always (no dependencies)
 * - sections: if template exists
 * - contexts: if providerId exists
 * - mode-sets: if providerId AND template exist
 * - tag-sets: if template exists
 *
 * @param state Current PCE state (for checking dependencies)
 * @param includeProviderDetection Whether to include provider detection (INITIALIZE only)
 * @param project Project for service access
 */
private fun buildCatalogOps(
    state: PCEState,
    includeProviderDetection: Boolean,
    project: Project
): List<AsyncOperation> {
    val ops = mutableListOf<AsyncOperation>()
    val providerId = state.persistent.providerId
    val template = state.persistent.template

    // Provider detection (INITIALIZE only)
    if (includeProviderDetection) {
        ops.add(object : AsyncOperation {
            override suspend fun execute(): BaseCommand {
                val aiService = AiIntegrationService.getInstance()
                val aiProviders = aiService.detectAvailableProvidersInfo()
                val providers = aiProviders.map {
                    ProviderInfo(it.id, it.name, it.priority)
                }
                return ProvidersDetected.create(ProvidersDetectedPayload(providers))
            }
        })
    }

    // Tokenizer libs (always load, no dependencies)
    ops.add(object : AsyncOperation {
        override suspend fun execute(): BaseCommand {
            val cliExecutor = project.service<CliExecutor>()
            val libs = try {
                val stdout = cliExecutor.execute(
                    args = listOf("list", "tokenizer-libs"),
                    timeoutMs = 20_000
                ).getOrThrow()
                json.decodeFromString<TokenizerLibsListSchema>(stdout).tokenizerLibs
            } catch (_: Exception) {
                emptyList()
            }
            return LibsLoaded.create(LibsLoadedPayload(libs))
        }
    })

    // Sections (loaded if template exists)
    if (template.isNotBlank()) {
        ops.add(object : AsyncOperation {
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
    }

    // Contexts (loaded if providerId exists)
    if (providerId.isNotBlank()) {
        ops.add(object : AsyncOperation {
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
    }

    // Mode-sets (loaded if providerId AND template exist)
    if (providerId.isNotBlank() && template.isNotBlank()) {
        ops.add(object : AsyncOperation {
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

    // Tag-sets (loaded if template exists)
    if (template.isNotBlank()) {
        ops.add(object : AsyncOperation {
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
    }

    return ops
}

// ============================================
// Rule Registration
// ============================================

/**
 * Register lifecycle domain rules.
 *
 * @param project Project for service access in async operations
 */
fun registerLifecycleRules(project: Project) {
    // Initialize: detect providers + load all available catalogs
    rule.invoke(Initialize, NoPayloadRuleConfig(
        condition = { _ -> true },
        apply = { state ->
            lgResult(asyncOps = buildCatalogOps(state, includeProviderDetection = true, project))
        }
    ))

    // Refresh: reload all available catalogs (no provider detection)
    rule.invoke(Refresh, NoPayloadRuleConfig(
        condition = { _ -> true },
        apply = { state ->
            lgResult(asyncOps = buildCatalogOps(state, includeProviderDetection = false, project))
        }
    ))
}
