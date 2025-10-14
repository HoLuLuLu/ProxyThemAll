package org.holululu.proxythemall

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Test utilities for mocking IntelliJ Platform dependencies
 */
object TestUtils {

    /**
     * Sets up mock IntelliJ Platform environment for pure JUnit 5 tests
     */
    fun setupMockIntellijEnvironment() {
        // Minimal mocking approach to avoid mockk 1.14.6 issues
        try {
            // Only mock the most essential static methods
            mockkStatic(ApplicationManager::class)
            val mockApplication = mockk<Application>(relaxed = true)
            every { ApplicationManager.getApplication() } returns mockApplication

            // Mock ProxyThemAllSettings
            mockkStatic(ProxyThemAllSettings::class)
            val mockSettings = mockk<ProxyThemAllSettings>(relaxed = true)
            every { mockSettings.showNotifications } returns true
            every { mockSettings.showStatusBarWidget } returns true
            every { mockSettings.applyProxyToGit } returns true
            every { mockSettings.enableGradleProxySupport } returns true
            every { ProxyThemAllSettings.getInstance() } returns mockSettings
            every { mockApplication.getService(ProxyThemAllSettings::class.java) } returns mockSettings

            // Mock NotificationGroupManager specifically
            mockkStatic(NotificationGroupManager::class)
            val mockNotificationGroupManager = mockk<NotificationGroupManager>(relaxed = true)
            every { NotificationGroupManager.getInstance() } returns mockNotificationGroupManager
            every { mockApplication.getService(NotificationGroupManager::class.java) } returns mockNotificationGroupManager

            // Make the Application.getService method return relaxed mocks for any other services
            every { mockApplication.getService(any<Class<*>>()) } returns mockk(relaxed = true)

            // Mock ProxySettings with minimal functionality
            mockkStatic(ProxySettings::class)
            val mockProxySettings = mockk<ProxySettings>(relaxed = true)
            val mockDirectProxy = object : ProxyConfiguration.DirectProxy {}
            every { mockProxySettings.getProxyConfiguration() } returns mockDirectProxy
            every { ProxySettings.getInstance() } returns mockProxySettings

        } catch (e: Exception) {
            // If mocking fails, just continue - tests should handle missing mocks gracefully
            println("Warning: Could not set up all mocks: ${e.message}")
        }
    }

    /**
     * Cleans up mock IntelliJ Platform environment
     */
    fun cleanupMockIntellijEnvironment() {
        try {
            // Unmock only the static mocks we actually set up
            unmockkStatic(ApplicationManager::class)
            unmockkStatic(ProxySettings::class)
            unmockkStatic(ProxyThemAllSettings::class)
            unmockkStatic(NotificationGroupManager::class)
        } catch (e: Exception) {
            // If cleanup fails, just continue - this shouldn't break tests
            println("Warning: Mock cleanup failed: ${e.message}")
        }
    }
}
