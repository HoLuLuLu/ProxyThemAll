package org.holululu.proxythemall.listeners

import org.holululu.proxythemall.models.ProxyState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for ProxyStateChangeListener functional interface
 */
class ProxyStateChangeListenerTest {

    @Test
    fun `listener receives ENABLED state change`() {
        // Given
        var receivedState: ProxyState? = null
        val listener = ProxyStateChangeListener { newState ->
            receivedState = newState
        }

        // When
        listener.onProxyStateChanged(ProxyState.ENABLED)

        // Then
        assertEquals(ProxyState.ENABLED, receivedState)
    }

    @Test
    fun `listener receives DISABLED state change`() {
        // Given
        var receivedState: ProxyState? = null
        val listener = ProxyStateChangeListener { newState ->
            receivedState = newState
        }

        // When
        listener.onProxyStateChanged(ProxyState.DISABLED)

        // Then
        assertEquals(ProxyState.DISABLED, receivedState)
    }

    @Test
    fun `listener can be implemented with class`() {
        // Given
        val states = mutableListOf<ProxyState>()
        val listener = object : ProxyStateChangeListener {
            override fun onProxyStateChanged(newState: ProxyState) {
                states.add(newState)
            }
        }

        // When
        listener.onProxyStateChanged(ProxyState.ENABLED)
        listener.onProxyStateChanged(ProxyState.DISABLED)
        listener.onProxyStateChanged(ProxyState.ENABLED)

        // Then
        assertEquals(3, states.size)
        assertEquals(ProxyState.ENABLED, states[0])
        assertEquals(ProxyState.DISABLED, states[1])
        assertEquals(ProxyState.ENABLED, states[2])
    }
}
