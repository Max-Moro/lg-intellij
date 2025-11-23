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
import lg.intellij.cli.CliExecutor
import lg.intellij.models.CliResult
import lg.intellij.models.InitResult
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Service for managing lg-cfg initialization and project state.
 *
 * Provides:
 * - Project initialization check (lg-cfg/ existence)
 * - Listing available presets
 * - Initializing lg-cfg with selected preset
 * - Conflict resolution (force overwrite)
 *
 * Thread safety: All public methods are suspend functions executing on appropriate dispatchers.
 * Exception: isInitialized() is synchronous and lightweight (just file system check).
 */
@Service(Service.Level.PROJECT)
class LgInitService(private val project: Project) {

    private val executor = project.service<CliExecutor>()

    /**
     * Checks if project is initialized with lg-cfg configuration.
     *
     * @return true if lg-cfg/ directory exists, false otherwise
     */
    fun isInitialized(): Boolean {
        val basePath = project.basePath ?: return false
        val lgCfgPath = Path.of(basePath, "lg-cfg")
        return lgCfgPath.exists()
    }

    /**
     * Lists available presets from CLI.
     * 
     * Executes: `lg init --list-presets`
     * 
     * @return List of preset names, or empty list on failure
     */
    suspend fun listPresets(): List<String> {
        return withContext(Dispatchers.Default) {
            try {
                val result = executor.execute(
                    args = listOf("init", "--list-presets"),
                    timeoutMs = 20_000 // Short timeout for list operation
                )
                
                when (result) {
                    is CliResult.Success -> parsePresets(result.data)
                    else -> {
                        LOG.warn("Failed to list presets: $result")
                        listOf("basic") // Fallback to basic preset
                    }
                }
            } catch (e: Exception) {
                LOG.error("Failed to list presets", e)
                listOf("basic")
            }
        }
    }
    
    /**
     * Initializes lg-cfg with selected preset.
     * 
     * Executes: `lg init --preset <preset> [--force]`
     * 
     * @param preset Preset name (e.g., "basic")
     * @param force If true, overwrites existing files
     * @return Initialization result with success status and details
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
                
                val result = executor.execute(
                    args = args,
                    timeoutMs = 120_000 // Longer timeout for file operations
                )
                
                when (result) {
                    is CliResult.Success -> parseInitResult(result.data)
                    is CliResult.Failure -> parseInitResult(result.stdout)
                    is CliResult.Timeout -> InitResult(
                        ok = false,
                        preset = preset,
                        error = "Initialization timeout after ${result.timeoutMs / 1000} seconds"
                    )
                    is CliResult.NotFound -> InitResult(
                        ok = false,
                        preset = preset,
                        error = result.message
                    )
                    is CliResult.Unavailable -> InitResult(
                        ok = false,
                        preset = preset,
                        error = result.message
                    )
                }
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
    
    /**
     * Parses presets list from JSON response.
     * 
     * Expected format:
     * ```json
     * { "presets": ["basic", "python", "web"] }
     * ```
     */
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
    
    /**
     * Parses init result from JSON response.
     * 
     * Expected formats:
     * 
     * Success:
     * ```json
     * {
     *   "ok": true,
     *   "preset": "basic",
     *   "target": "/path/to/lg-cfg",
     *   "created": ["sections.yaml"],
     *   "conflicts": []
     * }
     * ```
     * 
     * Conflicts:
     * ```json
     * {
     *   "ok": false,
     *   "preset": "basic",
     *   "created": [],
     *   "conflicts": ["sections.yaml"],
     *   "message": "Use --force to overwrite existing files."
     * }
     * ```
     */
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

        /**
         * Gets the service instance for the project.
         */
        fun getInstance(project: Project): LgInitService = project.service()
    }
}

