package org.holululu.proxythemall.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.holululu.proxythemall.core.ProxyController

/**
 * Main action class for the ProxyThemAll plugin
 * Delegates functionality to the ProxyController
 */
class ProxyThemAllAction : AnAction() {

    private val proxyController = ProxyController.instance

    override fun actionPerformed(e: AnActionEvent) {
        proxyController.handleProxyToggle(e.project)
    }
}
