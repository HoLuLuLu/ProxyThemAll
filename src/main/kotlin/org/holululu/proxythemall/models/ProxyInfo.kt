package org.holululu.proxythemall.models

/**
 * Data class to hold proxy information
 */
data class ProxyInfo(
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val type: String = "http",
    val nonProxyHosts: Set<String>
)
