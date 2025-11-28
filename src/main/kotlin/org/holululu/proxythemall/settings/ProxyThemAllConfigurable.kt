package org.holululu.proxythemall.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.widgets.ProxyStatusBarWidget
import javax.swing.JComponent

/**
 * Settings configurable for ProxyThemAll plugin
 */
class ProxyThemAllConfigurable : Configurable {

    private var settingsComponent: DialogPanel? = null
    private val settings = ProxyThemAllSettings.getInstance()
    private var originalShowStatusBarWidget: Boolean = settings.showStatusBarWidget

    // Store original values to detect changes in ProxyThemAll settings
    private var originalShowNotifications: Boolean = settings.showNotifications
    private var originalApplyProxyToGit: Boolean = settings.applyProxyToGit
    private var originalEnableGradleProxySupport: Boolean = settings.enableGradleProxySupport
    private var originalEnableGradleGlobalFallback: Boolean = settings.enableGradleGlobalFallback

    override fun getDisplayName(): String = "ProxyThemAll"

    override fun createComponent(): JComponent {
        // Store all original values when the component is created
        originalShowStatusBarWidget = settings.showStatusBarWidget
        originalShowNotifications = settings.showNotifications
        originalApplyProxyToGit = settings.applyProxyToGit
        originalEnableGradleProxySupport = settings.enableGradleProxySupport
        originalEnableGradleGlobalFallback = settings.enableGradleGlobalFallback

        settingsComponent = panel {
            group("Notifications") {
                row {
                    checkBox("Show notifications when proxy state changes")
                        .bindSelected(settings::showNotifications)
                        .comment("Display balloon notifications when proxy is enabled or disabled")
                }
            }

            group("Status Bar") {
                row {
                    checkBox("Show status bar widget")
                        .bindSelected(settings::showStatusBarWidget)
                        .comment("Display proxy status indicator in the status bar")
                }
            }

            group("Git Integration") {
                row {
                    checkBox("Apply proxy settings to Git")
                        .bindSelected(settings::applyProxyToGit)
                        .comment("Automatically configure Git to use proxy when proxy is enabled")
                }
            }

            group("Gradle Integration") {
                row {
                    checkBox("Apply proxy settings to Gradle")
                        .bindSelected(settings::enableGradleProxySupport)
                        .comment("Enable proxy configuration for Gradle builds")
                }
                row {
                    checkBox("Allow global Gradle configuration fallback")
                        .bindSelected(settings::enableGradleGlobalFallback)
                        .comment("Apply proxy to global ~/.gradle/gradle.properties when project doesn't have gradle.properties")
                }
            }

            group("Proxy Backup Management") {
                row {
                    button("Clear Stored Proxy Configuration") {
                        val result = Messages.showYesNoDialog(
                            "Are you sure you want to clear the stored proxy configuration?\n\n" +
                                    "This will permanently delete the backed-up proxy settings from secure storage.\n" +
                                    "You will need to reconfigure your proxy manually if IntelliJ forgets it.",
                            "Clear Stored Proxy Configuration",
                            Messages.getWarningIcon()
                        )

                        if (result == Messages.YES) {
                            try {
                                org.holululu.proxythemall.services.ProxyCredentialsStorage.getInstance()
                                    .clearStoredConfiguration()
                                settings.lastKnownProxyEnabled = false

                                Messages.showInfoMessage(
                                    "Stored proxy configuration has been cleared successfully.",
                                    "ProxyThemAll"
                                )
                            } catch (e: Exception) {
                                Messages.showErrorDialog(
                                    "Failed to clear stored proxy configuration: ${e.message}",
                                    "ProxyThemAll - Error"
                                )
                            }
                        }
                    }.comment("Remove backed-up proxy settings from secure storage")
                }
            }
        }

        return settingsComponent!!
    }

    override fun isModified(): Boolean {
        return settingsComponent?.isModified() ?: false
    }

    override fun apply() {
        settingsComponent?.apply()

        // Check if any ProxyThemAll settings have changed
        val proxySettingsChanged = originalShowNotifications != settings.showNotifications ||
                originalApplyProxyToGit != settings.applyProxyToGit ||
                originalEnableGradleProxySupport != settings.enableGradleProxySupport ||
                originalEnableGradleGlobalFallback != settings.enableGradleGlobalFallback

        // Check if the status bar widget visibility setting has changed
        val widgetVisibilityChanged = originalShowStatusBarWidget != settings.showStatusBarWidget

        // If any proxy-related settings changed, perform cleanup and reapplication
        if (proxySettingsChanged) {
            ApplicationManager.getApplication().invokeLater {
                // Trigger cleanup and reapplication of proxy settings for all open projects
                // This ensures a clean state and respects the new settings across all projects
                // The Git and Gradle services will now automatically clean up when features are disabled
                ProxyController.instance.cleanupAndReapplyProxySettings()
            }
        }

        // Update status bar widgets in all open projects when settings change
        updateStatusBarWidgets()

        // Show restart notification if widget visibility changed
        if (widgetVisibilityChanged) {
            Messages.showWarningDialog(
                "Please restart the IDE for the status bar widget visibility changes to take effect.",
                "ProxyThemAll - Restart Required"
            )
        }

        // Update all original values for future comparisons
        originalShowStatusBarWidget = settings.showStatusBarWidget
        originalShowNotifications = settings.showNotifications
        originalApplyProxyToGit = settings.applyProxyToGit
        originalEnableGradleProxySupport = settings.enableGradleProxySupport
        originalEnableGradleGlobalFallback = settings.enableGradleGlobalFallback
    }

    private fun updateStatusBarWidgets() {
        ApplicationManager.getApplication().invokeLater {
            ProjectManager.getInstance().openProjects.forEach { project ->
                val statusBar = WindowManager.getInstance().getStatusBar(project)
                // Update the proxy status bar widget - this will re-evaluate isAvailable() for the widget factory
                // and show/hide the widget accordingly
                statusBar?.updateWidget(ProxyStatusBarWidget.WIDGET_ID)
            }
        }
    }

    override fun reset() {
        settingsComponent?.reset()
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
