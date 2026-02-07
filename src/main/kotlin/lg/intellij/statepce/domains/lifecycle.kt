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
 */
package lg.intellij.statepce.domains

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import lg.intellij.cli.CliClient
import lg.intellij.ai.AiIntegrationService
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

/**
 * Build async operations for loading all available catalogs.
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
                return ProvidersDetected.create(providers)
            }
        })
    }

    // Tokenizer libs (always load, no dependencies)
    ops.add(object : AsyncOperation {
        override suspend fun execute(): BaseCommand {
            val cliClient = project.service<CliClient>()
            val libs = cliClient.listTokenizerLibs()
            return LibsLoaded.create(libs)
        }
    })

    // Sections (loaded if template exists)
    if (template.isNotBlank()) {
        ops.add(object : AsyncOperation {
            override suspend fun execute(): BaseCommand {
                val cliClient = project.service<CliClient>()
                val sections = cliClient.listSections()
                return SectionsLoaded.create(sections)
            }
        })
    }

    // Contexts (loaded if providerId exists)
    if (providerId.isNotBlank()) {
        ops.add(object : AsyncOperation {
            override suspend fun execute(): BaseCommand {
                val cliClient = project.service<CliClient>()
                val contexts = cliClient.listContexts(providerId)
                return ContextsLoaded.create(contexts)
            }
        })
    }

    // Mode-sets (loaded if providerId AND template exist)
    if (providerId.isNotBlank() && template.isNotBlank()) {
        ops.add(object : AsyncOperation {
            override suspend fun execute(): BaseCommand {
                val cliClient = project.service<CliClient>()
                val modeSets = cliClient.listModeSets(template, providerId)
                return ModeSetsLoaded.create(modeSets)
            }
        })
    }

    // Tag-sets (loaded if template exists)
    if (template.isNotBlank()) {
        ops.add(object : AsyncOperation {
            override suspend fun execute(): BaseCommand {
                val cliClient = project.service<CliClient>()
                val tagSets = cliClient.listTagSets(template)
                return TagSetsLoaded.create(tagSets)
            }
        })
    }

    return ops
}

// ============================================
// Rule Registration
// ============================================

fun registerLifecycleRules(project: Project) {
    rule.invoke(Initialize, NoPayloadRuleConfig(
        condition = { _ -> true },
        apply = { state ->
            lgResult(asyncOps = buildCatalogOps(state, includeProviderDetection = true, project))
        }
    ))

    rule.invoke(Refresh, NoPayloadRuleConfig(
        condition = { _ -> true },
        apply = { state ->
            lgResult(asyncOps = buildCatalogOps(state, includeProviderDetection = false, project))
        }
    ))
}
