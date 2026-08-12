package org.holululu.proxythemall.services.gradle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.holululu.proxythemall.models.ProxyInfo
import org.holululu.proxythemall.settings.ProxyThemAllSettings
import java.io.File

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

        // Name of the dedicated changelist that keeps proxy edits out of accidental commits
        private const val CHANGELIST_NAME = "ProxyThemAll"
    }

    // Serializes read-modify-write cycles; several projects can share ~/.gradle/gradle.properties
    private val gradleFileLock = Any()

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

                            val statusMessage = if (proxyInfo.hasCredentials) {
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

                            val statusMessage = if (proxyInfo.hasCredentials) {
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
                    LOG.warn("Failed to set Gradle proxy", e)
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
                    LOG.warn("Failed to remove Gradle proxy settings", e)
                    onComplete("proxy removal failed")
                }
            }
        }.queue()
    }

    /**
     * Removes proxy properties from project gradle.properties through the VFS
     */
    private fun removeProjectGradleProxyProperties(project: Project?, gradlePropertiesFile: File) {
        val p = project ?: run {
            // No project context, use direct file I/O
            removeProxyPropertiesFromFile(gradlePropertiesFile)
            return
        }

        writeThroughChangelist(
            project = p,
            file = gradlePropertiesFile,
            createIfMissing = false,
            transform = GradlePropertiesText::removeManagedSection,
            afterMove = { cleanupProxyThemAllChangelist(p) }
        )
    }

    /**
     * Configures project-level gradle.properties file
     */
    private fun configureProjectGradleProperties(project: Project?, gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        val p = project ?: run {
            // No project context, just modify the file normally
            configureGradlePropertiesFile(gradlePropertiesFile, proxyInfo)
            return
        }

        writeThroughChangelist(
            project = p,
            file = gradlePropertiesFile,
            createIfMissing = true,
            transform = { GradlePropertiesText.withProxySection(it, proxyInfo) }
        )
    }

    /**
     * Writes the given content to a file and files the resulting change under the ProxyThemAll
     * changelist.
     *
     * The change is moved after ChangeListManager's refresh rather than by switching the user's
     * default changelist: change-to-list assignment happens asynchronously, so switching the
     * default list around the write is a race that files the change in the user's own list.
     *
     * Callers run on a background thread (Task.Backgroundable), so the blocking file and VFS work
     * happens there and only the write command is dispatched to the EDT.
     */
    private fun writeThroughChangelist(
        project: Project,
        file: File,
        createIfMissing: Boolean,
        transform: (String) -> String,
        afterMove: () -> Unit = {}
    ) {
        // Resolve and create the file off the EDT; only the write command needs it
        val virtualFile = try {
            findOrCreateVirtualFile(file, createIfMissing)
        } catch (e: Exception) {
            LOG.warn("Could not resolve ${file.path} in the VFS", e)
            null
        }

        if (virtualFile == null) {
            LOG.warn("No VFS entry for ${file.path}, writing directly")
            synchronized(gradleFileLock) { file.writeText(transform(readFileOrEmpty(file))) }
            return
        }

        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater

            try {
                val documentManager = FileDocumentManager.getInstance()
                val document = documentManager.getCachedDocument(virtualFile)

                if (document != null) {
                    // The file is open in an editor. Its document is the authoritative content -
                    // reading the VFS would miss unsaved edits, and writing the VFS would later be
                    // overwritten when the still-dirty document is saved.
                    val newContent = transform(document.text)
                    WriteCommandAction.runWriteCommandAction(project) {
                        // setText, not the `text` property: Document.getText returns String while
                        // setText takes CharSequence, so Kotlin exposes `text` as a val
                        document.text = newContent
                        documentManager.saveDocument(document)
                    }
                    LOG.info("Wrote ${file.name} through its open document")
                } else {
                    // Read through the VFS so read and write agree on content and charset
                    val newContent = transform(VfsUtilCore.loadText(virtualFile))
                    WriteCommandAction.runWriteCommandAction(project) {
                        VfsUtil.saveText(virtualFile, newContent)
                    }
                    LOG.info("Wrote ${file.name} through the VFS")
                }

                moveToProxyChangelist(project, virtualFile, afterMove)
            } catch (e: Exception) {
                LOG.warn("Failed to write ${file.name} with changelist management", e)
            }
        }, ModalityState.defaultModalityState())
    }

    /**
     * Resolves the file in the VFS, optionally creating it first.
     *
     * File creation and VFS refresh are blocking, so they run off the EDT.
     */
    private fun findOrCreateVirtualFile(file: File, createIfMissing: Boolean): VirtualFile? {
        if (createIfMissing && !file.exists()) {
            synchronized(gradleFileLock) {
                if (!file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
            }
        }

        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    }

    /**
     * Moves the change for the given file into the ProxyThemAll changelist once VCS has
     * registered it.
     */
    private fun moveToProxyChangelist(project: Project, virtualFile: VirtualFile, afterMove: () -> Unit) {
        val changeListManager = ChangeListManager.getInstance(project)

        if (!changeListManager.areChangeListsEnabled()) {
            LOG.debug("Changelists are not enabled for ${project.name}, skipping changelist management")
            return
        }

        // invokeAfterUpdate waits for the change to actually exist before we try to move it
        changeListManager.invokeAfterUpdate(
            {
                if (project.isDisposed) return@invokeAfterUpdate

                try {
                    val change = changeListManager.getChange(virtualFile)
                    if (change == null) {
                        LOG.debug("No VCS change recorded for ${virtualFile.name}, nothing to file")
                    } else {
                        val proxyChangelist = changeListManager.findChangeList(CHANGELIST_NAME)
                            ?: changeListManager.addChangeList(
                                CHANGELIST_NAME,
                                "Proxy configuration changes managed by ProxyThemAll plugin. " +
                                        "Do not commit these changes."
                            ).also { LOG.info("Created ProxyThemAll changelist") }

                        changeListManager.moveChangesTo(proxyChangelist, change)
                        LOG.info("Filed ${virtualFile.name} under the ProxyThemAll changelist")
                    }

                    afterMove()
                } catch (e: Exception) {
                    LOG.warn("Failed to file the change under the ProxyThemAll changelist", e)
                }
            },
            InvokeAfterUpdateMode.SILENT,
            null,
            ModalityState.defaultModalityState()
        )
    }

    /**
     * Removes the ProxyThemAll changelist once it is empty.
     *
     * Emptiness is only meaningful after a ChangeListManager refresh, so the check runs inside
     * invokeAfterUpdate. Changes that are not ours are moved back to the default list instead of
     * being reverted - reverting risked destroying concurrent user edits.
     */
    private fun cleanupProxyThemAllChangelist(project: Project) {
        val changeListManager = ChangeListManager.getInstance(project)
        if (!changeListManager.areChangeListsEnabled()) return

        changeListManager.invokeAfterUpdate(
            {
                if (project.isDisposed) return@invokeAfterUpdate

                try {
                    val changelist = changeListManager.findChangeList(CHANGELIST_NAME) ?: return@invokeAfterUpdate
                    val changes = changelist.changes.toList()

                    if (changes.isEmpty()) {
                        changeListManager.removeChangeList(changelist)
                        LOG.info("Deleted empty ProxyThemAll changelist")
                        return@invokeAfterUpdate
                    }

                    LOG.info("ProxyThemAll changelist still holds ${changes.size} change(s), moving them to the default list")
                    changeListManager.moveChangesTo(changeListManager.defaultChangeList, changes)

                    // Let the platform drop the list as soon as the move has been processed
                    changeListManager.scheduleAutomaticEmptyChangeListDeletion(changelist)
                } catch (e: Exception) {
                    LOG.warn("Failed to cleanup ProxyThemAll changelist", e)
                }
            },
            InvokeAfterUpdateMode.SILENT,
            null,
            ModalityState.defaultModalityState()
        )
    }

    /**
     * Gets the project-level gradle.properties file
     */
    private fun getGradlePropertiesFile(project: Project?): File? {
        val basePath = project?.basePath ?: return null
        val baseDir = File(basePath)
        return if (baseDir.isDirectory) File(baseDir, "gradle.properties") else null
    }

    /**
     * Gets the global gradle.properties file, honouring GRADLE_USER_HOME.
     *
     * Does not create the directory: this is a read path, and callers that write go through
     * findOrCreateVirtualFile.
     */
    private fun getGlobalGradlePropertiesFile(): File {
        val gradleUserHome = System.getenv("GRADLE_USER_HOME")?.takeIf { it.isNotBlank() }
        val gradleDir = gradleUserHome?.let { File(it) } ?: File(System.getProperty("user.home"), ".gradle")
        return File(gradleDir, "gradle.properties")
    }


    /**
     * Configures global gradle.properties file
     */
    private fun configureGlobalGradleProperties(proxyInfo: ProxyInfo) {
        configureGradlePropertiesFile(getGlobalGradlePropertiesFile(), proxyInfo)
    }

    /**
     * Configures gradle.properties file while preserving existing content and structure
     */
    private fun configureGradlePropertiesFile(gradlePropertiesFile: File, proxyInfo: ProxyInfo) {
        val newContent = GradlePropertiesText.withProxySection(readFileOrEmpty(gradlePropertiesFile), proxyInfo)
        gradlePropertiesFile.writeText(newContent)
        LOG.info("Wrote ProxyThemAll managed proxy settings to ${gradlePropertiesFile.name}")
    }

    /**
     * Removes proxy properties from a gradle.properties file while preserving structure
     *
     * @return true when the file content actually changed
     */
    private fun removeProxyPropertiesFromFile(gradlePropertiesFile: File): Boolean {
        if (!gradlePropertiesFile.exists()) {
            return false
        }

        val originalContent = gradlePropertiesFile.readText()
        val newContent = GradlePropertiesText.removeManagedSection(originalContent)
        if (originalContent == newContent) {
            return false
        }

        gradlePropertiesFile.writeText(newContent)
        LOG.info("Removed existing ProxyThemAll managed proxy settings")
        return true
    }

    private fun readFileOrEmpty(file: File): String =
        if (file.exists() && file.length() > 0) file.readText() else ""
}
