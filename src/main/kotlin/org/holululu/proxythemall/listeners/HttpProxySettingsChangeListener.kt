package org.holululu.proxythemall.listeners

import com.intellij.openapi.diagnostic.Logger
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.models.ProxyState

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
    private var lastProcessedState: ProxyState? = null

    /**
     * Called when the proxy state changes due to HTTP proxy settings modifications
     */
    override fun onProxyStateChanged(newState: ProxyState) {
        // Only process if the state actually changed to avoid unnecessary cleanup cycles
        if (lastProcessedState != newState) {
            lastProcessedState = newState

            LOG.info("HTTP proxy settings changed, new state: $newState - triggering cleanup and reapplication for all projects")

            // Determine target state based on the new proxy state
            val targetEnabled = when (newState) {
                ProxyState.ENABLED -> true
                ProxyState.DISABLED -> false
                ProxyState.NOT_CONFIGURED -> false
            }

            // Trigger cleanup and reapplication for all open projects with the appropriate target state
            proxyController.cleanupAndReapplyProxySettingsForAllProjects(targetEnabled)

            LOG.debug("Cleanup and reapplication completed for HTTP proxy settings change across all projects")
        }
    }

    /**
     * Registers this listener with the ProxyStateChangeManager
     */
    fun register() {
        ProxyStateChangeManager.instance.addListener(this)
        LOG.debug("HttpProxySettingsChangeListener registered")
    }

    /**
     * Unregisters this listener from the ProxyStateChangeManager
     */
    fun unregister() {
        ProxyStateChangeManager.instance.removeListener(this)
        LOG.debug("HttpProxySettingsChangeListener unregistered")
    }
}
