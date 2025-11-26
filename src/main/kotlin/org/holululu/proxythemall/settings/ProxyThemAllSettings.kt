package org.holululu.proxythemall.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Settings state for ProxyThemAll plugin
 */
@State(
    name = "ProxyThemAllSettings",
    storages = [Storage("ProxyThemAllSettings.xml")]
)
@Service
class ProxyThemAllSettings : PersistentStateComponent<ProxyThemAllSettings> {

    companion object {
        fun getInstance(): ProxyThemAllSettings {
            return ApplicationManager.getApplication().getService(ProxyThemAllSettings::class.java)
        }
    }

    // Show notifications when proxy state changes
    var showNotifications: Boolean = true

    // Show status bar widget
    var showStatusBarWidget: Boolean = true

    // Apply proxy settings to Git
    var applyProxyToGit: Boolean = true

    // Enable Gradle proxy support
    var enableGradleProxySupport: Boolean = false

    // Allow global Gradle configuration fallback
    var enableGradleGlobalFallback: Boolean = false

    override fun getState(): ProxyThemAllSettings {
        return this
    }

    override fun loadState(state: ProxyThemAllSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
