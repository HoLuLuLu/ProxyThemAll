package org.holululu.proxythemall.listeners

import com.intellij.openapi.application.ApplicationManager
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.widgets.ProxyStatusBarWidget

/**
 * Listener implementation that updates the status bar widget when proxy state changes
 */
class WidgetStateChangeListener(private val widget: ProxyStatusBarWidget) : ProxyStateChangeListener {

    override fun onProxyStateChanged(newState: ProxyState) {
        // Update widget on EDT when state changes
        ApplicationManager.getApplication().invokeLater {
            widget.updateWidget()
        }
    }
}
