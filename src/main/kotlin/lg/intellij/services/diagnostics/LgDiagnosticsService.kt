package lg.intellij.services.diagnostics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliClient
import lg.intellij.cli.CliException
import lg.intellij.models.DiagReportSchema
import lg.intellij.services.LgErrorReportingService

/**
 * Service for running diagnostics and cache management.
 */
@Service(Service.Level.PROJECT)
class LgDiagnosticsService(private val project: Project) {

    private val cliClient: CliClient
        get() = project.service()

    /**
     * Runs diagnostics without cache rebuild.
     *
     * @return Diagnostic report or null if diagnostics failed (user already notified)
     */
    suspend fun runDiagnostics(): DiagReportSchema? {
        return withContext(Dispatchers.IO) {
            LOG.debug("Running diagnostics")
            try {
                cliClient.diag()
            } catch (e: CliException) {
                LgErrorReportingService.getInstance().reportCliException(project, "Diagnostics", e)
                null
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
            try {
                cliClient.diagRebuildCache()
            } catch (e: CliException) {
                LgErrorReportingService.getInstance().reportCliException(project, "Cache Rebuild", e)
                null
            }
        }
    }

    /**
     * Builds diagnostic bundle (ZIP with diag.json, lg-cfg, git metadata).
     *
     * @return Pair of diagnostic report and bundle path (if available), or null if failed (user already notified)
     */
    suspend fun buildBundle(): Pair<DiagReportSchema, String?>? {
        return withContext(Dispatchers.IO) {
            LOG.debug("Building diagnostic bundle")
            try {
                cliClient.diagBundle()
            } catch (e: CliException) {
                LgErrorReportingService.getInstance().reportCliException(project, "Bundle Build", e)
                null
            }
        }
    }

    companion object {
        private val LOG = logger<LgDiagnosticsService>()
    }
}
