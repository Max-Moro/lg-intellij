package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.services.generation.LgStatsService
import lg.intellij.services.generation.StatsException
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.toolwindow.LgIncludedFilesPanel

/**
 * Action to load and display included files for the selected section.
 * 
 * Phase 11: Full implementation.
 * 
 * Fetches file list via `lg report sec:...`, parses files[] array,
 * updates Included Files panel and auto-switches to that tab.
 */
class LgShowIncludedFilesAction : AnAction(
    LgBundle.message("action.show.included.text"),
    LgBundle.message("action.show.included.description"),
    AllIcons.Actions.ShowAsTree
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panelState = project.service<LgPanelStateService>()
        val statsService = project.service<LgStatsService>()
        
        val selectedSection = panelState.state.selectedSection ?: "all"
        val target = "sec:$selectedSection"
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.show.included.progress", selectedSection),
            true
        ) {
            private var paths: List<String>? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.show.included.progress.text", selectedSection)
                
                try {
                    val report = runBlocking {
                        statsService.getStats(target)
                    }
                    
                    // Extract file paths from report
                    paths = report.files.map { it.path }.sorted()
                    
                    LOG.info("Loaded ${paths?.size ?: 0} included files for section '$selectedSection'")
                    
                } catch (e: StatsException) {
                    LOG.warn("Failed to load included files", e)
                    // Error already reported by LgStatsService
                } catch (e: Exception) {
                    LOG.error("Unexpected error loading included files", e)
                }
            }
            
            override fun onSuccess() {
                val loadedPaths = paths ?: return
                
                // Find Tool Window
                val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow("Listing Generator") ?: return
                
                // Update Included Files panel
                updateIncludedFilesPanel(toolWindow, loadedPaths)
                
                // Switch to Included Files tab
                switchToIncludedFilesTab(toolWindow)
            }
        }.queue()
    }
    
    private fun updateIncludedFilesPanel(toolWindow: ToolWindow, paths: List<String>) {
        val contentManager = toolWindow.contentManager
        
        // Get main content (contains splitter with Control Panel and Included Files)
        val content = contentManager.contents.firstOrNull() ?: return
        val splitter = content.component as? com.intellij.ui.OnePixelSplitter ?: return
        
        // Second component is Included Files panel
        val panel = splitter.secondComponent as? LgIncludedFilesPanel ?: return
        
        panel.setPaths(paths)
    }
    
    private fun switchToIncludedFilesTab(toolWindow: ToolWindow) {
        // In splitter layout, we don't need to switch tabs
        // Just ensure tool window is visible
        if (!toolWindow.isVisible) {
            toolWindow.show()
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    
    companion object {
        private val LOG = logger<LgShowIncludedFilesAction>()
    }
}

