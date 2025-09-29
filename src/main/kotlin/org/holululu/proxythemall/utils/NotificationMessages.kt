package org.holululu.proxythemall.utils

import com.intellij.notification.NotificationType
import org.holululu.proxythemall.models.NotificationData

/**
 * Utility object containing predefined notification messages
 */
object NotificationMessages {

    fun proxyEnabled(): NotificationData = NotificationData(
        title = "Proxy Enabled",
        message = "You are now using a proxy.",
        type = NotificationType.INFORMATION
    )

    fun proxyDisabled(): NotificationData = NotificationData(
        title = "Proxy Disabled",
        message = "You are now not using any proxy.",
        type = NotificationType.INFORMATION
    )

    fun proxyConfigurationRequired(): NotificationData = NotificationData(
        title = "Proxy Configuration Required",
        message = "You have to configure a proxy first. Go to IDE Settings > HTTP Proxy to set up your proxy configuration.",
        type = NotificationType.WARNING
    )
}
