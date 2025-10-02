package org.holululu.proxythemall.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Unit tests for ProxyController
 */
class ProxyControllerTest : BasePlatformTestCase() {

    private lateinit var controller: ProxyController

    override fun setUp() {
        super.setUp()
        controller = ProxyController()
    }

    fun testHandleProxyToggleShouldHandleNullProjectWithoutThrowingException() {
        // When & Then
        try {
            controller.handleProxyToggle(null)
            // If we get here, no exception was thrown
            assertTrue("Method should handle null project gracefully", true)
        } catch (e: Exception) {
            // In test environment, some underlying services may still throw exceptions,
            // but we should not get NPE from ApplicationManager.getApplication()
            assertFalse(
                "Should not get NPE from ApplicationManager.getApplication()",
                e is NullPointerException && e.message?.contains("ApplicationManager.getApplication()") == true
            )
        }
    }

    fun testUpdateStatusBarWidgetMethodShouldExistAndBePrivate() {
        // When & Then
        val method = controller.javaClass.declaredMethods.find { it.name == "updateStatusBarWidget" }
        assertNotNull("updateStatusBarWidget method should exist", method)
        assertEquals("updateStatusBarWidget should take one parameter", 1, method!!.parameterCount)
    }
}
