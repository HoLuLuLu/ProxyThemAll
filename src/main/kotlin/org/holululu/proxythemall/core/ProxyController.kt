package org.holululu.proxythemall.core

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import org.holululu.proxythemall.listeners.ProxyStateChangeManager
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.notifications.NotificationService
import org.holululu.proxythemall.services.ProxyService
import org.holululu.proxythemall.services.git.GitProxyService
import org.holululu.proxythemall.services.gradle.GradleProxyService
import org.holululu.proxythemall.utils.NotificationMessages
import org.holululu.proxythemall.utils.NotificationMessages.MESSAGE_SEPARATOR
import org.holululu.proxythemall.widgets.ProxyStatusBarWidget

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
                ProxyState.ENABLED -> {
                    handleProxyDisable(project)
                }

                ProxyState.DISABLED -> {
                    handleProxyEnable(project)
                }

                ProxyState.NOT_CONFIGURED -> {
                    LOG.info("Proxy not configured - showing configuration required notification")
                    notificationService.showNotification(
                        project,
                        NotificationMessages.proxyConfigurationRequired(project)
                    )
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to handle proxy toggle", e)
            // Show error notification to user
            notificationService.showNotification(
                project,
                NotificationMessages.proxyDisabled("Error: ${e.message ?: "Unknown error occurred"}")
            )
        }
    }

    /**
     * Handles disabling the proxy
     */
    private fun handleProxyDisable(project: Project?) {
        try {
            val newState = proxyService.toggleProxy()
            if (newState == ProxyState.DISABLED) {
                stateChangeManager.notifyStateChanged()
                configureProxyServices(project, false)
                updateStatusBarWidget(project)
                LOG.debug("Proxy disabled successfully")
            } else {
                LOG.warn("Expected DISABLED state after toggle, got: $newState")
            }
        } catch (e: Exception) {
            LOG.error("Failed to disable proxy", e)
            throw e
        }
    }

    /**
     * Handles enabling the proxy
     */
    private fun handleProxyEnable(project: Project?) {
        try {
            val newState = proxyService.toggleProxy()
            if (newState == ProxyState.ENABLED) {
                stateChangeManager.notifyStateChanged()
                configureProxyServices(project, true)
                updateStatusBarWidget(project)
                LOG.debug("Proxy enabled successfully")
            } else {
                LOG.warn("Expected ENABLED state after toggle, got: $newState")
            }
        } catch (e: Exception) {
            LOG.error("Failed to enable proxy", e)
            throw e
        }
    }

    /**
     * Configures Git and Gradle proxy services and shows unified notification
     */
    private fun configureProxyServices(project: Project?, isEnabled: Boolean) {
        var gitStatus = ""
        var gradleStatus = ""
        var completedCount = 0

        val onComplete = {
            completedCount++
            if (completedCount == 2) {
                val combinedStatus = buildString {
                    if (gitStatus.isNotEmpty()) append(MESSAGE_SEPARATOR).append("Git: $gitStatus")
                    if (gradleStatus.isNotEmpty()) append(MESSAGE_SEPARATOR).append("Gradle: $gradleStatus")
                }

                val notification = if (isEnabled) {
                    NotificationMessages.proxyEnabled(combinedStatus)
                } else {
                    NotificationMessages.proxyDisabled(combinedStatus)
                }

                notificationService.showNotification(project, notification)
                LOG.debug("Proxy services configured: $combinedStatus")
            }
        }

        // Configure Git proxy
        try {
            gitProxyService.configureGitProxy(project) { status ->
                gitStatus = status
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Git proxy", e)
            gitStatus = "Git configuration failed"
            onComplete()
        }

        // Configure Gradle proxy
        try {
            gradleProxyService.configureGradleProxy(project) { status ->
                gradleStatus = status
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Gradle proxy", e)
            gradleStatus = "Gradle configuration failed"
            onComplete()
        }
    }

    /**
     * Configures Git and Gradle proxy services silently without showing notifications
     */
    private fun configureProxyServicesSilently(project: Project?) {
        var gitStatus = ""
        var gradleStatus = ""
        var completedCount = 0

        val onComplete = {
            completedCount++
            if (completedCount == 2) {
                val combinedStatus = buildString {
                    if (gitStatus.isNotEmpty()) append(MESSAGE_SEPARATOR).append("Git: $gitStatus")
                    if (gradleStatus.isNotEmpty()) append(MESSAGE_SEPARATOR).append("Gradle: $gradleStatus")
                }

                LOG.debug("Proxy services configured silently: $combinedStatus")
            }
        }

        // Configure Git proxy
        try {
            gitProxyService.configureGitProxy(project) { status ->
                gitStatus = status
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Git proxy", e)
            gitStatus = "Git configuration failed"
            onComplete()
        }

        // Configure Gradle proxy
        try {
            gradleProxyService.configureGradleProxy(project) { status ->
                gradleStatus = status
                onComplete()
            }
        } catch (e: Exception) {
            LOG.warn("Failed to configure Gradle proxy", e)
            gradleStatus = "Gradle configuration failed"
            onComplete()
        }
    }

    /**
     * Updates the status bar widget to reflect the current proxy state
     */
    private fun updateStatusBarWidget(project: Project?) {
        project?.let { p ->
            val statusBar = WindowManager.getInstance().getStatusBar(p)
            statusBar?.updateWidget(ProxyStatusBarWidget.WIDGET_ID)
        }
    }

    /**
     * Performs a complete cleanup and reapplication of proxy settings for all open projects.
     * This method ensures a clean state by:
     * 1. Disabling all proxy settings (IDE, Git, Gradle)
     * 2. Reapplying the proxy configuration based on the desired state
     *
     * @param targetEnabled The desired proxy state after cleanup (true = enabled, false = disabled)
     */
    fun cleanupAndReapplyProxySettingsForAllProjects(targetEnabled: Boolean) {
        cleanupAndReapplyProxySettingsForAllProjectsInternal(targetEnabled, showNotifications = true)
    }

    /**
     * Performs a complete cleanup and reapplication of proxy settings for all open projects silently.
     * This method ensures a clean state by:
     * 1. Disabling all proxy settings (IDE, Git, Gradle)
     * 2. Reapplying the proxy configuration based on the desired state
     *
     * This version does not show notifications to avoid duplicate notifications when triggered
     * by the HttpProxySettingsChangeListener.
     *
     * @param targetEnabled The desired proxy state after cleanup (true = enabled, false = disabled)
     */
    fun cleanupAndReapplyProxySettingsForAllProjectsSilently(targetEnabled: Boolean) {
        cleanupAndReapplyProxySettingsForAllProjectsInternal(targetEnabled, showNotifications = false)
    }

    /**
     * Internal method that performs the actual cleanup and reapplication logic.
     *
     * @param targetEnabled The desired proxy state after cleanup (true = enabled, false = disabled)
     * @param showNotifications Whether to show notifications during the process
     */
    private fun cleanupAndReapplyProxySettingsForAllProjectsInternal(
        targetEnabled: Boolean,
        showNotifications: Boolean
    ) {
        try {
            LOG.info("Starting proxy cleanup and reapplication process for all projects (target: ${if (targetEnabled) "enabled" else "disabled"})")

            // Get all open projects
            val openProjects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.toList()
            LOG.debug("Found ${openProjects.size} open projects")

            if (targetEnabled) {
                // When target is enabled, we should NOT clean up first - just ensure proxy is enabled and configure services
                LOG.debug("Target is enabled - ensuring proxy is enabled and configuring services")

                val currentState = proxyService.getCurrentProxyState()
                LOG.debug("Current proxy state: $currentState")

                // If proxy is not enabled, try to enable it
                if (currentState != ProxyState.ENABLED) {
                    val newState = proxyService.forceEnableProxy()
                    if (newState == ProxyState.ENABLED) {
                        LOG.debug("Proxy enabled before service configuration")
                    } else {
                        LOG.warn("Failed to enable proxy before service configuration, current state: $newState")
                    }
                }

                // Configure project-specific settings for each project (Git and Gradle)
                openProjects.forEach { project ->
                    try {
                        LOG.debug("Configuring services for project: ${project.name}")
                        if (showNotifications) {
                            configureProxyServices(project, true)
                        } else {
                            configureProxyServicesSilently(project)
                        }
                        updateStatusBarWidget(project)
                    } catch (e: Exception) {
                        LOG.warn("Failed to configure services for project ${project.name}", e)
                    }
                }
            } else {
                // When target is disabled, perform full cleanup
                LOG.debug("Target is disabled - performing full cleanup")

                // Step 1: Clean up project-specific settings for each project first
                openProjects.forEach { project ->
                    try {
                        LOG.debug("Cleaning up project: ${project.name}")
                        performProjectSpecificCleanup(project)
                    } catch (e: Exception) {
                        LOG.warn("Failed to cleanup project ${project.name}", e)
                    }
                }

                // Step 2: Disable IDE proxy (global cleanup)
                performGlobalCleanup()

                // Step 3: Configure project-specific settings for each project (should remove any remaining settings)
                openProjects.forEach { project ->
                    try {
                        LOG.debug("Configuring services for project: ${project.name}")
                        if (showNotifications) {
                            configureProxyServices(project, false)
                        } else {
                            configureProxyServicesSilently(project)
                        }
                        updateStatusBarWidget(project)
                    } catch (e: Exception) {
                        LOG.warn("Failed to configure services for project ${project.name}", e)
                    }
                }
            }

            // Notify listeners
            stateChangeManager.notifyStateChanged()

            LOG.info("Proxy cleanup and reapplication completed successfully for all ${openProjects.size} projects")
        } catch (e: Exception) {
            LOG.error("Failed to cleanup and reapply proxy settings for all projects", e)
            // Show error notification to first project if available
            val firstProject = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.firstOrNull()
            notificationService.showNotification(
                firstProject,
                NotificationMessages.proxyDisabled("Settings cleanup failed: ${e.message ?: "Unknown error"}")
            )
        }
    }

    /**
     * Performs a complete cleanup and reapplication of proxy settings.
     * This method ensures a clean state by:
     * 1. Disabling all proxy settings (IDE, Git, Gradle)
     * 2. Reapplying the proxy configuration based on the desired state
     *
     * @param project The current project
     * @param targetEnabled The desired proxy state after cleanup (true = enabled, false = disabled)
     */
    fun cleanupAndReapplyProxySettings(project: Project?, targetEnabled: Boolean) {
        try {
            LOG.info("Starting proxy cleanup and reapplication process (target: ${if (targetEnabled) "enabled" else "disabled"})")

            // Step 1: Force cleanup - disable all proxy settings to get clean state
            performCompleteCleanup(project)

            // Step 2: Apply the desired proxy state
            if (targetEnabled) {
                // Enable proxy if target state is enabled
                val newState = proxyService.toggleProxy()
                if (newState == ProxyState.ENABLED) {
                    LOG.debug("Proxy enabled after cleanup")
                    configureProxyServices(project, true)
                } else {
                    LOG.warn("Failed to enable proxy after cleanup, current state: $newState")
                }
            } else {
                // Keep proxy disabled (already cleaned up)
                LOG.debug("Proxy kept disabled after cleanup")
                configureProxyServices(project, false)
            }

            // Update UI and notify listeners
            updateStatusBarWidget(project)
            stateChangeManager.notifyStateChanged()

            LOG.info("Proxy cleanup and reapplication completed successfully")
        } catch (e: Exception) {
            LOG.error("Failed to cleanup and reapply proxy settings", e)
            // Show error notification to user
            notificationService.showNotification(
                project,
                NotificationMessages.proxyDisabled("Settings cleanup failed: ${e.message ?: "Unknown error"}")
            )
        }
    }

    /**
     * Convenience method for cleanup and reapplication based on current proxy state.
     * This determines the target state from the current proxy configuration and applies to all projects.
     */
    fun cleanupAndReapplyProxySettings() {
        val currentState = proxyService.getCurrentProxyState()
        val targetEnabled = when (currentState) {
            ProxyState.ENABLED -> true
            ProxyState.DISABLED -> false
            ProxyState.NOT_CONFIGURED -> false
        }
        cleanupAndReapplyProxySettingsForAllProjects(targetEnabled)
    }

    /**
     * Performs complete cleanup of all proxy settings (IDE, Git, Gradle)
     */
    private fun performCompleteCleanup(project: Project?) {
        LOG.debug("Performing complete proxy cleanup")

        // Force disable IDE proxy settings
        try {
            val proxySettings = com.intellij.util.net.ProxySettings.getInstance()
            val directProxy = object : com.intellij.util.net.ProxyConfiguration.DirectProxy {}
            proxySettings.setProxyConfiguration(directProxy)
            LOG.debug("IDE proxy settings cleaned up")
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup IDE proxy settings", e)
        }

        // Force cleanup Git proxy settings
        try {
            gitProxyService.removeGitProxySettings(project) { status ->
                LOG.debug("Git proxy cleanup: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Git proxy settings", e)
        }

        // Force cleanup Gradle proxy settings
        try {
            gradleProxyService.removeGradleProxySettings(project) { status ->
                LOG.debug("Gradle proxy cleanup: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Gradle proxy settings", e)
        }
    }

    /**
     * Performs cleanup of global proxy settings (IDE proxy settings)
     */
    private fun performGlobalCleanup() {
        LOG.debug("Performing global proxy cleanup")

        // Force disable IDE proxy settings (global)
        try {
            val proxySettings = com.intellij.util.net.ProxySettings.getInstance()
            val directProxy = object : com.intellij.util.net.ProxyConfiguration.DirectProxy {}
            proxySettings.setProxyConfiguration(directProxy)
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

        // Force cleanup Git proxy settings for this project
        try {
            gitProxyService.removeGitProxySettings(project) { status ->
                LOG.debug("Git proxy cleanup for ${project.name}: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Git proxy settings for project ${project.name}", e)
        }

        // Force cleanup Gradle proxy settings for this project
        try {
            gradleProxyService.removeGradleProxySettings(project) { status ->
                LOG.debug("Gradle proxy cleanup for ${project.name}: $status")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to cleanup Gradle proxy settings for project ${project.name}", e)
        }
    }

    /**
     * Cleans up proxy settings for a specific disabled feature across all open projects
     *
     * @param feature The feature to clean up ("git" or "gradle")
     */
    fun cleanupDisabledFeature(feature: String) {
        try {
            LOG.info("Cleaning up disabled feature: $feature")

            val openProjects = com.intellij.openapi.project.ProjectManager.getInstance().openProjects.toList()
            LOG.debug("Cleaning up $feature proxy settings for ${openProjects.size} open projects")

            openProjects.forEach { project ->
                try {
                    when (feature.lowercase()) {
                        "git" -> {
                            gitProxyService.removeGitProxySettings(project) { status ->
                                LOG.debug("Git proxy cleanup for ${project.name}: $status")
                            }
                        }

                        "gradle" -> {
                            gradleProxyService.removeGradleProxySettings(project) { status ->
                                LOG.debug("Gradle proxy cleanup for ${project.name}: $status")
                            }
                        }

                        else -> {
                            LOG.warn("Unknown feature for cleanup: $feature")
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to cleanup $feature proxy settings for project ${project.name}", e)
                }
            }

            LOG.info("Completed cleanup for disabled feature: $feature")
        } catch (e: Exception) {
            LOG.error("Failed to cleanup disabled feature: $feature", e)
        }
    }
}
