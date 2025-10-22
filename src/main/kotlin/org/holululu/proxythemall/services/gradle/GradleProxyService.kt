package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.services.ProxyInfoExtractor
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Service responsible for managing Gradle proxy configurations
 *
 * This service acts as a facade that coordinates between:
 * - ProxyInfoExtractor: Extracts proxy information from IntelliJ settings
 * - GradleProxyConfigurer: Configures Gradle with the extracted proxy information
 */
class GradleProxyService {

    companion object {
        @JvmStatic
        val instance: GradleProxyService by lazy { GradleProxyService() }

        private val LOG = Logger.getInstance(GradleProxyService::class.java)
    }

    private val settings = ProxyThemAllSettings.getInstance()
    private val proxyInfoExtractor = ProxyInfoExtractor.instance
    private val gradleProxyConfigurer = GradleProxyConfigurer.instance

    /**
     * Configures Gradle proxy settings based on current IDE proxy configuration
     */
    fun configureGradleProxy(project: Project?, onComplete: (String) -> Unit) {
        if (!settings.enableGradleProxySupport) {
            // If Gradle proxy is disabled in settings, clean up any existing proxy settings
            gradleProxyConfigurer.removeGradleProxySettings(project) { status ->
                onComplete("Gradle proxy disabled in settings - $status")
            }
            return
        }

        try {
            val proxySettings = ProxySettings.getInstance()
            when (val proxyConfiguration = proxySettings.getProxyConfiguration()) {
                is ProxyConfiguration.DirectProxy -> {
                    // Remove proxy settings from Gradle
                    gradleProxyConfigurer.removeGradleProxySettings(project, onComplete)
                }

                else -> {
                    // Try to extract proxy information using modern API
                    val proxyInfo = proxyInfoExtractor.extractProxyInfo(proxyConfiguration)
                    if (proxyInfo != null) {
                        gradleProxyConfigurer.setGradleProxy(project, proxyInfo, onComplete)
                    } else {
                        gradleProxyConfigurer.removeGradleProxySettings(project, onComplete)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to configure Gradle proxy", e)
            onComplete("Gradle proxy configuration failed")
        }
    }

    /**
     * Removes Gradle proxy settings
     */
    fun removeGradleProxySettings(project: Project?, onComplete: (String) -> Unit) {
        // Always perform cleanup regardless of settings to ensure clean state
        gradleProxyConfigurer.removeGradleProxySettings(project, onComplete)
    }
}
