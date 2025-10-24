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
import lg.intellij.models.CliResult
import lg.intellij.models.ContextsListSchema
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionsListSchema
import lg.intellij.models.TagSetsListSchema
import lg.intellij.services.LgErrorReportingService

/**
 * Service for loading and caching catalog data from CLI.
 * 
 * Provides reactive access to sections, contexts, mode-sets, and tag-sets
 * via StateFlow. Automatically loads data on first access.
 * 
 * Lifecycle: Project-level (one instance per open project)
 */
@Service(Service.Level.PROJECT)
class LgCatalogService(
    private val project: Project,
    private val scope: CoroutineScope
) {
    
    private val cliExecutor: CliExecutor
        get() = project.service()
    
    private val errorReporting: LgErrorReportingService
        get() = LgErrorReportingService.getInstance()
    
    // JSON parser with lenient mode for robustness
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Reactive state flows
    private val _sections = MutableStateFlow<List<String>>(emptyList())
    val sections: StateFlow<List<String>> = _sections.asStateFlow()
    
    private val _contexts = MutableStateFlow<List<String>>(emptyList())
    val contexts: StateFlow<List<String>> = _contexts.asStateFlow()
    
    private val _modeSets = MutableStateFlow<ModeSetsListSchema?>(null)
    val modeSets: StateFlow<ModeSetsListSchema?> = _modeSets.asStateFlow()
    
    private val _tagSets = MutableStateFlow<TagSetsListSchema?>(null)
    val tagSets: StateFlow<TagSetsListSchema?> = _tagSets.asStateFlow()
    
    private val _branches = MutableStateFlow<List<String>>(emptyList())
    val branches: StateFlow<List<String>> = _branches.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)

    /**
     * Loads all catalog data sequentially.
     * 
     * Sequential execution prevents CLI conflicts due to:
     * - File system locking during config migrations
     * - Heavy I/O operations
     * - Potential race conditions in CLI internals
     * 
     * Safe to call multiple times (subsequent calls while loading are ignored).
     */
    suspend fun loadAll() {
        // Prevent concurrent loads
        if (_isLoading.value) {
            LOG.debug("Load already in progress, skipping")
            return
        }
        
        _isLoading.value = true
        
        try {
            withContext(Dispatchers.IO) {
                loadSections()
                loadContexts()
                loadModeSets()
                loadTagSets()
                loadBranches()
            }
            LOG.info("Catalog data loaded successfully")
        } catch (e: Exception) {
            val errorMsg = "Failed to load catalog data: ${e.message}"
            LOG.error(errorMsg, e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Reloads all catalog data (clears cache first).
     */
    suspend fun reload() {
        LOG.info("Reloading catalog data")
        
        // Clear current data
        _sections.value = emptyList()
        _contexts.value = emptyList()
        _modeSets.value = null
        _tagSets.value = null
        _branches.value = emptyList()
        
        // Load fresh data
        loadAll()
    }
    
    /**
     * Loads only Git branches (public API for on-demand loading).
     */
    suspend fun loadBranchesOnly() {
        withContext(Dispatchers.IO) {
            loadBranches()
        }
    }
    
    /**
     * Loads sections list from CLI.
     */
    private suspend fun loadSections() {
        try {
            val result = cliExecutor.execute(
                args = listOf("list", "sections"),
                timeoutMs = 30_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    val schema = json.decodeFromString<SectionsListSchema>(result.data)
                    _sections.value = schema.sections
                    LOG.debug("Loaded ${schema.sections.size} sections")
                }
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Loading sections", result)
                    _sections.value = emptyList()
                }
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Loading sections", result.timeoutMs)
                    _sections.value = emptyList()
                }
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Loading sections")
                    _sections.value = emptyList()
                }
            }
            
        } catch (e: Exception) {
            LOG.error("Unexpected error loading sections", e)
            _sections.value = emptyList()
        }
    }
    
    /**
     * Loads contexts list from CLI.
     */
    private suspend fun loadContexts() {
        try {
            val result = cliExecutor.execute(
                args = listOf("list", "contexts"),
                timeoutMs = 30_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    val schema = json.decodeFromString<ContextsListSchema>(result.data)
                    _contexts.value = schema.contexts
                    LOG.debug("Loaded ${schema.contexts.size} contexts")
                }
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Loading contexts", result)
                    _contexts.value = emptyList()
                }
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Loading contexts", result.timeoutMs)
                    _contexts.value = emptyList()
                }
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Loading contexts")
                    _contexts.value = emptyList()
                }
            }
            
        } catch (e: Exception) {
            LOG.error("Unexpected error loading contexts", e)
            _contexts.value = emptyList()
        }
    }
    
    /**
     * Loads mode-sets from CLI.
     */
    private suspend fun loadModeSets() {
        try {
            val result = cliExecutor.execute(
                args = listOf("list", "mode-sets"),
                timeoutMs = 30_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    val schema = json.decodeFromString<ModeSetsListSchema>(result.data)
                    _modeSets.value = schema
                    LOG.debug("Loaded ${schema.modeSets.size} mode-sets")
                }
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Loading mode-sets", result)
                    _modeSets.value = null
                }
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Loading mode-sets", result.timeoutMs)
                    _modeSets.value = null
                }
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Loading mode-sets")
                    _modeSets.value = null
                }
            }
            
        } catch (e: Exception) {
            LOG.error("Unexpected error loading mode-sets", e)
            _modeSets.value = null
        }
    }
    
    /**
     * Loads tag-sets from CLI.
     */
    private suspend fun loadTagSets() {
        try {
            val result = cliExecutor.execute(
                args = listOf("list", "tag-sets"),
                timeoutMs = 30_000
            )
            
            when (result) {
                is CliResult.Success -> {
                    val schema = json.decodeFromString<TagSetsListSchema>(result.data)
                    _tagSets.value = schema
                    LOG.debug("Loaded ${schema.tagSets.size} tag-sets")
                }
                is CliResult.Failure -> {
                    errorReporting.reportCliFailure(project, "Loading tag-sets", result)
                    _tagSets.value = null
                }
                is CliResult.Timeout -> {
                    errorReporting.reportTimeout(project, "Loading tag-sets", result.timeoutMs)
                    _tagSets.value = null
                }
                is CliResult.NotFound -> {
                    errorReporting.reportCliNotFound(project, "Loading tag-sets")
                    _tagSets.value = null
                }
            }
            
        } catch (e: Exception) {
            LOG.error("Unexpected error loading tag-sets", e)
            _tagSets.value = null
        }
    }
    
    /**
     * Loads Git branches from Git service.
     */
    private suspend fun loadBranches() {
        try {
            val gitService = project.service<lg.intellij.services.git.LgGitService>()
            
            if (!gitService.isGitAvailable()) {
                LOG.debug("Git not available, skipping branches load")
                _branches.value = emptyList()
                return
            }
            
            val branches = gitService.getBranches()
            _branches.value = branches
            LOG.debug("Loaded ${branches.size} branches")
            
        } catch (e: NoClassDefFoundError) {
            // Git4Idea plugin not available
            LOG.debug("Git4Idea plugin not available")
            _branches.value = emptyList()
        } catch (e: Exception) {
            LOG.error("Unexpected error loading branches", e)
            _branches.value = emptyList()
        }
    }
    
    companion object {
        private val LOG = logger<LgCatalogService>()
        
        /**
         * Gets the service instance for the project.
         */
        fun getInstance(project: Project): LgCatalogService = project.service()
    }
}

