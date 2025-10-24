package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliExecutor
import lg.intellij.models.CliResult
import lg.intellij.services.LgErrorReportingService
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
 * 
 * Phase 7: Foundation implementation.
 * Phase 8: Will be enhanced with VirtualFile integration.
 */
@Service(Service.Level.PROJECT)
class LgGenerationService(private val project: Project) {
    
    private val cliExecutor: CliExecutor
        get() = project.service()
    
    private val panelState: LgPanelStateService
        get() = project.service()
    
    private val errorReporting: LgErrorReportingService
        get() = LgErrorReportingService.getInstance()
    
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
            
            when (result) {
                is CliResult.Success -> {
                    LOG.info("${targetType.displayName.replaceFirstChar { it.uppercase() }} generated successfully for '$targetName'")
                    result.data
                }
                
                is CliResult.Failure -> {
                    val operationName = "${targetType.displayName.replaceFirstChar { it.uppercase() }} Generation"
                    LOG.warn("$operationName failed: exit code ${result.exitCode}")
                    errorReporting.reportCliFailure(project, operationName, result)
                    null
                }
                
                is CliResult.Timeout -> {
                    val operationName = "${targetType.displayName.replaceFirstChar { it.uppercase() }} Generation"
                    LOG.warn("$operationName timeout")
                    errorReporting.reportTimeout(project, operationName, result.timeoutMs)
                    null
                }
                
                is CliResult.NotFound -> {
                    val operationName = "${targetType.displayName.replaceFirstChar { it.uppercase() }} Generation"
                    LOG.warn("CLI not found")
                    errorReporting.reportCliNotFound(project, operationName)
                    null
                }
            }
        }
    }
    
    companion object {
        private val LOG = logger<LgGenerationService>()
    }
}
