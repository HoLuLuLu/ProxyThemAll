package org.holululu.proxythemall.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.services.gradle.GradleProxyService

/**
 * Basic test for GradleProxyService functionality
 * Note: Full testing would require mocking Gradle properties file operations
 */
class GradleProxyServiceTest : BasePlatformTestCase() {

    private lateinit var gradleProxyService: GradleProxyService

    override fun setUp() {
        super.setUp()
        gradleProxyService = GradleProxyService.instance
    }

    fun testGradleProxyServiceInstance() {
        // Test that the service can be instantiated
        assertNotNull(gradleProxyService)
    }

    fun testConfigureGradleProxyDoesNotThrow() {
        // Test that configuring Gradle proxy doesn't throw exceptions
        // This is a basic smoke test
        try {
            gradleProxyService.configureGradleProxy(project) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            fail("configureGradleProxy should not throw exceptions: ${e.message}")
        }
    }

    fun testRemoveGradleProxySettingsDoesNotThrow() {
        // Test that removing Gradle proxy settings doesn't throw exceptions
        // This is a basic smoke test
        try {
            gradleProxyService.removeGradleProxySettings(project) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            fail("removeGradleProxySettings should not throw exceptions: ${e.message}")
        }
    }
}
