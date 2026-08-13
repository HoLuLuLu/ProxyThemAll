package org.holululu.proxythemall.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.services.ProxyCredentialsStorage
import org.holululu.proxythemall.widgets.ProxyStatusBarWidget
import org.holululu.proxythemall.widgets.ProxyStatusBarWidgetFactory
import javax.swing.JComponent

/**
 * Settings configurable for ProxyThemAll plugin
 */
class ProxyThemAllConfigurable : Configurable {

    private var settingsComponent: DialogPanel? = null
    private val settings = ProxyThemAllSettings.getInstance()
    // Store original values to detect changes in ProxyThemAll settings
    private var originalApplyProxyToGit: Boolean = settings.applyProxyToGit
    private var originalEnableGradleProxySupport: Boolean = settings.enableGradleProxySupport
    private var originalEnableGradleGlobalFallback: Boolean = settings.enableGradleGlobalFallback

    override fun getDisplayName(): String = "ProxyThemAll"

    override fun createComponent(): JComponent {
        // Store all original values when the component is created
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
                        .comment(
                            "Enable proxy configuration for Gradle builds.<br/>" +
                                    "<b>Warning:</b> when the proxy requires authentication, the username and " +
                                    "password are written in plain text to gradle.properties. The ProxyThemAll " +
                                    "changelist reduces the risk of committing them, but it is not a security " +
                                    "boundary - a commit of all changes will include them."
                        )
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
                            // PasswordSafe access must not run on the EDT
                            ApplicationManager.getApplication().executeOnPooledThread {
                                val error = try {
                                    ProxyCredentialsStorage.getInstance().clearStoredConfiguration()
                                    settings.lastKnownProxyEnabled = false
                                    null
                                } catch (e: Exception) {
                                    e.message ?: "unknown error"
                                }

                                ApplicationManager.getApplication().invokeLater {
                                    if (error == null) {
                                        Messages.showInfoMessage(
                                            "Stored proxy configuration has been cleared successfully.",
                                            "ProxyThemAll"
                                        )
                                    } else {
                                        Messages.showErrorDialog(
                                            "Failed to clear stored proxy configuration: $error",
                                            "ProxyThemAll - Error"
                                        )
                                    }
                                }
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

        // Only settings that affect how the proxy is applied need a reapplication.
        // The notification toggle is cosmetic - reapplying would needlessly rerun git commands
        // and rewrite gradle.properties in every open project.
        val proxySettingsChanged = originalApplyProxyToGit != settings.applyProxyToGit ||
                originalEnableGradleProxySupport != settings.enableGradleProxySupport ||
                originalEnableGradleGlobalFallback != settings.enableGradleGlobalFallback

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

        // Update all original values for future comparisons
        originalApplyProxyToGit = settings.applyProxyToGit
        originalEnableGradleProxySupport = settings.enableGradleProxySupport
        originalEnableGradleGlobalFallback = settings.enableGradleGlobalFallback
    }

    private fun updateStatusBarWidgets() {
        ApplicationManager.getApplication().invokeLater {
            ProjectManager.getInstance().openProjects.forEach { project ->
                if (project.isDisposed) return@forEach

                // StatusBarWidgetsManager re-evaluates the factory's isAvailable(), which adds or
                // removes the widget. StatusBar.updateWidget() only repaints an existing widget,
                // which is why hiding it used to require an IDE restart.
                project.service<StatusBarWidgetsManager>()
                    .updateWidget(ProxyStatusBarWidgetFactory::class.java)

                WindowManager.getInstance().getStatusBar(project)
                    ?.updateWidget(ProxyStatusBarWidget.WIDGET_ID)
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
