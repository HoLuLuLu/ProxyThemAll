package org.holululu.proxythemall.models

/**
 * Represents the current state of the proxy configuration
 */
enum class ProxyState {
    ENABLED,
    DISABLED,
    NOT_CONFIGURED;

    /**
     * Whether proxy settings should be applied for this state
     */
    val isProxyActive: Boolean
        get() = this == ENABLED
}
