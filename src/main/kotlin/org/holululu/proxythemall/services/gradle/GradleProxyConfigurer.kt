package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.Alarm
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.settings.ProxyThemAllSettings
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
        private fun getSettings() = ProxyThemAllSettings.getInstance()

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

    /**
     * Checks if the given project is a Gradle project by looking for Gradle build files
     */
    private fun isGradleProject(project: Project?): Boolean {
        if (project == null) return false

        val basePath = project.basePath ?: return false
        val baseDir = File(basePath)

        // Check for Gradle build files
        return baseDir.resolve("build.gradle").exists() ||
                baseDir.resolve("build.gradle.kts").exists() ||
                baseDir.resolve("settings.gradle").exists() ||
                baseDir.resolve("settings.gradle.kts").exists()
    }

    /**
     * Sets proxy for Gradle using extracted proxy information with direct credential support
     * Returns a status message for inclusion in notifications
     */
    fun setGradleProxy(project: Project?, proxyInfo: ProxyInfo, onComplete: (String) -> Unit) {
        // Run Gradle configuration in background thread to avoid EDT violations
        object : Task.Backgroundable(project, "Configuring Gradle Proxy", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Check if this is a Gradle project
                    val isGradle = isGradleProject(project)

                    if (isGradle) {
                        // It's a Gradle project - apply to project gradle.properties
                        val gradlePropertiesFile = getGradlePropertiesFile(project)
                        if (gradlePropertiesFile != null) {
                            configureProjectGradleProperties(project, gradlePropertiesFile, proxyInfo)

                            val statusMessage = if (hasCredentials(proxyInfo)) {
                                "configured for project with authentication"
                            } else {
                                "configured for project"
                            }

                            LOG.info("Gradle proxy configured for Gradle project: ${proxyInfo.host}:${proxyInfo.port}")
                            onComplete(statusMessage)
                        } else {
                            // Gradle project but no gradle.properties file exists yet - create it
                            val newGradlePropertiesFile = project?.basePath?.let { File(it, "gradle.properties") }
                            if (newGradlePropertiesFile != null) {
                                configureProjectGradleProperties(project, newGradlePropertiesFile, proxyInfo)
                                LOG.info("Gradle proxy configured for Gradle project (created gradle.properties): ${proxyInfo.host}:${proxyInfo.port}")
                                onComplete("configured for project")
                            } else {
                                LOG.warn("Could not create gradle.properties for Gradle project")
                                onComplete("configuration failed - could not create gradle.properties")
                            }
                        }
                    } else {
                        // Not a Gradle project - check if global fallback is enabled
                        if (getSettings().enableGradleGlobalFallback) {
                            // Global fallback is enabled - apply to global gradle.properties
                            configureGlobalGradleProperties(proxyInfo)

                            val statusMessage = if (hasCredentials(proxyInfo)) {
                                "configured globally with authentication"
                            } else {
                                "configured globally"
                            }

                            LOG.info("Gradle proxy configured globally (not a Gradle project, fallback enabled): ${proxyInfo.host}:${proxyInfo.port}")
                            onComplete(statusMessage)
                        } else {
                            // Global fallback is disabled - skip configuration
                            LOG.info("Skipping Gradle proxy configuration (not a Gradle project, fallback disabled)")
                            onComplete("not a Gradle project - skipped")
                        }
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
                    // Check if this is a Gradle project
                    val isGradle = isGradleProject(project)

                    if (isGradle) {
                        // It's a Gradle project - remove from project gradle.properties
                        val gradlePropertiesFile = getGradlePropertiesFile(project)
                        if (gradlePropertiesFile != null && gradlePropertiesFile.exists()) {
                            // Use VFS for proper removal like we do for adding
                            removeProjectGradleProxyProperties(project, gradlePropertiesFile)

                            LOG.info("Project-level Gradle proxy settings removed")
                            onComplete("proxy removed from project")
                        } else {
                            onComplete("no proxy settings found")
                        }
                    } else {
                        // Not a Gradle project - check if global fallback is enabled
                        if (getSettings().enableGradleGlobalFallback) {
                            // Global fallback is enabled - remove from global gradle.properties
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
                        } else {
                            // Global fallback is disabled - nothing to remove
                            LOG.info("Skipping Gradle proxy removal (not a Gradle project, fallback disabled)")
                            onComplete("not a Gradle project - skipped")
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
     * Removes proxy properties from project gradle.properties using VFS
     */
    private fun removeProjectGradleProxyProperties(project: Project?, gradlePropertiesFile: File) {
        project?.let { p ->
            // Read and prepare the content without proxy settings
            val existingContent = if (gradlePropertiesFile.exists() && gradlePropertiesFile.length() > 0) {
                gradlePropertiesFile.readText()
            } else {
                ""
            }

            val contentWithoutProxy = removeProxyThemAllSection(existingContent)

            // Wait for VCS to be ready before performing changelist operations
            executeWhenVcsReady(p, {
                try {
                    // Verify VCS is ready
                    if (!isVcsReady(p)) {
                        LOG.warn("VCS not ready for removal, falling back to direct file modification")
                        removeProxyPropertiesFromFile(gradlePropertiesFile)
                        return@executeWhenVcsReady
                    }

                    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gradlePropertiesFile)

                    virtualFile?.let { vf ->
                        // Use WriteCommandAction to modify the file - this properly notifies VCS
                        WriteCommandAction.runWriteCommandAction(p) {
                            vf.setBinaryContent(contentWithoutProxy.toByteArray())
                            LOG.info("Removed proxy settings using WriteCommandAction")
                        }

                        // Schedule changelist cleanup with a delay to let VCS process the change
                        ApplicationManager.getApplication().invokeLater({
                            cleanupProxyThemAllChangelist(project)
                        }, ModalityState.defaultModalityState())
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to remove proxy settings with VFS", e)
                }
            })
        } ?: run {
            // No project context, use direct file I/O
            removeProxyPropertiesFromFile(gradlePropertiesFile)
        }
    }

    /**
     * Cleans up the ProxyThemAll changelist based on its content:
     * 1. If empty: removes the changelist
     * 2. If only whitespace changes: reverts changes and removes the changelist
     * 3. If real changes unrelated to ProxyThemAll: moves changes to default changelist and removes ProxyThemAll changelist
     */
    private fun cleanupProxyThemAllChangelist(project: Project?) {
        project?.let { p ->
            ApplicationManager.getApplication().invokeLater({
                try {
                    val changeListManager = ChangeListManager.getInstance(p)
                    val proxyChangelist = changeListManager.findChangeList("ProxyThemAll")

                    proxyChangelist?.let { changelist ->
                        val changes = changelist.changes.toList()

                        when {
                            // Case 1: Changelist is empty
                            changes.isEmpty() -> {
                                changeListManager.removeChangeList(changelist)
                                LOG.info("Deleted empty ProxyThemAll changelist")
                            }

                            // Case 2: Only whitespace/empty line changes
                            areOnlyWhitespaceChanges(changes) -> {
                                LOG.info("ProxyThemAll changelist contains only whitespace changes, reverting...")

                                // Rollback the changes
                                changes.forEach { change ->
                                    try {
                                        changeListManager.scheduleAutomaticEmptyChangeListDeletion(changelist)
                                        // Revert the change by restoring original content
                                        val virtualFile = change.virtualFile
                                        if (virtualFile != null && change.beforeRevision != null) {
                                            WriteCommandAction.runWriteCommandAction(p) {
                                                val originalContent = change.beforeRevision?.content
                                                if (originalContent != null) {
                                                    virtualFile.setBinaryContent(originalContent.toByteArray())
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        LOG.warn("Failed to revert change for file: ${change.virtualFile?.path}", e)
                                    }
                                }

                                // After reverting, the changelist should be empty, remove it
                                if (changelist.changes.isEmpty()) {
                                    changeListManager.removeChangeList(changelist)
                                    LOG.info("Reverted whitespace changes and deleted ProxyThemAll changelist")
                                } else {
                                    LOG.warn("Some changes could not be reverted, changelist still contains changes")
                                }
                            }

                            // Case 3: Real changes unrelated to ProxyThemAll
                            else -> {
                                LOG.info("ProxyThemAll changelist contains real changes, moving to default changelist...")

                                val defaultChangelist = changeListManager.defaultChangeList

                                // Move all changes to the default changelist
                                changes.forEach { change ->
                                    try {
                                        changeListManager.moveChangesTo(defaultChangelist, change)
                                    } catch (e: Exception) {
                                        LOG.warn(
                                            "Failed to move change to default changelist: ${change.virtualFile?.path}",
                                            e
                                        )
                                    }
                                }

                                // After moving, remove the ProxyThemAll changelist
                                if (changelist.changes.isEmpty()) {
                                    changeListManager.removeChangeList(changelist)
                                    LOG.info("Moved changes to default changelist and deleted ProxyThemAll changelist")
                                } else {
                                    LOG.warn("Some changes could not be moved, ProxyThemAll changelist still contains changes")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to cleanup ProxyThemAll changelist", e)
                }
            }, ModalityState.defaultModalityState())
        }
    }

    /**
     * Checks if all changes in the list are only whitespace/empty line changes
     * Returns true if all changes are whitespace-only, false otherwise
     */
    private fun areOnlyWhitespaceChanges(changes: List<com.intellij.openapi.vcs.changes.Change>): Boolean {
        if (changes.isEmpty()) return false

        return changes.all { change ->
            try {
                val beforeContent = change.beforeRevision?.content ?: ""
                val afterContent = change.afterRevision?.content ?: ""

                // Split into lines and filter out blank lines, then compare
                val beforeLines = beforeContent.lines().filterNot { it.isBlank() }
                val afterLines = afterContent.lines().filterNot { it.isBlank() }

                // If the non-blank lines are identical, it's only a whitespace change
                beforeLines == afterLines
            } catch (e: Exception) {
                LOG.warn("Failed to analyze change for file: ${change.virtualFile?.path}", e)
                // If we can't determine, assume it's a real change to be safe
                false
            }
        }
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
     * Checks if VCS and ChangeListManager are ready for operations
     */
    private fun isVcsReady(project: Project): Boolean {
        return try {
            val changeListManager = ChangeListManager.getInstance(project)
            // Check if the manager is initialized and has at least the default changelist
            changeListManager.changeLists.isNotEmpty()
        } catch (e: Exception) {
            LOG.warn("VCS not ready: ${e.message}")
            false
        }
    }

    /**
     * Waits for VCS to be ready and then executes the given action
     * Uses IntelliJ's Alarm for proper delayed execution without blocking threads
     */
    private fun executeWhenVcsReady(project: Project, action: () -> Unit, retryCount: Int = 0) {
        val maxRetries = 10

        ApplicationManager.getApplication().invokeLater({
            if (isVcsReady(project)) {
                // VCS is ready, execute the action
                action()
            } else if (retryCount < maxRetries) {
                // VCS not ready yet, schedule retry after delay using Alarm (non-blocking)
                val delayMs: Long = (300 * (retryCount + 1)).toLong() // Increasing delay: 300ms, 600ms, 900ms...
                LOG.info("VCS not ready (attempt ${retryCount + 1}/$maxRetries), retrying in ${delayMs}ms")

                // Create alarm and schedule retry
                val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
                val nextRetryCount = retryCount + 1
                alarm.addRequest(
                    {
                        try {
                            executeWhenVcsReady(project, action, nextRetryCount)
                        } finally {
                            alarm.dispose()
                        }
                    },
                    delayMs
                )
            } else {
                // Max retries reached, execute action anyway (VCS checks will handle fallback)
                LOG.warn("VCS not ready after $maxRetries attempts, proceeding anyway")
                action()
            }
        }, ModalityState.defaultModalityState())
    }

    /**
     * Configures project-level gradle.properties file
     */
    private fun configureProjectGradleProperties(project: Project?, gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        project?.let { p ->
            // Prepare the new content
            val newContent = buildGradlePropertiesContent(gradlePropertiesFile, proxyInfo)

            // Wait for VCS to be ready before performing changelist operations
            executeWhenVcsReady(p, {
                try {
                    val changeListManager = ChangeListManager.getInstance(p)

                    // Verify VCS is still ready
                    if (!isVcsReady(p)) {
                        LOG.warn("VCS not ready for changelist operations, falling back to direct file modification")
                        configureGradlePropertiesFile(gradlePropertiesFile, proxyInfo)
                        return@executeWhenVcsReady
                    }

                    val originalActiveList = changeListManager.defaultChangeList

                    // Find or create ProxyThemAll changelist
                    var proxyChangelist = changeListManager.findChangeList("ProxyThemAll")
                    if (proxyChangelist == null) {
                        proxyChangelist = changeListManager.addChangeList(
                            "ProxyThemAll",
                            "Proxy configuration changes managed by ProxyThemAll plugin. Do not commit these changes."
                        )
                        LOG.info("Created ProxyThemAll changelist")
                    }

                    // Switch to ProxyThemAll as active BEFORE modifying file
                    changeListManager.defaultChangeList = proxyChangelist
                    LOG.info("Switched to ProxyThemAll changelist as active")

                    try {
                        // Get or create the virtual file
                        var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gradlePropertiesFile)
                        if (virtualFile == null) {
                            // File doesn't exist, create it first
                            gradlePropertiesFile.createNewFile()
                            virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gradlePropertiesFile)
                        }

                        virtualFile?.let { vf ->
                            // Use WriteCommandAction to modify the file - this is the proper IntelliJ way
                            WriteCommandAction.runWriteCommandAction(p) {
                                vf.setBinaryContent(newContent.toByteArray())
                                LOG.info("Modified gradle.properties using WriteCommandAction while ProxyThemAll is active")
                            }
                        }
                    } finally {
                        // Always restore original active changelist
                        changeListManager.defaultChangeList = originalActiveList
                        LOG.info("Restored original active changelist")
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to configure gradle.properties with changelist management", e)
                }
            })
        } ?: run {
            // No project context, just modify the file normally
            configureGradlePropertiesFile(gradlePropertiesFile, proxyInfo)
        }
    }

    /**
     * Builds the complete gradle.properties file content
     */
    private fun buildGradlePropertiesContent(gradlePropertiesFile: File, proxyInfo: ProxyInfo): String {
        // Read existing content if file exists
        val existingContent = if (gradlePropertiesFile.exists() && gradlePropertiesFile.length() > 0) {
            gradlePropertiesFile.readText()
        } else {
            ""
        }

        // Remove any existing ProxyThemAll section from the existing content
        val contentWithoutProxy = removeProxyThemAllSection(existingContent)

        // Build new proxy settings
        val proxySettings = buildProxySettingsContent(proxyInfo)

        // Combine
        return if (contentWithoutProxy.isBlank()) {
            proxySettings
        } else {
            val separator = if (contentWithoutProxy.endsWith("\n")) "\n" else "\n\n"
            contentWithoutProxy + separator + proxySettings
        }
    }

    /**
     * Removes ProxyThemAll section from content string
     */
    private fun removeProxyThemAllSection(content: String): String {
        if (content.isBlank()) return content

        val lines = content.lines().toMutableList()
        var startIndex = -1
        var endIndex = -1

        // Find the ProxyThemAll managed section
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line == PROXY_SECTION_START) {
                startIndex = maxOf(0, i - 1)
            } else if (line == PROXY_SECTION_END && startIndex != -1) {
                endIndex = i
                break
            }
        }

        // Remove the managed section if found
        if (startIndex != -1 && endIndex != -1) {
            for (i in endIndex downTo startIndex) {
                lines.removeAt(i)
            }
        }

        return lines.joinToString("\n")
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
