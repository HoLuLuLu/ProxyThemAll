package org.holululu.proxythemall.services

import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.models.ProxyState

/**
 * Service responsible for managing proxy configurations and state
 */
class ProxyService {

    companion object {
        @JvmStatic
        val instance: ProxyService by lazy { ProxyService() }
    }

    // Store the last proxy configuration for toggling
    private var lastProxyConfiguration: ProxyConfiguration? = null

    /**
     * Determines the current proxy state
     */
    fun getCurrentProxyState(): ProxyState {
        val proxySettings = ProxySettings.getInstance()

        return when {
            isProxyEnabled(proxySettings) -> ProxyState.ENABLED
            isProxyConfigured() -> ProxyState.DISABLED
            else -> ProxyState.NOT_CONFIGURED
        }
    }

    /**
     * Toggles the proxy state between enabled and disabled
     * @return the new proxy state after toggling
     */
    fun toggleProxy(): ProxyState {
        val proxySettings = ProxySettings.getInstance()
        val currentState = getCurrentProxyState()

        return when (currentState) {
            ProxyState.ENABLED -> {
                storeCurrentProxySettings(proxySettings)
                disableProxy(proxySettings)
                ProxyState.DISABLED
            }

            ProxyState.DISABLED -> {
                enableProxy(proxySettings)
                ProxyState.ENABLED
            }

            ProxyState.NOT_CONFIGURED -> ProxyState.NOT_CONFIGURED
        }
    }

    /**
     * Checks if proxy is currently enabled
     */
    private fun isProxyEnabled(proxySettings: ProxySettings): Boolean {
        return try {
            val proxyConfiguration = proxySettings.getProxyConfiguration()
            proxyConfiguration !is ProxyConfiguration.DirectProxy
        } catch (_: Exception) {
            // If we can't determine proxy state, assume it's disabled
            false
        }
    }

    /**
     * Stores the current proxy settings for later restoration
     */
    private fun storeCurrentProxySettings(proxySettings: ProxySettings) {
        try {
            lastProxyConfiguration = proxySettings.getProxyConfiguration()
        } catch (_: Exception) {
            // Ignore errors when storing settings
        }
    }

    /**
     * Disables the proxy by setting DirectProxy configuration
     */
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

    /**
     * Enables the proxy by restoring the last stored configuration
     */
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

    /**
     * Checks if a proxy configuration is available
     */
    private fun isProxyConfigured(): Boolean {
        // Check if we have a stored proxy configuration that's not DirectProxy
        return lastProxyConfiguration != null &&
                lastProxyConfiguration !is ProxyConfiguration.DirectProxy
    }
}
