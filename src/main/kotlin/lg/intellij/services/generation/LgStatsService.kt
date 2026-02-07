package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lg.intellij.cli.CliClient
import lg.intellij.cli.CliException
import lg.intellij.models.ReportSchema
import lg.intellij.services.LgErrorReportingService
import lg.intellij.statepce.PCEStateStore

/**
 * Service for fetching statistics via CLI 'lg report' command.
 */
@Service(Service.Level.PROJECT)
class LgStatsService(private val project: Project) {

    private val cliClient: CliClient
        get() = project.service()

    private val store: PCEStateStore
        get() = PCEStateStore.getInstance(project)

    /**
     * Fetches statistics for the specified target.
     *
     * All parameters (tokenization, modes, tags, taskText) are taken from PCEStateStore.
     *
     * @param target Target specifier (e.g., "sec:all", "ctx:template-name")
     * @return Parsed statistics report or null if stats collection failed (user already notified)
     */
    suspend fun getStats(target: String): ReportSchema? {
        return withContext(Dispatchers.IO) {
            val isContext = target.startsWith("ctx:")

            val sectionInfo = if (!isContext) {
                val sectionName = target.removePrefix("sec:")
                store.getBusinessState().configuration.sections.find { it.name == sectionName }
            } else null

            val params = CliArgsBuilder.buildCliParams(
                store,
                BuildParamsOptions(includeProvider = isContext, sectionInfo = sectionInfo)
            )

            LOG.debug("Fetching stats for '$target'")

            try {
                val report = cliClient.report(target, params)
                LOG.info("Stats fetched successfully for '$target'")
                report
            } catch (e: CliException) {
                LgErrorReportingService.getInstance().reportCliException(project, "Stats Collection", e)
                null
            }
        }
    }

    companion object {
        private val LOG = logger<LgStatsService>()
    }
}
