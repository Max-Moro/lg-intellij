package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.handleWith
import lg.intellij.services.state.LgPanelStateService

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
    
    private val panelState: LgPanelStateService
        get() = project.service()

    /**
     * Generates content for the specified target.
     * 
     * @param targetType Type of target (section or context)
     * @param targetName Name of target (e.g., "all", "common", etc.)
     * @return Generated content or null if generation failed (user already notified)
     */
    suspend fun generate(targetType: GenerationTarget, targetName: String): String? {
        return withContext(Dispatchers.IO) {
            val params = CliArgsBuilder.fromPanelState(panelState)
            val target = "${targetType.prefix}:$targetName"
            val (args, stdinData) = CliArgsBuilder.buildRenderArgs(target, params)
            
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
