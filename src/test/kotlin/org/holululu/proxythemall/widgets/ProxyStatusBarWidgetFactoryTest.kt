package org.holululu.proxythemall.widgets

import org.junit.Test

/**
 * Unit tests for ProxyStatusBarWidgetFactory
 */
class ProxyStatusBarWidgetFactoryTest {

    @Test
    fun `factory should have correct ID`() {
        // Given
        val factory = ProxyStatusBarWidgetFactory()

        // When
        val id = factory.getId()

        // Then
        assert(id == "ProxyThemAll.StatusBar") { "Factory should have correct ID" }
        assert(id == ProxyStatusBarWidget.WIDGET_ID) { "Factory ID should match widget ID" }
    }

    @Test
    fun `factory should have correct display name`() {
        // Given
        val factory = ProxyStatusBarWidgetFactory()

        // When
        val displayName = factory.getDisplayName()

        // Then
        assert(displayName == "ProxyThemAll Status") { "Factory should have correct display name" }
    }
}
