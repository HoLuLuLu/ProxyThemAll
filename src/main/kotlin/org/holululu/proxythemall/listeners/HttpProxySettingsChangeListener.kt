package org.holululu.proxythemall.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import org.holululu.proxythemall.core.ProxyController
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyCredentialsStorage
import org.holululu.proxythemall.services.ProxyInfoExtractor
import org.holululu.proxythemall.services.ProxyService
import org.holululu.proxythemall.settings.ProxyThemAllSettings

/**
 * Listener that handles HTTP proxy settings changes by triggering cleanup and reapplication
 *
 * This listener ensures that whenever HTTP proxy settings are changed in IntelliJ's built-in
 * proxy settings, the plugin performs a complete cleanup and reapplication to maintain
 * a consistent state across IDE, Git, and Gradle proxy configurations.
 */
class HttpProxySettingsChangeListener : ProxyStateChangeListener {

    companion object {
        @JvmStatic
        val instance: HttpProxySettingsChangeListener by lazy { HttpProxySettingsChangeListener() }

        private val LOG = Logger.getInstance(HttpProxySettingsChangeListener::class.java)
    }

    private val proxyController = ProxyController.instance
    private val proxyService = ProxyService.instance

    // Written from the polling thread and read on the EDT
    @Volatile
    private var lastProcessedState: ProxyState? = null

    // Last configuration we acted on. ProxyStateChangeManager decides when to notify; this is an
    // idempotency guard so a redundant notifyStateChanged() does not reapply everything again.
    @Volatile
    private var lastProcessedConfiguration: ProxyConfiguration? = null

    /**
     * Called when the proxy state changes due to HTTP proxy settings modifications
     */
    override fun onProxyStateChanged(newState: ProxyState) {
        val currentConfiguration = currentConfiguration()
        val configurationChanged = currentConfiguration != lastProcessedConfiguration
        if (lastProcessedState == newState && !configurationChanged) {
            LOG.debug("Proxy state and configuration unchanged ($newState), skipping cleanup")
            return
        }

        lastProcessedState = newState
        lastProcessedConfiguration = currentConfiguration
        LOG.info("HTTP proxy settings changed, new state: $newState - reapplying configuration for all projects")

        handleProxyBackup(newState)

        // Use the silent version to avoid duplicate notifications
        proxyController.cleanupAndReapplyProxySettingsForAllProjectsSilently(newState.isProxyActive)
    }

    /**
     * Reads the proxy configuration currently held by the IDE, or null when it cannot be read
     */
    private fun currentConfiguration(): ProxyConfiguration? = try {
        ProxySettings.getInstance().getProxyConfiguration()
    } catch (e: Exception) {
        LOG.warn("Could not read the current proxy configuration", e)
        null
    }

    /**
     * Handles backing up proxy configuration to PasswordSafe when state changes
     */
    private fun handleProxyBackup(newState: ProxyState) {
        try {
            val settings = ProxyThemAllSettings.getInstance()

            when (newState) {
                ProxyState.ENABLED -> {
                    LOG.info("Proxy enabled - backing up configuration to PasswordSafe")

                    // Reading the credential store and writing to PasswordSafe are both slow and
                    // must not run on the EDT, so extraction happens here too
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            val proxyInfo = ProxyInfoExtractor.instance
                                .extractProxyInfo(ProxySettings.getInstance().getProxyConfiguration())

                            if (proxyInfo == null) {
                                LOG.warn("Could not extract proxy info for backup")
                                return@executeOnPooledThread
                            }

                            ProxyCredentialsStorage.getInstance().saveProxyConfiguration(proxyInfo)
                            LOG.info("Proxy configuration backed up successfully")
                        } catch (e: Exception) {
                            LOG.warn("Failed to save proxy configuration to PasswordSafe", e)
                        }
                    }
                    settings.lastKnownProxyEnabled = true
                }

                ProxyState.DISABLED, ProxyState.NOT_CONFIGURED -> {
                    // Update flag but never delete from PasswordSafe
                    LOG.debug("Proxy disabled/not configured - updating state flag only (backup preserved)")
                    settings.lastKnownProxyEnabled = false
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to handle proxy backup", e)
        }
    }

    /**
     * Registers this listener with the ProxyStateChangeManager.
     *
     * Registration is idempotent, so calling this once per opened project is harmless.
     */
    fun register() {
        // IntelliJ provides no proxy settings change topic, so detection is poll based
        ProxyStateChangeManager.instance.addListener(this)
        lastProcessedState = proxyService.getCurrentProxyState()
        lastProcessedConfiguration = currentConfiguration()
        LOG.debug("HttpProxySettingsChangeListener registered")
    }
}
