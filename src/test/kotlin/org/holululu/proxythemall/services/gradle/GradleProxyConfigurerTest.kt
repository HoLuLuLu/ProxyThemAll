package org.holululu.proxythemall.services.gradle

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.models.ProxyInfo
import java.io.File

/**
 * Test for GradleProxyConfigurer functionality with file-based proxy configuration
 */
class GradleProxyConfigurerTest : BasePlatformTestCase() {

    private lateinit var gradleProxyConfigurer: GradleProxyConfigurer
    private var tempPropertiesFile: File? = null

    override fun setUp() {
        super.setUp()
        gradleProxyConfigurer = GradleProxyConfigurer.instance
    }

    override fun tearDown() {
        tempPropertiesFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        super.tearDown()
    }

    fun testHasCredentials() {
        // Test the hasCredentials helper method
        val hasCredentialsMethod = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "hasCredentials",
            ProxyInfo::class.java
        )
        hasCredentialsMethod.isAccessible = true

        // Test with credentials
        val proxyWithCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "testuser",
            password = "testpass",
            nonProxyHosts = emptySet()
        )
        val hasCredentials = hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithCredentials) as Boolean
        assertTrue("Should have credentials", hasCredentials)

        // Test without credentials
        val proxyWithoutCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            nonProxyHosts = emptySet()
        )
        val hasNoCredentials = hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithoutCredentials) as Boolean
        assertFalse("Should not have credentials", hasNoCredentials)

        // Test with blank credentials
        val proxyWithBlankCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "",
            password = "  ",
            nonProxyHosts = emptySet()
        )
        val hasBlankCredentials =
            hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithBlankCredentials) as Boolean
        assertFalse("Should not have credentials when blank", hasBlankCredentials)
    }

    fun testBuildProxySettingsContentWithoutCredentials() {
        // Test that proxy settings content is built correctly without credentials
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            nonProxyHosts = setOf("localhost", "127.*", "[::1]", "internal.company.com")
        )

        // Use reflection to access the private method for testing
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "buildProxySettingsContent",
            ProxyInfo::class.java
        )
        method.isAccessible = true
        val content = method.invoke(gradleProxyConfigurer, proxyInfo) as String

        // Verify section markers are present
        assertTrue(
            "Should contain start marker",
            content.contains("=== ProxyThemAll Managed Proxy Settings - START ===")
        )
        assertTrue("Should contain end marker", content.contains("=== ProxyThemAll Managed Proxy Settings - END ==="))

        // Verify host and port are set
        assertTrue("Should contain HTTP proxy host", content.contains("systemProp.http.proxyHost=proxy.example.com"))
        assertTrue("Should contain HTTP proxy port", content.contains("systemProp.http.proxyPort=8080"))
        assertTrue("Should contain HTTPS proxy host", content.contains("systemProp.https.proxyHost=proxy.example.com"))
        assertTrue("Should contain HTTPS proxy port", content.contains("systemProp.https.proxyPort=8080"))

        // Verify non-proxy hosts are set (including custom ones)
        assertTrue("Should contain non-proxy hosts", content.contains("internal.company.com"))
        assertTrue("Should contain localhost", content.contains("localhost"))
        assertTrue("Should contain 127.*", content.contains("127.*"))
        assertTrue("Should contain [::1]", content.contains("[::1]"))

        // Verify JVM args are configured for proxy selector (fallback approach)
        assertTrue(
            "Should contain JVM args for proxy selector",
            content.contains("org.gradle.jvmargs=-Djava.net.useSystemProxies=true")
        )

        // Verify credentials are NOT set (fallback approach)
        assertFalse("Should not contain HTTP proxy user", content.contains("systemProp.http.proxyUser="))
        assertFalse("Should not contain HTTP proxy password", content.contains("systemProp.http.proxyPassword="))
        assertFalse("Should not contain HTTPS proxy user", content.contains("systemProp.https.proxyUser="))
        assertFalse("Should not contain HTTPS proxy password", content.contains("systemProp.https.proxyPassword="))
    }

    fun testBuildProxySettingsContentWithCredentials() {
        // Test that proxy settings content is built correctly WITH credentials
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "testuser",
            password = "testpass",
            nonProxyHosts = setOf("localhost", "127.*", "[::1]")
        )

        // Use reflection to access the private method for testing
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "buildProxySettingsContent",
            ProxyInfo::class.java
        )
        method.isAccessible = true
        val content = method.invoke(gradleProxyConfigurer, proxyInfo) as String

        // Verify section markers are present
        assertTrue(
            "Should contain start marker",
            content.contains("=== ProxyThemAll Managed Proxy Settings - START ===")
        )
        assertTrue("Should contain end marker", content.contains("=== ProxyThemAll Managed Proxy Settings - END ==="))

        // Verify host and port are set
        assertTrue("Should contain HTTP proxy host", content.contains("systemProp.http.proxyHost=proxy.example.com"))
        assertTrue("Should contain HTTP proxy port", content.contains("systemProp.http.proxyPort=8080"))
        assertTrue("Should contain HTTPS proxy host", content.contains("systemProp.https.proxyHost=proxy.example.com"))
        assertTrue("Should contain HTTPS proxy port", content.contains("systemProp.https.proxyPort=8080"))

        // Verify non-proxy hosts are set (default ones)
        assertTrue("Should contain localhost", content.contains("localhost"))
        assertTrue("Should contain 127.*", content.contains("127.*"))
        assertTrue("Should contain [::1]", content.contains("[::1]"))

        // Verify credentials ARE set (direct credential support)
        assertTrue("Should contain HTTP proxy user", content.contains("systemProp.http.proxyUser=testuser"))
        assertTrue("Should contain HTTP proxy password", content.contains("systemProp.http.proxyPassword=testpass"))
        assertTrue("Should contain HTTPS proxy user", content.contains("systemProp.https.proxyUser=testuser"))
        assertTrue("Should contain HTTPS proxy password", content.contains("systemProp.https.proxyPassword=testpass"))

        // Verify JVM args are NOT set when using direct credentials
        assertFalse(
            "Should not contain JVM args when using direct credentials",
            content.contains("org.gradle.jvmargs=-Djava.net.useSystemProxies=true")
        )
    }

    fun testRemoveProxyProperties() {
        // Test that proxy properties are properly removed using the new structure-preserving approach
        
        // Create a temporary properties file for this test
        tempPropertiesFile = File.createTempFile("gradle", ".properties")
        tempPropertiesFile!!.deleteOnExit()

        // Create a file with existing content and ProxyThemAll managed section
        val existingContent = """
            # Some existing comment
            pluginGroup=org.example
            pluginVersion=1.0.0
            
            # === ProxyThemAll Managed Proxy Settings - START ===
            # These settings are automatically managed by ProxyThemAll plugin
            # Manual changes to this section will be overwritten
            
            # HTTP Proxy Configuration
            systemProp.http.proxyHost=proxy.example.com
            systemProp.http.proxyPort=8080
            
            # HTTPS Proxy Configuration
            systemProp.https.proxyHost=proxy.example.com
            systemProp.https.proxyPort=8080
            
            # Non-proxy hosts (pipe-separated)
            systemProp.http.nonProxyHosts=localhost|127.*|[::1]
            systemProp.https.nonProxyHosts=localhost|127.*|[::1]
            
            # Proxy Authentication
            systemProp.http.proxyUser=testuser
            systemProp.http.proxyPassword=testpass
            systemProp.https.proxyUser=testuser
            systemProp.https.proxyPassword=testpass
            
            # === ProxyThemAll Managed Proxy Settings - END ===
            
            # More existing content
            org.gradle.caching=true
        """.trimIndent()

        tempPropertiesFile!!.writeText(existingContent)

        // Use reflection to access the private method
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "removeProxyPropertiesFromFile",
            File::class.java
        )
        method.isAccessible = true
        val result = method.invoke(gradleProxyConfigurer, tempPropertiesFile!!) as Boolean

        assertTrue("Should have removed properties", result)

        // Read the file content back and verify the ProxyThemAll section was removed
        val finalContent = tempPropertiesFile!!.readText()

        // Verify the ProxyThemAll managed section is completely removed
        assertFalse(
            "Should not contain ProxyThemAll start marker",
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings - START ===")
        )
        assertFalse(
            "Should not contain ProxyThemAll end marker",
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings - END ===")
        )
        assertFalse(
            "Should not contain proxy host",
            finalContent.contains("systemProp.http.proxyHost=proxy.example.com")
        )
        assertFalse(
            "Should not contain proxy authentication",
            finalContent.contains("systemProp.http.proxyUser=testuser")
        )

        // Verify existing content is preserved
        assertTrue("Should preserve existing comment", finalContent.contains("# Some existing comment"))
        assertTrue("Should preserve existing property", finalContent.contains("pluginGroup=org.example"))
        assertTrue("Should preserve existing property", finalContent.contains("pluginVersion=1.0.0"))
        assertTrue("Should preserve existing property", finalContent.contains("org.gradle.caching=true"))
        assertTrue("Should preserve other comments", finalContent.contains("# More existing content"))
    }

    fun testPreserveFileStructureAndComments() {
        // Test that the new implementation preserves existing file structure and comments

        // Create a temporary properties file for this test
        tempPropertiesFile = File.createTempFile("gradle", ".properties")
        tempPropertiesFile!!.deleteOnExit()

        // Create a file similar to the actual gradle.properties with rich comments and structure
        val originalContent = """
            # IntelliJ Platform Artifacts Repositories -> https://plugins.jetbrains.com/docs/intellij/intellij-artifacts.html
            pluginGroup=org.holululu.proxythemall
            pluginName = ProxyThemAll
            pluginRepositoryUrl = https://github.com/HoLuLuLu/ProxyThemAll
            # SemVer format -> https://semver.org
            pluginVersion=0.0.3

            # Supported build number ranges and IntelliJ Platform versions -> https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html
            pluginSinceBuild = 243

            # IntelliJ Platform Properties -> https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#configuration-intellij-extension
            platformType = IC
            platformVersion = 2024.3.6

            # Plugin Dependencies -> https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html
            # Example: platformPlugins = com.jetbrains.php:203.4449.22, org.intellij.scala:2023.3.27@EAP
            platformPlugins =
            # Example: platformBundledPlugins = com.intellij.java
            platformBundledPlugins=Git4Idea

            # Gradle Releases -> https://github.com/gradle/gradle/releases
            gradleVersion = 9.0.0

            # Enable Gradle Configuration Cache -> https://docs.gradle.org/current/userguide/configuration_cache.html
            org.gradle.configuration-cache = true

            # Enable Gradle Build Cache -> https://docs.gradle.org/current/userguide/build_cache.html
            org.gradle.caching = true
        """.trimIndent()

        tempPropertiesFile!!.writeText(originalContent)

        // Create proxy info with credentials
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "testuser",
            password = "testpass",
            nonProxyHosts = setOf("localhost", "127.*", "[::1]", "internal.company.com")
        )

        // Use reflection to access the private method to configure the file
        val configureMethod = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "configureGradlePropertiesFile",
            File::class.java,
            ProxyInfo::class.java
        )
        configureMethod.isAccessible = true
        configureMethod.invoke(gradleProxyConfigurer, tempPropertiesFile!!, proxyInfo)

        // Read the content after adding proxy settings
        val contentWithProxy = tempPropertiesFile!!.readText()

        // Verify all original content is preserved
        assertTrue(
            "Should preserve IntelliJ Platform comment",
            contentWithProxy.contains("# IntelliJ Platform Artifacts Repositories")
        )
        assertTrue(
            "Should preserve plugin group",
            contentWithProxy.contains("pluginGroup=org.holululu.proxythemall")
        )
        assertTrue(
            "Should preserve plugin name",
            contentWithProxy.contains("pluginName = ProxyThemAll")
        )
        assertTrue(
            "Should preserve SemVer comment",
            contentWithProxy.contains("# SemVer format -> https://semver.org")
        )
        assertTrue(
            "Should preserve version",
            contentWithProxy.contains("pluginVersion=0.0.3")
        )
        assertTrue(
            "Should preserve build number comment",
            contentWithProxy.contains("# Supported build number ranges")
        )
        assertTrue(
            "Should preserve platform properties",
            contentWithProxy.contains("platformType = IC")
        )
        assertTrue(
            "Should preserve example comments",
            contentWithProxy.contains("# Example: platformPlugins")
        )
        assertTrue(
            "Should preserve gradle version",
            contentWithProxy.contains("gradleVersion = 9.0.0")
        )
        assertTrue(
            "Should preserve configuration cache",
            contentWithProxy.contains("org.gradle.configuration-cache = true")
        )
        assertTrue(
            "Should preserve build cache",
            contentWithProxy.contains("org.gradle.caching = true")
        )

        // Verify proxy settings were added with proper structure
        assertTrue(
            "Should contain ProxyThemAll start marker",
            contentWithProxy.contains("# === ProxyThemAll Managed Proxy Settings - START ===")
        )
        assertTrue(
            "Should contain ProxyThemAll end marker",
            contentWithProxy.contains("# === ProxyThemAll Managed Proxy Settings - END ===")
        )
        assertTrue(
            "Should contain proxy host",
            contentWithProxy.contains("systemProp.http.proxyHost=proxy.example.com")
        )
        assertTrue(
            "Should contain proxy authentication",
            contentWithProxy.contains("systemProp.http.proxyUser=testuser")
        )
        assertTrue(
            "Should contain custom non-proxy hosts",
            contentWithProxy.contains("localhost|127.*|[::1]|internal.company.com")
        )

        // Now remove proxy settings and verify original structure is restored
        val removeMethod = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "removeProxyPropertiesFromFile",
            File::class.java
        )
        removeMethod.isAccessible = true
        val removed = removeMethod.invoke(gradleProxyConfigurer, tempPropertiesFile!!) as Boolean

        assertTrue("Should have removed proxy settings", removed)

        val finalContent = tempPropertiesFile!!.readText()

        // Verify proxy settings are completely removed
        assertFalse(
            "Should not contain ProxyThemAll markers",
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings")
        )
        assertFalse(
            "Should not contain proxy settings",
            finalContent.contains("systemProp.http.proxyHost")
        )

        // Verify all original content is still preserved
        assertTrue(
            "Should still preserve IntelliJ Platform comment",
            finalContent.contains("# IntelliJ Platform Artifacts Repositories")
        )
        assertTrue(
            "Should still preserve plugin group",
            finalContent.contains("pluginGroup=org.holululu.proxythemall")
        )
        assertTrue(
            "Should still preserve plugin name",
            finalContent.contains("pluginName = ProxyThemAll")
        )
        assertTrue(
            "Should still preserve all comments and properties",
            finalContent.contains("org.gradle.caching = true")
        )

        // Verify the content is essentially the same as original (allowing for minor whitespace differences)
        val normalizedOriginal = originalContent.replace("\\s+".toRegex(), " ").trim()
        val normalizedFinal = finalContent.replace("\\s+".toRegex(), " ").trim()
        assertEquals("Content should be restored to original state", normalizedOriginal, normalizedFinal)
    }
}
