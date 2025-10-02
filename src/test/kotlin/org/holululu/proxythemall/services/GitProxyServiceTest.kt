package org.holululu.proxythemall.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.services.git.GitProxyService

/**
 * Basic test for GitProxyService functionality
 * Note: Full testing would require mocking Git command execution
 */
class GitProxyServiceTest : BasePlatformTestCase() {

    private lateinit var gitProxyService: GitProxyService

    override fun setUp() {
        super.setUp()
        gitProxyService = GitProxyService.instance
    }

    fun testGitProxyServiceInstance() {
        // Test that the service can be instantiated
        assertNotNull(gitProxyService)
    }

    fun testConfigureGitProxyDoesNotThrow() {
        // Test that configuring Git proxy doesn't throw exceptions
        // This is a basic smoke test
        try {
            gitProxyService.configureGitProxy(project) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            fail("configureGitProxy should not throw exceptions: ${e.message}")
        }
    }

    fun testRemoveGitProxySettingsDoesNotThrow() {
        // Test that removing Git proxy settings doesn't throw exceptions
        // This is a basic smoke test
        try {
            gitProxyService.removeGitProxySettings(project) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            fail("removeGitProxySettings should not throw exceptions: ${e.message}")
        }
    }
}
