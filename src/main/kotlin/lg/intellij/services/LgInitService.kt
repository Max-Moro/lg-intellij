package lg.intellij.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import lg.intellij.cli.CliExecutionException
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.CliNotFoundException
import lg.intellij.cli.CliTimeoutException
import lg.intellij.models.InitResult
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Service for managing lg-cfg initialization and project state.
 */
@Service(Service.Level.PROJECT)
class LgInitService(private val project: Project) {

    private val executor = project.service<CliExecutor>()

    /**
     * Checks if project is initialized with lg-cfg configuration.
     */
    fun isInitialized(): Boolean {
        val basePath = project.basePath ?: return false
        val lgCfgPath = Path.of(basePath, "lg-cfg")
        return lgCfgPath.exists()
    }

    /**
     * Lists available presets from CLI.
     */
    suspend fun listPresets(): List<String> {
        return withContext(Dispatchers.Default) {
            try {
                val output = executor.execute(
                    args = listOf("init", "--list-presets"),
                    timeoutMs = 20_000
                )
                parsePresets(output.stdout)
            } catch (e: Exception) {
                LOG.warn("Failed to list presets: ${e.message}")
                listOf("basic")
            }
        }
    }

    /**
     * Initializes lg-cfg with selected preset.
     */
    suspend fun initWithPreset(preset: String, force: Boolean = false): InitResult {
        return withContext(Dispatchers.Default) {
            try {
                val args = buildList {
                    add("init")
                    add("--preset")
                    add(preset)
                    if (force) {
                        add("--force")
                    }
                }

                val output = executor.execute(
                    args = args,
                    timeoutMs = 120_000
                )
                parseInitResult(output.stdout)
            } catch (e: CliExecutionException) {
                // init command returns JSON in stdout even on non-zero exit (e.g., conflict report)
                parseInitResult(e.stdout)
            } catch (e: CliTimeoutException) {
                InitResult(
                    ok = false,
                    preset = preset,
                    error = "Initialization timeout after ${e.timeoutMs / 1000} seconds"
                )
            } catch (e: CliNotFoundException) {
                InitResult(
                    ok = false,
                    preset = preset,
                    error = e.message ?: "CLI not found"
                )
            } catch (e: Exception) {
                LOG.error("Failed to initialize with preset: $preset", e)
                InitResult(
                    ok = false,
                    preset = preset,
                    error = "Unexpected error: ${e.message}"
                )
            }
        }
    }

    private fun parsePresets(json: String): List<String> {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject
            val presetsArray = root["presets"]?.jsonArray ?: return emptyList()
            presetsArray.mapNotNull { it.jsonPrimitive.content }
        } catch (e: Exception) {
            LOG.error("Failed to parse presets JSON", e)
            emptyList()
        }
    }

    private fun parseInitResult(json: String): InitResult {
        return try {
            val root = Json.parseToJsonElement(json).jsonObject

            val ok = root["ok"]?.jsonPrimitive?.content?.toBoolean() ?: false
            val preset = root["preset"]?.jsonPrimitive?.content ?: ""
            val target = root["target"]?.jsonPrimitive?.content
            val message = root["message"]?.jsonPrimitive?.content
            val error = root["error"]?.jsonPrimitive?.content

            val created = root["created"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.content
            } ?: emptyList()

            val conflicts = root["conflicts"]?.jsonArray?.mapNotNull {
                it.jsonPrimitive.content
            } ?: emptyList()

            InitResult(
                ok = ok,
                preset = preset,
                target = target,
                created = created,
                conflicts = conflicts,
                error = error,
                message = message
            )
        } catch (e: Exception) {
            LOG.error("Failed to parse init result JSON", e)
            InitResult(
                ok = false,
                error = "Failed to parse response: ${e.message}"
            )
        }
    }

    companion object {
        private val LOG = logger<LgInitService>()

        fun getInstance(project: Project): LgInitService = project.service()
    }
}
