package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliClient
import lg.intellij.cli.CliException
import lg.intellij.services.LgErrorReportingService
import lg.intellij.statepce.PCEStateStore

/**
 * Target type for generation.
 */
enum class GenerationTarget(val prefix: String, val displayName: String) {
    SECTION("sec", "listing"),
    CONTEXT("ctx", "context")
}

/**
 * Service for generating content via CLI.
 *
 * Unified approach for both listings and contexts.
 */
@Service(Service.Level.PROJECT)
class LgGenerationService(private val project: Project) {

    private val cliClient: CliClient
        get() = project.service()

    private val store: PCEStateStore
        get() = PCEStateStore.getInstance(project)

    /**
     * Generates content for the specified target.
     *
     * @param targetType Type of target (section or context)
     * @param targetName Name of target (e.g., "all", "common", etc.)
     * @return Generated content or null if generation failed (user already notified)
     */
    suspend fun generate(targetType: GenerationTarget, targetName: String): String? {
        return withContext(Dispatchers.IO) {
            val target = "${targetType.prefix}:$targetName"
            val isContext = targetType == GenerationTarget.CONTEXT

            val sectionInfo = if (!isContext) {
                store.getBusinessState().configuration.sections.find { it.name == targetName }
            } else null

            val params = CliArgsBuilder.buildCliParams(
                store,
                BuildParamsOptions(includeProvider = isContext, sectionInfo = sectionInfo)
            )

            val operationName = "${targetType.displayName.replaceFirstChar { it.uppercase() }} Generation"
            LOG.debug("Generating ${targetType.displayName} for '$targetName'")

            try {
                val content = cliClient.render(target, params)
                LOG.info("${targetType.displayName.replaceFirstChar { it.uppercase() }} generated successfully for '$targetName'")
                content
            } catch (e: CliException) {
                LgErrorReportingService.getInstance().reportCliException(project, operationName, e)
                null
            }
        }
    }

    companion object {
        private val LOG = logger<LgGenerationService>()
    }
}
