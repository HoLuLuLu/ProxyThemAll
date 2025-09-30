package org.holululu.proxythemall.notifications

import com.intellij.notification.NotificationGroupManager
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
     * Shows a notification with the specified data
     */
    fun showNotification(project: Project?, notificationData: NotificationData) {
        // Check if notifications are enabled in settings
        val settings = ProxyThemAllSettings.getInstance()
        if (!settings.showNotifications) {
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
