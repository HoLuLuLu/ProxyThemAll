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

            // Perform cleanup and reapplication on IDE startup
            // This ensures a clean state every time the IDE starts
            performStartupCleanup()

            LOG.info("ProxyThemAll plugin initial setup completed successfully")
        } catch (e: Exception) {
            LOG.error("Failed to perform initial setup for ProxyThemAll plugin", e)
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
