package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import org.holululu.proxythemall.models.ProxyInfo


/**
 * Service responsible for extracting proxy information from ProxyConfiguration objects
 */
class ProxyInfoExtractor {

    companion object {
        @JvmStatic
        val instance: ProxyInfoExtractor by lazy { ProxyInfoExtractor() }

        private val LOG = Logger.getInstance(ProxyInfoExtractor::class.java)
    }

    /**
     * Extracts proxy information from ProxyConfiguration using modern API
     */
    fun extractProxyInfo(proxyConfiguration: ProxyConfiguration): ProxyInfo? {
        return when (proxyConfiguration) {
            is ProxyConfiguration.DirectProxy -> handleDirectProxy()
            is ProxyConfiguration.StaticProxyConfiguration -> extractStaticProxyInfo(proxyConfiguration)
            is ProxyConfiguration.ProxyAutoConfiguration -> handlePacProxy()
            is ProxyConfiguration.AutoDetectProxy -> handleAutoDetectProxy()
        }
    }

    /**
     * Handles direct proxy configuration (no proxy)
     */
    private fun handleDirectProxy(): ProxyInfo? {
        return null
    }

    /**
     * Handles PAC (Proxy Auto-Configuration) - cannot extract specific proxy details
     */
    private fun handlePacProxy(): ProxyInfo? {
        LOG.info("PAC proxy configuration detected - cannot extract specific proxy details")
        return null
    }

    /**
     * Handles auto-detect proxy configuration - cannot extract specific proxy details
     */
    private fun handleAutoDetectProxy(): ProxyInfo? {
        LOG.info("Auto-detect proxy configuration detected - cannot extract specific proxy details")
        return null
    }

    /**
     * Extracts proxy information from StaticProxyConfiguration using proper API methods
     */
    private fun extractStaticProxyInfo(proxyConfiguration: ProxyConfiguration.StaticProxyConfiguration): ProxyInfo? {
        return try {
            val host = proxyConfiguration.host
            val port = proxyConfiguration.port

            if (host.isBlank() || port <= 0) {
                LOG.warn("Invalid static proxy configuration: host=$host, port=$port")
                return null
            }


            // Determine proxy type from protocol
            val type = when (proxyConfiguration.protocol.toString().uppercase()) {
                "SOCKS" -> "socks5"
                "HTTP" -> "http"
                else -> "http" // Default to HTTP
            }

            val credentials = ProxyCredentialStore.getInstance().getCredentials(host, port)
            val username = credentials?.userName
            val password = credentials?.getPasswordAsString()

            ProxyInfo(
                host = host,
                port = port,
                username = username,
                password = password,
                type = type
            )
        } catch (e: Exception) {
            LOG.warn("Failed to extract static proxy configuration", e)
            null
        }
    }

}
