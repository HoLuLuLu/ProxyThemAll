package org.holululu.proxythemall.services.gradle

import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.services.gradle.GradlePropertiesText.detectLineSeparator

/**
 * Pure text manipulation of gradle.properties content for the ProxyThemAll managed section.
 *
 * Kept free of IntelliJ and file system APIs so the data-loss scenarios (marker on the first line,
 * repeated applies, user properties adjacent to the markers) are directly testable.
 */
object GradlePropertiesText {

    const val SECTION_START = "# === ProxyThemAll Managed Proxy Settings - START ==="
    const val SECTION_END = "# === ProxyThemAll Managed Proxy Settings - END ==="

    private const val JVM_ARGS_KEY = "org.gradle.jvmargs"
    private const val PROXY_SELECTOR_ARG = "-Djava.net.useSystemProxies=true"
    private const val HOSTS_SEPARATOR = "|"

    // Keys the plugin writes. Both buildSection and isOwnLine use these, so ownership detection
    // cannot drift away from what is actually written.
    private const val SOCKS_HOST = "systemProp.socksProxyHost"
    private const val SOCKS_PORT = "systemProp.socksProxyPort"
    private const val HTTP_HOST = "systemProp.http.proxyHost"
    private const val HTTP_PORT = "systemProp.http.proxyPort"
    private const val HTTPS_HOST = "systemProp.https.proxyHost"
    private const val HTTPS_PORT = "systemProp.https.proxyPort"
    private const val HTTP_NON_PROXY_HOSTS = "systemProp.http.nonProxyHosts"
    private const val HTTPS_NON_PROXY_HOSTS = "systemProp.https.nonProxyHosts"

    // The JDK keeps a separate bypass list for socket-level connections (sun.net.spi
    // DefaultProxySelector consults socksNonProxyHosts for the "socket" scheme). Without it a SOCKS
    // proxy ignores the user's exceptions entirely, because http.nonProxyHosts is only consulted
    // for the http scheme.
    private const val SOCKS_NON_PROXY_HOSTS = "systemProp.socksNonProxyHosts"
    private const val HTTP_USER = "systemProp.http.proxyUser"
    private const val HTTP_PASSWORD = "systemProp.http.proxyPassword"
    private const val HTTPS_USER = "systemProp.https.proxyUser"
    private const val HTTPS_PASSWORD = "systemProp.https.proxyPassword"

    // Every key here must also be written by buildSection, and every key buildSection writes must be
    // listed here - an unlisted key is treated as a user's own line and preserved on removal.
    private val OWN_KEYS = setOf(
        SOCKS_HOST, SOCKS_PORT, SOCKS_NON_PROXY_HOSTS,
        HTTP_HOST, HTTP_PORT, HTTPS_HOST, HTTPS_PORT,
        HTTP_NON_PROXY_HOSTS, HTTPS_NON_PROXY_HOSTS,
        HTTP_USER, HTTP_PASSWORD, HTTPS_USER, HTTPS_PASSWORD
    )

    // Comment lines the plugin emits inside its section
    private const val COMMENT_MANAGED = "# These settings are automatically managed by ProxyThemAll plugin"
    private const val COMMENT_OVERWRITTEN = "# Manual changes to this section will be overwritten"
    private const val COMMENT_SOCKS = "# SOCKS Proxy Configuration"
    private const val COMMENT_HTTP = "# HTTP Proxy Configuration"
    private const val COMMENT_HTTPS = "# HTTPS Proxy Configuration"
    private const val COMMENT_NON_PROXY = "# Non-proxy hosts (pipe-separated)"
    private const val COMMENT_AUTH = "# Proxy Authentication (stored in plain text - do not commit)"
    private const val COMMENT_JVM_ARGS = "# JVM arguments for IDE ProxySelector/Authenticator fallback"

    private val OWN_COMMENTS = setOf(
        COMMENT_MANAGED, COMMENT_OVERWRITTEN, COMMENT_SOCKS, COMMENT_HTTP, COMMENT_HTTPS,
        COMMENT_NON_PROXY, COMMENT_AUTH, COMMENT_JVM_ARGS
    )

    /**
     * Replaces any existing managed section with a freshly built one, appended at the end.
     *
     * The original line separator and trailing newline are preserved so a CRLF checkout does not
     * turn into a whole-file diff.
     */
    fun withProxySection(content: String, proxyInfo: ProxyInfo): String {
        val separator = detectLineSeparator(content)
        val base = removeManagedSection(content)

        // Only manage the JVM args key when the user has not set it themselves - a duplicate key
        // wins in a .properties file and would silently drop their -Xmx settings.
        val includeJvmArgs = !hasOwnJvmArgs(base)
        val section = buildSection(proxyInfo, includeJvmArgs).replace("\n", separator)

        if (base.isBlank()) return section + separator

        val prefix = if (base.endsWith(separator)) base else base + separator
        return prefix + separator + section + separator
    }

    /**
     * Removes the managed section, anchored strictly on the markers.
     *
     * Only lines the plugin itself wrote are deleted. Anything else found between the markers - a
     * property or comment the user added inside our block - is kept and left in place of the block,
     * because silently discarding a user's edit is worse than an untidy file.
     *
     * A single blank line directly above the START marker is dropped as well, since that is what
     * [withProxySection] inserts - but only when it really is blank, never a user's property.
     *
     * Note: when this rewrites the file it normalises every line to one separator (see
     * [detectLineSeparator]), so a file with mixed endings comes back uniform.
     */
    fun removeManagedSection(content: String): String {
        if (content.isBlank()) return content

        val separator = detectLineSeparator(content)
        val endedWithNewline = content.endsWith("\n")
        val lines = content.lines().toMutableList()

        // A trailing separator produces a final empty element; drop it and restore it at the end.
        if (endedWithNewline && lines.lastOrNull()?.isEmpty() == true) lines.removeAt(lines.size - 1)

        var changed = false
        while (true) {
            val start = lines.indexOfFirst { it.trim() == SECTION_START }
            if (start < 0) break
            val end = lines.subList(start, lines.size).indexOfFirst { it.trim() == SECTION_END }
            if (end < 0) break

            // Absolute index of the END marker, then widen over the blank separator line we added.
            var from = start
            val to = start + end
            if (from > 0 && lines[from - 1].isBlank()) from--

            // Rescue everything inside the block that is not ours before dropping it
            val foreign = lines.subList(from, to + 1).filterNot { isOwnLine(it) }

            repeat(to - from + 1) { lines.removeAt(from) }
            lines.addAll(from, foreign)
            changed = true
        }

        if (!changed) return content

        val rebuilt = lines.joinToString(separator)
        return if (endedWithNewline && rebuilt.isNotEmpty()) rebuilt + separator else rebuilt
    }

    /**
     * True when the line is one the plugin writes itself, and may therefore be deleted.
     *
     * Blank lines and comments inside the block count as ours only when they match what
     * [buildSection] emits; anything else belongs to the user.
     */
    private fun isOwnLine(line: String): Boolean {
        val trimmed = line.trim()
        // Blank lines are treated as ours: the section is full of them, and a stray blank line the
        // user left inside the block is formatting rather than content worth rescuing
        if (trimmed.isEmpty()) return true
        if (trimmed == SECTION_START || trimmed == SECTION_END) return true
        if (trimmed in OWN_COMMENTS) return true

        val key = trimmed.substringBefore('=').trim()
        // The plugin only manages org.gradle.jvmargs when it holds exactly our own value; a user
        // setting such as -Xmx4g must never be treated as ours
        if (key == JVM_ARGS_KEY) return trimmed.substringAfter('=', "").trim() == PROXY_SELECTOR_ARG

        return OWN_KEYS.contains(key)
    }

    /**
     * Builds the managed section. Always uses `\n`; [withProxySection] rewrites the separator.
     */
    fun buildSection(proxyInfo: ProxyInfo, includeJvmArgs: Boolean): String = buildString {
        appendLine(SECTION_START)
        appendLine(COMMENT_MANAGED)
        appendLine(COMMENT_OVERWRITTEN)
        appendLine()

        if (proxyInfo.isSocks) {
            appendLine(COMMENT_SOCKS)
            appendProperty(SOCKS_HOST, proxyInfo.host)
            appendProperty(SOCKS_PORT, proxyInfo.port.toString())
            appendLine()
        } else {
            appendLine(COMMENT_HTTP)
            appendProperty(HTTP_HOST, proxyInfo.host)
            appendProperty(HTTP_PORT, proxyInfo.port.toString())
            appendLine()

            appendLine(COMMENT_HTTPS)
            appendProperty(HTTPS_HOST, proxyInfo.host)
            appendProperty(HTTPS_PORT, proxyInfo.port.toString())
            appendLine()
        }

        val nonProxyHosts = proxyInfo.bypassHosts.joinToString(HOSTS_SEPARATOR)
        appendLine(COMMENT_NON_PROXY)
        if (proxyInfo.isSocks) {
            // The JDK reads socksNonProxyHosts for socket connections; the http key would be ignored
            appendProperty(SOCKS_NON_PROXY_HOSTS, nonProxyHosts)
        } else {
            appendProperty(HTTP_NON_PROXY_HOSTS, nonProxyHosts)
            // https reuses the http list in the JDK; written for readability, not effect
            appendProperty(HTTPS_NON_PROXY_HOSTS, nonProxyHosts)
        }
        appendLine()

        if (proxyInfo.hasCredentials) {
            // WARNING: these land in plain text on disk. See the Gradle section of the README.
            appendLine(COMMENT_AUTH)
            appendProperty(HTTP_USER, proxyInfo.username)
            appendProperty(HTTP_PASSWORD, proxyInfo.password)
            appendProperty(HTTPS_USER, proxyInfo.username)
            appendProperty(HTTPS_PASSWORD, proxyInfo.password)
        } else if (includeJvmArgs) {
            appendLine(COMMENT_JVM_ARGS)
            appendProperty(JVM_ARGS_KEY, PROXY_SELECTOR_ARG)
        }

        appendLine()
        append(SECTION_END)
    }

    /**
     * True when the content sets org.gradle.jvmargs outside of our managed section.
     */
    private fun hasOwnJvmArgs(content: String): Boolean =
        content.lines().any { it.trimStart().substringBefore('=').trim() == JVM_ARGS_KEY }

    private fun StringBuilder.appendProperty(key: String, value: String?) {
        appendLine("$key=${escapePropertyValue(value.orEmpty())}")
    }

    /**
     * Escapes a value for java.util.Properties: backslashes, line breaks and a leading space.
     *
     * Without this a password containing a backslash loses characters, and one ending in a
     * backslash line-continues and swallows the following key.
     */
    private fun escapePropertyValue(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return if (escaped.startsWith(" ")) "\\" + escaped else escaped
    }

    /**
     * Returns `\r\n` when the content contains any CRLF, otherwise `\n`.
     *
     * Not a "dominant separator" vote: a single CRLF is enough. That is deliberate - because
     * [removeManagedSection] rebuilds the file with `joinToString(separator)`, a mixed-ending file is
     * normalised to one separator whichever way this decides, so counting would only change *which*
     * lines get rewritten. Genuinely preserving mixed endings would need per-line tracking, which is
     * more machinery than a cosmetic diff is worth.
     */
    private fun detectLineSeparator(content: String): String =
        if (content.contains("\r\n")) "\r\n" else "\n"
}
