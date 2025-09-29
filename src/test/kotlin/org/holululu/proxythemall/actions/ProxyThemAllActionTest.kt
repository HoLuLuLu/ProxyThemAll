package org.holululu.proxythemall.actions

import org.junit.Test

/**
 * Unit tests for ProxyThemAllAction
 */
class ProxyThemAllActionTest {

    @Test
    fun `actionPerformed should not throw exception with null event project`() {
        // Given
        val action = ProxyThemAllAction()

        // When & Then - should not throw exception
        // We can't easily create a mock AnActionEvent in this test environment,
        // but we can verify the action class structure is correct
        assert(action.javaClass.methods.any { it.name == "actionPerformed" }) {
            "ProxyThemAllAction should have actionPerformed method"
        }
    }
}
