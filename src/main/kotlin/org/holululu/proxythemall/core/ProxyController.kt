package org.holululu.proxythemall.core

import com.intellij.openapi.project.Project
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.notifications.NotificationService
import org.holululu.proxythemall.services.ProxyService
import org.holululu.proxythemall.utils.NotificationMessages

/**
 * Controller that orchestrates proxy toggle operations and user notifications
 */
class ProxyController {

    companion object {
        @JvmStatic
        val instance: ProxyController by lazy { ProxyController() }
    }

    private val proxyService = ProxyService.instance
    private val notificationService = NotificationService.instance

    /**
     * Handles the proxy toggle action and shows appropriate notifications
     */
    fun handleProxyToggle(project: Project?) {
        val currentState = proxyService.getCurrentProxyState()

        when (currentState) {
            ProxyState.ENABLED -> {
                proxyService.toggleProxy()
                notificationService.showNotification(project, NotificationMessages.proxyDisabled())
            }

            ProxyState.DISABLED -> {
                proxyService.toggleProxy()
                notificationService.showNotification(project, NotificationMessages.proxyEnabled())
            }

            ProxyState.NOT_CONFIGURED -> {
                notificationService.showNotification(project, NotificationMessages.proxyConfigurationRequired())
            }
        }
    }
}
