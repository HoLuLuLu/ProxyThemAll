package org.holululu.proxythemall.services.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.services.ProxyInfoExtractor
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Service responsible for managing Git proxy configurations
 *
 * This service acts as a facade that coordinates between:
 * - ProxyInfoExtractor: Extracts proxy information from IntelliJ settings
 * - GitProxyConfigurer: Configures Git with the extracted proxy information
 *
 * @param settings The settings instance. Defaults to the platform singleton.
 * @param proxyInfoExtractor The proxy info extractor. Defaults to the singleton instance.
 * @param gitProxyConfigurer The git proxy configurer. Defaults to the singleton instance.
 * @param proxySettings The proxy settings instance. Defaults to the platform singleton.
 */
class GitProxyService(
    private val settings: ProxyThemAllSettings = ProxyThemAllSettings.getInstance(),
    private val proxyInfoExtractor: ProxyInfoExtractor = ProxyInfoExtractor.instance,
    private val gitProxyConfigurer: GitProxyConfigurer = GitProxyConfigurer.instance,
    private val proxySettings: ProxySettings = ProxySettings.getInstance()
) {

    companion object {
        @JvmStatic
        val instance: GitProxyService by lazy { GitProxyService() }

        private val LOG = Logger.getInstance(GitProxyService::class.java)
    }

    /**
     * Configures Git proxy settings based on current IDE proxy configuration
     */
    fun configureGitProxy(project: Project?, onComplete: (String) -> Unit) {
        if (!settings.applyProxyToGit) {
            // If Git proxy is disabled in settings, clean up any existing proxy settings
            gitProxyConfigurer.removeGitProxySettings(project) { status ->
                onComplete("Git proxy disabled in settings - $status")
            }
            return
        }

        try {
            when (val proxyConfiguration = proxySettings.getProxyConfiguration()) {
                is ProxyConfiguration.DirectProxy -> {
                    // Remove proxy settings from Git
                    gitProxyConfigurer.removeGitProxySettings(project, onComplete)
                }

                else -> {
                    // Try to extract proxy information using modern API
                    val proxyInfo = proxyInfoExtractor.extractProxyInfo(proxyConfiguration)
                    if (proxyInfo != null) {
                        gitProxyConfigurer.setGitProxy(project, proxyInfo, onComplete)
                    } else {
                        gitProxyConfigurer.removeGitProxySettings(project, onComplete)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to configure Git proxy", e)
            onComplete("Git proxy configuration failed")
        }
    }

    /**
     * Removes Git proxy settings
     */
    fun removeGitProxySettings(project: Project?, onComplete: (String) -> Unit) {
        // Always perform cleanup regardless of settings to ensure clean state
        gitProxyConfigurer.removeGitProxySettings(project, onComplete)
    }
}
