package org.holululu.proxythemall.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.listeners.HttpProxySettingsChangeListener

/**
 * Startup service that initializes ProxyThemAll plugin components
 *
 * This service is responsible for registering listeners, performing cleanup
 * and reapplication of proxy settings on IDE startup, and any other necessary
 * initialization when the plugin starts.
 */
@Service
class ProxyThemAllStartupService {

    companion object {
        private val LOG = Logger.getInstance(ProxyThemAllStartupService::class.java)

        @JvmStatic
        fun getInstance(): ProxyThemAllStartupService {
            return ApplicationManager.getApplication().getService(ProxyThemAllStartupService::class.java)
        }
    }

    /**
     * Performs initial setup when called by the startup activity
     */
    fun performInitialSetup() {
        try {
            LOG.info("Performing initial setup for ProxyThemAll plugin")

            // Handle proxy backup and restore logic
            handleProxyBackupAndRestore()

            // Perform cleanup and reapplication on IDE startup
            // This ensures a clean state every time the IDE starts
            performStartupCleanup()

            LOG.info("ProxyThemAll plugin initial setup completed successfully")
        } catch (e: Exception) {
            LOG.error("Failed to perform initial setup for ProxyThemAll plugin", e)
        }
    }

    /**
     * Handles automatic backup and restore of proxy settings on startup
     */
    private fun handleProxyBackupAndRestore() {
        try {
            val proxyService = ProxyService.instance
            val currentState = proxyService.getCurrentProxyState()
            val settings = org.holululu.proxythemall.settings.ProxyThemAllSettings.getInstance()
            val credentialsStorage = ProxyCredentialsStorage.getInstance()
            val proxyInfoExtractor = ProxyInfoExtractor.instance

            LOG.info("Handling proxy backup and restore: currentState=$currentState, lastKnownProxyEnabled=${settings.lastKnownProxyEnabled}")

            when (currentState) {
                org.holululu.proxythemall.models.ProxyState.ENABLED -> {
                    // Backup current IntelliJ proxy to PasswordSafe
                    LOG.info("Proxy is currently enabled - backing up to PasswordSafe")
                    val proxyConfiguration = com.intellij.util.net.ProxySettings.getInstance().getProxyConfiguration()
                    val proxyInfo = proxyInfoExtractor.extractProxyInfo(proxyConfiguration)

                    if (proxyInfo != null) {
                        credentialsStorage.saveProxyConfiguration(proxyInfo)
                        settings.lastKnownProxyEnabled = true
                        LOG.info("Proxy configuration backed up successfully")
                    } else {
                        LOG.warn("Could not extract proxy info for backup")
                    }
                }

                org.holululu.proxythemall.models.ProxyState.NOT_CONFIGURED -> {
                    if (settings.lastKnownProxyEnabled && credentialsStorage.hasStoredConfiguration()) {
                        // Auto-restore from PasswordSafe
                        LOG.info("Proxy not configured but was previously enabled - attempting auto-restore")
                        val restored = ProxyRestoreService.getInstance().restoreProxyFromStorage()
                        if (restored) {
                            LOG.info("Proxy auto-restored successfully on startup")
                        } else {
                            LOG.warn("Failed to auto-restore proxy on startup")
                            settings.lastKnownProxyEnabled = false
                        }
                    } else {
                        LOG.debug("Proxy not configured and was not previously enabled - no restore needed")
                        settings.lastKnownProxyEnabled = false
                    }
                }

                org.holululu.proxythemall.models.ProxyState.DISABLED -> {
                    LOG.debug("Proxy is explicitly disabled - updating state flag")
                    settings.lastKnownProxyEnabled = false
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to handle proxy backup and restore", e)
        }
    }

    /**
     * Performs cleanup and reapplication of proxy settings on IDE startup
     */
    private fun performStartupCleanup() {
        ApplicationManager.getApplication().invokeLater {
            try {
                LOG.info("Performing startup cleanup and reapplication of proxy settings for all projects")

                // Trigger cleanup and reapplication for all open projects
                // This ensures all proxy configurations (IDE, Git, Gradle) are in sync across all projects
                ProxyController.instance.cleanupAndReapplyProxySettings()

                // Also trigger immediate proxy configuration check
                // This handles cases where proxy was manually configured before plugin startup
                HttpProxySettingsChangeListener.instance.onProxySettingsChanged()

                LOG.info("Startup cleanup and reapplication completed successfully for all projects")
            } catch (e: Exception) {
                LOG.error("Failed to perform startup cleanup", e)
            }
        }
    }
}
