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
 */
class GitProxyService {

    companion object {
        @JvmStatic
        val instance: GitProxyService by lazy { GitProxyService() }

        private val LOG = Logger.getInstance(GitProxyService::class.java)
    }

    private val settings = ProxyThemAllSettings.getInstance()
    private val proxyInfoExtractor = ProxyInfoExtractor.instance
    private val gitProxyConfigurer = GitProxyConfigurer.instance

    /**
     * Configures Git proxy settings based on current IDE proxy configuration
     */
    fun configureGitProxy(project: Project?, onComplete: (String) -> Unit) {
        if (!settings.applyProxyToGit) {
            onComplete("Git proxy disabled in settings")
            return
        }

        try {
            val proxySettings = ProxySettings.getInstance()
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
        if (!settings.applyProxyToGit) {
            onComplete("Git proxy disabled in settings")
            return
        }

        gitProxyConfigurer.removeGitProxySettings(project, onComplete)
    }
}
