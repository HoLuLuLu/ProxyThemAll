package org.holululu.proxythemall.services

import com.intellij.credentialStore.Credentials
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
import org.holululu.proxythemall.settings.ProxyThemAllSettings

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
                LOG.warn("Failed to restore proxy configuration to IntelliJ")
                showRestoreFailedNotification(project, "Failed to apply proxy settings")
                return false
            }

            // The proxy is now configured and enabled, so record that for the next startup
            ProxyThemAllSettings.getInstance().lastKnownProxyEnabled = true

            // Configure Git and Gradle for the restored proxy. The silent variant is used because
            // showRestoreSuccessNotification already reports the outcome.
            ApplicationManager.getApplication().invokeLater {
                try {
                    ProxyController.instance.cleanupAndReapplyProxySettingsForAllProjectsSilently(true)
                    showRestoreSuccessNotification(project, proxyInfo)
                    LOG.info("Proxy restored and activated successfully")
                } catch (e: Exception) {
                    LOG.warn("Failed to activate proxy after restore", e)
                    showRestoreFailedNotification(
                        project,
                        "Proxy settings restored but activation failed: ${e.message}"
                    )
                }
            }

            true
        } catch (e: Exception) {
            LOG.warn("Failed to restore and activate proxy", e)
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
            LOG.warn("Failed to restore proxy from storage", e)
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

            // Use the platform factory: a hand-rolled implementation has no equals/hashCode and
            // never compares equal to the platform's own configuration objects
            val exceptions = proxyInfo.nonProxyHosts.joinToString(",")
            proxySettings.setProxyConfiguration(
                ProxyConfiguration.proxy(protocol, proxyInfo.host, proxyInfo.port, exceptions)
            )

            // Set credentials if available
            if (!proxyInfo.username.isNullOrBlank() && !proxyInfo.password.isNullOrBlank()) {
                LOG.debug("Setting proxy credentials for ${proxyInfo.host}:${proxyInfo.port}")
                val credentialStore = ProxyCredentialStore.getInstance()
                val credentials = Credentials(proxyInfo.username, proxyInfo.password)
                // remember = true, otherwise the password is memory-only and the next restart
                // loses it again - which is exactly what this backup feature exists to prevent
                credentialStore.setCredentials(
                    proxyInfo.host,
                    proxyInfo.port,
                    credentials,
                    true
                )
            }

            LOG.info("Proxy configuration applied successfully to IntelliJ")
            true
        } catch (e: Exception) {
            LOG.warn("Failed to apply proxy configuration to IntelliJ", e)
            false
        }
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
