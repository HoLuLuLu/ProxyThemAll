package org.holululu.proxythemall.services.gradle

import org.holululu.proxythemall.models.ProxyInfo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.util.*

/**
 * Tests for the pure gradle.properties text manipulation used by [GradleProxyConfigurer].
 *
 * These cover the data-loss scenarios: a managed section starting at line 0, a user property
 * directly above the START marker, repeated applies, and an existing org.gradle.jvmargs.
 */
class GradlePropertiesTextTest {

    private val proxyInfo = ProxyInfo(
        host = "proxy.example.com",
        port = 8080,
        nonProxyHosts = setOf("localhost", "127.*", "[::1]")
    )

    private fun countSections(content: String) =
        content.lines().count { it.trim() == GradlePropertiesText.SECTION_START }

    private fun parse(content: String): Properties = Properties().apply { load(content.reader()) }

    @Test
    fun `add then remove restores newline terminated content byte for byte`() {
        val original = "# comment\npluginGroup=org.example\norg.gradle.caching=true\n"

        val applied = GradlePropertiesText.withProxySection(original, proxyInfo)
        assertTrue(applied.contains(GradlePropertiesText.SECTION_START), "section should be added")

        assertEquals(original, GradlePropertiesText.removeManagedSection(applied))
    }

    @Test
    fun `managed section starting at line 0 is removed`() {
        // A gradle.properties the plugin created itself starts with the marker on line 0.
        val applied = GradlePropertiesText.withProxySection("", proxyInfo)
        assertEquals(0, applied.indexOf(GradlePropertiesText.SECTION_START), "marker must be at line 0")

        val removed = GradlePropertiesText.removeManagedSection(applied)
        assertFalse(removed.contains(GradlePropertiesText.SECTION_START), "section must be removed")
        assertFalse(removed.contains("systemProp.http.proxyHost"), "properties must be removed")
    }

    @Test
    fun `repeated applies leave exactly one managed section`() {
        var content = "pluginGroup=org.example\n"
        repeat(5) { content = GradlePropertiesText.withProxySection(content, proxyInfo) }

        assertEquals(1, countSections(content), "only one managed section may exist")
        assertEquals("pluginGroup=org.example\n", GradlePropertiesText.removeManagedSection(content))
    }

    @Test
    fun `user property directly above the start marker survives removal`() {
        // No blank line between the user's last property and the marker.
        val content = "pluginGroup=org.example\n" +
                GradlePropertiesText.buildSection(proxyInfo, includeJvmArgs = true) + "\n"

        val removed = GradlePropertiesText.removeManagedSection(content)

        assertTrue(removed.contains("pluginGroup=org.example"), "user property must not be deleted")
    }

    @Test
    fun `existing org gradle jvmargs is not overridden`() {
        val original = "org.gradle.jvmargs=-Xmx4g\n"

        val applied = GradlePropertiesText.withProxySection(original, proxyInfo)

        assertEquals(1, applied.lines().count { it.startsWith("org.gradle.jvmargs") }, "must not duplicate the key")
        assertEquals("-Xmx4g", parse(applied).getProperty("org.gradle.jvmargs"))
    }

    @Test
    fun `backslash and leading space in credentials survive a properties round trip`() {
        val awkward = proxyInfo.copy(username = "dom\\user", password = "  p@ss\\word\\")

        val parsed = parse(GradlePropertiesText.withProxySection("", awkward))

        assertEquals("dom\\user", parsed.getProperty("systemProp.http.proxyUser"))
        assertEquals("  p@ss\\word\\", parsed.getProperty("systemProp.http.proxyPassword"))
    }

    @Test
    fun `crlf line separators are preserved`() {
        val original = "pluginGroup=org.example\r\norg.gradle.caching=true\r\n"

        val applied = GradlePropertiesText.withProxySection(original, proxyInfo)

        assertFalse(applied.contains(Regex("(?<!\\r)\\n")), "must not introduce a bare LF into a CRLF file")
        assertEquals(original, GradlePropertiesText.removeManagedSection(applied))
    }

    @Test
    fun `socks proxy is written as socks system properties`() {
        val socks = proxyInfo.copy(type = "socks5")

        val parsed = parse(GradlePropertiesText.withProxySection("", socks))

        assertEquals("proxy.example.com", parsed.getProperty("systemProp.socksProxyHost"))
        assertEquals("8080", parsed.getProperty("systemProp.socksProxyPort"))
        assertFalse(parsed.containsKey("systemProp.http.proxyHost"), "SOCKS must not be written as an HTTP proxy")
    }

    @Test
    fun `configuring and removing a real file preserves surrounding content`() {
        val file = File.createTempFile("gradle", ".properties").apply { deleteOnExit() }
        val original = "# header\npluginGroup=org.example\n\n# footer\norg.gradle.caching=true\n"
        file.writeText(original)

        file.writeText(GradlePropertiesText.withProxySection(file.readText(), proxyInfo))
        assertTrue(file.readText().contains("systemProp.http.proxyHost=proxy.example.com"))

        file.writeText(GradlePropertiesText.removeManagedSection(file.readText()))
        assertEquals(original, file.readText())
    }

    // --- lines a user added INSIDE the managed block must not be destroyed ---

    /**
     * Inserts [line] just before the END marker, simulating a user editing inside our block.
     */
    private fun withLineInsideBlock(content: String, line: String): String =
        content.lines().toMutableList().let { lines ->
            lines.add(lines.indexOfFirst { it.trim() == GradlePropertiesText.SECTION_END }, line)
            lines.joinToString("\n")
        }

    @Test
    fun `a user property inside the managed block survives removal`() {
        val applied = GradlePropertiesText.withProxySection("pluginGroup=org.example\n", proxyInfo)
        val tampered = withLineInsideBlock(applied, "my.note=1")

        val removed = GradlePropertiesText.removeManagedSection(tampered)

        assertTrue(removed.contains("my.note=1"), "a foreign property must be preserved: $removed")
        assertTrue(removed.contains("pluginGroup=org.example"), "content outside the block must survive")
        assertFalse(removed.contains(GradlePropertiesText.SECTION_START), "the block itself must be gone")
        assertFalse(removed.contains("systemProp."), "our own properties must be removed")
    }

    @Test
    fun `a user comment inside the managed block survives removal`() {
        val applied = GradlePropertiesText.withProxySection("", proxyInfo)
        val tampered = withLineInsideBlock(applied, "# my own note")

        val removed = GradlePropertiesText.removeManagedSection(tampered)

        assertTrue(removed.contains("# my own note"), "a foreign comment must be preserved: $removed")
        assertFalse(removed.contains("automatically managed by ProxyThemAll"), "our comments must go")
    }

    @Test
    fun `user jvmargs inside the block survive but ours are removed`() {
        // Without credentials the plugin writes org.gradle.jvmargs itself, so the key alone is not
        // enough to decide ownership - only our exact value is ours
        val applied = GradlePropertiesText.withProxySection("", proxyInfo)
        assertTrue(applied.contains("org.gradle.jvmargs=-Djava.net.useSystemProxies=true"))

        val tampered = withLineInsideBlock(applied, "org.gradle.jvmargs=-Xmx4g")
        val removed = GradlePropertiesText.removeManagedSection(tampered)

        assertTrue(removed.contains("org.gradle.jvmargs=-Xmx4g"), "the user's jvmargs must survive")
        assertFalse(removed.contains("useSystemProxies"), "our own jvmargs must be removed")
    }

    @Test
    fun `reapplying after tampering keeps the foreign line and one block`() {
        val applied = GradlePropertiesText.withProxySection("pluginGroup=org.example\n", proxyInfo)
        val tampered = withLineInsideBlock(applied, "my.note=1")

        val reapplied = GradlePropertiesText.withProxySection(tampered, proxyInfo.copy(port = 9090))

        assertEquals(1, countSections(reapplied), "only one managed section may exist")
        assertTrue(reapplied.contains("my.note=1"), "the foreign line must survive a reapply")
        assertEquals("9090", parse(reapplied).getProperty("systemProp.http.proxyPort"))
    }
}
