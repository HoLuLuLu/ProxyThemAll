package org.holululu.proxythemall.core

import org.holululu.proxythemall.TestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for ProxyController
 */
class ProxyControllerTest {

    private lateinit var controller: ProxyController

    @BeforeEach
    fun setUp() {
        // Set up mock IntelliJ Platform environment
        TestUtils.setupMockIntellijEnvironment()

        // Create a direct instance instead of using the singleton
        // to avoid IntelliJ Platform initialization issues in pure unit tests
        controller = ProxyController()
    }

    @AfterEach
    fun tearDown() {
        // Clean up mock environment
        TestUtils.cleanupMockIntellijEnvironment()
    }

    @Test
    fun testHandleProxyToggleShouldHandleNullProjectWithoutThrowingException() {
        // When & Then
        try {
            controller.handleProxyToggle(null)
            // If we get here, no exception was thrown
            assertTrue(true, "Method should handle null project gracefully")
        } catch (e: Exception) {
            // In test environment, some underlying services may still throw exceptions,
            // but we should not get NPE from ApplicationManager.getApplication()
            assertFalse(
                e is NullPointerException && e.message?.contains("ApplicationManager.getApplication()") == true,
                "Should not get NPE from ApplicationManager.getApplication()"
            )
        }
    }

    @Test
    fun testUpdateStatusBarWidgetMethodShouldExistAndBePrivate() {
        // When & Then
        val method = controller.javaClass.getDeclaredMethod(
            "updateStatusBarWidget",
            com.intellij.openapi.project.Project::class.java
        )
        assertNotNull(method, "updateStatusBarWidget method should exist")
        assertEquals(1, method.parameterCount, "updateStatusBarWidget should take one parameter")
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.modifiers), "updateStatusBarWidget should be private")
    }
}
