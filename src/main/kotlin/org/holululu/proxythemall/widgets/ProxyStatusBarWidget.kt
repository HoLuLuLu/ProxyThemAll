package org.holululu.proxythemall.widgets

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.listeners.ProxyStateChangeManager
import org.holululu.proxythemall.listeners.WidgetStateChangeListener
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * Status bar widget that displays the current proxy state
 */
class ProxyStatusBarWidget(project: Project) : EditorBasedWidget(project), StatusBarWidget.IconPresentation {

    companion object {
        const val WIDGET_ID = "ProxyThemAll.StatusBar"
    }

    private val proxyService = ProxyService.instance
    private val proxyController = ProxyController.instance
    private val stateChangeManager = ProxyStateChangeManager.instance

    // State change listener
    private val stateChangeListener = WidgetStateChangeListener(this)

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getTooltipText(): String {
        return when (proxyService.getCurrentProxyState()) {
            ProxyState.ENABLED -> "Proxy is enabled - Click to disable"
            ProxyState.DISABLED -> "Proxy is disabled - Click to enable"
            ProxyState.NOT_CONFIGURED -> "Proxy is not configured"
        }
    }

    override fun getIcon(): Icon {
        return when (proxyService.getCurrentProxyState()) {
            ProxyState.ENABLED -> ProxyIcons.PROXY_ENABLED
            ProxyState.DISABLED -> ProxyIcons.PROXY_DISABLED
            ProxyState.NOT_CONFIGURED -> ProxyIcons.PROXY_NOT_CONFIGURED
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { _ ->
            proxyController.handleProxyToggle(project)
            // Update the status bar after state change
            myStatusBar?.updateWidget(WIDGET_ID)
        }
    }

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)

        // Register as a state change listener with the manager
        stateChangeManager.addListener(stateChangeListener)
    }

    override fun dispose() {
        // Remove the state change listener from the manager
        stateChangeManager.removeListener(stateChangeListener)

        super.dispose()
    }

    /**
     * Updates the widget display when proxy state changes
     */
    fun updateWidget() {
        myStatusBar?.updateWidget(WIDGET_ID)
    }
}
