package org.holululu.proxythemall.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for ProxyStatusBarWidgetFactory
 */
class ProxyStatusBarWidgetFactoryTest {

    private lateinit var factory: ProxyStatusBarWidgetFactory

    @BeforeEach
    fun setUp() {
        factory = ProxyStatusBarWidgetFactory()
    }

    @Test
    fun testFactoryShouldHaveCorrectId() {
        // When
        val id = factory.getId()

        // Then
        assertEquals("ProxyThemAll.StatusBar", id, "Factory should have correct ID")
        assertEquals(ProxyStatusBarWidget.WIDGET_ID, id, "Factory ID should match widget ID")
    }

    @Test
    fun testFactoryShouldHaveCorrectDisplayName() {
        // When
        val displayName = factory.getDisplayName()

        // Then
        assertEquals("ProxyThemAll Status", displayName, "Factory should have correct display name")
    }
}
