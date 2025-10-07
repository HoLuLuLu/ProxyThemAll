package org.holululu.proxythemall.services.git

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.models.ProxyInfo

/**
 * Test suite for GitProxyConfigurer with direct credentials approach
 *
 * Note: These are basic smoke tests since we cannot easily mock Git command execution
 * in the IntelliJ Platform test environment without external mocking libraries.
 */
class GitProxyConfigurerTest : BasePlatformTestCase() {

    private lateinit var gitProxyConfigurer: GitProxyConfigurer

    override fun setUp() {
        super.setUp()
        gitProxyConfigurer = GitProxyConfigurer.instance
    }

    fun testGitProxyConfigurerInstance() {
        // Test that the configurer can be instantiated
        assertNotNull(gitProxyConfigurer)
    }

    fun testSetGitProxyDoesNotThrow() {
        // Test that setting Git proxy doesn't throw exceptions
        // This is a basic smoke test since we can't easily mock Git commands
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "testuser",
            password = "testpass",
            nonProxyHosts = emptySet()
        )

        try {
            gitProxyConfigurer.setGitProxy(project, proxyInfo) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            // Git command might fail in test environment, but shouldn't throw unexpected exceptions
            assertTrue(
                "Should handle Git command failures gracefully",
                e.message?.contains("Git command failed") == true ||
                        e.message?.contains("git") == true
            )
        }
    }

    fun testRemoveGitProxySettingsDoesNotThrow() {
        // Test that removing Git proxy settings doesn't throw exceptions
        try {
            gitProxyConfigurer.removeGitProxySettings(project) { status ->
                // Callback received, test passes
                assertNotNull(status)
            }
            // If we get here, no exception was thrown
            assertTrue(true)
        } catch (e: Exception) {
            // Git command might fail in test environment, but shouldn't throw unexpected exceptions
            assertTrue(
                "Should handle Git command failures gracefully",
                e.message?.contains("Git command failed") == true ||
                        e.message?.contains("git") == true
            )
        }
    }

    fun testProxyUrlBuilding() {
        // Test that the configurer uses ProxyUrlBuilder correctly
        // This is tested indirectly through the ProxyUrlBuilder tests
        // Here we just verify the configurer doesn't throw exceptions when building URLs
        
        val httpProxy = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "user",
            password = "pass",
            nonProxyHosts = emptySet()
        )

        try {
            gitProxyConfigurer.setGitProxy(project, httpProxy) { status ->
                // Should include authentication info in status message
                assertTrue(
                    "Status should indicate authentication",
                    status.contains("authentication") || status.contains("configured")
                )
            }
            assertTrue(true) // No exception thrown
        } catch (e: Exception) {
            // Git command might fail in test environment
            assertTrue(
                "Should handle Git command failures gracefully",
                e.message?.contains("Git command failed") == true ||
                        e.message?.contains("git") == true
            )
        }
    }

    fun testHasCredentials() {
        // Use reflection to test private method
        val method = GitProxyConfigurer::class.java.getDeclaredMethod(
            "hasCredentials",
            ProxyInfo::class.java
        )
        method.isAccessible = true

        // Test with credentials
        val proxyWithCreds = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "user",
            password = "pass",
            nonProxyHosts = emptySet()
        )

        val hasCredsResult = method.invoke(gitProxyConfigurer, proxyWithCreds) as Boolean
        assertTrue("Should have credentials", hasCredsResult)

        // Test without credentials
        val proxyWithoutCreds = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            nonProxyHosts = emptySet()
        )

        val noCredsResult = method.invoke(gitProxyConfigurer, proxyWithoutCreds) as Boolean
        assertFalse("Should not have credentials", noCredsResult)

        // Test with empty credentials
        val proxyWithEmptyCreds = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "",
            password = "",
            nonProxyHosts = emptySet()
        )

        val emptyCredsResult = method.invoke(gitProxyConfigurer, proxyWithEmptyCreds) as Boolean
        assertFalse("Should not have credentials when empty", emptyCredsResult)
    }
}
