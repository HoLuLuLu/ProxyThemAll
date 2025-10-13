package org.holululu.proxythemall

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import io.mockk.*
import org.holululu.proxythemall.listeners.ProxyStateChangeManager
import org.holululu.proxythemall.notifications.NotificationService
import org.holululu.proxythemall.services.ProxyInfoExtractor
import org.holululu.proxythemall.services.ProxyService
import org.holululu.proxythemall.services.git.GitProxyConfigurer
import org.holululu.proxythemall.services.git.GitProxyService
import org.holululu.proxythemall.services.gradle.GradleProxyConfigurer
import org.holululu.proxythemall.services.gradle.GradleProxyService
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Test utilities for mocking IntelliJ Platform dependencies
 */
object TestUtils {

    /**
     * Sets up mock IntelliJ Platform environment for pure JUnit 5 tests
     */
    fun setupMockIntellijEnvironment() {
        // Mock static getInstance methods
        mockkStatic(ApplicationManager::class)
        mockkStatic(ProxySettings::class)
        mockkStatic(ProxyThemAllSettings::class)

        // Mock ApplicationManager
        val mockApplication = mockk<Application>()
        every { ApplicationManager.getApplication() } returns mockApplication

        // Mock ProxyThemAllSettings
        val mockSettings = mockk<ProxyThemAllSettings>()
        every { mockSettings.showNotifications } returns true
        every { mockSettings.showStatusBarWidget } returns true
        every { mockSettings.applyProxyToGit } returns true
        every { mockSettings.enableGradleProxySupport } returns true
        every { ProxyThemAllSettings.getInstance() } returns mockSettings
        every { mockApplication.getService(ProxyThemAllSettings::class.java) } returns mockSettings

        // Mock ProxySettings
        val mockProxySettings = mockk<ProxySettings>()
        val mockDirectProxy = object : ProxyConfiguration.DirectProxy {}
        every { mockProxySettings.getProxyConfiguration() } returns mockDirectProxy
        every { mockProxySettings.setProxyConfiguration(any()) } just Runs
        every { ProxySettings.getInstance() } returns mockProxySettings

        // Mock service objects using mockkObject
        mockkObject(ProxyInfoExtractor)
        every { ProxyInfoExtractor.instance } returns mockk<ProxyInfoExtractor> {
            every { extractProxyInfo(any()) } returns null
        }

        mockkObject(GitProxyConfigurer)
        every { GitProxyConfigurer.instance } returns mockk<GitProxyConfigurer> {
            every { setGitProxy(any(), any(), any()) } answers {
                val callback = thirdArg<(String) -> Unit>()
                callback("Git proxy configured")
            }
            every { removeGitProxySettings(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Git proxy removed")
            }
        }

        mockkObject(GradleProxyConfigurer)
        every { GradleProxyConfigurer.instance } returns mockk<GradleProxyConfigurer> {
            every { setGradleProxy(any(), any(), any()) } answers {
                val callback = thirdArg<(String) -> Unit>()
                callback("Gradle proxy configured")
            }
            every { removeGradleProxySettings(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Gradle proxy removed")
            }
        }

        // Mock service singletons
        mockkObject(ProxyService)
        every { ProxyService.instance } returns mockk<ProxyService> {
            every { getCurrentProxyState() } returns org.holululu.proxythemall.models.ProxyState.DISABLED
            every { toggleProxy() } returns org.holululu.proxythemall.models.ProxyState.ENABLED
            every { getCurrentProxyConfiguration() } returns mockDirectProxy
        }

        mockkObject(GitProxyService)
        every { GitProxyService.instance } returns mockk<GitProxyService> {
            every { configureGitProxy(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Git proxy configured")
            }
            every { removeGitProxySettings(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Git proxy removed")
            }
        }

        mockkObject(GradleProxyService)
        every { GradleProxyService.instance } returns mockk<GradleProxyService> {
            every { configureGradleProxy(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Gradle proxy configured")
            }
            every { removeGradleProxySettings(any(), any()) } answers {
                val callback = secondArg<(String) -> Unit>()
                callback("Gradle proxy removed")
            }
        }

        mockkObject(NotificationService)
        every { NotificationService.instance } returns mockk<NotificationService> {
            every { showNotification(any(), any()) } just Runs
        }

        mockkObject(ProxyStateChangeManager)
        every { ProxyStateChangeManager.instance } returns mockk<ProxyStateChangeManager> {
            every { notifyStateChanged() } just Runs
        }
    }

    /**
     * Cleans up mock IntelliJ Platform environment
     */
    fun cleanupMockIntellijEnvironment() {
        // Unmock all static and object mocks
        unmockkStatic(ApplicationManager::class)
        unmockkStatic(ProxySettings::class)
        unmockkStatic(ProxyThemAllSettings::class)

        unmockkObject(ProxyInfoExtractor)
        unmockkObject(GitProxyConfigurer)
        unmockkObject(GradleProxyConfigurer)
        unmockkObject(ProxyService)
        unmockkObject(GitProxyService)
        unmockkObject(GradleProxyService)
        unmockkObject(NotificationService)
        unmockkObject(ProxyStateChangeManager)
    }
}
