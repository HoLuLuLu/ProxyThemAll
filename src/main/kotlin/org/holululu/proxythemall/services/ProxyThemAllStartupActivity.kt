package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.holululu.proxythemall.listeners.HttpProxySettingsChangeListener

/**
 * Startup activity that initializes ProxyThemAll plugin components
 *
 * This activity is executed after the IDE startup is complete and ensures that
 * the HttpProxySettingsChangeListener is properly registered with the ProxyStateChangeManager.
 */
class ProxyThemAllStartupActivity : StartupActivity {

    companion object {
        private val LOG = Logger.getInstance(ProxyThemAllStartupActivity::class.java)
    }

    override fun runActivity(project: Project) {
        try {
            LOG.info("Initializing ProxyThemAll plugin via startup activity")

            // Register the HTTP proxy settings change listener
            // This ensures that changes to IntelliJ's built-in proxy settings
            // trigger cleanup and reapplication of proxy configurations
            HttpProxySettingsChangeListener.instance.register()

            // Get the startup service instance to trigger its initialization
            // This will perform the startup cleanup and reapplication
            val startupService = ProxyThemAllStartupService.getInstance()
            startupService.performInitialSetup()

            LOG.info("ProxyThemAll plugin initialized successfully via startup activity")
        } catch (e: Exception) {
            LOG.error("Failed to initialize ProxyThemAll plugin via startup activity", e)
        }
    }
}
