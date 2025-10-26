package lg.intellij.services.state

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lg.intellij.models.AiInteractionMode

/**
 * Project-level service for storing Control Panel UI state.
 * 
 * Phase 6: Full implementation with collections (modes/tags) and effective values.
 * Phase 9: Added reactive taskText for Stats Dialog sync.
 * 
 * Empty string fields ("") mean "use application defaults".
 * Effective values methods provide fallback to LgSettingsService defaults.
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
class LgPanelStateService(private val project: Project) : SimplePersistentStateComponent<LgPanelStateService.State>(State()) {
    
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
        var selectedSection by string("all")
        
        /** Tokenizer library (empty = use default from app settings) */
        var tokenizerLib by string("")
        
        /** Encoder name (empty = use default) */
        var encoder by string("")
        
        /** Context limit in tokens (0 = use default) */
        var ctxLimit by property(0)
        
        /** Selected modes: modeSetId -> modeId */
        var modes by map<String, String>()
        
        /** Active tags: tagSetId -> Set<tagId> */
        var tags by map<String, MutableSet<String>>()
        
        /** Task description text */
        var taskText by string("")
        
        /** Target branch for review mode */
        var targetBranch by string("")
    }

    /**
     * Returns effective tokenizer library (with fallback to application defaults).
     */
    fun getEffectiveTokenizerLib(): String {
        val value = state.tokenizerLib
        if (!value.isNullOrBlank()) {
            return value
        }

        return "tiktoken"
    }

    /**
     * Returns effective encoder (with fallback to application defaults).
     */
    fun getEffectiveEncoder(): String {
        val value = state.encoder
        if (!value.isNullOrBlank()) {
            return value
        }

        return "cl100k_base"
    }

    /**
     * Returns effective context limit (with fallback to application defaults).
     */
    fun getEffectiveContextLimit(): Int {
        val value = state.ctxLimit
        if (value > 0) {
            return value
        }

        return 128000
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
