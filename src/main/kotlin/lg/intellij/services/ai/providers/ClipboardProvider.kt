package lg.intellij.services.ai.providers

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import lg.intellij.services.ai.AiProvider
import java.awt.datatransfer.StringSelection

/**
 * Провайдер для копирования контента в буфер обмена.
 * 
 * Всегда доступен, используется как fallback если другие провайдеры недоступны.
 */
class ClipboardProvider : AiProvider {
    
    override val id = "clipboard"
    override val name = "Clipboard"
    override val priority = 10 // Низкий приоритет (fallback)
    
    /**
     * Clipboard всегда доступен.
     */
    override suspend fun isAvailable(): Boolean = true
    
    /**
     * Копирует контент в буфер обмена и показывает notification.
     */
    override suspend fun send(content: String) {
        // Копировать в clipboard
        CopyPasteManager.getInstance().setContents(StringSelection(content))
        
        // Показать notification
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(
                "Copied to Clipboard",
                "Content copied. You can paste it into any AI chat.",
                NotificationType.INFORMATION
            )
            .notify(null) // null = application-level notification
    }
}

