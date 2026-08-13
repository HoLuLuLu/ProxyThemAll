package org.holululu.proxythemall.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.net.ProxyConfiguration
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Manager class responsible for handling proxy state change listeners and detection
 *
 * The IntelliJ platform exposes no message bus topic for proxy configuration changes, so changes
 * made in Settings are detected by polling. Changes made through this plugin notify directly.
 */
class ProxyStateChangeManager(
    // Resolved lazily so constructing the manager does not require a running application
    private val proxyServiceProvider: () -> ProxyService = { ProxyService.instance }
) : Disposable {

    companion object {
        @JvmStatic
        val instance: ProxyStateChangeManager by lazy { ProxyStateChangeManager() }

        // Check interval for proxy state changes (in seconds)
        private const val STATE_CHECK_INTERVAL = 2L

        private val LOG = Logger.getInstance(ProxyStateChangeManager::class.java)
    }

    private val proxyService: ProxyService get() = proxyServiceProvider()
    private val listeners = mutableListOf<ProxyStateChangeListener>()

    // Store the last known proxy state to detect changes; written from the polling thread
    @Volatile
    private var lastKnownProxyState: ProxyState? = null

    // Last seen configuration, so edits that leave the state unchanged are still detected
    @Volatile
    private var lastKnownConfiguration: ProxyConfiguration? = null

    // Scheduled task for periodic state checking
    @Volatile
    private var stateCheckTask: ScheduledFuture<*>? = null

    /**
     * Adds a listener to be notified when proxy state changes.
     *
     * Registering the same listener twice is a no-op: duplicates would multiply the work done
     * per state change by the number of open projects.
     */
    fun addListener(listener: ProxyStateChangeListener) {
        synchronized(listeners) {
            if (listeners.any { it === listener }) {
                LOG.debug("Listener already registered: ${listener::class.simpleName}")
                return
            }

            listeners.add(listener)
            LOG.debug("Added proxy state change listener: ${listener::class.simpleName}. Total listeners: ${listeners.size}")

            // Start periodic checking if this is the first listener
            if (listeners.size == 1) {
                LOG.info("Starting periodic proxy state checking (interval: ${STATE_CHECK_INTERVAL}s)")
                startPeriodicStateCheck()
            }
        }
    }

    /**
     * Removes a state change listener
     */
    fun removeListener(listener: ProxyStateChangeListener) {
        synchronized(listeners) {
            listeners.removeIf { it === listener }

            // Stop periodic checking if no listeners remain
            if (listeners.isEmpty()) {
                stopPeriodicStateCheck()
            }
        }
    }

    /**
     * Checks for proxy changes and notifies listeners when something changed.
     *
     * Both the state and the configuration are compared: editing host, port, protocol or exceptions
     * while the proxy stays enabled leaves the state at ENABLED, and would otherwise go unnoticed
     * until the user toggled the proxy off and on again.
     */
    fun checkForStateChanges() {
        // Two reads, so a user edit landing between them can pair a stale state with a fresh
        // configuration. That resolves itself: the pair is stored, and the next tick sees the
        // mismatch and notifies again - at worst one extra tick, never a lost change.
        val currentState = proxyService.getCurrentProxyState()
        val currentConfiguration = proxyService.getCurrentConfiguration()

        // Keep the restorable configuration up to date while the proxy is active, so toggling
        // off and on again works even if the plugin never saw the original toggle
        if (currentState == ProxyState.ENABLED) {
            proxyService.rememberActiveConfiguration()
        }

        val stateChanged = lastKnownProxyState != currentState
        val configurationChanged = lastKnownConfiguration != currentConfiguration
        if (!stateChanged && !configurationChanged) {
            return
        }

        if (stateChanged) {
            LOG.info("Proxy state changed from $lastKnownProxyState to $currentState - notifying listeners")
        } else {
            LOG.info("Proxy configuration changed while $currentState - notifying listeners")
        }

        // Remember before notifying: listeners reapply the configuration and end by calling
        // notifyStateChanged(), so stale values here would cause a reapply on every poll tick
        lastKnownProxyState = currentState
        lastKnownConfiguration = currentConfiguration
        notifyListeners(currentState)
    }

    /**
     * Forces a state change notification to all listeners
     * Useful when we know the state has changed programmatically
     */
    fun notifyStateChanged() {
        val currentState = proxyService.getCurrentProxyState()
        lastKnownProxyState = currentState
        lastKnownConfiguration = proxyService.getCurrentConfiguration()
        notifyListeners(currentState)
    }

    /**
     * Notifies all registered listeners about proxy state change
     */
    private fun notifyListeners(newState: ProxyState) {
        val currentListeners = synchronized(listeners) { listeners.toList() }

        currentListeners.forEach { listener ->
            try {
                listener.onProxyStateChanged(newState)
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                // Keep going so one failing listener cannot block the others, but do not hide it
                LOG.warn("Proxy state change listener failed: ${listener::class.simpleName}", e)
            }
        }
    }

    /**
     * Starts the periodic state checking task
     */
    private fun startPeriodicStateCheck() {
        if (stateCheckTask?.isCancelled != false) {
            stateCheckTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                {
                    try {
                        checkForStateChanges()
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        LOG.warn("Periodic proxy state check failed", e)
                    }
                },
                STATE_CHECK_INTERVAL, // Initial delay
                STATE_CHECK_INTERVAL, // Period
                TimeUnit.SECONDS
            )
        }
    }

    /**
     * Stops the periodic state checking task
     */
    private fun stopPeriodicStateCheck() {
        stateCheckTask?.cancel(false)
        stateCheckTask = null
    }

    /**
     * Cancels the polling task and drops all listeners, so nothing survives a plugin unload
     */
    override fun dispose() {
        synchronized(listeners) {
            listeners.clear()
            stopPeriodicStateCheck()
        }
    }
}
