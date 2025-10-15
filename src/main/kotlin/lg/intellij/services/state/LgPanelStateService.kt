package lg.intellij.services.state

import com.intellij.openapi.components.*

/**
 * Project-level service for storing Control Panel UI state.
 * 
 * Phase 4: Mock implementation with basic state properties.
 * All fields are initialized with hardcoded defaults.
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
    
    /**
     * Persistent state class.
     */
    class State : BaseState() {
        /** Selected template (context) name */
        var selectedTemplate by string("default")
        
        /** Selected section name */
        var selectedSection by string("all")
        
        /** Tokenizer library (tiktoken, tokenizers, sentencepiece) */
        var tokenizerLib by string("tiktoken")
        
        /** Encoder name */
        var encoder by string("cl100k_base")
        
        /** Context limit in tokens */
        var ctxLimit by property(128000)
        
        /** Selected mode for dev-stage mode-set */
        var selectedMode by string("development")
        
        /** Task description text */
        var taskText by string("")
        
        /** Target branch for review mode */
        var targetBranch by string("main")
    }
    
    companion object {
        /**
         * Returns the project-scoped instance of LgPanelStateService.
         */
        fun getInstance(project: com.intellij.openapi.project.Project): LgPanelStateService {
            return project.service()
        }
    }
}
