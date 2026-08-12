package org.holululu.proxythemall.listeners

import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests listener bookkeeping in [ProxyStateChangeManager].
 *
 * Duplicate registration used to multiply the work done per state change by the number of open
 * projects, and a listener that threw could stop the others from being notified.
 */
class ProxyStateChangeManagerTest {

    /**
     * Minimal ProxySettings so the manager can query a state without a running application
     */
    private class FakeProxySettings(
        var configuration: ProxyConfiguration = ProxyConfiguration.direct
    ) : ProxySettings {
        override fun getProxyConfiguration(): ProxyConfiguration = configuration
        override fun setProxyConfiguration(configuration: ProxyConfiguration) {
            this.configuration = configuration
        }
    }

    private fun manager(): ProxyStateChangeManager {
        val proxyService = ProxyService(FakeProxySettings())
        return ProxyStateChangeManager { proxyService }
    }

    /**
     * Manager plus the settings backing it, so a test can change the configuration mid-flight
     */
    private fun managerWith(settings: FakeProxySettings): ProxyStateChangeManager {
        val proxyService = ProxyService(settings)
        return ProxyStateChangeManager { proxyService }
    }

    private fun httpProxy(port: Int = 8080) = ProxyConfiguration.proxy(
        ProxyConfiguration.ProxyProtocol.HTTP, "proxy.test.local", port, "localhost"
    )

    private fun socksProxy(port: Int = 8080) = ProxyConfiguration.proxy(
        ProxyConfiguration.ProxyProtocol.SOCKS, "proxy.test.local", port, "localhost"
    )

    private class RecordingListener : ProxyStateChangeListener {
        val states = mutableListOf<ProxyState>()
        override fun onProxyStateChanged(newState: ProxyState) {
            states.add(newState)
        }
    }

    @Test
    fun `registering the same listener twice notifies it once`() {
        val manager = manager()
        val listener = RecordingListener()

        manager.addListener(listener)
        manager.addListener(listener)
        manager.notifyStateChanged()

        assertEquals(1, listener.states.size, "a duplicate registration must not double the work")
        manager.dispose()
    }

    @Test
    fun `a removed listener is no longer notified`() {
        val manager = manager()
        val listener = RecordingListener()

        manager.addListener(listener)
        manager.removeListener(listener)
        manager.notifyStateChanged()

        assertEquals(0, listener.states.size)
        manager.dispose()
    }

    @Test
    fun `a failing listener does not prevent the others from being notified`() {
        val manager = manager()
        val failing = ProxyStateChangeListener { throw IllegalStateException("boom") }
        val healthy = RecordingListener()

        manager.addListener(failing)
        manager.addListener(healthy)
        manager.notifyStateChanged()

        assertEquals(1, healthy.states.size, "a broken listener must not block the rest")
        manager.dispose()
    }

    @Test
    fun `dispose drops all listeners`() {
        val manager = manager()
        val listener = RecordingListener()

        manager.addListener(listener)
        manager.dispose()
        manager.notifyStateChanged()

        assertEquals(0, listener.states.size, "dispose must leave nothing registered")
    }

    // --- change detection: the proxy stays ENABLED, only its configuration changes ---

    @Test
    fun `changing the port while enabled notifies once`() {
        val settings = FakeProxySettings(httpProxy(port = 8080))
        val manager = managerWith(settings)
        val listener = RecordingListener()
        manager.addListener(listener)

        manager.checkForStateChanges()          // first tick establishes the baseline
        listener.states.clear()

        settings.configuration = httpProxy(port = 9090)
        manager.checkForStateChanges()

        assertEquals(1, listener.states.size, "a port change must be detected")
        assertEquals(ProxyState.ENABLED, listener.states.single())
        manager.dispose()
    }

    @Test
    fun `switching protocol from http to socks while enabled notifies`() {
        val settings = FakeProxySettings(httpProxy())
        val manager = managerWith(settings)
        val listener = RecordingListener()
        manager.addListener(listener)

        manager.checkForStateChanges()
        listener.states.clear()

        settings.configuration = socksProxy()
        manager.checkForStateChanges()

        assertEquals(1, listener.states.size, "a protocol change must be detected")
        manager.dispose()
    }

    @Test
    fun `an unchanged configuration never notifies again`() {
        // Guards against reapplying the whole configuration on every 2s poll tick
        val settings = FakeProxySettings(httpProxy())
        val manager = managerWith(settings)
        val listener = RecordingListener()
        manager.addListener(listener)

        manager.checkForStateChanges()
        listener.states.clear()

        repeat(5) { manager.checkForStateChanges() }

        assertEquals(0, listener.states.size, "an idle proxy must not trigger any work")
        manager.dispose()
    }

    @Test
    fun `a poll tick right after notifyStateChanged does not notify twice`() {
        // notifyStateChanged runs at the end of reapplication; if it left the remembered
        // configuration stale, the next tick would reapply again - forever
        val settings = FakeProxySettings(httpProxy())
        val manager = managerWith(settings)
        val listener = RecordingListener()
        manager.addListener(listener)

        manager.notifyStateChanged()
        assertEquals(1, listener.states.size)

        manager.checkForStateChanges()

        assertEquals(1, listener.states.size, "the tick after notifyStateChanged must be a no-op")
        manager.dispose()
    }
}
