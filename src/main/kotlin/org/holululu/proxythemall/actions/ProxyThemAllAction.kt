package org.holululu.proxythemall.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings

class ProxyThemAllAction : AnAction() {

    // Store the last proxy configuration for toggling
    private var lastProxyConfiguration: ProxyConfiguration? = null

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project? = e.project
        val proxySettings = ProxySettings.getInstance()

        // Check current proxy state using modern API
        val wasUsingProxy = isProxyEnabled(proxySettings)

        if (wasUsingProxy) {
            // Store current proxy settings before disabling
            storeCurrentProxySettings(proxySettings)
            // Disable proxy
            disableProxy(proxySettings)
            showNotification(
                project,
                "Proxy Disabled",
                "You are now not using any proxy.",
                NotificationType.INFORMATION
            )
        } else {
            // Check if proxy is configured before enabling
            if (isProxyConfigured()) {
                // Enable proxy
                enableProxy(proxySettings)
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

    private fun isProxyEnabled(proxySettings: ProxySettings): Boolean {
        return try {
            val proxyConfiguration = proxySettings.getProxyConfiguration()
            proxyConfiguration !is ProxyConfiguration.DirectProxy
        } catch (_: Exception) {
            // If we can't determine proxy state, assume it's disabled
            false
        }
    }

    private fun storeCurrentProxySettings(proxySettings: ProxySettings) {
        try {
            lastProxyConfiguration = proxySettings.getProxyConfiguration()
        } catch (_: Exception) {
            // Ignore errors when storing settings
        }
    }

    private fun disableProxy(proxySettings: ProxySettings) {
        try {
            // Create a DirectProxy configuration to disable proxy
            val directProxy = object : ProxyConfiguration.DirectProxy {}
            proxySettings.setProxyConfiguration(directProxy)
        } catch (_: Exception) {
            // If modern API fails, we can't disable proxy
            // This is a limitation of the modern API approach
        }
    }

    private fun enableProxy(proxySettings: ProxySettings) {
        try {
            // Restore the last stored proxy configuration
            lastProxyConfiguration?.let { config ->
                proxySettings.setProxyConfiguration(config)
            }
        } catch (_: Exception) {
            // If we can't restore proxy configuration, do nothing
        }
    }

    private fun isProxyConfigured(): Boolean {
        // Check if we have a stored proxy configuration that's not DirectProxy
        return lastProxyConfiguration != null &&
                lastProxyConfiguration !is ProxyConfiguration.DirectProxy
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
