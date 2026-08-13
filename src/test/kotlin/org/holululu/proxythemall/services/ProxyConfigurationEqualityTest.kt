package org.holululu.proxythemall.services

import com.intellij.util.net.ProxyConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * The change detection in HttpProxySettingsChangeListener relies on ProxyConfiguration equality.
 * If the platform's implementations stopped implementing equals, every poll tick would look like a
 * change and reapply Git and Gradle configuration every 2 seconds.
 */
class ProxyConfigurationEqualityTest {

    private fun proxy(host: String = "proxy.example.com", port: Int = 8080, exceptions: String = "localhost") =
        ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, host, port, exceptions)

    @Test
    fun `identical static configurations compare equal`() {
        assertEquals(proxy(), proxy())
    }

    @Test
    fun `a changed host port or exception list compares unequal`() {
        assertNotEquals(proxy(), proxy(host = "other.example.com"))
        assertNotEquals(proxy(), proxy(port = 3128))
        assertNotEquals(proxy(), proxy(exceptions = "localhost,build.example.com"))
    }

    @Test
    fun `direct is a stable singleton`() {
        val direct: ProxyConfiguration = ProxyConfiguration.direct
        val static: ProxyConfiguration = proxy()

        assertEquals(direct, ProxyConfiguration.direct)
        assertNotEquals(direct, static)
    }
}
