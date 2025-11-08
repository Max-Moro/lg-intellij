package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindowManager
import lg.intellij.LgBundle
import lg.intellij.services.state.LgWorkspaceStateService
import lg.intellij.ui.toolwindow.LgIncludedFilesPanel

/**
 * Action for switching between Tree and Flat view modes in Included Files panel.
 *
 * Toggles ViewMode in LgWorkspaceStateService and triggers panel rebuild.
 */
class LgToggleTreeViewModeAction : AnAction(
    LgBundle.message("action.toggle.view.text"),
    LgBundle.message("action.toggle.view.description"),
    AllIcons.Actions.ShowAsTree
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val workspaceState = project.service<LgWorkspaceStateService>()
        
        // Toggle between Tree and Flat
        workspaceState.state.includedFilesViewMode = when (workspaceState.state.includedFilesViewMode) {
            LgWorkspaceStateService.ViewMode.TREE -> LgWorkspaceStateService.ViewMode.FLAT
            LgWorkspaceStateService.ViewMode.FLAT -> LgWorkspaceStateService.ViewMode.TREE
        }
        
        // Trigger panel update
        updateIncludedFilesPanel(project)
    }
    
    private fun updateIncludedFilesPanel(project: com.intellij.openapi.project.Project) {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("Listing Generator") ?: return
        
        val contentManager = toolWindow.contentManager
        val content = contentManager.contents.firstOrNull() ?: return
        val splitter = content.component as? com.intellij.ui.OnePixelSplitter ?: return
        
        // Second component is Included Files panel
        val panel = splitter.secondComponent as? LgIncludedFilesPanel ?: return
        
        panel.rebuildTree()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
        
        // Update icon based on current mode
        if (project != null) {
            val workspaceState = project.service<LgWorkspaceStateService>()
            e.presentation.icon = when (workspaceState.state.includedFilesViewMode) {
                LgWorkspaceStateService.ViewMode.TREE -> AllIcons.Actions.ShowAsTree
                LgWorkspaceStateService.ViewMode.FLAT -> AllIcons.Actions.ListFiles
            }
        }
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

