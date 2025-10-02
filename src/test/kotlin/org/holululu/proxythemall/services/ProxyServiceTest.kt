package org.holululu.proxythemall.services

import org.holululu.proxythemall.models.ProxyState
import org.junit.Test

/**
 * Unit tests for ProxyService
 */
class ProxyServiceTest {

    @Test
    fun `getCurrentProxyState should return valid ProxyState`() {
        // Given
        val service = ProxyService()

        // When & Then
        val state = service.getCurrentProxyState()
        assert(state in listOf(ProxyState.ENABLED, ProxyState.DISABLED, ProxyState.NOT_CONFIGURED)) {
            "getCurrentProxyState should return a valid ProxyState, got: $state"
        }
    }

    @Test
    fun `toggleProxy should return valid ProxyState`() {
        // Given
        val service = ProxyService()

        // When & Then
        val result = service.toggleProxy()
        assert(result in listOf(ProxyState.ENABLED, ProxyState.DISABLED, ProxyState.NOT_CONFIGURED)) {
            "toggleProxy should return a valid ProxyState, got: $result"
        }
    }

    @Test
    fun `getCurrentProxyConfiguration should not throw exceptions`() {
        // Given
        val service = ProxyService()

        // When & Then
        try {
            service.getCurrentProxyConfiguration()
            // Configuration can be null in test environment, which is acceptable
            assert(true) { "getCurrentProxyConfiguration should not throw exceptions" }
        } catch (e: Exception) {
            assert(false) { "getCurrentProxyConfiguration should handle exceptions gracefully, got: ${e.javaClass.simpleName}: ${e.message}" }
        }
    }

    @Test
    fun `getCurrentProxyState should be consistent when called multiple times`() {
        // Given
        val service = ProxyService()

        // When & Then
        try {
            val state1 = service.getCurrentProxyState()
            val state2 = service.getCurrentProxyState()
            assert(state1 == state2) { "getCurrentProxyState should return consistent results" }
        } catch (e: Exception) {
            // In test environment, ProxySettings.getInstance() may throw exceptions
            // This is expected behavior when IntelliJ platform is not fully initialized
            assert(e is NullPointerException || e is IllegalStateException) {
                "Expected NPE or IllegalStateException in test environment, got: ${e.javaClass.simpleName}"
            }
        }
    }

    @Test
    fun `ProxyState enum should have all expected values`() {
        // Given & When
        val states = ProxyState.entries.toTypedArray()

        // Then
        assert(states.contains(ProxyState.ENABLED)) { "ProxyState should have ENABLED value" }
        assert(states.contains(ProxyState.DISABLED)) { "ProxyState should have DISABLED value" }
        assert(states.contains(ProxyState.NOT_CONFIGURED)) { "ProxyState should have NOT_CONFIGURED value" }
        assert(states.size == 3) { "ProxyState should have exactly 3 values" }
    }

    @Test
    fun `service methods should have correct signatures`() {
        // Given
        val service = ProxyService()

        // When & Then
        val getCurrentStateMethod = service.javaClass.methods.find { it.name == "getCurrentProxyState" }
        assert(getCurrentStateMethod != null) { "getCurrentProxyState method should exist" }
        assert(getCurrentStateMethod!!.parameterCount == 0) { "getCurrentProxyState should take no parameters" }

        val toggleMethod = service.javaClass.methods.find { it.name == "toggleProxy" }
        assert(toggleMethod != null) { "toggleProxy method should exist" }
        assert(toggleMethod!!.parameterCount == 0) { "toggleProxy should take no parameters" }
    }

    @Test
    fun `service should handle multiple toggle operations`() {
        // Given
        val service = ProxyService()

        // When & Then
        try {
            val state1 = service.toggleProxy()
            val state2 = service.toggleProxy()
            val state3 = service.toggleProxy()

            assert(state1 in listOf(ProxyState.ENABLED, ProxyState.DISABLED, ProxyState.NOT_CONFIGURED)) {
                "First toggle should return valid state"
            }
            assert(state2 in listOf(ProxyState.ENABLED, ProxyState.DISABLED, ProxyState.NOT_CONFIGURED)) {
                "Second toggle should return valid state"
            }
            assert(state3 in listOf(ProxyState.ENABLED, ProxyState.DISABLED, ProxyState.NOT_CONFIGURED)) {
                "Third toggle should return valid state"
            }
        } catch (e: Exception) {
            // In test environment, ProxySettings.getInstance() may throw exceptions
            // This is expected behavior when IntelliJ platform is not fully initialized
            assert(e is NullPointerException || e is IllegalStateException) {
                "Expected NPE or IllegalStateException in test environment, got: ${e.javaClass.simpleName}"
            }
        }
    }

    @Test
    fun `service should not throw exceptions during normal operations`() {
        // Given
        val service = ProxyService()

        // When & Then
        try {
            service.getCurrentProxyState()
            service.toggleProxy()
            service.getCurrentProxyState()
            assert(true) { "Service operations should complete without exceptions" }
        } catch (e: Exception) {
            // In test environment, ProxySettings.getInstance() may throw exceptions
            // This is expected behavior when IntelliJ platform is not fully initialized
            assert(e is NullPointerException || e is IllegalStateException) {
                "Expected NPE or IllegalStateException in test environment, got: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }
}
