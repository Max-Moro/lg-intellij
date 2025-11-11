package lg.intellij.services.catalog

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import lg.intellij.cli.CliExecutor
import lg.intellij.cli.handleWith
import lg.intellij.cli.handleWithFallback
import lg.intellij.models.ContextsListSchema
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionsListSchema
import lg.intellij.models.TagSetsListSchema
import lg.intellij.services.state.LgPanelStateService

/**
 * Service for loading and caching catalog data from CLI.
 * 
 * Provides reactive access to sections, contexts, mode-sets, and tag-sets
 * via StateFlow. Automatically loads data on first access.
 * 
 * Lifecycle: Project-level (one instance per open project)
 */
@Service(Service.Level.PROJECT)
class LgCatalogService(private val project: Project) {
    
    private val cliExecutor: CliExecutor
        get() = project.service()
    
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
     * Loads all catalog data with parallel CLI execution.
     *
     * Independent CLI requests (sections, contexts, mode-sets, tag-sets, branches)
     * are executed in parallel for better responsiveness.
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
                // Parallel loading of all independent catalog data
                coroutineScope {
                    launch { loadSections() }
                    launch { loadContexts() }
                    launch { loadModeSets() }
                    launch { loadTagSets() }
                    launch { loadBranches() }
                }

                // Actualize panel state after loading catalogs
                actualizeState()
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
        val result = cliExecutor.execute(
            args = listOf("list", "sections"),
            timeoutMs = 30_000
        )

        val sections = result.handleWithFallback(
            project = project,
            operationName = "Loading sections",
            logger = LOG,
            fallback = emptyList()
        ) { success ->
            val schema = json.decodeFromString<SectionsListSchema>(success.data)
            LOG.debug("Loaded ${schema.sections.size} sections")
            schema.sections
        }

        _sections.value = sections
    }
    
    /**
     * Loads contexts list from CLI.
     */
    private suspend fun loadContexts() {
        val result = cliExecutor.execute(
            args = listOf("list", "contexts"),
            timeoutMs = 30_000
        )

        val contexts = result.handleWithFallback(
            project = project,
            operationName = "Loading contexts",
            logger = LOG,
            fallback = emptyList()
        ) { success ->
            val schema = json.decodeFromString<ContextsListSchema>(success.data)
            LOG.debug("Loaded ${schema.contexts.size} contexts")
            schema.contexts
        }

        _contexts.value = contexts
    }
    
    /**
     * Loads mode-sets from CLI.
     */
    private suspend fun loadModeSets() {
        val result = cliExecutor.execute(
            args = listOf("list", "mode-sets"),
            timeoutMs = 30_000
        )

        val modeSets = result.handleWith(
            project = project,
            operationName = "Loading mode-sets",
            logger = LOG
        ) { success ->
            val schema = json.decodeFromString<ModeSetsListSchema>(success.data)
            LOG.debug("Loaded ${schema.modeSets.size} mode-sets")
            schema
        }

        _modeSets.value = modeSets
    }
    
    /**
     * Loads tag-sets from CLI.
     */
    private suspend fun loadTagSets() {
        val result = cliExecutor.execute(
            args = listOf("list", "tag-sets"),
            timeoutMs = 30_000
        )

        val tagSets = result.handleWith(
            project = project,
            operationName = "Loading tag-sets",
            logger = LOG
        ) { success ->
            val schema = json.decodeFromString<TagSetsListSchema>(success.data)
            LOG.debug("Loaded ${schema.tagSets.size} tag-sets")
            schema
        }

        _tagSets.value = tagSets
    }
    
    /**
     * Loads Git branches from Git service.
     */
    private suspend fun loadBranches() {
        val gitService = project.service<lg.intellij.services.git.LgGitService>()

        if (!gitService.isGitAvailable()) {
            LOG.debug("Git not available, skipping branches load")
            _branches.value = emptyList()
            return
        }

        val branches = gitService.getBranches()
        _branches.value = branches
        LOG.debug("Loaded ${branches.size} branches")
    }

    /**
     * Actualizes LgPanelStateService by removing obsolete modes and tags.
     */
    private suspend fun actualizeState() {
        withContext(Dispatchers.EDT) {
            val panelState = project.service<LgPanelStateService>()
            val modeSets = _modeSets.value
            val tagSets = _tagSets.value

            val changed = panelState.actualizeState(modeSets, tagSets)

            if (changed) {
                LOG.info("Panel state actualized: obsolete modes/tags removed")
            }
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

