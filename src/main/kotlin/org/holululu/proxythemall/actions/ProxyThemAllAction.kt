package org.holululu.proxythemall.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService

/**
 * Main action class for the ProxyThemAll plugin
 * Delegates functionality to the ProxyController
 */
class ProxyThemAllAction : AnAction(), DumbAware {

    private val proxyController = ProxyController.instance
    private val proxyService = ProxyService.instance

    // Reading the proxy configuration does not need the EDT
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.text = when (proxyService.getCurrentProxyState()) {
            ProxyState.ENABLED -> "Disable Proxy (ProxyThemAll)"
            ProxyState.DISABLED -> "Enable Proxy (ProxyThemAll)"
            ProxyState.NOT_CONFIGURED -> "Configure Proxy (ProxyThemAll)"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        proxyController.handleProxyToggle(e.project)
    }
}
