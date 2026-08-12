package org.holululu.proxythemall.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.listeners.HttpProxySettingsChangeListener
import org.holululu.proxythemall.listeners.ProxyStateChangeManager
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.settings.ProxyThemAllSettings
import java.util.concurrent.atomic.AtomicBoolean

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

    // The startup activity runs once per project, but this setup is application wide
    private val initialized = AtomicBoolean(false)

    /**
     * Performs initial setup when called by the startup activity.
     *
     * Only the first call does any work; subsequent projects opening reuse the same state.
     */
    fun performInitialSetup() {
        if (!initialized.compareAndSet(false, true)) {
            LOG.debug("ProxyThemAll initial setup already performed, skipping")
            return
        }

        try {
            LOG.info("Performing initial setup for ProxyThemAll plugin")

            // Tie the polling task's lifetime to the application so it cannot outlive the plugin
            Disposer.register(ApplicationManager.getApplication(), ProxyStateChangeManager.instance)

            // Register the listener that reacts to proxy settings changes
            HttpProxySettingsChangeListener.instance.register()

            // Handle proxy backup and restore logic
            handleProxyBackupAndRestore()

            // Perform cleanup and reapplication on IDE startup
            // This ensures a clean state every time the IDE starts
            performStartupCleanup()

            LOG.info("ProxyThemAll plugin initial setup completed successfully")
        } catch (e: Exception) {
            LOG.warn("Failed to perform initial setup for ProxyThemAll plugin", e)
        }
    }

    /**
     * Handles automatic backup and restore of proxy settings on startup
     */
    private fun handleProxyBackupAndRestore() {
        try {
            val currentState = ProxyService.instance.getCurrentProxyState()
            val settings = ProxyThemAllSettings.getInstance()
            val credentialsStorage = ProxyCredentialsStorage.getInstance()

            LOG.info("Handling proxy backup and restore: currentState=$currentState, lastKnownProxyEnabled=${settings.lastKnownProxyEnabled}")

            when (currentState) {
                ProxyState.ENABLED -> {
                    // Backup current IntelliJ proxy to PasswordSafe
                    LOG.info("Proxy is currently enabled - backing up to PasswordSafe")
                    val proxyConfiguration = ProxySettings.getInstance().getProxyConfiguration()
                    val proxyInfo = ProxyInfoExtractor.instance.extractProxyInfo(proxyConfiguration)

                    if (proxyInfo != null) {
                        credentialsStorage.saveProxyConfiguration(proxyInfo)
                        settings.lastKnownProxyEnabled = true
                        LOG.info("Proxy configuration backed up successfully")
                    } else {
                        LOG.warn("Could not extract proxy info for backup")
                    }
                }

                ProxyState.NOT_CONFIGURED -> {
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

                ProxyState.DISABLED -> {
                    LOG.debug("Proxy is explicitly disabled - updating state flag")
                    settings.lastKnownProxyEnabled = false
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to handle proxy backup and restore", e)
        }
    }

    /**
     * Performs cleanup and reapplication of proxy settings on IDE startup
     */
    private fun performStartupCleanup() {
        try {
            LOG.info("Scheduling startup cleanup and reapplication of proxy settings")

            // Schedule cleanup to run after startup completes
            // This gives time for VCS and other subsystems to initialize
            ApplicationManager.getApplication().invokeLater {
                try {
                    ProxyController.instance.cleanupAndReapplyProxySettings()
                    LOG.info("Startup cleanup and reapplication completed successfully for all projects")
                } catch (e: Exception) {
                    LOG.warn("Failed to perform startup cleanup", e)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to schedule startup cleanup", e)
        }
    }
}
