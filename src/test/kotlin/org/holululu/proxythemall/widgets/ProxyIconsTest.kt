package org.holululu.proxythemall.widgets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.Icon
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Unit tests for ProxyIcons
 */
class ProxyIconsTest {

    @Test
    fun testAllProxyIconsShouldHaveConsistentDimensions() {
        // Given - Get all Icon objects from ProxyIcons using Kotlin reflection
        val proxyIconsClass = ProxyIcons::class
        val iconProperties = proxyIconsClass.memberProperties
            .filter { property ->
                property.returnType.classifier == Icon::class
            }

        // Ensure we found some icons
        assertTrue(iconProperties.isNotEmpty(), "No Icon properties found in ProxyIcons class")

        // When - Extract dimensions from all icons
        val dimensions = iconProperties.map { property ->
            property.isAccessible = true
            val icon = property.get(ProxyIcons) as Icon
            Pair(icon.iconWidth, icon.iconHeight)
        }

        // Then - All icons should have the same dimensions
        val firstIconDimensions = dimensions.first()
        dimensions.forEachIndexed { index, (width, height) ->
            val propertyName = iconProperties[index].name
            assertEquals(
                firstIconDimensions.first,
                width,
                "Icon '$propertyName' should have width ${firstIconDimensions.first}, but found: $width"
            )
            assertEquals(
                firstIconDimensions.second,
                height,
                "Icon '$propertyName' should have height ${firstIconDimensions.second}, but found: $height"
            )
        }
    }
}
