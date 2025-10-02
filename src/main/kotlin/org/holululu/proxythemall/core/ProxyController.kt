package org.holululu.proxythemall.core

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
 */
class ProxyController {

    companion object {
        @JvmStatic
        val instance: ProxyController by lazy { ProxyController() }
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
        val currentState = proxyService.getCurrentProxyState()

        when (currentState) {
            ProxyState.ENABLED -> {
                proxyService.toggleProxy()
                stateChangeManager.notifyStateChanged() // Notify listeners about the state change

                // Configure Git and Gradle proxy and show unified notification
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
                        notificationService.showNotification(
                            project,
                            NotificationMessages.proxyDisabled(combinedStatus)
                        )
                    }
                }

                gitProxyService.configureGitProxy(project) { status ->
                    gitStatus = status
                    onComplete()
                }

                gradleProxyService.configureGradleProxy(project) { status ->
                    gradleStatus = status
                    onComplete()
                }

                updateStatusBarWidget(project)
            }

            ProxyState.DISABLED -> {
                proxyService.toggleProxy()
                stateChangeManager.notifyStateChanged() // Notify listeners about the state change

                // Configure Git and Gradle proxy and show unified notification
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
                        notificationService.showNotification(project, NotificationMessages.proxyEnabled(combinedStatus))
                    }
                }

                gitProxyService.configureGitProxy(project) { status ->
                    gitStatus = status
                    onComplete()
                }

                gradleProxyService.configureGradleProxy(project) { status ->
                    gradleStatus = status
                    onComplete()
                }

                updateStatusBarWidget(project)
            }

            ProxyState.NOT_CONFIGURED -> {
                notificationService.showNotification(project, NotificationMessages.proxyConfigurationRequired(project))
            }
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
