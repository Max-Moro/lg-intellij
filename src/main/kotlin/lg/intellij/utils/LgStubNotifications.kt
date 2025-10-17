package lg.intellij.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Utility object for showing stub notifications during development.
 * 
 * Phase 4: Used to indicate features that are not yet implemented.
 * Each stub notification indicates which phase will implement the feature.
 */
object LgStubNotifications {
    
    /**
     * Shows a notification indicating that a feature is not yet implemented.
     * 
     * @param project Project context (can be null for application-level notifications)
     * @param featureName Human-readable feature name
     * @param phase Phase number when this feature will be implemented
     */
    fun showNotImplemented(
        project: Project?,
        featureName: String,
        phase: Int
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LG Notifications")
            .createNotification(
                "Feature Not Implemented",
                "$featureName will be available in Phase $phase",
                NotificationType.INFORMATION
            )
            .notify(project)
    }
}
