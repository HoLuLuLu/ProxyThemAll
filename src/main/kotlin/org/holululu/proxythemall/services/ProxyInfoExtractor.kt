package org.holululu.proxythemall.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ProxyConfiguration
import org.holululu.proxythemall.models.ProxyInfo
import java.lang.reflect.Field

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
     * Extracts proxy information from StaticProxyConfiguration using reflection
     */
    private fun extractStaticProxyInfo(proxyConfiguration: ProxyConfiguration.StaticProxyConfiguration): ProxyInfo? {
        return try {
            val clazz = proxyConfiguration::class.java

            val host = extractHostFromStaticProxy(clazz, proxyConfiguration)
            val port = extractPortFromStaticProxy(clazz, proxyConfiguration)

            if (host.isNullOrBlank() || port == null || port <= 0) {
                LOG.warn("Invalid static proxy configuration: host=$host, port=$port")
                return null
            }

            val credentials = extractCredentialsFromStaticProxy(clazz, proxyConfiguration)
            val type = extractProxyTypeFromStaticProxy(clazz, proxyConfiguration)

            ProxyInfo(
                host = host,
                port = port,
                username = credentials.first,
                password = credentials.second,
                type = type
            )
        } catch (e: Exception) {
            LOG.warn("Failed to extract static proxy configuration", e)
            null
        }
    }

    /**
     * Extracts host information from StaticProxyConfiguration
     */
    private fun extractHostFromStaticProxy(clazz: Class<*>, proxyConfiguration: Any): String? {
        val hostField = findFieldByNames(clazz, "host", "myHost")
        hostField?.isAccessible = true
        return hostField?.get(proxyConfiguration) as? String
    }

    /**
     * Extracts port information from StaticProxyConfiguration
     */
    private fun extractPortFromStaticProxy(clazz: Class<*>, proxyConfiguration: Any): Int? {
        val portField = findFieldByNames(clazz, "port", "myPort")
        portField?.isAccessible = true
        return portField?.get(proxyConfiguration) as? Int
    }

    /**
     * Extracts credentials (username and password) from StaticProxyConfiguration
     */
    private fun extractCredentialsFromStaticProxy(clazz: Class<*>, proxyConfiguration: Any): Pair<String?, String?> {
        val usernameField = findFieldByNames(clazz, "login", "username")
        val passwordField = findFieldByNames(clazz, "password", "plainPassword")

        usernameField?.isAccessible = true
        passwordField?.isAccessible = true

        val username = usernameField?.get(proxyConfiguration) as? String
        val password = passwordField?.get(proxyConfiguration) as? String

        return Pair(
            username?.takeIf { it.isNotBlank() },
            password?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Extracts proxy type from StaticProxyConfiguration
     */
    private fun extractProxyTypeFromStaticProxy(clazz: Class<*>, proxyConfiguration: Any): String {
        return try {
            val protocolField = clazz.getDeclaredField("protocol")
            protocolField.isAccessible = true
            val protocol = protocolField[proxyConfiguration]
            when (protocol.toString().uppercase()) {
                "SOCKS" -> "socks5"
                "HTTP" -> "http"
                else -> "http"
            }
        } catch (_: Exception) {
            "http" // Default to HTTP
        }
    }

    /**
     * Finds a field by trying multiple possible names
     */
    private fun findFieldByNames(clazz: Class<*>, vararg fieldNames: String): Field? {
        for (fieldName in fieldNames) {
            try {
                return clazz.getDeclaredField(fieldName)
            } catch (_: NoSuchFieldException) {
                // Continue to next field name
            }
        }
        return null
    }
}
