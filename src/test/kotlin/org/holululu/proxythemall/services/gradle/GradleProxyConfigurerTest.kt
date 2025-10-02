package org.holululu.proxythemall.services.gradle

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.holululu.proxythemall.models.ProxyInfo
import java.io.File
import java.util.*

/**
 * Test for GradleProxyConfigurer functionality with direct credential support
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

    fun testSetProxyPropertiesWithoutCredentials() {
        // Test that proxy properties are set correctly without credentials
        val properties = Properties()
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = null, // Should be null with new approach
            password = null, // Should be null with new approach
            type = "http"
        )

        // Use reflection to access the private method for testing
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "setProxyProperties",
            Properties::class.java,
            ProxyInfo::class.java
        )
        method.isAccessible = true
        method.invoke(gradleProxyConfigurer, properties, proxyInfo)

        // Verify host and port are set
        assertEquals("proxy.example.com", properties.getProperty("systemProp.http.proxyHost"))
        assertEquals("8080", properties.getProperty("systemProp.http.proxyPort"))
        assertEquals("proxy.example.com", properties.getProperty("systemProp.https.proxyHost"))
        assertEquals("8080", properties.getProperty("systemProp.https.proxyPort"))

        // Verify non-proxy hosts are set
        assertEquals("localhost|127.*|[::1]", properties.getProperty("systemProp.http.nonProxyHosts"))
        assertEquals("localhost|127.*|[::1]", properties.getProperty("systemProp.https.nonProxyHosts"))

        // Verify JVM args are configured for proxy selector
        val jvmArgs = properties.getProperty("org.gradle.jvmargs")
        assertNotNull("JVM args should be set", jvmArgs)
        assertTrue("JVM args should contain proxy selector", jvmArgs.contains("-Djava.net.useSystemProxies=true"))

        // Verify credentials are NOT set (fallback approach)
        assertNull("HTTP proxy user should not be set", properties.getProperty("systemProp.http.proxyUser"))
        assertNull("HTTP proxy password should not be set", properties.getProperty("systemProp.http.proxyPassword"))
        assertNull("HTTPS proxy user should not be set", properties.getProperty("systemProp.https.proxyUser"))
        assertNull("HTTPS proxy password should not be set", properties.getProperty("systemProp.https.proxyPassword"))
    }

    fun testSetProxyPropertiesWithCredentials() {
        // Test that proxy properties are set correctly WITH credentials (direct credential support)
        val properties = Properties()
        val proxyInfo = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "testuser",
            password = "testpass",
            type = "http"
        )

        // Use reflection to access the private method for testing
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "setProxyProperties",
            Properties::class.java,
            ProxyInfo::class.java
        )
        method.isAccessible = true
        method.invoke(gradleProxyConfigurer, properties, proxyInfo)

        // Verify host and port are set
        assertEquals("proxy.example.com", properties.getProperty("systemProp.http.proxyHost"))
        assertEquals("8080", properties.getProperty("systemProp.http.proxyPort"))
        assertEquals("proxy.example.com", properties.getProperty("systemProp.https.proxyHost"))
        assertEquals("8080", properties.getProperty("systemProp.https.proxyPort"))

        // Verify non-proxy hosts are set
        assertEquals("localhost|127.*|[::1]", properties.getProperty("systemProp.http.nonProxyHosts"))
        assertEquals("localhost|127.*|[::1]", properties.getProperty("systemProp.https.nonProxyHosts"))

        // Verify credentials ARE set (direct credential support)
        assertEquals("testuser", properties.getProperty("systemProp.http.proxyUser"))
        assertEquals("testpass", properties.getProperty("systemProp.http.proxyPassword"))
        assertEquals("testuser", properties.getProperty("systemProp.https.proxyUser"))
        assertEquals("testpass", properties.getProperty("systemProp.https.proxyPassword"))

        // Verify JVM args are NOT set when using direct credentials
        val jvmArgs = properties.getProperty("org.gradle.jvmargs")
        if (jvmArgs != null) {
            assertFalse(
                "JVM args should not contain proxy selector when using direct credentials",
                jvmArgs.contains("-Djava.net.useSystemProxies=true")
            )
        }
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
            type = "http"
        )
        val hasCredentials = hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithCredentials) as Boolean
        assertTrue("Should have credentials", hasCredentials)

        // Test without credentials
        val proxyWithoutCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = null,
            password = null,
            type = "http"
        )
        val hasNoCredentials = hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithoutCredentials) as Boolean
        assertFalse("Should not have credentials", hasNoCredentials)

        // Test with blank credentials
        val proxyWithBlankCredentials = ProxyInfo(
            host = "proxy.example.com",
            port = 8080,
            username = "",
            password = "  ",
            type = "http"
        )
        val hasBlankCredentials =
            hasCredentialsMethod.invoke(gradleProxyConfigurer, proxyWithBlankCredentials) as Boolean
        assertFalse("Should not have credentials when blank", hasBlankCredentials)
    }

    fun testJvmArgsConfiguration() {
        // Test that JVM args are properly configured
        val properties = Properties()

        // Set existing JVM args
        properties.setProperty("org.gradle.jvmargs", "-Xmx2g -XX:+UseG1GC")

        val proxyInfo = ProxyInfo(
            host = "proxy.test.com",
            port = 3128,
            username = null,
            password = null,
            type = "http"
        )

        // Use reflection to access the private method
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "configureGradleJvmArgs",
            Properties::class.java
        )
        method.isAccessible = true

        // First call setProxyProperties to trigger JVM args configuration
        val setProxyMethod = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "setProxyProperties",
            Properties::class.java,
            ProxyInfo::class.java
        )
        setProxyMethod.isAccessible = true
        setProxyMethod.invoke(gradleProxyConfigurer, properties, proxyInfo)

        val jvmArgs = properties.getProperty("org.gradle.jvmargs")
        assertNotNull("JVM args should be set", jvmArgs)
        assertTrue("Should contain existing args", jvmArgs.contains("-Xmx2g"))
        assertTrue("Should contain existing args", jvmArgs.contains("-XX:+UseG1GC"))
        assertTrue("Should contain proxy selector", jvmArgs.contains("-Djava.net.useSystemProxies=true"))
    }

    fun testRemoveProxyProperties() {
        // Test that proxy properties are properly removed
        val properties = Properties()

        // Create a temporary properties file for this test
        tempPropertiesFile = File.createTempFile("gradle", ".properties")
        tempPropertiesFile!!.deleteOnExit()

        // Set up properties as if they were configured (including authentication properties)
        properties.setProperty("systemProp.http.proxyHost", "proxy.example.com")
        properties.setProperty("systemProp.http.proxyPort", "8080")
        properties.setProperty("systemProp.https.proxyHost", "proxy.example.com")
        properties.setProperty("systemProp.https.proxyPort", "8080")
        properties.setProperty("systemProp.http.nonProxyHosts", "localhost|127.*|[::1]")
        properties.setProperty("systemProp.https.nonProxyHosts", "localhost|127.*|[::1]")
        properties.setProperty("systemProp.http.proxyUser", "testuser")
        properties.setProperty("systemProp.http.proxyPassword", "testpass")
        properties.setProperty("systemProp.https.proxyUser", "testuser")
        properties.setProperty("systemProp.https.proxyPassword", "testpass")
        properties.setProperty("org.gradle.jvmargs", "-Xmx2g -Djava.net.useSystemProxies=true")

        // Save to temp file
        tempPropertiesFile!!.outputStream().use { output ->
            properties.store(output, "Test properties")
        }

        // Use reflection to access the private method
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "removeProxyPropertiesFromFile",
            File::class.java
        )
        method.isAccessible = true
        val result = method.invoke(gradleProxyConfigurer, tempPropertiesFile!!) as Boolean

        assertTrue("Should have removed properties", result)

        // Load properties back and verify removal
        val loadedProperties = Properties()
        tempPropertiesFile?.inputStream()?.use { input ->
            loadedProperties.load(input)
        }

        assertNull("HTTP proxy host should be removed", loadedProperties.getProperty("systemProp.http.proxyHost"))
        assertNull("HTTP proxy port should be removed", loadedProperties.getProperty("systemProp.http.proxyPort"))
        assertNull("HTTPS proxy host should be removed", loadedProperties.getProperty("systemProp.https.proxyHost"))
        assertNull("HTTPS proxy port should be removed", loadedProperties.getProperty("systemProp.https.proxyPort"))
        assertNull(
            "HTTP non-proxy hosts should be removed",
            loadedProperties.getProperty("systemProp.http.nonProxyHosts")
        )
        assertNull(
            "HTTPS non-proxy hosts should be removed",
            loadedProperties.getProperty("systemProp.https.nonProxyHosts")
        )

        // Verify authentication properties are also removed
        assertNull("HTTP proxy user should be removed", loadedProperties.getProperty("systemProp.http.proxyUser"))
        assertNull(
            "HTTP proxy password should be removed",
            loadedProperties.getProperty("systemProp.http.proxyPassword")
        )
        assertNull("HTTPS proxy user should be removed", loadedProperties.getProperty("systemProp.https.proxyUser"))
        assertNull(
            "HTTPS proxy password should be removed",
            loadedProperties.getProperty("systemProp.https.proxyPassword")
        )

        // JVM args should have proxy selector removed but keep other args
        val jvmArgs = loadedProperties.getProperty("org.gradle.jvmargs")
        if (jvmArgs != null) {
            assertTrue("Should keep existing args", jvmArgs.contains("-Xmx2g"))
            assertFalse("Should remove proxy selector", jvmArgs.contains("-Djava.net.useSystemProxies=true"))
        }
    }
}
