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

// Git routes HTTPS through http.proxy as well - there is no https.proxy config key
private const val HTTP_PROXY = "http.proxy"
private const val HTTP_NO_PROXY = "http.noproxy"
private const val GLOBAL_FLAG = "--global"
private const val UNSET_ALL_FLAG = "--unset-all"

private const val GIT_HOSTS_SEPARATOR = ","

// git config exits with 5 when the key to unset does not exist - not an error for us
private const val EXIT_CODE_KEY_MISSING = 5

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

    // Synchronization object to prevent concurrent Git operations
    private val gitOperationLock = Any()

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
                synchronized(gitOperationLock) {
                    try {
                        // Set proxy for current project if available, otherwise set globally
                        val scope = if (projectDir != null) emptyList() else listOf(GLOBAL_FLAG)
                        executeGitCommand(projectDir, listOf("config") + scope + listOf(HTTP_PROXY, proxyUrl))

                        // git's http.noproxy takes plain hosts/domains - glob patterns are ignored
                        val noProxyHosts = gitNoProxyHosts(proxyInfo)
                        if (noProxyHosts.isNotEmpty()) {
                            executeGitCommand(
                                projectDir,
                                listOf("config") + scope + listOf(HTTP_NO_PROXY, noProxyHosts)
                            )
                        }

                        val target = if (projectDir != null) "project" else "globally"
                        val statusMessage = if (proxyInfo.hasCredentials) {
                            "configured for $target with authentication"
                        } else {
                            "configured for $target"
                        }

                        // Never log proxyUrl - it embeds the credentials
                        LOG.info("Git proxy configured ($target): ${proxyInfo.host}:${proxyInfo.port}")
                        onComplete(statusMessage)
                    } catch (e: Exception) {
                        LOG.warn("Failed to set Git proxy", e)
                        onComplete("configuration failed")
                    }
                }
            }
        }.queue()
    }

    /**
     * Builds the comma-separated value for git's http.noproxy.
     *
     * Glob patterns such as `127.*` are dropped: git matches plain host and domain names only,
     * so passing them through would be silently ineffective.
     */
    private fun gitNoProxyHosts(proxyInfo: ProxyInfo): String =
        proxyInfo.bypassHosts
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains('*') }
            .joinToString(GIT_HOSTS_SEPARATOR)

    /**
     * Removes Git proxy settings
     * Returns a status message for inclusion in notifications
     */
    fun removeGitProxySettings(project: Project?, onComplete: (String) -> Unit) {
        val projectDir = getProjectDirectory(project)

        // Run Git commands in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Removing Git Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                synchronized(gitOperationLock) {
                    try {
                        // Only ever touch the scope we wrote to. Falling back to --global here
                        // would delete a proxy the user configured themselves.
                        val scope = if (projectDir != null) emptyList() else listOf(GLOBAL_FLAG)
                        val target = if (projectDir != null) "project" else "globally"

                        // --unset-all exits 5 when the key is absent, which executeGitCommand tolerates
                        val removedProxy = executeGitCommand(
                            projectDir, listOf("config") + scope + listOf(UNSET_ALL_FLAG, HTTP_PROXY)
                        ).exitCode == 0
                        val removedNoProxy = executeGitCommand(
                            projectDir, listOf("config") + scope + listOf(UNSET_ALL_FLAG, HTTP_NO_PROXY)
                        ).exitCode == 0

                        if (removedProxy || removedNoProxy) {
                            LOG.info("Git proxy settings removed ($target)")
                            onComplete("proxy removed from $target")
                        } else {
                            LOG.debug("No Git proxy settings present ($target)")
                            onComplete("no proxy settings found")
                        }
                    } catch (e: Exception) {
                        LOG.warn("Failed to remove Git proxy settings", e)
                        onComplete("proxy removal failed")
                    }
                }
            }
        }.queue()
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
     * Executes a Git command, throwing on failure.
     *
     * Exit code 5 ("key does not exist") is returned to the caller instead of throwing, so
     * removing a key that was never set is not treated as an error.
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

        if (processOutput.exitCode != 0 && processOutput.exitCode != EXIT_CODE_KEY_MISSING) {
            val errorMessage = "Git command failed with exit code ${processOutput.exitCode}: ${processOutput.stderr}"
            LOG.warn(errorMessage)
            throw RuntimeException(errorMessage)
        }

        return processOutput
    }

}
