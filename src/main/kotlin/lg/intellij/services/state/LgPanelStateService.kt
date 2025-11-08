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
class LgPanelStateService() : SimplePersistentStateComponent<LgPanelStateService.State>(State()) {
    
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

        /** Selected modes: modeSetId -> modeId */
        var modes by map<String, String>()

        /** Active tags: tagSetId -> Set<tagId> */
        var tags by map<String, MutableSet<String>>()

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
    }

    /**
     * Returns AI interaction mode based on current modes selection.
     *
     * Logic:
     * - If "ai-interaction" mode set is present → return its value
     * - Otherwise → default to AGENT
     *
     * @return Typed AI interaction mode
     */
    fun getAiInteractionMode(): AiInteractionMode {
        val aiInteractionMode = state.modes["ai-interaction"]
        return when (aiInteractionMode) {
            "ask" -> AiInteractionMode.ASK
            "agent" -> AiInteractionMode.AGENT
            else -> AiInteractionMode.AGENT // Default to AGENT
        }
    }

    /**
     * Actualizes state by removing obsolete modes and tags.
     *
     * @param modeSets Current mode-sets from CLI
     * @param tagSets Current tag-sets from CLI
     * @return true if state was changed
     */
    fun actualizeState(
        modeSets: ModeSetsListSchema?,
        tagSets: TagSetsListSchema?
    ): Boolean {
        var changed = false

        // Actualize modes
        if (modeSets != null) {
            val (validatedModes, modesChanged) = actualizeModes(state.modes, modeSets)
            if (modesChanged) {
                state.modes = validatedModes.toMutableMap()
                changed = true
                LOG.info("Actualized modes: removed obsolete entries")
            }
        }

        // Actualize tags
        if (tagSets != null) {
            val (validatedTags, tagsChanged) = actualizeTags(state.tags, tagSets)
            if (tagsChanged) {
                state.tags = validatedTags.mapValues { it.value.toMutableSet() }.toMutableMap()
                changed = true
                LOG.info("Actualized tags: removed obsolete entries")
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
