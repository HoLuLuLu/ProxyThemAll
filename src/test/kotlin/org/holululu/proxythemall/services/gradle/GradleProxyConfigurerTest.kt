package org.holululu.proxythemall.services.gradle

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Test for GradleProxyConfigurer functionality with file-based proxy configuration
 */
class GradleProxyConfigurerTest {

    private lateinit var gradleProxyConfigurer: GradleProxyConfigurer
    private var tempPropertiesFile: File? = null

    @BeforeEach
    fun setUp() {
        gradleProxyConfigurer = GradleProxyConfigurer.instance
    }

    @AfterEach
    fun tearDown() {
        tempPropertiesFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Test
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
        assertTrue(hasCredentials, "Should have credentials")

        // Test without credentials
        val proxyWithoutCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            nonProxyHosts = emptySet()
        )
        val hasNoCredentials = hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithoutCredentials) as Boolean
        assertFalse(hasNoCredentials, "Should not have credentials")

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
        assertFalse(hasBlankCredentials, "Should not have credentials when blank")
    }

    @Test
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
            content.contains("=== ProxyThemAll Managed Proxy Settings - START ==="),
            "Should contain start marker"
        )
        assertTrue(content.contains("=== ProxyThemAll Managed Proxy Settings - END ==="), "Should contain end marker")

        // Verify host and port are set
        assertTrue(content.contains("systemProp.http.proxyHost=proxy.example.com"), "Should contain HTTP proxy host")
        assertTrue(content.contains("systemProp.http.proxyPort=8080"), "Should contain HTTP proxy port")
        assertTrue(content.contains("systemProp.https.proxyHost=proxy.example.com"), "Should contain HTTPS proxy host")
        assertTrue(content.contains("systemProp.https.proxyPort=8080"), "Should contain HTTPS proxy port")

        // Verify non-proxy hosts are set (including custom ones)
        assertTrue(content.contains("internal.company.com"), "Should contain non-proxy hosts")
        assertTrue(content.contains("localhost"), "Should contain localhost")
        assertTrue(content.contains("127.*"), "Should contain 127.*")
        assertTrue(content.contains("[::1]"), "Should contain [::1]")

        // Verify JVM args are configured for proxy selector (fallback approach)
        assertTrue(
            content.contains("org.gradle.jvmargs=-Djava.net.useSystemProxies=true"),
            "Should contain JVM args for proxy selector"
        )

        // Verify credentials are NOT set (fallback approach)
        assertFalse(content.contains("systemProp.http.proxyUser="), "Should not contain HTTP proxy user")
        assertFalse(content.contains("systemProp.http.proxyPassword="), "Should not contain HTTP proxy password")
        assertFalse(content.contains("systemProp.https.proxyUser="), "Should not contain HTTPS proxy user")
        assertFalse(content.contains("systemProp.https.proxyPassword="), "Should not contain HTTPS proxy password")
    }

    @Test
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
            content.contains("=== ProxyThemAll Managed Proxy Settings - START ==="),
            "Should contain start marker"
        )
        assertTrue(content.contains("=== ProxyThemAll Managed Proxy Settings - END ==="), "Should contain end marker")

        // Verify host and port are set
        assertTrue(content.contains("systemProp.http.proxyHost=proxy.example.com"), "Should contain HTTP proxy host")
        assertTrue(content.contains("systemProp.http.proxyPort=8080"), "Should contain HTTP proxy port")
        assertTrue(content.contains("systemProp.https.proxyHost=proxy.example.com"), "Should contain HTTPS proxy host")
        assertTrue(content.contains("systemProp.https.proxyPort=8080"), "Should contain HTTPS proxy port")

        // Verify non-proxy hosts are set (default ones)
        assertTrue(content.contains("localhost"), "Should contain localhost")
        assertTrue(content.contains("127.*"), "Should contain 127.*")
        assertTrue(content.contains("[::1]"), "Should contain [::1]")

        // Verify credentials ARE set (direct credential support)
        assertTrue(content.contains("systemProp.http.proxyUser=testuser"), "Should contain HTTP proxy user")
        assertTrue(content.contains("systemProp.http.proxyPassword=testpass"), "Should contain HTTP proxy password")
        assertTrue(content.contains("systemProp.https.proxyUser=testuser"), "Should contain HTTPS proxy user")
        assertTrue(content.contains("systemProp.https.proxyPassword=testpass"), "Should contain HTTPS proxy password")

        // Verify JVM args are NOT set when using direct credentials
        assertFalse(
            content.contains("org.gradle.jvmargs=-Djava.net.useSystemProxies=true"),
            "Should not contain JVM args when using direct credentials"
        )
    }

    @Test
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

        assertTrue(result, "Should have removed properties")

        // Read the file content back and verify the ProxyThemAll section was removed
        val finalContent = tempPropertiesFile!!.readText()

        // Verify the ProxyThemAll managed section is completely removed
        assertFalse(
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings - START ==="),
            "Should not contain ProxyThemAll start marker"
        )
        assertFalse(
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings - END ==="),
            "Should not contain ProxyThemAll end marker"
        )
        assertFalse(
            finalContent.contains("systemProp.http.proxyHost=proxy.example.com"),
            "Should not contain proxy host"
        )
        assertFalse(
            finalContent.contains("systemProp.http.proxyUser=testuser"),
            "Should not contain proxy authentication"
        )

        // Verify existing content is preserved
        assertTrue(finalContent.contains("# Some existing comment"), "Should preserve existing comment")
        assertTrue(finalContent.contains("pluginGroup=org.example"), "Should preserve existing property")
        assertTrue(finalContent.contains("pluginVersion=1.0.0"), "Should preserve existing property")
        assertTrue(finalContent.contains("org.gradle.caching=true"), "Should preserve existing property")
        assertTrue(finalContent.contains("# More existing content"), "Should preserve other comments")
    }

    @Test
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
            contentWithProxy.contains("# IntelliJ Platform Artifacts Repositories"),
            "Should preserve IntelliJ Platform comment"
        )
        assertTrue(
            contentWithProxy.contains("pluginGroup=org.holululu.proxythemall"),
            "Should preserve plugin group"
        )
        assertTrue(
            contentWithProxy.contains("pluginName = ProxyThemAll"),
            "Should preserve plugin name"
        )
        assertTrue(
            contentWithProxy.contains("# SemVer format -> https://semver.org"),
            "Should preserve SemVer comment"
        )
        assertTrue(
            contentWithProxy.contains("pluginVersion=0.0.3"),
            "Should preserve version"
        )
        assertTrue(
            contentWithProxy.contains("# Supported build number ranges"),
            "Should preserve build number comment"
        )
        assertTrue(
            contentWithProxy.contains("platformType = IC"),
            "Should preserve platform properties"
        )
        assertTrue(
            contentWithProxy.contains("# Example: platformPlugins"),
            "Should preserve example comments"
        )
        assertTrue(
            contentWithProxy.contains("gradleVersion = 9.0.0"),
            "Should preserve gradle version"
        )
        assertTrue(
            contentWithProxy.contains("org.gradle.configuration-cache = true"),
            "Should preserve configuration cache"
        )
        assertTrue(
            contentWithProxy.contains("org.gradle.caching = true"),
            "Should preserve build cache"
        )

        // Verify proxy settings were added with proper structure
        assertTrue(
            contentWithProxy.contains("# === ProxyThemAll Managed Proxy Settings - START ==="),
            "Should contain ProxyThemAll start marker"
        )
        assertTrue(
            contentWithProxy.contains("# === ProxyThemAll Managed Proxy Settings - END ==="),
            "Should contain ProxyThemAll end marker"
        )
        assertTrue(
            contentWithProxy.contains("systemProp.http.proxyHost=proxy.example.com"),
            "Should contain proxy host"
        )
        assertTrue(
            contentWithProxy.contains("systemProp.http.proxyUser=testuser"),
            "Should contain proxy authentication"
        )
        assertTrue(
            contentWithProxy.contains("localhost|127.*|[::1]|internal.company.com"),
            "Should contain custom non-proxy hosts"
        )

        // Now remove proxy settings and verify original structure is restored
        val removeMethod = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "removeProxyPropertiesFromFile",
            File::class.java
        )
        removeMethod.isAccessible = true
        val removed = removeMethod.invoke(gradleProxyConfigurer, tempPropertiesFile!!) as Boolean

        assertTrue(removed, "Should have removed proxy settings")

        val finalContent = tempPropertiesFile!!.readText()

        // Verify proxy settings are completely removed
        assertFalse(
            finalContent.contains("=== ProxyThemAll Managed Proxy Settings"),
            "Should not contain ProxyThemAll markers"
        )
        assertFalse(
            finalContent.contains("systemProp.http.proxyHost"),
            "Should not contain proxy settings"
        )

        // Verify all original content is still preserved
        assertTrue(
            finalContent.contains("# IntelliJ Platform Artifacts Repositories"),
            "Should still preserve IntelliJ Platform comment"
        )
        assertTrue(
            finalContent.contains("pluginGroup=org.holululu.proxythemall"),
            "Should still preserve plugin group"
        )
        assertTrue(
            finalContent.contains("pluginName = ProxyThemAll"),
            "Should still preserve plugin name"
        )
        assertTrue(
            finalContent.contains("org.gradle.caching = true"),
            "Should still preserve all comments and properties"
        )

        // Verify the content is essentially the same as original (allowing for minor whitespace differences)
        val normalizedOriginal = originalContent.replace("\\s+".toRegex(), " ").trim()
        val normalizedFinal = finalContent.replace("\\s+".toRegex(), " ").trim()
        assertEquals(normalizedOriginal, normalizedFinal, "Content should be restored to original state")
    }
}
