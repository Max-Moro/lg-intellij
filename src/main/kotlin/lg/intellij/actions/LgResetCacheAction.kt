package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.services.diagnostics.LgDiagnosticsService

/**
 * Action to reset LG cache via 'lg diag --rebuild-cache'.
 * 
 * Shows confirmation dialog before executing.
 * 
 * Phase 14: Doctor Diagnostics implementation.
 */
class LgResetCacheAction : AnAction(
    LgBundle.message("action.reset.cache.text"),
    LgBundle.message("action.reset.cache.description"),
    AllIcons.Actions.GC
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Confirmation dialog
        val result = Messages.showYesNoDialog(
            project,
            LgBundle.message("action.reset.cache.confirm.message"),
            LgBundle.message("action.reset.cache.confirm.title"),
            Messages.getQuestionIcon()
        )
        
        if (result != Messages.YES) {
            return
        }
        
        val diagnosticsService = project.service<LgDiagnosticsService>()
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.reset.cache.progress"),
            true
        ) {
            private var success = false
            
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.reset.cache.progress.text")
                
                val result = runBlocking {
                    diagnosticsService.rebuildCache()
                }
                
                success = (result != null)
            }
            
            override fun onSuccess() {
                if (success) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LG Notifications")
                        .createNotification(
                            LgBundle.message("action.reset.cache.success.title"),
                            LgBundle.message("action.reset.cache.success.message"),
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            }
        }.queue()
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

