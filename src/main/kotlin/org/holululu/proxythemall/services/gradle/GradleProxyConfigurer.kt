package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.ProxyInfo
import java.io.File
import java.util.*

/**
 * Service responsible for configuring Gradle proxy settings
 */
class GradleProxyConfigurer {

    companion object {
        @JvmStatic
        val instance: GradleProxyConfigurer by lazy { GradleProxyConfigurer() }

        private val LOG = Logger.getInstance(GradleProxyConfigurer::class.java)

        // Gradle proxy properties
        private const val HTTP_PROXY_HOST = "systemProp.http.proxyHost"
        private const val HTTP_PROXY_PORT = "systemProp.http.proxyPort"
        private const val HTTP_PROXY_USER = "systemProp.http.proxyUser"
        private const val HTTP_PROXY_PASSWORD = "systemProp.http.proxyPassword"
        private const val HTTPS_PROXY_HOST = "systemProp.https.proxyHost"
        private const val HTTPS_PROXY_PORT = "systemProp.https.proxyPort"
        private const val HTTPS_PROXY_USER = "systemProp.https.proxyUser"
        private const val HTTPS_PROXY_PASSWORD = "systemProp.https.proxyPassword"
        private const val HTTP_NON_PROXY_HOSTS = "systemProp.http.nonProxyHosts"
        private const val HTTPS_NON_PROXY_HOSTS = "systemProp.https.nonProxyHosts"
    }

    /**
     * Sets proxy for Gradle using extracted proxy information
     */
    fun setGradleProxy(project: Project?, proxyInfo: ProxyInfo, onComplete: (String) -> Unit) {
        // Run Gradle configuration in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Configuring Gradle Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gradlePropertiesFile = getGradlePropertiesFile(project)
                    if (gradlePropertiesFile != null) {
                        configureProjectGradleProperties(gradlePropertiesFile, proxyInfo)
                        LOG.info("Gradle proxy configured for project: ${proxyInfo.host}:${proxyInfo.port}")
                        onComplete("configured for project")
                    } else {
                        configureGlobalGradleProperties(proxyInfo)
                        LOG.info("Gradle proxy configured globally: ${proxyInfo.host}:${proxyInfo.port}")
                        onComplete("configured globally")
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
     * Sets proxy properties in the Properties object
     */
    private fun setProxyProperties(properties: Properties, proxyInfo: ProxyInfo) {
        // HTTP proxy settings
        properties.setProperty(HTTP_PROXY_HOST, proxyInfo.host)
        properties.setProperty(HTTP_PROXY_PORT, proxyInfo.port.toString())

        // HTTPS proxy settings
        properties.setProperty(HTTPS_PROXY_HOST, proxyInfo.host)
        properties.setProperty(HTTPS_PROXY_PORT, proxyInfo.port.toString())

        // Set authentication if provided
        if (!proxyInfo.username.isNullOrBlank()) {
            properties.setProperty(HTTP_PROXY_USER, proxyInfo.username)
            properties.setProperty(HTTPS_PROXY_USER, proxyInfo.username)
        }

        if (!proxyInfo.password.isNullOrBlank()) {
            properties.setProperty(HTTP_PROXY_PASSWORD, proxyInfo.password)
            properties.setProperty(HTTPS_PROXY_PASSWORD, proxyInfo.password)
        }

        // Set non-proxy hosts if needed (localhost, 127.0.0.1, etc.)
        val nonProxyHosts = "localhost|127.*|[::1]"
        properties.setProperty(HTTP_NON_PROXY_HOSTS, nonProxyHosts)
        properties.setProperty(HTTPS_NON_PROXY_HOSTS, nonProxyHosts)
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

        // Remove all proxy-related properties
        val proxyKeys = listOf(
            HTTP_PROXY_HOST, HTTP_PROXY_PORT, HTTP_PROXY_USER, HTTP_PROXY_PASSWORD,
            HTTPS_PROXY_HOST, HTTPS_PROXY_PORT, HTTPS_PROXY_USER, HTTPS_PROXY_PASSWORD,
            HTTP_NON_PROXY_HOSTS, HTTPS_NON_PROXY_HOSTS
        )

        for (key in proxyKeys) {
            if (properties.remove(key) != null) {
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
