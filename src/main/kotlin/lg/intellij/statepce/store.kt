package lg.intellij.statepce

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import lg.intellij.models.ShellType
import lg.intellij.stateengine.AsyncOperation
import lg.intellij.stateengine.BaseCommand
import lg.intellij.stateengine.RuleResult
import lg.intellij.stateengine.StateListener
import lg.intellij.stateengine.StateStore
import lg.intellij.statepce.domains.DEFAULT_CTX_LIMIT
import lg.intellij.statepce.domains.DEFAULT_ENCODER
import lg.intellij.statepce.domains.DEFAULT_TOKENIZER_LIB

// ============================================
// LG Rule Result
// ============================================

/**
 * Extended rule result for LG with typed mutation lambdas.
 *
 * Each lambda receives current state and returns updated state.
 * Uses Kotlin's .copy() for type-safe immutable updates.
 *
 * Each mutation type targets a different part of PCEState:
 * - persistent -> PersistentState (saved between sessions)
 * - config -> ConfigurationState (loaded from CLI)
 * - env -> EnvironmentState (detected at runtime)
 */
interface LGRuleResult : RuleResult {
    /** Mutation lambda for persistent state (saved to workspace.xml) */
    val persistent: ((PersistentState) -> PersistentState)?

    /** Mutation lambda for configuration state (in-memory, from CLI) */
    val config: ((ConfigurationState) -> ConfigurationState)?

    /** Mutation lambda for environment state (in-memory, runtime) */
    val env: ((EnvironmentState) -> EnvironmentState)?
}

/**
 * Builder function for creating LGRuleResult instances.
 *
 * Example usage:
 * ```
 * lgResult(
 *     persistent = { s -> s.copy(providerId = "claude-cli") },
 *     asyncOps = listOf(loadCatalogsOp)
 * )
 * ```
 */
fun lgResult(
    persistent: ((PersistentState) -> PersistentState)? = null,
    config: ((ConfigurationState) -> ConfigurationState)? = null,
    env: ((EnvironmentState) -> EnvironmentState)? = null,
    asyncOps: List<AsyncOperation>? = null,
    followUp: List<BaseCommand>? = null
): LGRuleResult = object : LGRuleResult {
    override val persistent = persistent
    override val config = config
    override val env = env
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
     * Apply typed mutation lambdas from rule result.
     *
     * Each lambda receives current state snapshot and returns updated state.
     * Persistent mutations are synced back to BaseState for platform persistence.
     */
    override suspend fun applyMutations(result: LGRuleResult) {
        result.persistent?.let { mutator ->
            val current = toPersistentState()
            val updated = mutator(current)
            syncToPersistentData(updated)
        }

        result.config?.let { mutator ->
            configuration = mutator(configuration)
        }

        result.env?.let { mutator ->
            environment = mutator(environment)
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
     * Converts BaseState data to immutable PersistentState snapshot.
     *
     * Uses Map covariance (MutableMap is-a Map) for nested map types.
     * providerSettings requires explicit conversion (String → Any?).
     */
    private fun toPersistentState(): PersistentState {
        return PersistentState(
            providerId = state.providerId ?: "",
            template = state.template ?: "",
            section = state.section ?: "",
            modesByContextProvider = state.modesByContextProvider.toMap(),
            tagsByContext = state.tagsByContext.toMap(),
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
     * Converts string-based provider settings to Any-based (immutable).
     */
    private fun convertProviderSettings(
        source: Map<String, MutableMap<String, String>>
    ): Map<String, Map<String, Any?>> {
        return source.mapValues { (_, settings) ->
            settings.mapValues { (_, v) -> v as Any? }
        }
    }

    // ============================================
    // Mutation Methods
    // ============================================

    /**
     * Syncs immutable PersistentState back to mutable BaseState for platform persistence.
     *
     * This is the ONLY place where PersistentState → PersistentStateData conversion happens.
     * BaseState property delegates detect actual changes via equals().
     */
    private fun syncToPersistentData(updated: PersistentState) {
        state.providerId = updated.providerId
        state.template = updated.template
        state.section = updated.section
        state.tokenizerLib = updated.tokenizerLib
        state.encoder = updated.encoder
        state.ctxLimit = updated.ctxLimit
        state.cliScope = updated.cliScope
        state.cliShell = updated.cliShell
        state.targetBranch = updated.targetBranch
        state.taskText = updated.taskText

        // Convert immutable maps to mutable for BaseState storage
        @Suppress("UNCHECKED_CAST")
        state.modesByContextProvider = updated.modesByContextProvider
            .mapValues { (_, providerMap) ->
                providerMap.mapValues { (_, modes) ->
                    modes.toMutableMap()
                }.toMutableMap()
            }.toMutableMap()

        @Suppress("UNCHECKED_CAST")
        state.tagsByContext = updated.tagsByContext
            .mapValues { (_, tagMap) ->
                tagMap.mapValues { (_, tags) ->
                    tags.toMutableSet()
                }.toMutableMap()
            }.toMutableMap()

        // Convert Any? values to String for storage
        state.providerSettings = updated.providerSettings
            .mapValues { (_, settings) ->
                settings.mapValues { (_, v) -> v?.toString() ?: "" }.toMutableMap()
            }.toMutableMap()

        LOG.debug("Persistent state synced")
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

    /**
     * Clears all state to defaults.
     * Resets persistent state (saved to disk) and in-memory state.
     * Emits change so UI updates immediately.
     */
    fun clearAll() {
        loadState(PersistentStateData())
        configuration = createDefaultConfigurationState()
        environment = createDefaultEnvironmentState()
        emit()
        LOG.debug("All state cleared to defaults")
    }
}
