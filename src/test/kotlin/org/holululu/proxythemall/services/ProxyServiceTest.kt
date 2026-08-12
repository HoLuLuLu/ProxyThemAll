package org.holululu.proxythemall.services

import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.models.ProxyState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests the proxy toggle state machine.
 *
 * ProxySettings is an interface and ProxyConfiguration provides real factories, so no mocking
 * library is needed: a hand written in-memory implementation is enough.
 */
class ProxyServiceTest {

    /**
     * In-memory ProxySettings holding whatever configuration was last set
     */
    private class FakeProxySettings(
        initial: ProxyConfiguration = ProxyConfiguration.direct
    ) : ProxySettings {
        var configuration: ProxyConfiguration = initial

        override fun getProxyConfiguration(): ProxyConfiguration = configuration

        override fun setProxyConfiguration(configuration: ProxyConfiguration) {
            this.configuration = configuration
        }
    }

    private val staticProxy = ProxyConfiguration.proxy(
        ProxyConfiguration.ProxyProtocol.HTTP, "proxy.example.com", 8080, "localhost"
    )

    @Test
    fun `a static proxy configuration reports ENABLED`() {
        val service = ProxyService(FakeProxySettings(staticProxy))

        assertEquals(ProxyState.ENABLED, service.getCurrentProxyState())
    }

    @Test
    fun `a direct configuration with nothing remembered reports NOT_CONFIGURED`() {
        val service = ProxyService(FakeProxySettings())

        assertEquals(ProxyState.NOT_CONFIGURED, service.getCurrentProxyState())
    }

    @Test
    fun `toggling off then on restores the original configuration`() {
        val settings = FakeProxySettings(staticProxy)
        val service = ProxyService(settings)

        assertEquals(ProxyState.DISABLED, service.toggleProxy())
        assertSame(ProxyConfiguration.direct, settings.configuration, "proxy must be switched to direct")
        assertEquals(ProxyState.DISABLED, service.getCurrentProxyState(), "a remembered proxy means DISABLED")

        assertEquals(ProxyState.ENABLED, service.toggleProxy())
        assertSame(staticProxy, settings.configuration, "the original configuration must come back")
    }

    @Test
    fun `toggling does nothing when no proxy was ever configured`() {
        val settings = FakeProxySettings()
        val service = ProxyService(settings)

        assertEquals(ProxyState.NOT_CONFIGURED, service.toggleProxy())
        assertSame(ProxyConfiguration.direct, settings.configuration)
    }

    @Test
    fun `getCurrentProxyState does not mutate the remembered configuration`() {
        val settings = FakeProxySettings(staticProxy)
        val service = ProxyService(settings)

        // Querying while enabled must not make the state sticky
        repeat(3) { service.getCurrentProxyState() }
        settings.configuration = ProxyConfiguration.direct

        assertEquals(
            ProxyState.NOT_CONFIGURED,
            service.getCurrentProxyState(),
            "a query must not have remembered the configuration"
        )
    }

    @Test
    fun `rememberActiveConfiguration enables a later toggle`() {
        val settings = FakeProxySettings(staticProxy)
        val service = ProxyService(settings)

        service.rememberActiveConfiguration()
        settings.configuration = ProxyConfiguration.direct

        assertEquals(ProxyState.ENABLED, service.forceEnableProxy())
        assertSame(staticProxy, settings.configuration)
    }

    @Test
    fun `forceEnableProxy is a no-op when the proxy is already enabled`() {
        val settings = FakeProxySettings(staticProxy)
        val service = ProxyService(settings)

        assertEquals(ProxyState.ENABLED, service.forceEnableProxy())
        assertSame(staticProxy, settings.configuration)
    }

    @Test
    fun `forceEnableProxy reports NOT_CONFIGURED without a remembered configuration`() {
        val service = ProxyService(FakeProxySettings())

        assertEquals(ProxyState.NOT_CONFIGURED, service.forceEnableProxy())
    }

    @Test
    fun `getCurrentConfiguration reflects the live configuration`() {
        val settings = FakeProxySettings()
        val service = ProxyService(settings)

        assertEquals(ProxyConfiguration.direct, service.getCurrentConfiguration())

        settings.configuration = staticProxy
        assertEquals(staticProxy, service.getCurrentConfiguration())
    }

    @Test
    fun `getCurrentConfiguration returns null instead of throwing`() {
        val failing = object : ProxySettings {
            override fun getProxyConfiguration(): ProxyConfiguration = throw IllegalStateException("boom")
            override fun setProxyConfiguration(configuration: ProxyConfiguration) = Unit
        }

        assertNull(ProxyService(failing).getCurrentConfiguration())
    }
}
