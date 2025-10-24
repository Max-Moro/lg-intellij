package lg.intellij.services.diagnostics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.models.CliResult
import lg.intellij.models.DiagReportSchema
import lg.intellij.services.LgErrorReportingService

/**
 * Service for running diagnostics and cache management.
 * 
 * Features:
 * - Run diagnostics via 'lg diag'
 * - Rebuild cache via 'lg diag --rebuild-cache'
 * - Build diagnostic bundle via 'lg diag --bundle'
 * 
 * Phase 14: Doctor Diagnostics implementation.
 */
@Service(Service.Level.PROJECT)
class LgDiagnosticsService(private val project: Project) {
    
    private val cliExecutor: CliExecutor
        get() = project.service()
    
    private val errorReporting: LgErrorReportingService
        get() = LgErrorReportingService.getInstance()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Runs diagnostics without cache rebuild.
     * 
     * @return Diagnostic report or null if diagnostics failed (user already notified)
     */
    suspend fun runDiagnostics(): DiagReportSchema? {
        return withContext(Dispatchers.IO) {
            LOG.debug("Running diagnostics")
            
            val result = cliExecutor.execute(
                args = listOf("diag"),
                timeoutMs = 60_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    try {
                        json.decodeFromString<DiagReportSchema>(result.data)
                    } catch (e: Exception) {
                        LOG.error("Failed to parse diagnostics JSON", e)
                        null
                    }
                }
                
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Diagnostics", result)
                    null
                }
                
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Diagnostics", result.timeoutMs)
                    null
                }
                
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Diagnostics")
                    null
                }
            }
        }
    }
    
    /**
     * Rebuilds cache and runs diagnostics.
     * 
     * @return Diagnostic report after rebuild or null if rebuild failed (user already notified)
     */
    suspend fun rebuildCache(): DiagReportSchema? {
        return withContext(Dispatchers.IO) {
            LOG.debug("Rebuilding cache")
            
            val result = cliExecutor.execute(
                args = listOf("diag", "--rebuild-cache"),
                timeoutMs = 60_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    try {
                        json.decodeFromString<DiagReportSchema>(result.data)
                    } catch (e: Exception) {
                        LOG.error("Failed to parse diagnostics JSON after rebuild", e)
                        null
                    }
                }
                
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Cache Rebuild", result)
                    null
                }
                
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Cache Rebuild", result.timeoutMs)
                    null
                }
                
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Cache Rebuild")
                    null
                }
            }
        }
    }
    
    /**
     * Builds diagnostic bundle (ZIP with diag.json, lg-cfg, git metadata).
     * 
     * Returns pair of (report, bundlePath).
     * Bundle path is extracted from stderr (even on success).
     * 
     * @return Pair of diagnostic report and bundle path (if available), or null if bundle build failed (user already notified)
     */
    suspend fun buildBundle(): Pair<DiagReportSchema, String?>? {
        return withContext(Dispatchers.IO) {
            LOG.debug("Building diagnostic bundle")
            
            val result = cliExecutor.execute(
                args = listOf("diag", "--bundle"),
                timeoutMs = 60_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    val report = try {
                        json.decodeFromString<DiagReportSchema>(result.data)
                    } catch (e: Exception) {
                        LOG.error("Failed to parse diagnostics JSON from bundle", e)
                        return@withContext null
                    }
                    
                    // Извлекаем bundle path из stderr (даже при успехе CLI пишет туда путь)
                    val bundlePath = extractBundlePath(result.stderr)
                    
                    Pair(report, bundlePath)
                }
                
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Bundle Build", result)
                    null
                }
                
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Bundle Build", result.timeoutMs)
                    null
                }
                
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Bundle Build")
                    null
                }
            }
        }
    }
    
    /**
     * Extracts bundle path from stderr using regex.
     * 
     * Matches pattern: "Diagnostic bundle written to: <path>"
     */
    private fun extractBundlePath(stderr: String): String? {
        val regex = Regex("""Diagnostic bundle written to:\s*(.+)\s*$""", RegexOption.MULTILINE)
        val match = regex.find(stderr)
        return match?.groupValues?.getOrNull(1)?.trim()
    }
    
    companion object {
        private val LOG = logger<LgDiagnosticsService>()
    }
}
