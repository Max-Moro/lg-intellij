package lg.intellij.services.ai.providers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import lg.intellij.services.ai.AiProvider
import lg.intellij.services.ai.ProviderModeInfo
import java.awt.datatransfer.StringSelection

/**
 * Provider for copying content to clipboard.
 *
 * Always available, used as fallback if other providers are unavailable.
 */
class ClipboardProvider : AiProvider {

    override val id = "clipboard"
    override val name = "Clipboard"
    override val priority = 10 // Low priority (fallback)

    /**
     * Clipboard is always available.
     */
    override suspend fun isAvailable(): Boolean = true

    /**
     * Copies content to clipboard and shows a notification.
     */
    override suspend fun send(content: String, runs: String) {
        // Copy to clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(content))

        // Show notification
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(
                "Copied to Clipboard",
                "Content copied. You can paste it into any AI chat.",
                NotificationType.INFORMATION
            )
            .notify(null) // null = application-level notification
    }

    override fun getSupportedModes(): List<ProviderModeInfo> {
        // Clipboard is universal - compatible with all modes, doesn't generate runs
        return emptyList()
    }
}
