package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.utils.ProxyUrlBuilder
import java.io.File

private const val GRADLE_HOSTS_SEPERATOR = "|"

/**
 * Service responsible for configuring Gradle proxy settings with direct credential support
 *
 * This configurer supports both direct credential injection via Gradle's built-in
 * authentication properties and fallback to IDE's ProxySelector/Authenticator.
 */
class GradleProxyConfigurer {

    companion object {
        @JvmStatic
        val instance: GradleProxyConfigurer by lazy { GradleProxyConfigurer() }

        private val LOG = Logger.getInstance(GradleProxyConfigurer::class.java)

        // Gradle proxy properties - using JVM system properties approach
        private const val HTTP_PROXY_HOST = "systemProp.http.proxyHost"
        private const val HTTP_PROXY_PORT = "systemProp.http.proxyPort"
        private const val HTTPS_PROXY_HOST = "systemProp.https.proxyHost"
        private const val HTTPS_PROXY_PORT = "systemProp.https.proxyPort"
        private const val HTTP_NON_PROXY_HOSTS = "systemProp.http.nonProxyHosts"
        private const val HTTPS_NON_PROXY_HOSTS = "systemProp.https.nonProxyHosts"

        // Gradle authentication properties for direct credential support
        private const val HTTP_PROXY_USER = "systemProp.http.proxyUser"
        private const val HTTP_PROXY_PASSWORD = "systemProp.http.proxyPassword"
        private const val HTTPS_PROXY_USER = "systemProp.https.proxyUser"
        private const val HTTPS_PROXY_PASSWORD = "systemProp.https.proxyPassword"

        // JVM arguments to enable IDE's ProxySelector and Authenticator
        private const val GRADLE_JVM_ARGS = "org.gradle.jvmargs"
        private const val PROXY_SELECTOR_ARG = "-Djava.net.useSystemProxies=true"

        // ProxyThemAll managed section markers
        private const val PROXY_SECTION_START = "# === ProxyThemAll Managed Proxy Settings - START ==="
        private const val PROXY_SECTION_END = "# === ProxyThemAll Managed Proxy Settings - END ==="
    }

    private val proxyUrlBuilder = ProxyUrlBuilder.instance

    /**
     * Sets proxy for Gradle using extracted proxy information with direct credential support
     * Returns a status message for inclusion in notifications
     */
    fun setGradleProxy(project: Project?, proxyInfo: ProxyInfo, onComplete: (String) -> Unit) {
        // Run Gradle configuration in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Configuring Gradle Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gradlePropertiesFile = getGradlePropertiesFile(project)
                    if (gradlePropertiesFile != null) {
                        configureProjectGradleProperties(gradlePropertiesFile, proxyInfo)

                        val statusMessage = if (hasCredentials(proxyInfo)) {
                            "configured for project with authentication"
                        } else {
                            "configured for project"
                        }
                        
                        LOG.info("Gradle proxy configured for project: ${proxyInfo.host}:${proxyInfo.port}")
                        onComplete(statusMessage)
                    } else {
                        configureGlobalGradleProperties(proxyInfo)

                        val statusMessage = if (hasCredentials(proxyInfo)) {
                            "configured globally with authentication"
                        } else {
                            "configured globally"
                        }
                        
                        LOG.info("Gradle proxy configured globally: ${proxyInfo.host}:${proxyInfo.port}")
                        onComplete(statusMessage)
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to set Gradle proxy", e)
                    onComplete("configuration failed")
                }
            }
        }.queue()
    }

    /**
     * Removes Gradle proxy settings
     */
    fun removeGradleProxySettings(project: Project?, onComplete: (String) -> Unit) {
        // Run Gradle configuration in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Removing Gradle Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    var removedAny = false

                    // First try to remove project-level settings
                    val gradlePropertiesFile = getGradlePropertiesFile(project)
                    if (gradlePropertiesFile != null && gradlePropertiesFile.exists()) {
                        if (removeProxyPropertiesFromFile(gradlePropertiesFile)) {
                            LOG.info("Project-level Gradle proxy settings removed")
                            removedAny = true
                            onComplete("proxy removed from project")
                        }
                    }

                    // If no project-level settings were removed, try global settings
                    if (!removedAny) {
                        val globalGradlePropertiesFile = getGlobalGradlePropertiesFile()
                        if (globalGradlePropertiesFile.exists()) {
                            if (removeProxyPropertiesFromFile(globalGradlePropertiesFile)) {
                                LOG.info("Global Gradle proxy settings removed")
                                onComplete("proxy removed globally")
                            } else {
                                onComplete("no proxy settings found")
                            }
                        } else {
                            onComplete("no proxy settings found")
                        }
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to remove Gradle proxy settings", e)
                    onComplete("proxy removal failed")
                }
            }
        }.queue()
    }

    /**
     * Gets the project-level gradle.properties file
     */
    private fun getGradlePropertiesFile(project: Project?): File? {
        return project?.let { p ->
            p.basePath?.let { basePath ->
                File(basePath, "gradle.properties").takeIf {
                    File(basePath).exists() && File(basePath).isDirectory
                }
            }
        }
    }

    /**
     * Gets the global gradle.properties file
     */
    private fun getGlobalGradlePropertiesFile(): File {
        val userHome = System.getProperty("user.home")
        val gradleDir = File(userHome, ".gradle")
        if (!gradleDir.exists()) {
            gradleDir.mkdirs()
        }
        return File(gradleDir, "gradle.properties")
    }

    /**
     * Configures project-level gradle.properties file
     */
    private fun configureProjectGradleProperties(gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        configureGradlePropertiesFile(gradlePropertiesFile, proxyInfo)
    }

    /**
     * Configures global gradle.properties file
     */
    private fun configureGlobalGradleProperties(proxyInfo: ProxyInfo) {
        val gradlePropertiesFile = getGlobalGradlePropertiesFile()
        configureGradlePropertiesFile(gradlePropertiesFile, proxyInfo)
    }

    /**
     * Configures gradle.properties file while preserving existing content and structure
     */
    private fun configureGradlePropertiesFile(gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        // First, remove any existing ProxyThemAll managed settings
        removeProxyThemAllManagedSettings(gradlePropertiesFile)

        // Then append new proxy settings at the end
        appendProxySettings(gradlePropertiesFile, proxyInfo)
    }

    /**
     * Checks if proxy info contains credentials
     */
    private fun hasCredentials(proxyInfo: ProxyInfo): Boolean {
        return !proxyInfo.username.isNullOrBlank() && !proxyInfo.password.isNullOrBlank()
    }

    /**
     * Removes proxy properties from a gradle.properties file while preserving structure
     */
    private fun removeProxyPropertiesFromFile(gradlePropertiesFile: File): Boolean {
        if (!gradlePropertiesFile.exists()) {
            return false
        }

        // Use the new structure-preserving method
        val originalContent = gradlePropertiesFile.readText()
        removeProxyThemAllManagedSettings(gradlePropertiesFile)
        val newContent = gradlePropertiesFile.readText()

        // Return true if content was actually changed
        return originalContent != newContent
    }

    /**
     * Removes ProxyThemAll managed settings from the gradle.properties file while preserving structure
     */
    private fun removeProxyThemAllManagedSettings(gradlePropertiesFile: File) {
        if (!gradlePropertiesFile.exists()) {
            return
        }

        val lines = gradlePropertiesFile.readLines().toMutableList()
        var startIndex = -1
        var endIndex = -1

        // Find the ProxyThemAll managed section
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line == PROXY_SECTION_START) {
                startIndex = i - 1
            } else if (line == PROXY_SECTION_END && startIndex != -1) {
                endIndex = i
                break
            }
        }

        // Remove the managed section if found
        if (startIndex != -1 && endIndex != -1) {
            // Remove from end to start to maintain indices
            for (i in endIndex downTo startIndex) {
                lines.removeAt(i)
            }

            // Write the modified content back to the file
            gradlePropertiesFile.writeText(lines.joinToString("\n"))
            LOG.info("Removed existing ProxyThemAll managed proxy settings")
        }
    }

    /**
     * Appends proxy settings to the gradle.properties file with proper commenting
     */
    private fun appendProxySettings(gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        val proxySettings = buildProxySettingsContent(proxyInfo)

        // Ensure the file exists
        if (!gradlePropertiesFile.exists()) {
            gradlePropertiesFile.createNewFile()
        }

        // Read existing content
        val existingContent = if (gradlePropertiesFile.length() > 0) {
            gradlePropertiesFile.readText()
        } else {
            ""
        }

        // Append proxy settings with proper spacing
        val newContent = if (existingContent.isBlank()) {
            proxySettings
        } else {
            val separator = if (existingContent.endsWith("\n")) "\n" else "\n\n"
            existingContent + separator + proxySettings
        }

        gradlePropertiesFile.writeText(newContent)
        LOG.info("Appended ProxyThemAll managed proxy settings to gradle.properties")
    }

    /**
     * Builds the proxy settings content with proper formatting and comments
     */
    private fun buildProxySettingsContent(proxyInfo: ProxyInfo): String {
        val content = StringBuilder()

        // Add section start marker
        content.appendLine(PROXY_SECTION_START)
        content.appendLine("# These settings are automatically managed by ProxyThemAll plugin")
        content.appendLine("# Manual changes to this section will be overwritten")
        content.appendLine()

        // Add proxy host and port settings
        content.appendLine("# HTTP Proxy Configuration")
        content.appendLine("$HTTP_PROXY_HOST=${proxyInfo.host}")
        content.appendLine("$HTTP_PROXY_PORT=${proxyInfo.port}")
        content.appendLine()

        content.appendLine("# HTTPS Proxy Configuration")
        content.appendLine("$HTTPS_PROXY_HOST=${proxyInfo.host}")
        content.appendLine("$HTTPS_PROXY_PORT=${proxyInfo.port}")
        content.appendLine()

        // Builds the non-proxy hosts configuration and formats them for Gradle (pipe-separated)
        val nonProxyHosts = proxyInfo.nonProxyHosts.joinToString(GRADLE_HOSTS_SEPERATOR)

        // Add non-proxy hosts
        content.appendLine("# Non-proxy hosts (pipe-separated)")
        content.appendLine("$HTTP_NON_PROXY_HOSTS=$nonProxyHosts")
        content.appendLine("$HTTPS_NON_PROXY_HOSTS=$nonProxyHosts")
        content.appendLine()

        // Add authentication if available
        if (hasCredentials(proxyInfo)) {
            content.appendLine("# Proxy Authentication")
            content.appendLine("$HTTP_PROXY_USER=${proxyInfo.username}")
            content.appendLine("$HTTP_PROXY_PASSWORD=${proxyInfo.password}")
            content.appendLine("$HTTPS_PROXY_USER=${proxyInfo.username}")
            content.appendLine("$HTTPS_PROXY_PASSWORD=${proxyInfo.password}")
        } else {
            content.appendLine("# JVM arguments for IDE ProxySelector/Authenticator fallback")
            content.appendLine("$GRADLE_JVM_ARGS=$PROXY_SELECTOR_ARG")
        }

        content.appendLine()
        content.append(PROXY_SECTION_END)

        return content.toString()
    }
}
