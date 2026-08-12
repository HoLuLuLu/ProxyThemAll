package org.holululu.proxythemall.services.git

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the Git proxy configuration logic that does not need a running IDE.
 */
class GitProxyConfigurerTest {

    private val configurer = GitProxyConfigurer.instance

    private fun noProxyHosts(proxyInfo: ProxyInfo): String {
        val method = GitProxyConfigurer::class.java.getDeclaredMethod("gitNoProxyHosts", ProxyInfo::class.java)
        method.isAccessible = true
        return method.invoke(configurer, proxyInfo) as String
    }

    private fun proxy(vararg hosts: String) = ProxyInfo(
        host = "proxy.example.com",
        port = 8080,
        nonProxyHosts = hosts.toSet()
    )

    @Test
    fun `credentials are detected from the proxy info`() {
        assertTrue(proxy().copy(username = "user", password = "pass").hasCredentials)
        assertFalse(proxy().hasCredentials)
        assertFalse(proxy().copy(username = "", password = "").hasCredentials)
        assertFalse(proxy().copy(username = "user", password = "  ").hasCredentials)
    }

    @Test
    fun `glob patterns are dropped because git cannot match them`() {
        // git's http.noproxy matches plain host and domain names only
        val hosts = noProxyHosts(proxy("*.internal", "10.*", "build.example.com")).split(",")

        assertTrue(hosts.none { it.contains('*') }, "glob patterns must be dropped: $hosts")
        assertTrue(hosts.contains("build.example.com"), "plain hosts must be kept: $hosts")
        // localhost comes from the essential bypass hosts, 127.* is a glob and is dropped
        assertTrue(hosts.contains("localhost"), "essential bypass hosts must be included: $hosts")
    }

    @Test
    fun `entries are trimmed and blanks removed`() {
        val hosts = noProxyHosts(proxy(" build.example.com ", "", "   ")).split(",")

        assertTrue(hosts.contains("build.example.com"), "entries must be trimmed: $hosts")
        assertTrue(hosts.none { it.isBlank() }, "blank entries must be removed: $hosts")
    }

    @Test
    fun `only the essential bypass hosts remain when the user configured none`() {
        val hosts = noProxyHosts(proxy()).split(",")

        // Of the essential hosts, "127.*" is a glob and is dropped; the other two are literal
        assertEquals(setOf("localhost", "[::1]"), hosts.filter { it.isNotEmpty() }.toSet())
    }
}
