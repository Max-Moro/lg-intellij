package lg.intellij.services.state

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lg.intellij.models.AiInteractionMode
import lg.intellij.models.ClaudeIntegrationMethod
import lg.intellij.models.ClaudeModel
import lg.intellij.models.ShellType

/**
 * Project-level service for storing Control Panel UI state.
 *
 * Phase 6: Full implementation with collections (modes/tags) and default values.
 * Phase 9: Added reactive taskText for Stats Dialog sync.
 *
 * All fields have sensible defaults configured via property delegates.
 * Empty string fields ("") are semantically valid (e.g., cliScope = "" means workspace root).
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


    companion object {
        /**
         * Returns the project-scoped instance of LgPanelStateService.
         */
        fun getInstance(project: Project): LgPanelStateService {
            return project.service()
        }
    }
}
