package org.holululu.proxythemall.models

import com.intellij.notification.NotificationType

/**
 * Data class representing notification information
 */
data class NotificationData(
    val title: String,
    val message: String,
    val type: NotificationType
)
