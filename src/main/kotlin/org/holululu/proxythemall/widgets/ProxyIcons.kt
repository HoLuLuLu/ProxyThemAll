package org.holululu.proxythemall.widgets

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Provides monochrome icons for different proxy states in the status bar
 */
object ProxyIcons {

    val PROXY_ENABLED: Icon by lazy {
        AllIcons.CodeWithMe.CwmInvite
    }

    val PROXY_DISABLED: Icon by lazy {
        AllIcons.Nodes.PpWeb
    }

    val PROXY_NOT_CONFIGURED: Icon by lazy {
        AllIcons.General.ShowWarning
    }
}
