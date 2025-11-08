package lg.intellij.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.models.ReportSchema
import lg.intellij.services.generation.LgStatsService
import lg.intellij.services.state.LgPanelStateService
import lg.intellij.ui.dialogs.LgStatsDialog

/**
 * Base action to show statistics dialog.
 *
 * Provides common functionality for stats loading and dialog display.
 */
abstract class LgShowStatsAction(
    text: String,
    description: String,
    icon: javax.swing.Icon
) : AnAction(text, description, icon) {
    
    /**
     * Determines target for stats.
     * Returns pair of (target specifier, display name) or (null, null) if unavailable.
     */
    protected abstract fun determineTarget(panelState: LgPanelStateService): Pair<String?, String?>
    
    /**
     * Checks if action should be enabled.
     */
    protected abstract fun hasValidTarget(panelState: LgPanelStateService): Boolean
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panelState = project.service<LgPanelStateService>()
        val statsService = project.service<LgStatsService>()
        
        val (target, targetName) = determineTarget(panelState)
        if (target == null || targetName == null) {
            LOG.warn("${this::class.simpleName} triggered but no valid target")
            return
        }
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.show.stats.progress", targetName),
            true
        ) {
            private var stats: ReportSchema? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.show.stats.progress.text", targetName)
                
                stats = runBlocking {
                    statsService.getStats(target)
                }
            }
            
            override fun onSuccess() {
                val result = stats
                if (result != null) {
                    openStatsDialog(project, result, target)
                }
            }
        }.queue()
    }
    
    /**
     * Opens Stats Dialog with loaded data.
     */
    protected fun openStatsDialog(project: Project, stats: ReportSchema, target: String) {
        val dialog = LgStatsDialog(project, stats, target)
        dialog.show()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        val panelState = project?.service<LgPanelStateService>()
        
        e.presentation.isEnabled = project != null && 
            panelState != null && 
            hasValidTarget(panelState)
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    
    companion object {
        private val LOG = logger<LgShowStatsAction>()
    }
}

