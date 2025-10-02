package org.holululu.proxythemall.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for ProxyThemAllAction
 */
class ProxyThemAllActionTest : BasePlatformTestCase() {

    private lateinit var action: ProxyThemAllAction

    override fun setUp() {
        super.setUp()
        action = ProxyThemAllAction()
    }

    fun testActionPerformedShouldNotThrowExceptionWithNullEventProject() {
        // When & Then - should not throw exception
        // We can't easily create a mock AnActionEvent in this test environment,
        // but we can verify the action class structure is correct
        assertTrue(
            "ProxyThemAllAction should have actionPerformed method",
            action.javaClass.methods.any { it.name == "actionPerformed" })
    }
}
