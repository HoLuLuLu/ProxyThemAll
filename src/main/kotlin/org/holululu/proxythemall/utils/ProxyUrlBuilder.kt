package org.holululu.proxythemall.utils

import org.holululu.proxythemall.models.ProxyInfo
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
        return if (hasCredentials(username, password)) {
            val encodedUsername = urlEncode(username!!)
            val encodedPassword = urlEncode(password!!)
            "$type://$encodedUsername:$encodedPassword@$host:$port"
        } else {
            "$type://$host:$port"
        }
    }

    /**
     * Checks if both username and password are provided
     */
    private fun hasCredentials(username: String?, password: String?): Boolean {
        return !username.isNullOrBlank() && !password.isNullOrBlank()
    }

    /**
     * Percent-encodes a userinfo component per RFC 3986.
     *
     * URLEncoder is not usable here: it is form encoding, so a space becomes '+' and would
     * authenticate as a literal plus sign rather than a space.
     */
    private fun urlEncode(value: String): String = buildString {
        for (byte in value.toByteArray(StandardCharsets.UTF_8)) {
            val char = byte.toInt().toChar()
            if (char.isUnreservedUserInfo()) {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    /**
     * RFC 3986 unreserved characters, which never need escaping in userinfo
     */
    private fun Char.isUnreservedUserInfo(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"
}
