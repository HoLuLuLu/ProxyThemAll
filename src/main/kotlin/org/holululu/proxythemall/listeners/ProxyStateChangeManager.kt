package org.holululu.proxythemall.listeners

import com.intellij.util.concurrency.AppExecutorUtil
import org.holululu.proxythemall.models.ProxyState
import org.holululu.proxythemall.services.ProxyService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Manager class responsible for handling proxy state change listeners and detection
 */
class ProxyStateChangeManager {

    companion object {
        @JvmStatic
        val instance: ProxyStateChangeManager by lazy { ProxyStateChangeManager() }

        // Check interval for proxy state changes (in seconds)
        private const val STATE_CHECK_INTERVAL = 2L
    }

    private val proxyService = ProxyService.instance
    private val listeners = mutableListOf<ProxyStateChangeListener>()

    // Store the last known proxy state to detect changes
    private var lastKnownProxyState: ProxyState? = null

    // Scheduled task for periodic state checking
    private var stateCheckTask: ScheduledFuture<*>? = null

    /**
     * Adds a listener to be notified when proxy state changes
     */
    fun addListener(listener: ProxyStateChangeListener) {
        synchronized(listeners) {
            listeners.add(listener)

            // Start periodic checking if this is the first listener
            if (listeners.size == 1) {
                startPeriodicStateCheck()
            }
        }
    }

    /**
     * Removes a state change listener
     */
    fun removeListener(listener: ProxyStateChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)

            // Stop periodic checking if no listeners remain
            if (listeners.isEmpty()) {
                stopPeriodicStateCheck()
            }
        }
    }

    /**
     * Checks for proxy state changes and notifies listeners if state has changed
     */
    fun checkForStateChanges() {
        val currentState = proxyService.getCurrentProxyState()
        if (lastKnownProxyState != currentState) {
            lastKnownProxyState = currentState
            notifyListeners(currentState)
        }
    }

    /**
     * Forces a state change notification to all listeners
     * Useful when we know the state has changed programmatically
     */
    fun notifyStateChanged() {
        val currentState = proxyService.getCurrentProxyState()
        lastKnownProxyState = currentState
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
            } catch (_: Exception) {
                // Ignore listener exceptions to prevent one bad listener from affecting others
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
                    } catch (_: Exception) {
                        // Ignore exceptions in background task
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
}
