package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import lg.intellij.LgBundle
import lg.intellij.statepce.LgCoordinatorService
import lg.intellij.statepce.PCEStateStore
import lg.intellij.statepce.domains.Initialize

/**
 * Action to reset all Control Panel settings to defaults.
 *
 * Clears persistent state (provider, context, modes, tags, tokenization)
 * and re-initializes the coordinator with fresh state.
 */
class LgClearStateAction : AnAction(
    LgBundle.message("action.clear.state.text"),
    LgBundle.message("action.clear.state.description"),
    AllIcons.Actions.Restart
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val result = Messages.showYesNoDialog(
            project,
            LgBundle.message("action.clear.state.confirm.message"),
            LgBundle.message("action.clear.state.confirm.title"),
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) {
            return
        }

        val store = PCEStateStore.getInstance(project)
        val coordinator = LgCoordinatorService.getInstance(project).coordinator

        object : Task.Backgroundable(
            project,
            LgBundle.message("action.clear.state.progress"),
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                store.clearAll()

                runBlocking {
                    coordinator.dispatch(Initialize.create())
                }
            }

            override fun onSuccess() {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("LG Notifications")
                    .createNotification(
                        LgBundle.message("action.clear.state.success.title"),
                        LgBundle.message("action.clear.state.success.message"),
                        NotificationType.INFORMATION
                    )
                    .notify(project)
            }
        }.queue()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
