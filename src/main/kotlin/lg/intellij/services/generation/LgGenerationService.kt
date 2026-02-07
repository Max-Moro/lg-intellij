package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.handleWith
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

    private val cliExecutor: CliExecutor
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

            // For sections, find section info and pass it for filtering
            val sectionInfo = if (!isContext) {
                store.getBusinessState().configuration.sections.find { it.name == targetName }
            } else null

            val params = CliArgsBuilder.buildCliParams(
                store,
                BuildParamsOptions(includeProvider = isContext, sectionInfo = sectionInfo)
            )
            val (args, stdinData) = CliArgsBuilder.buildCliArgs("render", target, params)

            LOG.debug("Generating ${targetType.displayName} for '$targetName' with args: $args")

            val result = cliExecutor.execute(
                args = args,
                stdinData = stdinData,
                timeoutMs = 120_000
            )

            val operationName = "${targetType.displayName.replaceFirstChar { it.uppercase() }} Generation"

            result.handleWith(
                project = project,
                operationName = operationName,
                logger = LOG
            ) { success ->
                LOG.info("${targetType.displayName.replaceFirstChar { it.uppercase() }} generated successfully for '$targetName'")
                success.data
            }
        }
    }

    companion object {
        private val LOG = logger<LgGenerationService>()
    }
}
