package org.holululu.proxythemall.utils

import org.holululu.proxythemall.models.ProxyInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Utility class for building proxy URLs
 */
class ProxyUrlBuilder {

    companion object {
        @JvmStatic
        val instance: ProxyUrlBuilder by lazy { ProxyUrlBuilder() }
    }

    /**
     * Builds proxy URL with authentication if provided
     */
    fun buildProxyUrl(proxyInfo: ProxyInfo): String {
        return buildProxyUrl(
            host = proxyInfo.host,
            port = proxyInfo.port,
            username = proxyInfo.username,
            password = proxyInfo.password,
            type = proxyInfo.type
        )
    }

    /**
     * Builds proxy URL with authentication if provided
     * Username and password are URL encoded to handle special characters
     */
    fun buildProxyUrl(
        host: String,
        port: Int,
        username: String? = null,
        password: String? = null,
        type: String = "http"
    ): String {
        val protocol = determineProtocol(type)
        return if (hasCredentials(username, password)) {
            val encodedUsername = urlEncode(username!!)
            val encodedPassword = urlEncode(password!!)
            "$protocol://$encodedUsername:$encodedPassword@$host:$port"
        } else {
            "$protocol://$host:$port"
        }
    }

    /**
     * Determines the protocol based on proxy type
     */
    private fun determineProtocol(type: String): String {
        return if (type == "socks5") "socks5" else "http"
    }

    /**
     * Checks if both username and password are provided
     */
    private fun hasCredentials(username: String?, password: String?): Boolean {
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }

    /**
     * URL encodes a string using UTF-8 encoding
     * Handles special characters that could break proxy URL parsing
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }
}
