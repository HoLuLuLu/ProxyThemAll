package org.holululu.proxythemall.models

/**
 * Data class to hold proxy information
 *
 * [nonProxyHosts] holds the user's own exception list exactly as configured in the IDE. Hosts that
 * should always bypass a proxy are added on top by [bypassHosts], so they are written to Git and
 * Gradle without ever being written back into the user's IDE settings.
 */
data class ProxyInfo(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val type: String = "http",
    val nonProxyHosts: Set<String>
) {

    companion object {
        /**
         * Hosts that should never go through a proxy, regardless of the user's exception list
         */
        val ESSENTIAL_BYPASS_HOSTS = setOf("localhost", "127.*", "[::1]")
    }

    /**
     * The user's exceptions plus the essential local hosts, for writing to Git and Gradle
     */
    val bypassHosts: Set<String>
        get() = ESSENTIAL_BYPASS_HOSTS + nonProxyHosts

    /**
     * True when both a username and a password are available
     */
    val hasCredentials: Boolean
        get() = !username.isNullOrBlank() && !password.isNullOrBlank()

    /**
     * True when this is a SOCKS proxy, which needs different system properties than HTTP
     */
    val isSocks: Boolean
        get() = type.lowercase().startsWith("socks")
}
