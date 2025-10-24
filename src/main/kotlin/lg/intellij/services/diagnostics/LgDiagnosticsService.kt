package lg.intellij.services.diagnostics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.handleWith
import lg.intellij.models.DiagReportSchema

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
            
            result.handleWith(
                project = project,
                operationName = "Diagnostics",
                logger = LOG
            ) { success ->
                json.decodeFromString<DiagReportSchema>(success.data)
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
            
            result.handleWith(
                project = project,
                operationName = "Cache Rebuild",
                logger = LOG
            ) { success ->
                json.decodeFromString<DiagReportSchema>(success.data)
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
            
            result.handleWith(
                project = project,
                operationName = "Bundle Build",
                logger = LOG
            ) { success ->
                val report = json.decodeFromString<DiagReportSchema>(success.data)
                // Извлекаем bundle path из stderr (даже при успехе CLI пишет туда путь)
                val bundlePath = extractBundlePath(success.stderr)
                Pair(report, bundlePath)
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
