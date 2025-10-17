package lg.intellij.services.catalog

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.models.EncodersListSchema
import lg.intellij.cli.models.TokenizerLibsListSchema
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * Service for loading and caching tokenizer libraries and encoders.
 * 
 * Application-level service (shared across all projects).
 * Encoders are cached per library with 1-hour TTL.
 */
@Service(Service.Level.APP)
class TokenizerCatalogService(
    private val scope: CoroutineScope
) {
    
    // JSON parser
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Reactive state for libraries
    private val _libraries = MutableStateFlow<List<String>>(emptyList())
    val libraries: StateFlow<List<String>> = _libraries.asStateFlow()
    
    // Cache for encoders with TTL
    private data class EncodersCacheEntry(
        val encoders: List<String>,
        val timestamp: TimeSource.Monotonic.ValueTimeMark
    )
    
    private val encodersCache = ConcurrentHashMap<String, EncodersCacheEntry>()
    private val cacheTTL = 1.hours
    
    /**
     * Loads tokenizer libraries list.
     * Uses any available project's CliExecutor.
     */
    suspend fun loadLibraries(project: Project) {
        try {
            withContext(Dispatchers.IO) {
                val cliExecutor = project.service<CliExecutor>()
                
                val result = cliExecutor.execute(
                    args = listOf("list", "tokenizer-libs"),
                    timeoutMs = 20_000
                )
                
                val stdout = result.getOrThrow()
                val schema = json.decodeFromString<TokenizerLibsListSchema>(stdout)
                
                _libraries.value = schema.tokenizerLibs
                LOG.debug("Loaded ${schema.tokenizerLibs.size} tokenizer libraries")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load tokenizer libraries: ${e.message}", e)
            _libraries.value = emptyList()
        }
    }
    
    /**
     * Gets encoders for the specified library.
     * Returns cached value if available and fresh (< 1 hour old).
     * 
     * @param lib Library name (e.g., "tiktoken", "tokenizers")
     * @param project Project for CLI access
     * @return List of encoder names (may be empty on error)
     */
    suspend fun getEncoders(lib: String, project: Project): List<String> {
        // Check cache first
        val cached = encodersCache[lib]
        if (cached != null && !isCacheExpired(cached)) {
            LOG.debug("Using cached encoders for $lib")
            return cached.encoders
        }
        
        // Load fresh data
        return loadEncoders(lib, project)
    }
    
    /**
     * Loads encoders from CLI and updates cache.
     */
    private suspend fun loadEncoders(lib: String, project: Project): List<String> {
        return try {
            withContext(Dispatchers.IO) {
                val cliExecutor = project.service<CliExecutor>()
                
                val result = cliExecutor.execute(
                    args = listOf("list", "encoders", "--lib", lib),
                    timeoutMs = 20_000
                )
                
                val stdout = result.getOrThrow()
                val schema = json.decodeFromString<EncodersListSchema>(stdout)
                
                // Update cache
                encodersCache[lib] = EncodersCacheEntry(
                    encoders = schema.encoders,
                    timestamp = TimeSource.Monotonic.markNow()
                )
                
                LOG.debug("Loaded ${schema.encoders.size} encoders for $lib")
                schema.encoders
            }
        } catch (e: Exception) {
            LOG.warn("Failed to load encoders for $lib: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Invalidates cache for the specified library.
     */
    fun invalidateEncoders(lib: String) {
        encodersCache.remove(lib)
        LOG.debug("Invalidated encoders cache for $lib")
    }
    
    /**
     * Invalidates all cached encoders.
     */
    fun invalidateAll() {
        encodersCache.clear()
        LOG.debug("Invalidated all encoders cache")
    }
    
    /**
     * Checks if cache entry is expired (> 1 hour old).
     */
    private fun isCacheExpired(entry: EncodersCacheEntry): Boolean {
        return entry.timestamp.elapsedNow() > cacheTTL
    }
    
    companion object {
        private val LOG = logger<TokenizerCatalogService>()
        
        /**
         * Gets the singleton service instance.
         */
        fun getInstance(): TokenizerCatalogService = service()
    }
}

