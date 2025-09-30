package org.holululu.proxythemall.notifications

import com.intellij.notification.NotificationType
import org.holululu.proxythemall.models.NotificationData
import org.junit.Test

/**
 * Unit tests for NotificationService
 */
class NotificationServiceTest {

    @Test
    fun `NotificationService should be singleton`() {
        // Given & When
        val instance1 = NotificationService.instance
        val instance2 = NotificationService.instance

        // Then
        assert(instance1 === instance2) { "NotificationService should be a singleton" }
    }

    @Test
    fun `NotificationData should be properly structured`() {
        // Given & When
        val notificationData = NotificationData(
            title = "Test Title",
            message = "Test Message",
            type = NotificationType.INFORMATION
        )

        // Then
        assert(notificationData.title == "Test Title") { "Title should be set correctly" }
        assert(notificationData.message == "Test Message") { "Message should be set correctly" }
        assert(notificationData.type == NotificationType.INFORMATION) { "Type should be set correctly" }
    }

    @Test
    fun `NotificationData should handle different notification types`() {
        // Test INFORMATION type
        val infoData = NotificationData("Info", "Info message", NotificationType.INFORMATION)
        assert(infoData.type == NotificationType.INFORMATION) { "Should handle INFORMATION type" }

        // Test WARNING type
        val warningData = NotificationData("Warning", "Warning message", NotificationType.WARNING)
        assert(warningData.type == NotificationType.WARNING) { "Should handle WARNING type" }

        // Test ERROR type
        val errorData = NotificationData("Error", "Error message", NotificationType.ERROR)
        assert(errorData.type == NotificationType.ERROR) { "Should handle ERROR type" }
    }

    @Test
    fun `NotificationData should handle empty strings`() {
        // Given & When
        val notificationData = NotificationData(
            title = "",
            message = "",
            type = NotificationType.INFORMATION
        )

        // Then
        assert(notificationData.title == "") { "Should handle empty title" }
        assert(notificationData.message == "") { "Should handle empty message" }
    }
}
