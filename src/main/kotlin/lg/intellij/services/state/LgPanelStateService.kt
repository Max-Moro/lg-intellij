package lg.intellij.services.state

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lg.intellij.models.AiInteractionMode
import lg.intellij.models.ClaudeIntegrationMethod
import lg.intellij.models.ClaudeModel
import lg.intellij.models.CodexReasoningEffort
import lg.intellij.models.ModeSetsListSchema
import lg.intellij.models.ShellType
import lg.intellij.models.TagSetsListSchema

/**
 * Project-level service for storing Control Panel UI state.
 *
 * All fields have sensible defaults configured via property delegates.
 * Empty string fields ("") are semantically valid (e.g., cliScope = "" means workspace root).
 * Includes reactive taskText via StateFlow for synchronization across UI components.
 *
 * Storage: workspace file (.idea/workspace.xml) - not committed to VCS.
 * Persistence: automatic via SimplePersistentStateComponent.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "LgPanelState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    category = SettingsCategory.TOOLS
)
class LgPanelStateService : SimplePersistentStateComponent<LgPanelStateService.State>(State()) {
    
    private val _taskTextFlow = MutableStateFlow(state.taskText ?: "")
    
    /**
     * Reactive flow for task text changes.
     * UI components can subscribe to get notified when task text changes.
     */
    val taskTextFlow: StateFlow<String> = _taskTextFlow.asStateFlow()
    
    /**
     * Updates task text and notifies subscribers.
     */
    fun updateTaskText(newText: String) {
        state.taskText = newText
        _taskTextFlow.value = newText
    }

    /**
     * Overridden to sync Flow when state is loaded from persistence.
     *
     * Called by platform when restoring saved state (e.g., on IDE restart).
     */
    override fun loadState(state: State) {
        super.loadState(state)
        _taskTextFlow.value = state.taskText ?: ""
    }
    
    /**
     * Persistent state class.
     */
    class State : BaseState() {
        /** Selected template (context) name */
        var selectedTemplate by string("")

        /** Selected section name */
        var selectedSection by string("")

        /** Tokenizer library */
        var tokenizerLib by string("tiktoken")

        /** Encoder name */
        var encoder by string("cl100k_base")

        /** Context limit in tokens */
        var ctxLimit by property(128000)

        /** Task description text */
        var taskText by string("")

        /** Target branch for review mode */
        var targetBranch by string("")

        /** CLI scope - relative path from workspace root (empty = root) */
        var cliScope by string("")

        /** Terminal shell type */
        var cliShell by enum(ShellType.getDefault())

        /** Claude model selection */
        var claudeModel by enum(ClaudeModel.SONNET)

        /** Claude integration method */
        var claudeIntegrationMethod by enum(ClaudeIntegrationMethod.SESSION)

        /** Codex reasoning effort level */
        var codexReasoningEffort by enum(CodexReasoningEffort.MEDIUM)

        /** Selected AI provider (moved from Settings) */
        var providerId by string("")

        /** Context-dependent modes storage */
        /** Structure: modesByContextProvider[contextName][providerId][modeSetId] = modeId */
        var modesByContextProvider by map<String, MutableMap<String, MutableMap<String, String>>>()

        /** Context-dependent tags storage */
        /** Structure: tagsByContext[contextName][tagSetId] = Set<tagId> */
        var tagsByContext by map<String, MutableMap<String, MutableSet<String>>>()
    }

    /**
     * Gets modes for specific context and provider.
     * Returns empty map if no modes saved for this combination.
     */
    fun getCurrentModes(ctx: String, provider: String): Map<String, String> {
        return state.modesByContextProvider[ctx]?.get(provider) ?: emptyMap()
    }

    /**
     * Sets modes for specific context and provider.
     */
    fun setCurrentModes(ctx: String, provider: String, modes: Map<String, String>) {
        val contextModes = state.modesByContextProvider.getOrPut(ctx) { mutableMapOf() }
        contextModes[provider] = modes.toMutableMap()
    }

    /**
     * Gets tags for specific context.
     * Returns empty map if no tags saved for this context.
     */
    fun getCurrentTags(ctx: String): Map<String, Set<String>> {
        return state.tagsByContext[ctx]?.mapValues { it.value.toSet() } ?: emptyMap()
    }

    /**
     * Sets tags for specific context.
     */
    fun setCurrentTags(ctx: String, tags: Map<String, Set<String>>) {
        state.tagsByContext[ctx] = tags.mapValues { it.value.toMutableSet() }.toMutableMap()
    }

    /**
     * Gets the 'runs' string from the integration mode-set for current selection.
     *
     * @param ctx Current context name
     * @param provider Current provider ID
     * @param modeSets Mode sets from CLI
     * @return runs string or null if not found
     */
    fun getIntegrationModeRuns(
        ctx: String,
        provider: String,
        modeSets: ModeSetsListSchema?
    ): String? {
        if (modeSets == null) return null

        // Find integration mode-set
        val integrationSet = modeSets.modeSets.find { it.integration == true }
            ?: return null

        // Get selected mode for this mode-set
        val currentModes = getCurrentModes(ctx, provider)
        val selectedModeId = currentModes[integrationSet.id]

        if (selectedModeId == null) {
            // No mode selected, try first mode
            val firstMode = integrationSet.modes.firstOrNull()
            return firstMode?.runs?.get(provider)
        }

        // Find the mode and get runs for provider
        val mode = integrationSet.modes.find { it.id == selectedModeId }
        return mode?.runs?.get(provider)
    }

    /**
     * Actualizes state by removing obsolete modes and tags for specific context/provider.
     *
     * @param ctx Current context name
     * @param provider Current provider ID
     * @param modeSets Current mode-sets from CLI
     * @param tagSets Current tag-sets from CLI
     * @return true if state was changed
     */
    fun actualizeState(
        ctx: String,
        provider: String,
        modeSets: ModeSetsListSchema?,
        tagSets: TagSetsListSchema?
    ): Boolean {
        var changed = false

        // Actualize modes for current (ctx, provider)
        if (modeSets != null) {
            val currentModes = getCurrentModes(ctx, provider)
            val (validatedModes, modesChanged) = actualizeModes(currentModes, modeSets)
            if (modesChanged) {
                setCurrentModes(ctx, provider, validatedModes)
                changed = true
                LOG.info("Actualized modes for context '$ctx', provider '$provider': removed obsolete entries")
            }
        }

        // Actualize tags for current context
        if (tagSets != null) {
            val currentTags = getCurrentTags(ctx)
            val (validatedTags, tagsChanged) = actualizeTags(currentTags.mapValues { it.value.toMutableSet() }, tagSets)
            if (tagsChanged) {
                setCurrentTags(ctx, validatedTags.mapValues { it.value.toSet() })
                changed = true
                LOG.info("Actualized tags for context '$ctx': removed obsolete entries")
            }
        }

        return changed
    }

    /**
     * Actualizes modes: removes non-existent mode-sets and modes.
     */
    private fun actualizeModes(
        currentModes: Map<String, String>,
        modeSets: ModeSetsListSchema
    ): Pair<Map<String, String>, Boolean> {
        val modeSetById = modeSets.modeSets.associateBy { it.id }

        val validatedModes = currentModes.filterKeys { modeSetId ->
            modeSetById[modeSetId]?.modes?.any { it.id == currentModes[modeSetId] } ?: false
        }

        val changed = validatedModes != currentModes

        return validatedModes to changed
    }

    /**
     * Actualizes tags: removes non-existent tag-sets and tags.
     */
    private fun actualizeTags(
        currentTags: Map<String, MutableSet<String>>,
        tagSets: TagSetsListSchema
    ): Pair<Map<String, Set<String>>, Boolean> {
        val tagSetToTags = tagSets.tagSets.associate { ts ->
            ts.id to ts.tags.mapTo(mutableSetOf()) { it.id }
        }

        val validatedTags = currentTags.mapNotNull { (tagSetId, tagIds) ->
            tagSetToTags[tagSetId]?.let { availableTags ->
                val validTags = tagIds.intersect(availableTags)
                if (validTags.isNotEmpty()) tagSetId to validTags else null
            }
        }.toMap()

        val changed = validatedTags != currentTags.mapValues { it.value.toSet() }

        return validatedTags to changed
    }


    companion object {
        private val LOG = logger<LgPanelStateService>()
        /**
         * Returns the project-scoped instance of LgPanelStateService.
         */
        fun getInstance(project: Project): LgPanelStateService {
            return project.service()
        }
    }
}
