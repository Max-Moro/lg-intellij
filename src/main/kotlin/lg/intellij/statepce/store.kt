package lg.intellij.statepce

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.SectionInfo
import lg.intellij.models.ShellType
import lg.intellij.models.TagSetsListSchema
import lg.intellij.stateengine.StateListener
import lg.intellij.stateengine.StateStore
import lg.intellij.statepce.domains.DEFAULT_CTX_LIMIT
import lg.intellij.statepce.domains.DEFAULT_ENCODER
import lg.intellij.statepce.domains.DEFAULT_TOKENIZER_LIB

// ============================================
// LG Rule Result
// ============================================

/**
 * Extended rule result for LG with three mutation types.
 * Inherits asyncOps and followUp from base RuleResult.
 *
 * Each mutation type targets a different part of PCEState:
 * - mutations -> PersistentState (saved between sessions)
 * - configMutations -> ConfigurationState (loaded from CLI)
 * - envMutations -> EnvironmentState (detected at runtime)
 */
interface LGRuleResult : RuleResult {
    /** Mutations to persistent state (saved to workspace.xml) */
    val mutations: Map<String, Any?>?

    /** Mutations to configuration state (in-memory, from CLI) */
    val configMutations: Map<String, Any?>?

    /** Mutations to environment state (in-memory, runtime) */
    val envMutations: Map<String, Any?>?
}

/**
 * Builder function for creating LGRuleResult instances.
 *
 * Example usage:
 * ```
 * lgResult(
 *     mutations = mapOf("providerId" to "claude-cli"),
 *     asyncOps = listOf(loadCatalogsOp)
 * )
 * ```
 */
fun lgResult(
    mutations: Map<String, Any?>? = null,
    configMutations: Map<String, Any?>? = null,
    envMutations: Map<String, Any?>? = null,
    asyncOps: List<AsyncOperation>? = null,
    followUp: List<BaseCommand>? = null
): LGRuleResult = object : LGRuleResult {
    override val mutations = mutations
    override val configMutations = configMutations
    override val envMutations = envMutations
    override val asyncOps = asyncOps
    override val followUp = followUp
}

// ============================================
// PCE State Store
// ============================================

/**
 * PCE State Store — single source of truth for Control Panel state.
 *
 * Implements StateStore for use with StateCoordinator.
 * Uses SimplePersistentStateComponent for automatic persistence of PersistentState.
 *
 * State is divided into three parts:
 * - **Persistent** (P): Saved between IDE sessions via platform persistence
 * - **Configuration** (C): Loaded from CLI, stored in-memory
 * - **Environment** (E): Detected at runtime, stored in-memory
 *
 * Storage: workspace file (.idea/workspace.xml) - not committed to VCS.
 * Persistence: automatic via SimplePersistentStateComponent.
 *
 * @see PCEState
 * @see LGRuleResult
 */
@Service(Service.Level.PROJECT)
@State(
    name = "PCEState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    category = SettingsCategory.TOOLS
)
class PCEStateStore(
    @Suppress("unused") private val project: Project
) : SimplePersistentStateComponent<PCEStateStore.PersistentStateData>(PersistentStateData()),
    StateStore<PCEState, LGRuleResult> {

    /**
     * BaseState class for persistence (automatic save via platform).
     *
     * Uses property delegates for automatic change tracking.
     * Platform calls getState()/loadState() automatically on save/load.
     */
    class PersistentStateData : BaseState() {
        /** Selected AI provider ID */
        var providerId by string("")

        /** Selected template (context) name */
        var template by string("")

        /** Selected section name */
        var section by string("")

        /**
         * Modes by context and provider.
         * Structure: [contextName][providerId][modeSetId] = modeId
         */
        var modesByContextProvider by map<String, MutableMap<String, MutableMap<String, String>>>()

        /**
         * Tags by context.
         * Structure: [contextName][tagSetId] = Set<tagId>
         */
        var tagsByContext by map<String, MutableMap<String, MutableSet<String>>>()

        /** Tokenizer library (tiktoken, tokenizers, sentencepiece) */
        var tokenizerLib by string(DEFAULT_TOKENIZER_LIB)

        /** Encoder name (o200k_base, cl100k_base, etc.) */
        var encoder by string(DEFAULT_ENCODER)

        /** Context limit in tokens */
        var ctxLimit by property(DEFAULT_CTX_LIMIT)

        /** CLI scope - relative path from workspace root (empty = root) */
        var cliScope by string("")

        /** Terminal shell type */
        var cliShell by enum(ShellType.getDefault())

        /** Target branch for review mode */
        var targetBranch by string("")

        /** Task description text */
        var taskText by string("")

        /**
         * Dynamic provider settings.
         * Structure: [providerId][settingKey] = settingValue
         *
         * Allows providers to store their specific settings
         * (e.g., Claude model, Codex reasoning effort) without
         * requiring changes to this class.
         */
        var providerSettings by map<String, MutableMap<String, String>>()
    }

    // In-memory state (not persisted)
    private var configuration = createDefaultConfigurationState()
    private var environment = createDefaultEnvironmentState()

    // Listeners for state changes
    private val listeners = mutableSetOf<StateListener<PCEState>>()

    companion object {
        private val LOG = logger<PCEStateStore>()

        /**
         * Returns the project-scoped instance of PCEStateStore.
         */
        fun getInstance(project: Project): PCEStateStore = project.service()
    }

    // ============================================
    // StateStore Interface Implementation
    // ============================================

    /**
     * Get current combined business state snapshot.
     *
     * Combines persistent data (from BaseState) with in-memory
     * configuration and environment state.
     *
     * Named getBusinessState() to avoid conflict with
     * SimplePersistentStateComponent.getState().
     */
    override fun getBusinessState(): PCEState {
        return PCEState(
            persistent = toPersistentState(),
            configuration = configuration,
            environment = environment
        )
    }

    /**
     * Apply mutations from rule result.
     *
     * Dispatches mutations to appropriate handlers based on type:
     * - mutations -> updatePersistent() (saved to disk)
     * - configMutations -> updateConfiguration() (in-memory)
     * - envMutations -> updateEnvironment() (in-memory)
     */
    override suspend fun applyMutations(result: LGRuleResult) {
        result.mutations?.takeIf { it.isNotEmpty() }?.let {
            updatePersistent(it)
        }

        result.configMutations?.takeIf { it.isNotEmpty() }?.let {
            updateConfiguration(it)
        }

        result.envMutations?.takeIf { it.isNotEmpty() }?.let {
            updateEnvironment(it)
        }
    }

    /**
     * Emit state change to all subscribers.
     * Call after mutations are applied.
     */
    override fun emit() {
        val currentState = getBusinessState()

        for (listener in listeners) {
            try {
                listener(currentState)
            } catch (e: Exception) {
                LOG.debug("Listener error: $e")
            }
        }
    }

    /**
     * Subscribe to state changes.
     *
     * @param listener Callback invoked when state changes
     * @return Unsubscribe function
     */
    override fun subscribe(listener: StateListener<PCEState>): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    // ============================================
    // Conversion Methods
    // ============================================

    /**
     * Converts BaseState data to PersistentState data class.
     */
    private fun toPersistentState(): PersistentState {
        return PersistentState(
            providerId = state.providerId ?: "",
            template = state.template ?: "",
            section = state.section ?: "",
            modesByContextProvider = state.modesByContextProvider.toMutableMap(),
            tagsByContext = state.tagsByContext.toMutableMap(),
            tokenizerLib = state.tokenizerLib ?: DEFAULT_TOKENIZER_LIB,
            encoder = state.encoder ?: DEFAULT_ENCODER,
            ctxLimit = state.ctxLimit,
            cliScope = state.cliScope ?: "",
            cliShell = state.cliShell,
            targetBranch = state.targetBranch ?: "",
            taskText = state.taskText ?: "",
            providerSettings = convertProviderSettings(state.providerSettings)
        )
    }

    /**
     * Converts string-based provider settings to Any-based.
     */
    private fun convertProviderSettings(
        source: Map<String, MutableMap<String, String>>
    ): MutableMap<String, MutableMap<String, Any?>> {
        return source.mapValues { (_, settings) ->
            settings.mapValues { (_, v) -> v as Any? }.toMutableMap()
        }.toMutableMap()
    }

    // ============================================
    // Mutation Methods
    // ============================================

    /**
     * Updates persistent state (automatically saved by platform).
     *
     * Applies partial updates to the BaseState properties.
     * Platform detects changes via property delegates and saves automatically.
     */
    private fun updatePersistent(mutations: Map<String, Any?>) {
        for ((key, value) in mutations) {
            when (key) {
                "providerId" -> state.providerId = value as? String ?: ""
                "template" -> state.template = value as? String ?: ""
                "section" -> state.section = value as? String ?: ""
                "tokenizerLib" -> state.tokenizerLib = value as? String ?: DEFAULT_TOKENIZER_LIB
                "encoder" -> state.encoder = value as? String ?: DEFAULT_ENCODER
                "ctxLimit" -> state.ctxLimit = value as? Int ?: DEFAULT_CTX_LIMIT
                "cliScope" -> state.cliScope = value as? String ?: ""
                "cliShell" -> state.cliShell = value as? ShellType ?: ShellType.getDefault()
                "targetBranch" -> state.targetBranch = value as? String ?: ""
                "taskText" -> state.taskText = value as? String ?: ""
                "modesByContextProvider" -> {
                    @Suppress("UNCHECKED_CAST")
                    state.modesByContextProvider = (value as? Map<String, MutableMap<String, MutableMap<String, String>>>)
                        ?.toMutableMap() ?: mutableMapOf()
                }
                "tagsByContext" -> {
                    @Suppress("UNCHECKED_CAST")
                    state.tagsByContext = (value as? Map<String, MutableMap<String, MutableSet<String>>>)
                        ?.toMutableMap() ?: mutableMapOf()
                }
                "providerSettings" -> {
                    @Suppress("UNCHECKED_CAST")
                    val anyMap = value as? Map<String, MutableMap<String, Any?>> ?: emptyMap()
                    // Convert Any? to String for storage
                    state.providerSettings = anyMap.mapValues { (_, settings) ->
                        settings.mapValues { (_, v) -> v?.toString() ?: "" }.toMutableMap()
                    }.toMutableMap()
                }
            }
        }

        LOG.debug("Persistent state updated: ${mutations.keys.joinToString()}")
    }

    /**
     * Updates configuration state (in-memory only).
     *
     * Creates a new ConfigurationState instance with updated fields.
     */
    private fun updateConfiguration(mutations: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        configuration = configuration.copy(
            contexts = mutations["contexts"] as? List<String> ?: configuration.contexts,
            sections = mutations["sections"] as? List<SectionInfo> ?: configuration.sections,
            modeSets = mutations["modeSets"] as? ModeSetsListSchema ?: configuration.modeSets,
            tagSets = mutations["tagSets"] as? TagSetsListSchema ?: configuration.tagSets,
            tokenizerLibs = mutations["tokenizerLibs"] as? List<String> ?: configuration.tokenizerLibs,
            encoders = mutations["encoders"] as? List<EncoderEntry> ?: configuration.encoders
        )

        LOG.debug("Configuration state updated: ${mutations.keys.joinToString()}")
    }

    /**
     * Updates environment state (in-memory only).
     *
     * Creates a new EnvironmentState instance with updated fields.
     */
    private fun updateEnvironment(mutations: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        environment = environment.copy(
            providers = mutations["providers"] as? List<ProviderInfo> ?: environment.providers,
            branches = mutations["branches"] as? List<String> ?: environment.branches
        )

        LOG.debug("Environment state updated: ${mutations.keys.joinToString()}")
    }

    // ============================================
    // Query Methods (Helpers)
    // ============================================

    /**
     * Gets modes for specific context and provider.
     * Returns empty map if no modes saved for this combination.
     *
     * @param ctx Context name (template)
     * @param provider Provider ID
     * @return Map of modeSetId to selected modeId
     */
    fun getCurrentModes(ctx: String, provider: String): Map<String, String> {
        return state.modesByContextProvider[ctx]?.get(provider) ?: emptyMap()
    }

    /**
     * Gets tags for specific context.
     * Returns empty map if no tags saved for this context.
     *
     * @param ctx Context name (template)
     * @return Map of tagSetId to selected tag IDs
     */
    fun getCurrentTags(ctx: String): Map<String, Set<String>> {
        return state.tagsByContext[ctx]?.mapValues { it.value.toSet() } ?: emptyMap()
    }

    /**
     * Gets the 'runs' string from the integration mode-set for current selection.
     *
     * Integration mode-sets define how to send content to different AI providers.
     * The 'runs' field contains provider-specific command templates.
     *
     * @param ctx Current context name
     * @param provider Current provider ID
     * @return runs string or null if not found
     */
    fun getIntegrationModeRuns(ctx: String, provider: String): String? {
        val modeSets = configuration.modeSets
        val integrationSet = modeSets.modeSets.find { it.integration == true }
            ?: return null

        val currentModes = getCurrentModes(ctx, provider)
        val selectedModeId = currentModes[integrationSet.id]

        if (selectedModeId == null) {
            // No mode selected, use first mode as default
            val firstMode = integrationSet.modes.firstOrNull()
            return firstMode?.runs?.get(provider)
        }

        val mode = integrationSet.modes.find { it.id == selectedModeId }
        return mode?.runs?.get(provider)
    }

    /**
     * Gets provider-specific setting value.
     *
     * @param providerId Provider ID
     * @param key Setting key
     * @return Setting value or null if not set
     */
    fun getProviderSetting(providerId: String, key: String): String? {
        return state.providerSettings[providerId]?.get(key)
    }

    // ============================================
    // Lifecycle Methods
    // ============================================

}
