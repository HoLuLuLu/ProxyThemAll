package org.holululu.proxythemall.notifications

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.NotificationData
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Service responsible for displaying notifications to the user
 */
class NotificationService {

    companion object {
        @JvmStatic
        val instance: NotificationService by lazy { NotificationService() }

        private const val NOTIFICATION_GROUP_ID = "ProxyThemAll.Notifications"
    }

    /**
     * Shows a notification with the specified data.
     *
     * The "show notifications" setting only suppresses informational state change balloons.
     * Warnings and errors are always shown - silently swallowing a failure would leave the user
     * with a broken proxy setup and no indication why.
     */
    fun showNotification(project: Project?, notificationData: NotificationData) {
        val isInformational = notificationData.type == NotificationType.INFORMATION
        if (isInformational && !ProxyThemAllSettings.getInstance().showNotifications) {
            return
        }

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                notificationData.title,
                notificationData.message,
                notificationData.type
            )

        // Add actions if any are provided
        notificationData.actions.forEach { action ->
            notification.addAction(action)
        }

        notification.notify(project)
    }
}
