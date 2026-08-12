package org.holululu.proxythemall.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.listeners.ProxyStateChangeManager
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.notifications.NotificationService
import org.holululu.proxythemall.services.ProxyCredentialsStorage
import org.holululu.proxythemall.services.ProxyService
import org.holululu.proxythemall.services.git.GitProxyService
import org.holululu.proxythemall.services.gradle.GradleProxyService
import org.holululu.proxythemall.utils.NotificationMessages
import org.holululu.proxythemall.utils.NotificationMessages.MESSAGE_SEPARATOR
import org.holululu.proxythemall.widgets.ProxyStatusBarWidget
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Controller that orchestrates proxy toggle operations and user notifications
 *
 * This controller coordinates between the core ProxyService and the specialized
 * Git and Gradle proxy services to provide a unified proxy management experience.
 */
class ProxyController {

    companion object {
        @JvmStatic
        val instance: ProxyController by lazy { ProxyController() }

        private val LOG = Logger.getInstance(ProxyController::class.java)
    }

    private val proxyService = ProxyService.instance
    private val gitProxyService = GitProxyService.instance
    private val gradleProxyService = GradleProxyService.instance
    private val notificationService = NotificationService.instance
    private val stateChangeManager = ProxyStateChangeManager.instance

    /**
     * Handles the proxy toggle action and shows appropriate notifications
     */
    fun handleProxyToggle(project: Project?) {
        try {
            val currentState = proxyService.getCurrentProxyState()
            LOG.debug("Current proxy state: $currentState")

            when (currentState) {
                ProxyState.ENABLED -> toggleProxyTo(ProxyState.DISABLED, project)
                ProxyState.DISABLED -> toggleProxyTo(ProxyState.ENABLED, project)
                ProxyState.NOT_CONFIGURED -> showConfigurationRequiredNotification(project)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to handle proxy toggle", e)
            notificationService.showNotification(
                project,
                NotificationMessages.proxyOperationFailed(
                    "Could not toggle the proxy: ${e.message ?: "unknown error"}"
                )
            )
        }
    }

    /**
     * Toggles the proxy and applies the resulting configuration to every open project.
     *
     * @param expectedState the state the toggle is expected to produce
     */
    private fun toggleProxyTo(expectedState: ProxyState, project: Project?) {
        val newState = proxyService.toggleProxy()
        if (newState != expectedState) {
            LOG.warn("Expected $expectedState state after toggle, got: $newState")
            return
        }

        // Apply to all open projects directly rather than waiting for the polling listener
        applyToAllProjects(newState.isProxyActive, showNotifications = true, notificationProject = project)
        stateChangeManager.notifyStateChanged()
        LOG.debug("Proxy toggled to $newState successfully")
    }

    /**
     * Shows the "configuration required" notification, offering a restore when a backup exists
     */
    private fun showConfigurationRequiredNotification(project: Project?) {
        LOG.info("Proxy not configured - showing configuration required notification")

        // PasswordSafe access is slow and must not run on the EDT
        ApplicationManager.getApplication().executeOnPooledThread {
            val hasStoredConfig = try {
                ProxyCredentialsStorage.getInstance().hasStoredConfiguration()
            } catch (e: Exception) {
                LOG.warn("Failed to check stored configuration", e)
                false
            }
            LOG.debug("Stored proxy configuration exists: $hasStoredConfig")

            notificationService.showNotification(
                project,
                NotificationMessages.proxyConfigurationRequired(project, hasStoredConfig)
            )
        }
    }

    /**
     * Configures Git and Gradle proxy services for a single project.
     *
     * Both services complete on their own background threads, so the shared status is collected
     * through atomics; the notification is emitted once both have reported.
     */
    private fun configureProxyServices(project: Project?, isEnabled: Boolean, showNotification: Boolean) {
        val gitStatus = AtomicReference("")
        val gradleStatus = AtomicReference("")
        val remaining = AtomicInteger(2)

        val onComplete = {
            if (remaining.decrementAndGet() == 0) {
                val combinedStatus = buildString {
                    gitStatus.get().takeIf { it.isNotEmpty() }?.let { append(MESSAGE_SEPARATOR).append("Git: $it") }
                    gradleStatus.get().takeIf { it.isNotEmpty() }
                        ?.let { append(MESSAGE_SEPARATOR).append("Gradle: $it") }
                }

                if (showNotification) {
                    val notification = if (isEnabled) {
                        NotificationMessages.proxyEnabled(combinedStatus)
                    } else {
                        NotificationMessages.proxyDisabled(combinedStatus)
                    }
                    notificationService.showNotification(project, notification)
                }

                LOG.debug("Proxy services configured: $combinedStatus")
            }
        }

        try {
            gitProxyService.configureGitProxy(project) { status ->
                gitStatus.set(status)
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Git proxy", e)
            gitStatus.set("Git configuration failed")
            onComplete()
        }

        try {
            gradleProxyService.configureGradleProxy(project) { status ->
                gradleStatus.set(status)
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Gradle proxy", e)
            gradleStatus.set("Gradle configuration failed")
            onComplete()
        }
    }

    /**
     * Updates the status bar widget to reflect the current proxy state
     */
    private fun updateStatusBarWidget(project: Project?) {
        project ?: return

        // Swing access must happen on the EDT; callers include the polling thread
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                WindowManager.getInstance().getStatusBar(project)?.updateWidget(ProxyStatusBarWidget.WIDGET_ID)
            }
        }
    }

    /**
     * Performs a complete cleanup and reapplication of proxy settings for all open projects.
     *
     * @param targetEnabled The desired proxy state after cleanup (true = enabled, false = disabled)
     */
    fun cleanupAndReapplyProxySettingsForAllProjects(targetEnabled: Boolean) {
        applyToAllProjects(targetEnabled, showNotifications = true)
        stateChangeManager.notifyStateChanged()
    }

    /**
     * Same as [cleanupAndReapplyProxySettingsForAllProjects] but without notifications.
     *
     * Used by the settings change listener, which would otherwise duplicate the balloon shown
     * by the operation that triggered it.
     */
    fun cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled: Boolean) {
        applyToAllProjects(targetEnabled, showNotifications = false)
    }

    /**
     * Convenience method for cleanup and reapplication based on current proxy state.
     */
    fun cleanupAndReapplyProxySettings() {
        cleanupAndReapplyProxySettingsForAllProjects(proxyService.getCurrentProxyState().isProxyActive)
    }

    /**
     * Applies the desired proxy state to every open project.
     *
     * @param targetEnabled the desired proxy state
     * @param showNotifications whether to emit state change balloons
     * @param notificationProject project to attach the balloon to; defaults to each project itself
     */
    private fun applyToAllProjects(
        targetEnabled: Boolean,
        showNotifications: Boolean,
        notificationProject: Project? = null
    ) {
        try {
            LOG.info("Applying proxy configuration to all projects (target: ${if (targetEnabled) "enabled" else "disabled"})")

            val openProjects = ProjectManager.getInstance().openProjects.toList()
            LOG.debug("Found ${openProjects.size} open projects")

            if (targetEnabled) {
                ensureProxyEnabled()
            } else {
                // Clean up project-specific settings first, then the IDE proxy itself
                openProjects.forEach { project ->
                    runForProject(project) { performProjectSpecificCleanup(project) }
                }
                performGlobalCleanup()
            }

            openProjects.forEach { project ->
                runForProject(project) {
                    LOG.debug("Configuring services for project: ${project.name}")
                    // Only the project the user acted in should show the balloon
                    val notify = showNotifications && (notificationProject == null || notificationProject == project)
                    configureProxyServices(project, targetEnabled, notify)
                    updateStatusBarWidget(project)
                }
            }

            LOG.info("Proxy configuration applied to all ${openProjects.size} projects")
        } catch (e: Exception) {
            LOG.warn("Failed to apply proxy settings for all projects", e)
            notificationService.showNotification(
                ProjectManager.getInstance().openProjects.firstOrNull(),
                NotificationMessages.proxyOperationFailed(
                    "Applying the proxy configuration failed: ${e.message ?: "unknown error"}"
                )
            )
        }
    }

    /**
     * Runs a per-project action, isolating failures so one project cannot break the others
     */
    private fun runForProject(project: Project, action: () -> Unit) {
        if (project.isDisposed) return

        try {
            action()
        } catch (e: Exception) {
            LOG.warn("Proxy operation failed for project ${project.name}", e)
        }
    }

    /**
     * Makes sure the IDE proxy is enabled before configuring the dependent services
     */
    private fun ensureProxyEnabled() {
        val currentState = proxyService.getCurrentProxyState()
        LOG.debug("Current proxy state: $currentState")

        if (currentState == ProxyState.ENABLED) return

        val newState = proxyService.forceEnableProxy()
        if (newState == ProxyState.ENABLED) {
            LOG.debug("Proxy enabled before service configuration")
        } else {
            LOG.warn("Failed to enable proxy before service configuration, current state: $newState")
        }
    }

    /**
     * Performs cleanup of global proxy settings (IDE proxy settings)
     */
    @Suppress("UnstableApiUsage")
    private fun performGlobalCleanup() {
        LOG.debug("Performing global proxy cleanup")

        try {
            ProxySettings.getInstance().setProxyConfiguration(ProxyConfiguration.direct)
            LOG.debug("Global IDE proxy settings cleaned up")
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup global IDE proxy settings", e)
        }
    }

    /**
     * Performs cleanup of project-specific proxy settings (Git, Gradle)
     */
    private fun performProjectSpecificCleanup(project: Project) {
        LOG.debug("Performing project-specific proxy cleanup for: ${project.name}")

        try {
            gitProxyService.removeGitProxySettings(project) { status ->
                LOG.debug("Git proxy cleanup for ${project.name}: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Git proxy settings for project ${project.name}", e)
        }

        try {
            gradleProxyService.removeGradleProxySettings(project) { status ->
                LOG.debug("Gradle proxy cleanup for ${project.name}: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Gradle proxy settings for project ${project.name}", e)
        }
    }
}
