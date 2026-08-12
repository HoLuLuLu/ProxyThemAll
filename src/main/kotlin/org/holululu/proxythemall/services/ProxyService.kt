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
 *
 * ProxySettings.setProxyConfiguration is marked experimental by the platform but is the only way
 * to change the IDE proxy, which is this plugin's entire purpose.
 *
 * @param proxySettings The ProxySettings instance to use. Defaults to the platform singleton.
 *                      Can be injected for testing purposes.
 */
@Suppress("UnstableApiUsage")
class ProxyService(
    private val proxySettings: ProxySettings = ProxySettings.getInstance()
) {

    companion object {
        @JvmStatic
        val instance: ProxyService by lazy { ProxyService() }

        private val LOG = Logger.getInstance(ProxyService::class.java)
    }

    // Store the last proxy configuration for toggling; read from the EDT and the polling thread
    @Volatile
    private var lastProxyConfiguration: ProxyConfiguration? = null

    /**
     * Determines the current proxy state.
     *
     * This is a pure query: it is called on every status bar repaint and must not mutate state.
     * Remembering the active configuration happens in [rememberActiveConfiguration].
     */
    fun getCurrentProxyState(): ProxyState {
        return try {
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
     * Returns the proxy configuration currently held by the IDE, or null when it cannot be read.
     *
     * Used to detect edits that keep the state unchanged, such as a new port while the proxy stays
     * enabled. The platform's configuration objects implement equals, so they can be compared.
     */
    fun getCurrentConfiguration(): ProxyConfiguration? = try {
        proxySettings.getProxyConfiguration()
    } catch (e: Exception) {
        LOG.warn("Failed to read the current proxy configuration", e)
        null
    }

    /**
     * Remembers the currently active proxy configuration so it can be restored when re-enabling
     */
    fun rememberActiveConfiguration() {
        try {
            val currentConfig = proxySettings.getProxyConfiguration()
            if (currentConfig !is ProxyConfiguration.DirectProxy) {
                lastProxyConfiguration = currentConfig
                LOG.debug("Stored current proxy configuration for future use")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to store current proxy configuration", e)
        }
    }

    /**
     * Toggles the proxy state between enabled and disabled
     * @return the new proxy state after toggling
     */
    fun toggleProxy(): ProxyState {
        return try {
            val currentState = getCurrentProxyState()

            when (currentState) {
                ProxyState.ENABLED -> {
                    rememberActiveConfiguration()
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
            LOG.warn("Failed to toggle proxy", e)
            getCurrentProxyState() // Return current state if toggle fails
        }
    }

    /**
     * Forces the proxy to be enabled by using the current proxy configuration
     * This is useful when we know there's a proxy configuration but it might not be stored
     * @return the new proxy state after enabling
     */
    fun forceEnableProxy(): ProxyState {
        return try {
            val currentConfig = proxySettings.getProxyConfiguration()

            // If current config is already a non-direct proxy, we're already enabled
            if (currentConfig !is ProxyConfiguration.DirectProxy) {
                LOG.debug("Proxy is already enabled")
                return ProxyState.ENABLED
            }

            // Try to enable using stored configuration
            lastProxyConfiguration?.let { config ->
                proxySettings.setProxyConfiguration(config)
                LOG.debug("Proxy enabled using stored configuration")
                return ProxyState.ENABLED
            }

            // If no stored configuration, we can't enable
            LOG.warn("Cannot force enable proxy - no configuration available")
            ProxyState.NOT_CONFIGURED
        } catch (e: Exception) {
            LOG.warn("Failed to force enable proxy", e)
            getCurrentProxyState()
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
     * Disables the proxy by setting DirectProxy configuration
     */
    private fun disableProxy(proxySettings: ProxySettings) {
        try {
            // Use the platform factory: an anonymous implementation has no equals/hashCode and
            // would never compare equal to the platform's own instances
            proxySettings.setProxyConfiguration(ProxyConfiguration.direct)
            LOG.debug("Proxy disabled successfully")
        } catch (e: Exception) {
            LOG.warn("Failed to disable proxy", e)
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
                LOG.debug("Proxy enabled successfully using stored configuration")
            } ?: run {
                // If no stored configuration, try to use current configuration if it's not direct
                val currentConfig = proxySettings.getProxyConfiguration()
                if (currentConfig !is ProxyConfiguration.DirectProxy) {
                    LOG.debug("Proxy already enabled with current configuration")
                } else {
                    LOG.warn("No stored proxy configuration available to restore")
                    throw IllegalStateException("No proxy configuration available to enable")
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to enable proxy", e)
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
            val currentConfig = proxySettings.getProxyConfiguration()
            val hasCurrentConfig = currentConfig !is ProxyConfiguration.DirectProxy

            hasStoredConfig || hasCurrentConfig
        } catch (e: Exception) {
            LOG.debug("Failed to check if proxy is configured", e)
            false
        }
    }
}
