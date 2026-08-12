package org.holululu.proxythemall.services.gradle

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests the file-level behaviour of [GradleProxyConfigurer].
 *
 * The pure content transformation is covered by [GradlePropertiesTextTest]; this suite verifies
 * that reading and writing a real gradle.properties preserves the user's content.
 */
class GradleProxyConfigurerTest {

    private val configurer = GradleProxyConfigurer.instance
    private var tempPropertiesFile: File? = null

    @AfterEach
    fun tearDown() {
        tempPropertiesFile?.takeIf { it.exists() }?.delete()
    }

    private fun tempFile(content: String): File =
        File.createTempFile("gradle", ".properties").apply {
            deleteOnExit()
            writeText(content)
            tempPropertiesFile = this
        }

    private fun configure(file: File, proxyInfo: ProxyInfo) {
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "configureGradlePropertiesFile", File::class.java, ProxyInfo::class.java
        )
        method.isAccessible = true
        method.invoke(configurer, file, proxyInfo)
    }

    private fun removeFrom(file: File): Boolean {
        val method = GradleProxyConfigurer::class.java.getDeclaredMethod(
            "removeProxyPropertiesFromFile", File::class.java
        )
        method.isAccessible = true
        return method.invoke(configurer, file) as Boolean
    }

    private val proxyInfo = ProxyInfo(
        host = "proxy.example.com",
        port = 8080,
        username = "testuser",
        password = "testpass",
        nonProxyHosts = setOf("localhost", "127.*", "[::1]", "internal.company.com")
    )

    @Test
    fun `configuring then removing restores the original file byte for byte`() {
        val original = """
            # IntelliJ Platform Artifacts Repositories
            pluginGroup=org.holululu.proxythemall
            pluginName = ProxyThemAll

            # Enable Gradle Build Cache
            org.gradle.caching = true
        """.trimIndent() + "\n"
        val file = tempFile(original)

        configure(file, proxyInfo)
        val withProxy = file.readText()
        assertTrue(withProxy.contains("systemProp.http.proxyHost=proxy.example.com"))
        assertTrue(withProxy.contains("systemProp.http.proxyUser=testuser"))
        assertTrue(withProxy.contains("localhost|127.*|[::1]|internal.company.com"))
        assertTrue(withProxy.contains("pluginName = ProxyThemAll"), "user content must survive")

        assertTrue(removeFrom(file), "removal should report a change")
        assertEquals(original, file.readText())
    }

    @Test
    fun `removing reports no change when there is no managed section`() {
        val file = tempFile("pluginGroup=org.example\n")

        assertFalse(removeFrom(file), "nothing to remove")
        assertEquals("pluginGroup=org.example\n", file.readText())
    }

    @Test
    fun `repeated configuration does not accumulate sections`() {
        val file = tempFile("pluginGroup=org.example\n")

        repeat(3) { configure(file, proxyInfo) }

        val markers = file.readLines().count { it.trim() == GradlePropertiesText.SECTION_START }
        assertEquals(1, markers, "only one managed section may exist")

        removeFrom(file)
        assertEquals("pluginGroup=org.example\n", file.readText())
    }

    @Test
    fun `a foreign line inside the managed block survives a real removal`() {
        val file = tempFile("pluginGroup=org.example\n")
        configure(file, proxyInfo)

        // Simulate the user editing inside our block, as in verification section 6.4
        val tampered = file.readLines().toMutableList().also { lines ->
            lines.add(lines.indexOfFirst { it.trim() == GradlePropertiesText.SECTION_END }, "my.note=1")
        }
        file.writeText(tampered.joinToString("\n") + "\n")

        assertTrue(removeFrom(file), "removal should report a change")

        val result = file.readText()
        assertTrue(result.contains("my.note=1"), "the user's line must survive: $result")
        assertTrue(result.contains("pluginGroup=org.example"), "content outside the block must survive")
        assertFalse(result.contains("systemProp."), "our properties must be gone")
    }

    @Test
    fun `configuring an empty file writes only the managed section`() {
        val file = tempFile("")

        configure(file, proxyInfo)

        assertEquals(0, file.readText().indexOf(GradlePropertiesText.SECTION_START))
        assertTrue(removeFrom(file), "a section written at line 0 must be removable")
        assertFalse(file.readText().contains("systemProp."), "no proxy properties may remain")
    }
}
