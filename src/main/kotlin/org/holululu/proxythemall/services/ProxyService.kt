package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.models.ProxyState

/**
 * Service responsible for managing proxy configurations and state
 *
 * This service coordinates with the modern ProxyConfiguration API and works
 * alongside GitProxyService and GradleProxyService to provide unified proxy management.
 */
class ProxyService {

    companion object {
        @JvmStatic
        val instance: ProxyService by lazy { ProxyService() }

        private val LOG = Logger.getInstance(ProxyService::class.java)
    }

    // Store the last proxy configuration for toggling
    private var lastProxyConfiguration: ProxyConfiguration? = null

    /**
     * Determines the current proxy state
     */
    fun getCurrentProxyState(): ProxyState {
        return try {
            val proxySettings = ProxySettings.getInstance()
            when {
                isProxyEnabled(proxySettings) -> ProxyState.ENABLED
                isProxyConfigured() -> ProxyState.DISABLED
                else -> ProxyState.NOT_CONFIGURED
            }
        } catch (e: Exception) {
            LOG.warn("Failed to determine proxy state", e)
            ProxyState.NOT_CONFIGURED
        }
    }

    /**
     * Toggles the proxy state between enabled and disabled
     * @return the new proxy state after toggling
     */
    fun toggleProxy(): ProxyState {
        return try {
            val proxySettings = ProxySettings.getInstance()
            val currentState = getCurrentProxyState()

            when (currentState) {
                ProxyState.ENABLED -> {
                    storeCurrentProxySettings(proxySettings)
                    disableProxy(proxySettings)
                    ProxyState.DISABLED
                }

                ProxyState.DISABLED -> {
                    enableProxy(proxySettings)
                    ProxyState.ENABLED
                }

                ProxyState.NOT_CONFIGURED -> {
                    LOG.info("Cannot toggle proxy - no proxy configuration available")
                    ProxyState.NOT_CONFIGURED
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to toggle proxy", e)
            getCurrentProxyState() // Return current state if toggle fails
        }
    }

    /**
     * Gets the current proxy configuration for use by other services
     * @return the current ProxyConfiguration or null if not available
     */
    fun getCurrentProxyConfiguration(): ProxyConfiguration? {
        return try {
            ProxySettings.getInstance().getProxyConfiguration()
        } catch (e: Exception) {
            LOG.warn("Failed to get current proxy configuration", e)
            null
        }
    }

    /**
     * Checks if proxy is currently enabled
     */
    private fun isProxyEnabled(proxySettings: ProxySettings): Boolean {
        return try {
            val proxyConfiguration = proxySettings.getProxyConfiguration()
            proxyConfiguration !is ProxyConfiguration.DirectProxy
        } catch (e: Exception) {
            LOG.debug("Failed to check if proxy is enabled", e)
            false
        }
    }

    /**
     * Stores the current proxy settings for later restoration
     */
    private fun storeCurrentProxySettings(proxySettings: ProxySettings) {
        try {
            val currentConfig = proxySettings.getProxyConfiguration()
            if (currentConfig !is ProxyConfiguration.DirectProxy) {
                lastProxyConfiguration = currentConfig
                LOG.debug("Stored proxy configuration for later restoration")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to store current proxy settings", e)
        }
    }

    /**
     * Disables the proxy by setting DirectProxy configuration
     */
    private fun disableProxy(proxySettings: ProxySettings) {
        try {
            val directProxy = object : ProxyConfiguration.DirectProxy {}
            proxySettings.setProxyConfiguration(directProxy)
            LOG.debug("Proxy disabled successfully")
        } catch (e: Exception) {
            LOG.error("Failed to disable proxy", e)
            throw e
        }
    }

    /**
     * Enables the proxy by restoring the last stored configuration
     */
    private fun enableProxy(proxySettings: ProxySettings) {
        try {
            lastProxyConfiguration?.let { config ->
                proxySettings.setProxyConfiguration(config)
                LOG.debug("Proxy enabled successfully")
            } ?: run {
                LOG.warn("No stored proxy configuration available to restore")
                throw IllegalStateException("No proxy configuration available to enable")
            }
        } catch (e: Exception) {
            LOG.error("Failed to enable proxy", e)
            throw e
        }
    }

    /**
     * Checks if a proxy configuration is available
     */
    private fun isProxyConfigured(): Boolean {
        return try {
            // First check if we have a stored configuration
            val hasStoredConfig = lastProxyConfiguration != null &&
                    lastProxyConfiguration !is ProxyConfiguration.DirectProxy

            // Also check if there's currently a non-direct proxy configuration
            val proxySettings = ProxySettings.getInstance()
            val currentConfig = proxySettings.getProxyConfiguration()
            val hasCurrentConfig = currentConfig !is ProxyConfiguration.DirectProxy

            hasStoredConfig || hasCurrentConfig
        } catch (e: Exception) {
            LOG.debug("Failed to check if proxy is configured", e)
            false
        }
    }
}
