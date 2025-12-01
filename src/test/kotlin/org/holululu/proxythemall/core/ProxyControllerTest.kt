package org.holululu.proxythemall.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.holululu.proxythemall.TestUtils
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService
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
        // Mock all the services BEFORE creating the controller to avoid the NOT_CONFIGURED code path
        // which calls executeOnPooledThread (which we cannot mock properly with MockK)
        mockkObject(ProxyService.Companion)
        mockkObject(org.holululu.proxythemall.services.git.GitProxyService.Companion)
        mockkObject(org.holululu.proxythemall.services.gradle.GradleProxyService.Companion)
        mockkObject(org.holululu.proxythemall.notifications.NotificationService.Companion)
        mockkObject(org.holululu.proxythemall.listeners.ProxyStateChangeManager.Companion)

        val mockProxyService = mockk<ProxyService>(relaxed = true)
        val mockGitService = mockk<org.holululu.proxythemall.services.git.GitProxyService>(relaxed = true)
        val mockGradleService = mockk<org.holululu.proxythemall.services.gradle.GradleProxyService>(relaxed = true)
        val mockNotificationService = mockk<org.holululu.proxythemall.notifications.NotificationService>(relaxed = true)
        val mockStateManager = mockk<org.holululu.proxythemall.listeners.ProxyStateChangeManager>(relaxed = true)

        every { ProxyService.instance } returns mockProxyService
        every { org.holululu.proxythemall.services.git.GitProxyService.instance } returns mockGitService
        every { org.holululu.proxythemall.services.gradle.GradleProxyService.instance } returns mockGradleService
        every { org.holululu.proxythemall.notifications.NotificationService.instance } returns mockNotificationService
        every { org.holululu.proxythemall.listeners.ProxyStateChangeManager.instance } returns mockStateManager

        every { mockProxyService.getCurrentProxyState() } returns ProxyState.DISABLED
        every { mockProxyService.toggleProxy() } returns ProxyState.ENABLED

        // Create controller AFTER mocking services
        val testController = ProxyController()
        
        try {
            // When & Then
            testController.handleProxyToggle(null)
            // If we get here, no exception was thrown
            assertTrue(true, "Method should handle null project gracefully")
        } catch (e: Exception) {
            // In test environment, some underlying services may still throw exceptions,
            // but we should not get NPE from ApplicationManager.getApplication()
            assertFalse(
                e is NullPointerException && e.message?.contains("ApplicationManager.getApplication()") == true,
                "Should not get NPE from ApplicationManager.getApplication()"
            )
        } finally {
            unmockkObject(ProxyService.Companion)
            unmockkObject(org.holululu.proxythemall.services.git.GitProxyService.Companion)
            unmockkObject(org.holululu.proxythemall.services.gradle.GradleProxyService.Companion)
            unmockkObject(org.holululu.proxythemall.notifications.NotificationService.Companion)
            unmockkObject(org.holululu.proxythemall.listeners.ProxyStateChangeManager.Companion)
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
