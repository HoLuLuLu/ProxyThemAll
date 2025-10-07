package org.holululu.proxythemall.services.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.config.GitExecutableManager
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.utils.ProxyUrlBuilder
import java.io.File

private const val HTTP_PROXY = "http.proxy"
private const val HTTPS_PROXY = "https.proxy"
private const val HTTP_NO_PROXY = "http.noproxy"
private const val HTTPS_NO_PROXY = "https.noproxy"
private const val GLOBAL_FLAG = "--global"
private const val UNSET_FLAG = "--unset"

/**
 * Service responsible for configuring Git proxy settings using direct credentials
 *
 * This configurer uses authenticated proxy URLs when credentials are available,
 * or falls back to host/port only when no credentials are provided.
 */
class GitProxyConfigurer {

    companion object {
        @JvmStatic
        val instance: GitProxyConfigurer by lazy { GitProxyConfigurer() }

        private val LOG = Logger.getInstance(GitProxyConfigurer::class.java)
    }

    private val proxyUrlBuilder = ProxyUrlBuilder.instance

    /**
     * Sets proxy for Git using extracted proxy information
     * Uses authenticated proxy URLs when credentials are available
     * Returns a status message for inclusion in notifications
     */
    fun setGitProxy(project: Project?, proxyInfo: ProxyInfo, onComplete: (String) -> Unit) {
        val proxyUrl = proxyUrlBuilder.buildProxyUrl(proxyInfo)
        val projectDir = getProjectDirectory(project)

        // Run Git commands in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Configuring Git Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Set proxy for current project if available, otherwise set globally
                    if (projectDir != null) {
                        executeGitCommand(projectDir, listOf("config", HTTP_PROXY, proxyUrl))
                        executeGitCommand(projectDir, listOf("config", HTTPS_PROXY, proxyUrl))

                        // Set no-proxy hosts for project
                        val noProxyHosts = buildNoProxyHostsString(proxyInfo.nonProxyHosts)
                        if (noProxyHosts.isNotEmpty()) {
                            executeGitCommand(projectDir, listOf("config", HTTP_NO_PROXY, noProxyHosts))
                            executeGitCommand(projectDir, listOf("config", HTTPS_NO_PROXY, noProxyHosts))
                        }

                        val statusMessage = if (hasCredentials(proxyInfo)) {
                            "configured for project with authentication"
                        } else {
                            "configured for project"
                        }
                        
                        LOG.info("Git proxy configured for project: $proxyUrl")
                        onComplete(statusMessage)
                    } else {
                        executeGitCommand(null, listOf("config", GLOBAL_FLAG, HTTP_PROXY, proxyUrl))
                        executeGitCommand(null, listOf("config", GLOBAL_FLAG, HTTPS_PROXY, proxyUrl))

                        // Set no-proxy hosts globally
                        val noProxyHosts = buildNoProxyHostsString(proxyInfo.nonProxyHosts)
                        if (noProxyHosts.isNotEmpty()) {
                            executeGitCommand(null, listOf("config", GLOBAL_FLAG, HTTP_NO_PROXY, noProxyHosts))
                            executeGitCommand(null, listOf("config", GLOBAL_FLAG, HTTPS_NO_PROXY, noProxyHosts))
                        }

                        val statusMessage = if (hasCredentials(proxyInfo)) {
                            "configured globally with authentication"
                        } else {
                            "configured globally"
                        }
                        
                        LOG.info("Git proxy configured globally: $proxyUrl")
                        onComplete(statusMessage)
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to set Git proxy", e)
                    onComplete("configuration failed")
                }
            }
        }.queue()
    }

    /**
     * Removes Git proxy settings
     * Returns a status message for inclusion in notifications
     */
    fun removeGitProxySettings(project: Project?, onComplete: (String) -> Unit) {
        val projectDir = getProjectDirectory(project)

        // Run Git commands in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Removing Git Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    var removedAny = false

                    // First try to remove project-level settings if we have a project directory
                    if (projectDir != null) {
                        try {
                            executeGitCommand(projectDir, listOf("config", UNSET_FLAG, HTTP_PROXY))
                            executeGitCommand(projectDir, listOf("config", UNSET_FLAG, HTTPS_PROXY))
                            // Also remove no-proxy settings
                            try {
                                executeGitCommand(projectDir, listOf("config", UNSET_FLAG, HTTP_NO_PROXY))
                                executeGitCommand(projectDir, listOf("config", UNSET_FLAG, HTTPS_NO_PROXY))
                            } catch (e: Exception) {
                                LOG.debug("Project-level no-proxy settings not found: ${e.message}")
                            }
                            LOG.info("Project-level Git proxy settings removed")
                            removedAny = true
                            onComplete("proxy removed from project")
                        } catch (e: Exception) {
                            LOG.debug("Project-level proxy settings not found: ${e.message}")
                        }
                    }

                    // If no project-level settings were removed, try global settings
                    if (!removedAny) {
                        try {
                            executeGitCommand(null, listOf("config", GLOBAL_FLAG, UNSET_FLAG, HTTP_PROXY))
                            executeGitCommand(null, listOf("config", GLOBAL_FLAG, UNSET_FLAG, HTTPS_PROXY))
                            // Also remove global no-proxy settings
                            try {
                                executeGitCommand(null, listOf("config", GLOBAL_FLAG, UNSET_FLAG, HTTP_NO_PROXY))
                                executeGitCommand(null, listOf("config", GLOBAL_FLAG, UNSET_FLAG, HTTPS_NO_PROXY))
                            } catch (e: Exception) {
                                LOG.debug("Global no-proxy settings not found: ${e.message}")
                            }
                            LOG.info("Global Git proxy settings removed")
                            onComplete("proxy removed globally")
                        } catch (e: Exception) {
                            LOG.debug("Global proxy settings not found: ${e.message}")
                            onComplete("no proxy settings found")
                        }
                    }

                } catch (e: Exception) {
                    LOG.error("Failed to remove Git proxy settings", e)
                    onComplete("proxy removal failed")
                }
            }
        }.queue()
    }

    /**
     * Builds the no-proxy hosts string for Git configuration
     * Combines user-defined hosts with essential defaults and formats them for Git (comma-separated)
     */
    private fun buildNoProxyHostsString(userNonProxyHosts: Set<String>): String {
        // Essential defaults that should always be excluded from proxy
        val essentialDefaults = setOf("localhost", "127.*", "[::1]")

        // Combine user-defined hosts with essential defaults
        val allNonProxyHosts = essentialDefaults + userNonProxyHosts.filter { it.isNotBlank() }

        // Format for Git: comma-separated string
        return allNonProxyHosts.joinToString(",")
    }

    /**
     * Checks if proxy info contains credentials
     */
    private fun hasCredentials(proxyInfo: ProxyInfo): Boolean {
        return !proxyInfo.username.isNullOrBlank() && !proxyInfo.password.isNullOrBlank()
    }

    /**
     * Gets the project directory for Git commands
     */
    private fun getProjectDirectory(project: Project?): File? {
        return project?.let { p ->
            p.basePath?.let { basePath ->
                File(basePath).takeIf { it.exists() && it.isDirectory }
            }
        }
    }

    /**
     * Executes a Git command and throws exception on failure
     */
    private fun executeGitCommand(workingDirectory: File?, arguments: List<String>): ProcessOutput {
        // Get Git executable path from IDE settings
        val gitExecutable = try {
            GitExecutableManager.getInstance().pathToGit
        } catch (e: Exception) {
            LOG.warn("Failed to get Git executable from IDE settings, falling back to 'git'", e)
            "git"
        }

        val commandLine = GeneralCommandLine(gitExecutable)
        commandLine.addParameters(arguments)
        workingDirectory?.let { commandLine.workDirectory = it }

        val processOutput = ExecUtil.execAndGetOutput(commandLine, 10000)

        if (processOutput.exitCode != 0) {
            val errorMessage = "Git command failed with exit code ${processOutput.exitCode}: ${processOutput.stderr}"
            LOG.warn(errorMessage)
            throw RuntimeException(errorMessage)
        }

        return processOutput
    }

}
