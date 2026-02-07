package lg.intellij.services

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import lg.intellij.cli.CliException
import lg.intellij.cli.CliExecutionException
import lg.intellij.cli.CliNotFoundException
import lg.intellij.cli.CliTimeoutException
import java.awt.datatransfer.StringSelection

/**
 * Service for reporting CLI errors to users.
 *
 * Shows sticky notifications with full stderr output.
 */
@Service(Service.Level.APP)
class LgErrorReportingService {

    /**
     * Unified CLI exception handler for service-level code.
     *
     * Suppresses notifications for:
     * - Silent failures (CliNotFoundException with silent=true)
     * - Uninitialized projects (lg-cfg/ does not exist)
     *
     * @param project Project context
     * @param operation Human-readable operation name (e.g., "Listing Generation")
     * @param e CLI exception to handle
     */
    fun reportCliException(
        project: Project,
        operation: String,
        e: CliException
    ) {
        // Silent failures — no user notification
        if (e is CliNotFoundException && e.silent) {
            LOG.debug("$operation: CLI unavailable (silent)")
            return
        }

        // Suppress errors for uninitialized projects (lg-cfg/ doesn't exist yet)
        if (!LgInitService.getInstance(project).isInitialized()) {
            LOG.debug("$operation failed (project not initialized): ${e.message}")
            return
        }

        when (e) {
            is CliExecutionException -> {
                LOG.warn("$operation failed: exit code ${e.exitCode}")
                reportCliFailure(project, operation, e.stderr)
            }
            is CliTimeoutException -> {
                LOG.warn("$operation timeout")
                reportTimeout(project, operation, e.timeoutMs)
            }
            is CliNotFoundException -> {
                LOG.warn("$operation: CLI not found — ${e.message}")
                reportCliNotFound(project, operation, e.message ?: "")
            }
        }
    }

    /**
     * Reports CLI execution failure to user.
     *
     * Shows sticky notification with full stderr (Python traceback).
     *
     * @param project Project context (can be null for app-level errors)
     * @param operation Human-readable operation name
     * @param stderr Error output from the process
     */
    fun reportCliFailure(
        project: Project?,
        operation: String,
        stderr: String
    ) {
        val title = "$operation Failed"
        showCliError(project, title, stderr)
    }

    /**
     * Reports timeout error.
     */
    fun reportTimeout(
        project: Project?,
        operation: String,
        timeoutMs: Long
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Important")
            .createNotification(
                "$operation Timeout",
                "Operation exceeded timeout of ${timeoutMs / 1000} seconds. " +
                        "Check CLI configuration or increase timeout in Settings.",
                NotificationType.ERROR
            )
            .notify(project)
    }

    /**
     * Reports CLI not found error.
     */
    fun reportCliNotFound(
        project: Project?,
        operation: String,
        message: String
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Important")
            .createNotification(
                "$operation Failed",
                message,
                NotificationType.ERROR
            )
            .addAction(object : NotificationAction("Open settings") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    com.intellij.ide.actions.ShowSettingsUtilImpl.showSettingsDialog(
                        e.project,
                        "maxmoro.lg.settings",
                        null
                    )
                    notification.expire()
                }
            })
            .notify(project)
    }

    /**
     * Shows CLI error with full stderr output.
     *
     * STICKY notification (does not auto-expire).
     * Includes "Copy Error" action for easy sharing.
     */
    private fun showCliError(
        project: Project?,
        title: String,
        stderr: String
    ) {
        val content = if (stderr.isNotEmpty()) {
            val preview = stderr.lines().take(3).joinToString("<br/>")
            "<html><body><p>$preview</p><p><small>Click 'Copy Full Error' to see complete output.</small></p></body></html>"
        } else {
            "Unknown error"
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Important")
            .createNotification(
                title,
                content,
                NotificationType.ERROR
            )
            .addAction(object : NotificationAction("Copy full error") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    CopyPasteManager.getInstance().setContents(StringSelection(stderr))
                }
            })
            .notify(project)

        LOG.warn("CLI error:\n$stderr")
    }

    companion object {
        private val LOG = logger<LgErrorReportingService>()

        fun getInstance(): LgErrorReportingService = service()
    }
}
