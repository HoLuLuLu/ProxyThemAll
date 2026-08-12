package org.holululu.proxythemall.widgets

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Factory for creating ProxyStatusBarWidget instances
 */
class ProxyStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = ProxyStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "ProxyThemAll Status"

    override fun isAvailable(project: Project): Boolean {
        val settings = ProxyThemAllSettings.getInstance()
        return settings.showStatusBarWidget
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return ProxyStatusBarWidget(project)
    }

    // disposeWidget is intentionally not overridden: the interface default calls
    // Disposer.dispose(widget), which is what removes the widget's state change listener.

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
