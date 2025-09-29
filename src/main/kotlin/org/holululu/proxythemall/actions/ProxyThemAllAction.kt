package org.holululu.proxythemall.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.net.HttpConfigurable

class ProxyThemAllAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val httpConfigurable = HttpConfigurable.getInstance()

        // Toggle proxy settings (using deprecated API as it's still functional)
        @Suppress("DEPRECATION")
        val wasUsingProxy = httpConfigurable.USE_HTTP_PROXY

        if (wasUsingProxy) {
            // Disable proxy
            @Suppress("DEPRECATION")
            httpConfigurable.USE_HTTP_PROXY = false
            showNotification(
                project,
                "Proxy Disabled",
                "You are now not using any proxy.",
                NotificationType.INFORMATION
            )
        } else {
            // Check if proxy is configured before enabling
            if (isProxyConfigured(httpConfigurable)) {
                // Enable proxy
                @Suppress("DEPRECATION")
                httpConfigurable.USE_HTTP_PROXY = true
                showNotification(project, "Proxy Enabled", "You are now using a proxy.", NotificationType.INFORMATION)
            } else {
                // Show warning that proxy needs to be configured first
                showNotification(
                    project,
                    "Proxy Configuration Required",
                    "You have to configure a proxy first. Go to IDE Settings > HTTP Proxy to set up your proxy configuration.",
                    NotificationType.WARNING
                )
            }
        }
    }

    private fun isProxyConfigured(httpConfigurable: HttpConfigurable): Boolean {
        @Suppress("DEPRECATION")
        val proxyHost = httpConfigurable.PROXY_HOST

        @Suppress("DEPRECATION")
        val proxyPort = httpConfigurable.PROXY_PORT

        // Check if proxy host is configured and not empty
        return !proxyHost.isNullOrBlank() && proxyPort > 0
    }

    private fun showNotification(project: Project?, title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("ProxyThemAll Notifications")
            .createNotification(
                title,
                message,
                type
            )
            .notify(project)
    }
}
