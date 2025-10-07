package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.utils.ProxyUrlBuilder
import java.io.File
import java.util.*

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
        val properties = loadProperties(gradlePropertiesFile)
        setProxyProperties(properties, proxyInfo)
        saveProperties(properties, gradlePropertiesFile)
    }

    /**
     * Configures global gradle.properties file
     */
    private fun configureGlobalGradleProperties(proxyInfo: ProxyInfo) {
        val gradlePropertiesFile = getGlobalGradlePropertiesFile()
        val properties = loadProperties(gradlePropertiesFile)
        setProxyProperties(properties, proxyInfo)
        saveProperties(properties, gradlePropertiesFile)
    }

    /**
     * Sets proxy properties in the Properties object with direct credential support
     * Uses Gradle's built-in authentication properties when credentials are available,
     * falls back to IDE's ProxySelector/Authenticator when credentials are not available
     */
    private fun setProxyProperties(properties: Properties, proxyInfo: ProxyInfo) {
        // HTTP proxy settings - host and port
        properties.setProperty(HTTP_PROXY_HOST, proxyInfo.host)
        properties.setProperty(HTTP_PROXY_PORT, proxyInfo.port.toString())

        // HTTPS proxy settings - host and port
        properties.setProperty(HTTPS_PROXY_HOST, proxyInfo.host)
        properties.setProperty(HTTPS_PROXY_PORT, proxyInfo.port.toString())

        // Set non-proxy hosts - combine user-defined hosts with essential defaults
        val nonProxyHosts = buildNonProxyHostsString(proxyInfo.nonProxyHosts)
        properties.setProperty(HTTP_NON_PROXY_HOSTS, nonProxyHosts)
        properties.setProperty(HTTPS_NON_PROXY_HOSTS, nonProxyHosts)

        // Direct credential support - use Gradle's built-in authentication properties
        if (hasCredentials(proxyInfo)) {
            // Set HTTP authentication properties
            properties.setProperty(HTTP_PROXY_USER, proxyInfo.username)
            properties.setProperty(HTTP_PROXY_PASSWORD, proxyInfo.password)

            // Set HTTPS authentication properties
            properties.setProperty(HTTPS_PROXY_USER, proxyInfo.username)
            properties.setProperty(HTTPS_PROXY_PASSWORD, proxyInfo.password)

            LOG.info("Gradle proxy configured with direct authentication")
        } else {
            // Remove any existing authentication properties to avoid conflicts
            properties.remove(HTTP_PROXY_USER)
            properties.remove(HTTP_PROXY_PASSWORD)
            properties.remove(HTTPS_PROXY_USER)
            properties.remove(HTTPS_PROXY_PASSWORD)

            // Configure JVM to use system proxy settings and IDE's ProxySelector/Authenticator
            configureGradleJvmArgs(properties)
            LOG.info("Gradle proxy configured with IDE ProxySelector/Authenticator fallback")
        }
    }

    /**
     * Builds the non-proxy hosts string for Gradle configuration
     * Combines user-defined hosts with essential defaults and formats them for Gradle (pipe-separated)
     */
    private fun buildNonProxyHostsString(userNonProxyHosts: Set<String>): String {
        // Essential defaults that should always be excluded from proxy
        val essentialDefaults = setOf("localhost", "127.*", "[::1]")

        // Combine user-defined hosts with essential defaults
        val allNonProxyHosts = essentialDefaults + userNonProxyHosts.filter { it.isNotBlank() }

        // Format for Gradle: pipe-separated string
        return allNonProxyHosts.joinToString("|")
    }

    /**
     * Checks if proxy info contains credentials
     */
    private fun hasCredentials(proxyInfo: ProxyInfo): Boolean {
        return !proxyInfo.username.isNullOrBlank() && !proxyInfo.password.isNullOrBlank()
    }

    /**
     * Configures Gradle JVM arguments to use IDE's ProxySelector and Authenticator
     */
    private fun configureGradleJvmArgs(properties: Properties) {
        val existingJvmArgs = properties.getProperty(GRADLE_JVM_ARGS, "")

        // Add system proxy support if not already present
        if (!existingJvmArgs.contains(PROXY_SELECTOR_ARG)) {
            val newJvmArgs = if (existingJvmArgs.isBlank()) {
                PROXY_SELECTOR_ARG
            } else {
                "$existingJvmArgs $PROXY_SELECTOR_ARG"
            }
            properties.setProperty(GRADLE_JVM_ARGS, newJvmArgs)
            LOG.info("Added JVM proxy selector argument to Gradle configuration")
        }
    }

    /**
     * Removes proxy properties from a gradle.properties file
     */
    private fun removeProxyPropertiesFromFile(gradlePropertiesFile: File): Boolean {
        if (!gradlePropertiesFile.exists()) {
            return false
        }

        val properties = loadProperties(gradlePropertiesFile)
        var removedAny = false

        // Remove all proxy-related properties (including new authentication properties)
        val proxyKeys = listOf(
            HTTP_PROXY_HOST, HTTP_PROXY_PORT,
            HTTPS_PROXY_HOST, HTTPS_PROXY_PORT,
            HTTP_NON_PROXY_HOSTS, HTTPS_NON_PROXY_HOSTS,
            HTTP_PROXY_USER, HTTP_PROXY_PASSWORD,
            HTTPS_PROXY_USER, HTTPS_PROXY_PASSWORD,
            GRADLE_JVM_ARGS // Also remove JVM args when removing proxy
        )

        for (key in proxyKeys) {
            if (key == GRADLE_JVM_ARGS) {
                // For JVM args, only remove the proxy selector argument, not the entire property
                val existingJvmArgs = properties.getProperty(key, "")
                if (existingJvmArgs.contains(PROXY_SELECTOR_ARG)) {
                    val newJvmArgs = existingJvmArgs.replace(PROXY_SELECTOR_ARG, "").trim()
                    if (newJvmArgs.isBlank()) {
                        properties.remove(key)
                    } else {
                        properties.setProperty(key, newJvmArgs)
                    }
                    removedAny = true
                }
            } else if (properties.remove(key) != null) {
                removedAny = true
            }
        }

        if (removedAny) {
            saveProperties(properties, gradlePropertiesFile)
        }

        return removedAny
    }

    /**
     * Loads properties from a file
     */
    private fun loadProperties(file: File): Properties {
        val properties = Properties()
        if (file.exists()) {
            file.inputStream().use { input ->
                properties.load(input)
            }
        }
        return properties
    }

    /**
     * Saves properties to a file
     */
    private fun saveProperties(properties: Properties, file: File) {
        file.outputStream().use { output ->
            properties.store(output, "Gradle proxy configuration managed by ProxyThemAll plugin")
        }
    }
}
