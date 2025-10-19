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
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.services.catalog.LgCatalogService
import lg.intellij.services.catalog.TokenizerCatalogService

/**
 * Action to refresh all catalog data from CLI.
 * 
 * Forces reload of:
 * - Sections
 * - Contexts
 * - Mode-sets
 * - Tag-sets
 * - Tokenizer libraries
 * - Encoders cache
 * 
 * Shows progress notification and success/error feedback.
 */
class LgRefreshCatalogsAction : AnAction(
    LgBundle.message("action.refresh.text"),
    LgBundle.message("action.refresh.description"),
    AllIcons.Actions.Refresh
) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        object : Task.Backgroundable(
            project,
            LgBundle.message("action.refresh.progress.title"),
            true // cancellable
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = LgBundle.message("action.refresh.progress.text")
                
                try {
                    // Reload catalog service data
                    runBlocking {
                        val catalogService = project.service<LgCatalogService>()
                        catalogService.reload()
                    }
                    
                    // Invalidate tokenizer cache
                    val tokenizerService = TokenizerCatalogService.getInstance()
                    tokenizerService.invalidateAll()
                    
                    // Reload tokenizer libraries
                    runBlocking {
                        tokenizerService.loadLibraries(project)
                    }
                    
                    indicator.text = LgBundle.message("action.refresh.progress.completed")
                    
                    // Success notification
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LG Notifications")
                        .createNotification(
                            LgBundle.message("action.refresh.success.title"),
                            LgBundle.message("action.refresh.success.message"),
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                    
                } catch (e: Exception) {
                    // Error notification
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("LG Notifications")
                        .createNotification(
                            LgBundle.message("action.refresh.error.title"),
                            LgBundle.message("action.refresh.error.message", e.message ?: "Unknown error"),
                            NotificationType.ERROR
                        )
                        .notify(project)
                }
            }
        }.queue()
    }
    
    override fun update(e: AnActionEvent) {
        // Доступно только если проект открыт
        e.presentation.isEnabled = e.project != null
    }
    
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

