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
    }

    init {
        initialize()
    }

    /**
     * Initializes the plugin components
     */
    private fun initialize() {
        try {
            LOG.info("Initializing ProxyThemAll plugin")

            // Register the HTTP proxy settings change listener
            // This ensures that changes to IntelliJ's built-in proxy settings
            // trigger cleanup and reapplication of proxy configurations
            HttpProxySettingsChangeListener.instance.register()

            // Perform cleanup and reapplication on IDE startup
            // This ensures a clean state every time the IDE starts
            performStartupCleanup()

            LOG.info("ProxyThemAll plugin initialized successfully")
        } catch (e: Exception) {
            LOG.error("Failed to initialize ProxyThemAll plugin", e)
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

                LOG.info("Startup cleanup and reapplication completed successfully for all projects")
            } catch (e: Exception) {
                LOG.error("Failed to perform startup cleanup", e)
            }
        }
    }
}
