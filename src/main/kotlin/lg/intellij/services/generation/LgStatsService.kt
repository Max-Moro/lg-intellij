package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import lg.intellij.cli.CliExecutor
import lg.intellij.models.CliResult
import lg.intellij.models.ReportSchema
import lg.intellij.services.LgErrorReportingService
import lg.intellij.services.state.LgPanelStateService

/**
 * Service for fetching statistics via CLI 'lg report' command.
 * 
 * Phase 9: Stats collection foundation.
 */
@Service(Service.Level.PROJECT)
class LgStatsService(private val project: Project) {
    
    private val cliExecutor: CliExecutor
        get() = project.service()
    
    private val panelState: LgPanelStateService
        get() = project.service()
    
    private val errorReporting: LgErrorReportingService
        get() = LgErrorReportingService.getInstance()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true // Coerce invalid values to defaults
    }
    
    /**
     * Fetches statistics for the specified target.
     * 
     * @param target Target specifier (e.g., "sec:all", "ctx:template-name")
     * @param taskText Optional task description for --task parameter
     * @return Parsed statistics report
     * @throws StatsException if stats collection fails
     */
    suspend fun getStats(target: String, taskText: String? = null): ReportSchema {
        return withContext(Dispatchers.IO) {
            val params = CliArgsBuilder.fromPanelState(panelState).copy(
                taskText = taskText // Override task text if provided
            )
            
            val (args, stdinData) = CliArgsBuilder.buildReportArgs(target, params)
            
            LOG.debug("Fetching stats for '$target' with args: $args")
            
            val result = cliExecutor.execute(
                args = args,
                stdinData = stdinData,
                timeoutMs = 120_000
            )
            
            when (result) {
                 is CliResult.Success -> {
                     try {
                         // Parse JSON manually to replace problematic 'meta' field with empty map
                         val jsonElement = json.parseToJsonElement(result.data)
                         val jsonObject = jsonElement.jsonObject
                         
                         // Replace 'meta' field with empty object in all file objects
                         val cleanedFiles = jsonObject["files"]?.jsonArray?.map { fileElement ->
                             val fileObj = fileElement.jsonObject.toMutableMap()
                             fileObj["meta"] = JsonObject(emptyMap()) // Replace with empty object
                             JsonObject(fileObj)
                         }?.let { JsonArray(it) }
                         
                         val cleanedJson = jsonObject.toMutableMap().apply {
                             if (cleanedFiles != null) {
                                 put("files", cleanedFiles)
                             }
                         }
                         
                         val report = json.decodeFromJsonElement<ReportSchema>(
                             JsonObject(cleanedJson)
                         )
                         
                         LOG.info("Stats fetched successfully for '$target'")
                         report
                     } catch (e: Exception) {
                         LOG.error("Failed to parse stats JSON", e)
                         throw StatsException("Failed to parse stats: ${e.message}", e)
                     }
                 }
                
                is CliResult.Failure -> {
                    val operationName = "Stats Collection"
                    LOG.warn("$operationName failed: exit code ${result.exitCode}")
                    errorReporting.reportCliFailure(project, operationName, result)
                    throw StatsException("Failed to collect stats: ${result.stderr}")
                }
                
                is CliResult.Timeout -> {
                    val operationName = "Stats Collection"
                    LOG.warn("$operationName timeout")
                    errorReporting.reportTimeout(project, operationName, result.timeoutMs)
                    throw StatsException("Stats collection timed out")
                }
                
                is CliResult.NotFound -> {
                    val operationName = "Stats Collection"
                    LOG.warn("CLI not found")
                    errorReporting.reportCliNotFound(project, operationName)
                    throw StatsException("CLI not found")
                }
            }
        }
    }
    
    companion object {
        private val LOG = logger<LgStatsService>()
    }
}

/**
 * Exception thrown when stats collection fails.
 */
class StatsException(message: String, cause: Throwable? = null) : Exception(message, cause)

