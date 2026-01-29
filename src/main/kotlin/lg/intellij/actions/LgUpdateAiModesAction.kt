package lg.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import lg.intellij.LgBundle
import lg.intellij.services.ai.AiModesTemplateGenerator
import java.io.File

/**
 * Action to update ai-interaction.sec.yaml template.
 *
 * Collects supported modes from all registered AI providers
 * and generates/updates the canonical integration meta-section.
 */
class LgUpdateAiModesAction : AnAction(
    LgBundle.message("action.update.ai.modes.text"),
    LgBundle.message("action.update.ai.modes.description"),
    AllIcons.Actions.Refresh
) {

    private val log = logger<LgUpdateAiModesAction>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            LgBundle.message("action.update.ai.modes.progress"),
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = LgBundle.message("action.update.ai.modes.progress.text")

                try {
                    val generator = project.service<AiModesTemplateGenerator>()
                    val filePath = generator.generate()

                    // Open file in editor (on EDT)
                    ApplicationManager.getApplication().invokeLater {
                        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(filePath))
                        if (virtualFile != null) {
                            FileEditorManager.getInstance(project).openFile(virtualFile, true)
                        }

                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("LG Notifications")
                            .createNotification(
                                LgBundle.message("action.update.ai.modes.success.title"),
                                LgBundle.message("action.update.ai.modes.success.message"),
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                } catch (ex: Exception) {
                    log.error("Failed to update AI modes template", ex)

                    ApplicationManager.getApplication().invokeLater {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("LG Important")
                            .createNotification(
                                LgBundle.message("action.update.ai.modes.error.title"),
                                ex.message ?: "Unknown error",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
