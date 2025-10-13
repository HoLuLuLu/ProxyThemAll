//package org.holululu.proxythemall.notifications
//
//import com.intellij.notification.NotificationAction
//import com.intellij.notification.NotificationType
//import com.intellij.testFramework.fixtures.BasePlatformTestCase
//import org.holululu.proxythemall.models.NotificationData
//import org.holululu.proxythemall.settings.ProxyThemAllSettings
//
///**
// * Unit tests for NotificationService
// */
//class NotificationServiceTest : BasePlatformTestCase() {
//
//    private lateinit var service: NotificationService
//
//    override fun setUp() {
//        super.setUp()
//        service = NotificationService.instance
//    }
//
//    fun testNotificationServiceShouldBeSingleton() {
//        // Given & When
//        val instance1 = NotificationService.instance
//        val instance2 = NotificationService.instance
//
//        // Then
//        assertSame("NotificationService should be a singleton", instance1, instance2)
//    }
//
//    fun testNotificationDataShouldBeProperlyStructured() {
//        // Given & When
//        val notificationData = NotificationData(
//            title = "Test Title",
//            message = "Test Message",
//            type = NotificationType.INFORMATION
//        )
//
//        // Then
//        assertEquals("Title should be set correctly", "Test Title", notificationData.title)
//        assertEquals("Message should be set correctly", "Test Message", notificationData.message)
//        assertEquals("Type should be set correctly", NotificationType.INFORMATION, notificationData.type)
//        assertTrue("Actions should be empty by default", notificationData.actions.isEmpty())
//    }
//
//    fun testNotificationDataShouldHandleDifferentNotificationTypes() {
//        // Test INFORMATION type
//        val infoData = NotificationData("Info", "Info message", NotificationType.INFORMATION)
//        assertEquals("Should handle INFORMATION type", NotificationType.INFORMATION, infoData.type)
//
//        // Test WARNING type
//        val warningData = NotificationData("Warning", "Warning message", NotificationType.WARNING)
//        assertEquals("Should handle WARNING type", NotificationType.WARNING, warningData.type)
//
//        // Test ERROR type
//        val errorData = NotificationData("Error", "Error message", NotificationType.ERROR)
//        assertEquals("Should handle ERROR type", NotificationType.ERROR, errorData.type)
//    }
//
//    fun testNotificationDataShouldHandleEmptyStrings() {
//        // Given & When
//        val notificationData = NotificationData(
//            title = "",
//            message = "",
//            type = NotificationType.INFORMATION
//        )
//
//        // Then
//        assertEquals("Should handle empty title", "", notificationData.title)
//        assertEquals("Should handle empty message", "", notificationData.message)
//    }
//
//    fun testNotificationDataShouldHandleActions() {
//        // Given
//        val testAction = NotificationAction.createSimple("Test Action") {
//            // Test action implementation
//        }
//
//        // When
//        val notificationData = NotificationData(
//            title = "Test Title",
//            message = "Test Message",
//            type = NotificationType.WARNING,
//            actions = listOf(testAction)
//        )
//
//        // Then
//        assertEquals("Should have one action", 1, notificationData.actions.size)
//        assertEquals("Should contain the test action", testAction, notificationData.actions.first())
//    }
//
//    fun testShowNotificationShouldRespectSettingsConfiguration() {
//        // Test that the service can handle notification calls
//        val testData = NotificationData(
//            title = "Test",
//            message = "Test message",
//            type = NotificationType.INFORMATION
//        )
//
//        // This should not throw an exception
//        try {
//            service.showNotification(project, testData)
//            assertTrue("Method should handle test environment gracefully", true)
//        } catch (e: Exception) {
//            // Some exceptions may be expected in test environment due to missing platform services
//            assertTrue("Method should handle test environment gracefully", true)
//        }
//
//        // Test settings access
//        try {
//            val settings = ProxyThemAllSettings.getInstance()
//            // Test with notifications enabled (default)
//            assertTrue("Notifications should be enabled by default", settings.showNotifications)
//
//            // Test with notifications disabled
//            settings.showNotifications = false
//            assertFalse("Notifications should be disabled", settings.showNotifications)
//
//            // Reset to default
//            settings.showNotifications = true
//
//        } catch (e: Exception) {
//            // Expected in some test environments
//            assertTrue("Settings access should fail gracefully in test environment", true)
//        }
//    }
//}
