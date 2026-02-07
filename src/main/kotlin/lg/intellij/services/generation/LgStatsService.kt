package lg.intellij.services.generation

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.handleWith
import lg.intellij.models.ReportSchema
import lg.intellij.statepce.PCEStateStore

/**
 * Service for fetching statistics via CLI 'lg report' command.
 */
@Service(Service.Level.PROJECT)
class LgStatsService(private val project: Project) {

    private val cliExecutor: CliExecutor
        get() = project.service()

    private val store: PCEStateStore
        get() = PCEStateStore.getInstance(project)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true // Coerce invalid values to defaults
    }

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

            // For sections, find section info and pass it for filtering
            val sectionInfo = if (!isContext) {
                val sectionName = target.removePrefix("sec:")
                store.getBusinessState().configuration.sections.find { it.name == sectionName }
            } else null

            val params = CliArgsBuilder.buildCliParams(
                store,
                BuildParamsOptions(includeProvider = isContext, sectionInfo = sectionInfo)
            )
            val (args, stdinData) = CliArgsBuilder.buildCliArgs("report", target, params)

            LOG.debug("Fetching stats for '$target' with args: $args")

            val result = cliExecutor.execute(
                args = args,
                stdinData = stdinData,
                timeoutMs = 120_000
            )

            result.handleWith(
                project = project,
                operationName = "Stats Collection",
                logger = LOG
            ) { success ->
                // Parse JSON manually to replace problematic 'meta' field with empty map
                val jsonElement = json.parseToJsonElement(success.data)
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
            }
        }
    }

    companion object {
        private val LOG = logger<LgStatsService>()
    }
}
