package org.holululu.proxythemall.listeners

import org.holululu.proxythemall.models.ProxyState

/**
 * Interface for listening to proxy state changes
 */
fun interface ProxyStateChangeListener {
    /**
     * Called when the proxy state changes
     * @param newState the new proxy state
     */
    fun onProxyStateChanged(newState: ProxyState)
}
