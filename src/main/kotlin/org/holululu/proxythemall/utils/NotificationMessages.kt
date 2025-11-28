package org.holululu.proxythemall.utils

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.NotificationData

/**
 * Utility object containing predefined notification messages
 */
object NotificationMessages {
    const val MESSAGE_SEPARATOR = "; "

    fun proxyEnabled(toolsStatus: String? = null): NotificationData = NotificationData(
        title = "Proxy Enabled",
        message = buildString {
            append("You are now using a proxy.")
            toolsStatus?.let { append(it) }
        },
        type = NotificationType.INFORMATION
    )

    fun proxyDisabled(toolsStatus: String? = null): NotificationData = NotificationData(
        title = "Proxy Disabled",
        message = buildString {
            append("You are now not using any proxy.")
            toolsStatus?.let { append(it) }
        },
        type = NotificationType.INFORMATION
    )

    fun proxyConfigurationRequired(project: Project?, hasStoredConfig: Boolean): NotificationData {
        val actions = mutableListOf<NotificationAction>()

        // Add restore action FIRST if stored configuration exists in PasswordSafe
        // This makes it the primary/most visible action
        if (hasStoredConfig) {
            actions.add(
                NotificationAction.createSimple("Restore Last Known Proxy Settings") {
                    org.holululu.proxythemall.services.ProxyRestoreService.getInstance()
                        .restoreAndActivateProxy(project)
                }
            )
        }

        // Add HTTP Proxy Settings action (always available, but second if restore exists)
        actions.add(
            NotificationAction.createSimple("Open HTTP Proxy Settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, "HTTP Proxy")
            }
        )

        return NotificationData(
            title = "Proxy Configuration Required",
            message = "You have to configure a proxy first.",
            type = NotificationType.WARNING,
            actions = actions
        )
    }
}
