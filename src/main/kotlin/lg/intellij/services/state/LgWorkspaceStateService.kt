package lg.intellij.services.state

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Project-level service for storing workspace-specific UI state.
 * 
 * Stores UI presentation preferences that should not be synced between machines:
 * - View modes (tree/flat for Included Files)
 * - Tool Window layout (tab selection, splitter position)
 * - Other transient UI state
 * 
 * Storage: workspace file (.idea/workspace.xml) - not committed to VCS.
 * Persistence: automatic via SimplePersistentStateComponent.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "LgWorkspaceState",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
    category = SettingsCategory.UI,
    reportStatistic = false // UI state не интересен для статистики
)
class LgWorkspaceStateService : SimplePersistentStateComponent<LgWorkspaceStateService.State>(State()) {
    
    /**
     * View mode for Included Files panel.
     */
    enum class ViewMode {
        /** Tree view (hierarchical) */
        TREE,
        
        /** Flat view (all files in single list) */
        FLAT
    }
    
    /**
     * Persistent state class.
     */
    class State : BaseState() {
        /** View mode for Included Files panel */
        var includedFilesViewMode by enum(ViewMode.TREE)
        
        /** Splitter proportion between Control Panel and Included Files (0.0 - 1.0) */
        var splitterProportion by property(0.7f)
    }
    
    companion object {
        /**
         * Returns the project-scoped instance of LgWorkspaceStateService.
         */
        fun getInstance(project: Project): LgWorkspaceStateService {
            return project.service()
        }
    }
}

