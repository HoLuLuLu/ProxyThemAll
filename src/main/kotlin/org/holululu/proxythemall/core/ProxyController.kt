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
                    if (gitStatus.isNotEmpty()) append("Git: $gitStatus")
                    if (gradleStatus.isNotEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append("Gradle: $gradleStatus")
                    }
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
     * Updates the status bar widget to reflect the current proxy state
     */
    private fun updateStatusBarWidget(project: Project?) {
        project?.let { p ->
            val statusBar = WindowManager.getInstance().getStatusBar(p)
            statusBar?.updateWidget(ProxyStatusBarWidget.WIDGET_ID)
        }
    }
}
