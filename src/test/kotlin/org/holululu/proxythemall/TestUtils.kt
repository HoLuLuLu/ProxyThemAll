package org.holululu.proxythemall

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import io.mockk.*
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
            // Clear any existing mocks
            clearAllMocks()
            
            // Only mock the most essential static methods
            mockkStatic(ApplicationManager::class)

            // Create a NON-relaxed Application mock and set up behaviors explicitly
            val mockApplication = mockk<Application>()

            // Set up executeOnPooledThread FIRST before anything else
            every { mockApplication.executeOnPooledThread(any()) } answers {
                // Get the Runnable from the invocation and execute it immediately
                val runnable = invocation.args[0] as? Runnable
                runnable?.run()
                mockk(relaxed = true) // Return a mock Future
            }

            // Set up getService to return relaxed mocks
            every { mockApplication.getService(any<Class<*>>()) } returns mockk(relaxed = true)

            every { ApplicationManager.getApplication() } returns mockApplication

            // Mock ProxyThemAllSettings
            mockkStatic(ProxyThemAllSettings::class)
            val mockSettings = mockk<ProxyThemAllSettings>(relaxed = true)
            every { mockSettings.showNotifications } returns true
            every { mockSettings.showStatusBarWidget } returns true
            every { mockSettings.applyProxyToGit } returns true
            every { mockSettings.enableGradleProxySupport } returns true
            every { mockSettings.lastKnownProxyEnabled } returns false
            every { ProxyThemAllSettings.getInstance() } returns mockSettings
            every { mockApplication.getService(ProxyThemAllSettings::class.java) } returns mockSettings

            // Mock ProxyCredentialsStorage
            mockkStatic(org.holululu.proxythemall.services.ProxyCredentialsStorage::class)
            val mockCredentialsStorage =
                mockk<org.holululu.proxythemall.services.ProxyCredentialsStorage>(relaxed = true)
            every { mockCredentialsStorage.hasStoredConfiguration() } returns false
            every { mockCredentialsStorage.loadProxyConfiguration() } returns null
            every { org.holululu.proxythemall.services.ProxyCredentialsStorage.getInstance() } returns mockCredentialsStorage
            every { mockApplication.getService(org.holululu.proxythemall.services.ProxyCredentialsStorage::class.java) } returns mockCredentialsStorage

            // Mock NotificationGroupManager specifically
            mockkStatic(NotificationGroupManager::class)
            val mockNotificationGroupManager = mockk<NotificationGroupManager>(relaxed = true)
            every { NotificationGroupManager.getInstance() } returns mockNotificationGroupManager
            every { mockApplication.getService(NotificationGroupManager::class.java) } returns mockNotificationGroupManager

            // Mock ProxySettings with minimal functionality
            // Return DirectProxy so the proxy state will be DISABLED, avoiding the NOT_CONFIGURED path
            mockkStatic(ProxySettings::class)
            val mockProxySettings = mockk<ProxySettings>(relaxed = true)
            val mockDirectProxy = object : ProxyConfiguration.DirectProxy {}
            every { mockProxySettings.getProxyConfiguration() } returns mockDirectProxy
            every { ProxySettings.getInstance() } returns mockProxySettings
            every { mockApplication.getService(ProxySettings::class.java) } returns mockProxySettings

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
            unmockkStatic(org.holululu.proxythemall.services.ProxyCredentialsStorage::class)
            unmockkStatic(NotificationGroupManager::class)
        } catch (e: Exception) {
            // If cleanup fails, just continue - this shouldn't break tests
            println("Warning: Mock cleanup failed: ${e.message}")
        }
    }
}
