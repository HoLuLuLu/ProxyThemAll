package org.holululu.proxythemall.services

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.models.NotificationData
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.notifications.NotificationService

/**
 * Service responsible for restoring proxy settings from PasswordSafe backup to IntelliJ
 *
 * This service loads the stored proxy configuration and applies it to IntelliJ's proxy settings,
 * including credentials, then activates the proxy.
 */
@Service
class ProxyRestoreService {

    companion object {
        private val LOG = Logger.getInstance(ProxyRestoreService::class.java)

        @JvmStatic
        fun getInstance(): ProxyRestoreService {
            return ApplicationManager.getApplication().getService(ProxyRestoreService::class.java)
        }
    }

    private val credentialsStorage = ProxyCredentialsStorage.getInstance()
    private val notificationService = NotificationService.instance

    /**
     * Restores proxy configuration from PasswordSafe and activates it
     *
     * @param project The current project (can be null)
     * @return true if restore was successful, false otherwise
     */
    fun restoreAndActivateProxy(project: Project?): Boolean {
        LOG.info("Starting proxy restore and activation from PasswordSafe")

        return try {
            // Load configuration from PasswordSafe
            val proxyInfo = credentialsStorage.loadProxyConfiguration()

            if (proxyInfo == null) {
                LOG.warn("No stored proxy configuration found in PasswordSafe")
                showRestoreFailedNotification(project, "No stored proxy configuration found")
                return false
            }

            LOG.info("Loaded proxy configuration from PasswordSafe: host=${proxyInfo.host}, port=${proxyInfo.port}")

            // Apply proxy configuration to IntelliJ
            val restored = restoreProxyToIntelliJ(proxyInfo)

            if (!restored) {
                LOG.error("Failed to restore proxy configuration to IntelliJ")
                showRestoreFailedNotification(project, "Failed to apply proxy settings")
                return false
            }

            // Trigger proxy controller to handle the enabled state on EDT
            // This will configure Git and Gradle services
            ApplicationManager.getApplication().invokeLater {
                try {
                    ProxyController.instance.cleanupAndReapplyProxySettingsForAllProjects(true)

                    // Show success notification
                    showRestoreSuccessNotification(project, proxyInfo)

                    LOG.info("Proxy restored and activated successfully")
                } catch (e: Exception) {
                    LOG.error("Failed to activate proxy after restore", e)
                    showRestoreFailedNotification(
                        project,
                        "Proxy settings restored but activation failed: ${e.message}"
                    )
                }
            }

            true
        } catch (e: Exception) {
            LOG.error("Failed to restore and activate proxy", e)
            showRestoreFailedNotification(project, "Error: ${e.message ?: "Unknown error"}")
            false
        }
    }

    /**
     * Restores proxy configuration from PasswordSafe to IntelliJ without activating
     *
     * @return true if restore was successful, false otherwise
     */
    fun restoreProxyFromStorage(): Boolean {
        LOG.info("Restoring proxy configuration from PasswordSafe to IntelliJ")

        return try {
            val proxyInfo = credentialsStorage.loadProxyConfiguration()

            if (proxyInfo == null) {
                LOG.debug("No stored proxy configuration to restore")
                return false
            }

            restoreProxyToIntelliJ(proxyInfo)
        } catch (e: Exception) {
            LOG.error("Failed to restore proxy from storage", e)
            false
        }
    }

    /**
     * Applies the proxy configuration to IntelliJ's proxy settings
     *
     * @param proxyInfo The proxy configuration to apply
     * @return true if successful, false otherwise
     */
    @Suppress("UnstableApiUsage")
    private fun restoreProxyToIntelliJ(proxyInfo: ProxyInfo): Boolean {
        return try {
            LOG.debug("Applying proxy configuration to IntelliJ: host=${proxyInfo.host}, port=${proxyInfo.port}")

            val proxySettings = ProxySettings.getInstance()

            // Determine protocol type
            val protocol = when (proxyInfo.type.lowercase()) {
                "socks", "socks5" -> ProxyConfiguration.ProxyProtocol.SOCKS
                else -> ProxyConfiguration.ProxyProtocol.HTTP
            }

            // Create static proxy configuration using explicit implementation
            val exceptions = proxyInfo.nonProxyHosts.joinToString(",")
            val staticProxyConfig = StaticProxyConfigurationImpl(
                protocol,
                proxyInfo.host,
                proxyInfo.port,
                exceptions
            )

            // Apply proxy configuration
            proxySettings.setProxyConfiguration(staticProxyConfig)

            // Set credentials if available
            if (!proxyInfo.username.isNullOrBlank() && !proxyInfo.password.isNullOrBlank()) {
                LOG.debug("Setting proxy credentials for ${proxyInfo.host}:${proxyInfo.port}")
                val credentialStore = ProxyCredentialStore.getInstance()
                val credentials = com.intellij.credentialStore.Credentials(proxyInfo.username, proxyInfo.password)
                credentialStore.setCredentials(
                    proxyInfo.host,
                    proxyInfo.port,
                    credentials,
                    false
                )
            }

            LOG.info("Proxy configuration applied successfully to IntelliJ")
            true
        } catch (e: Exception) {
            LOG.error("Failed to apply proxy configuration to IntelliJ", e)
            false
        }
    }

    /**
     * Implementation of StaticProxyConfiguration for restoring proxy settings
     */
    private class StaticProxyConfigurationImpl(
        private val _protocol: ProxyConfiguration.ProxyProtocol,
        private val _host: String,
        private val _port: Int,
        private val _exceptions: String
    ) : ProxyConfiguration.StaticProxyConfiguration {
        override val protocol: ProxyConfiguration.ProxyProtocol get() = _protocol
        override val host: String get() = _host
        override val port: Int get() = _port
        override val exceptions: String get() = _exceptions
    }

    /**
     * Shows a success notification after proxy restore
     */
    private fun showRestoreSuccessNotification(project: Project?, proxyInfo: ProxyInfo) {
        val notification = NotificationData(
            title = "Proxy Restored",
            message = "Proxy settings have been restored and activated: ${proxyInfo.host}:${proxyInfo.port}",
            type = NotificationType.INFORMATION
        )
        notificationService.showNotification(project, notification)
    }

    /**
     * Shows a failure notification with option to open settings
     */
    private fun showRestoreFailedNotification(project: Project?, reason: String) {
        val notification = NotificationData(
            title = "Proxy Restore Failed",
            message = "Failed to restore proxy settings. $reason",
            type = NotificationType.ERROR,
            actions = listOf(
                NotificationAction.createSimple("Open HTTP Proxy Settings") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "HTTP Proxy")
                }
            )
        )
        notificationService.showNotification(project, notification)
    }
}
