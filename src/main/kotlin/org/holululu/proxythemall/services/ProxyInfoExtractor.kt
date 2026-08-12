package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.ProxyProtocol
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
            val type = when (proxyConfiguration.protocol) {
                ProxyProtocol.SOCKS -> "socks5"
                ProxyProtocol.HTTP -> "http"
            }

            // Only the user's own exceptions; the essential local hosts are added by
            // ProxyInfo.bypassHosts when writing Git and Gradle configuration, so a
            // backup/restore round trip never mutates the user's IDE settings
            val nonProxyHosts = extractNonProxyHosts(proxyConfiguration)

            // Get Credentials for the proxy
            val credentials = ProxyCredentialStore.getInstance().getCredentials(host, port)
            val username = credentials?.userName
            val password = credentials?.getPasswordAsString()

            ProxyInfo(
                host = host,
                port = port,
                username = username,
                password = password,
                type = type,
                nonProxyHosts = nonProxyHosts,
            )
        } catch (e: Exception) {
            LOG.warn("Failed to extract static proxy configuration", e)
            null
        }
    }

    /**
     * Splits IntelliJ's comma separated exceptions field into individual hosts.
     *
     * Entries are trimmed: a leading space would prevent the host from ever matching.
     */
    fun extractNonProxyHosts(proxyConfiguration: ProxyConfiguration.StaticProxyConfiguration): Set<String> =
        userExceptions(proxyConfiguration.exceptions)

    /**
     * Splits and trims a comma separated exceptions value
     */
    fun userExceptions(exceptions: String?): Set<String> =
        exceptions?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
}
