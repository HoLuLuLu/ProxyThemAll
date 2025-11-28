package org.holululu.proxythemall.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.messages.MessageBusConnection
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService

/**
 * Listener that handles HTTP proxy settings changes by triggering cleanup and reapplication
 *
 * This listener ensures that whenever HTTP proxy settings are changed in IntelliJ's built-in
 * proxy settings, the plugin performs a complete cleanup and reapplication to maintain
 * a consistent state across IDE, Git, and Gradle proxy configurations.
 */
class HttpProxySettingsChangeListener : ProxyStateChangeListener {

    companion object {
        @JvmStatic
        val instance: HttpProxySettingsChangeListener by lazy { HttpProxySettingsChangeListener() }

        private val LOG = Logger.getInstance(HttpProxySettingsChangeListener::class.java)
    }

    private val proxyController = ProxyController.instance
    private val proxyService = ProxyService.instance
    private var lastProcessedState: ProxyState? = null
    private var messageBusConnection: MessageBusConnection? = null

    /**
     * Called when the proxy state changes due to HTTP proxy settings modifications
     */
    override fun onProxyStateChanged(newState: ProxyState) {
        LOG.info("HttpProxySettingsChangeListener.onProxyStateChanged called with state: $newState, lastProcessedState: $lastProcessedState")
        
        // Only process if the state actually changed to avoid unnecessary cleanup cycles
        if (lastProcessedState != newState) {
            lastProcessedState = newState

            LOG.info("HTTP proxy settings changed, new state: $newState - triggering cleanup and reapplication for all projects")

            // Handle backup to PasswordSafe based on new state
            handleProxyBackup(newState)

            // Determine target state based on the new proxy state
            val targetEnabled = when (newState) {
                ProxyState.ENABLED -> true
                ProxyState.DISABLED -> false
                ProxyState.NOT_CONFIGURED -> false
            }

            // Trigger cleanup and reapplication for all open projects with the appropriate target state
            // Use the silent version to avoid duplicate notifications
            proxyController.cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled)

            LOG.debug("Cleanup and reapplication completed for HTTP proxy settings change across all projects")
        } else {
            LOG.debug("Proxy state unchanged ($newState), skipping cleanup")
        }
    }

    /**
     * Handles backing up proxy configuration to PasswordSafe when state changes
     */
    private fun handleProxyBackup(newState: ProxyState) {
        try {
            val settings = org.holululu.proxythemall.settings.ProxyThemAllSettings.getInstance()
            val credentialsStorage = org.holululu.proxythemall.services.ProxyCredentialsStorage.getInstance()
            val proxyInfoExtractor = org.holululu.proxythemall.services.ProxyInfoExtractor.instance

            when (newState) {
                ProxyState.ENABLED -> {
                    // Extract and backup to PasswordSafe
                    LOG.info("Proxy enabled - backing up configuration to PasswordSafe")
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

                ProxyState.DISABLED, ProxyState.NOT_CONFIGURED -> {
                    // Update flag but never delete from PasswordSafe
                    LOG.debug("Proxy disabled/not configured - updating state flag only (backup preserved)")
                    settings.lastKnownProxyEnabled = false
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to handle proxy backup", e)
        }
    }

    /**
     * Directly triggers proxy configuration when settings change
     */
    fun onProxySettingsChanged() {
        LOG.info("Direct proxy settings change detected - triggering immediate configuration")

        // Get current state and trigger configuration
        val currentState = proxyService.getCurrentProxyState()
        val targetEnabled = when (currentState) {
            ProxyState.ENABLED -> true
            ProxyState.DISABLED -> false
            ProxyState.NOT_CONFIGURED -> false
        }

        // Trigger immediate cleanup and reapplication
        // Use the silent version to avoid duplicate notifications
        ApplicationManager.getApplication().invokeLater {
            proxyController.cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled)
        }
    }

    /**
     * Registers this listener with the ProxyStateChangeManager and direct proxy settings listener
     */
    fun register() {
        // Register with our polling-based state change manager
        ProxyStateChangeManager.instance.addListener(this)

        // Also try to register for direct proxy settings changes
        try {
            messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            // Note: IntelliJ doesn't provide a direct proxy settings change topic,
            // so we'll rely on our polling mechanism and manual triggers
            LOG.debug("HttpProxySettingsChangeListener registered with polling mechanism")
        } catch (e: Exception) {
            LOG.warn("Failed to register direct proxy settings listener, using polling only", e)
        }
        
        LOG.debug("HttpProxySettingsChangeListener registered")
    }
}
