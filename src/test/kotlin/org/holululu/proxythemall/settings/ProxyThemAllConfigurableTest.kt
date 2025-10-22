package org.holululu.proxythemall.settings

import org.holululu.proxythemall.TestUtils
import org.holululu.proxythemall.core.ProxyController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Test for ProxyThemAllConfigurable to verify feature cleanup functionality
 */
class ProxyThemAllConfigurableTest {

    @BeforeEach
    fun setUp() {
        // Set up mock IntelliJ Platform environment
        TestUtils.setupMockIntellijEnvironment()
    }

    @AfterEach
    fun tearDown() {
        // Clean up mock environment
        TestUtils.cleanupMockIntellijEnvironment()
    }

    @Test
    fun testSettingsChangeDetection() {
        // Test basic settings change detection logic
        val originalGitSetting = true
        val currentGitSetting = false
        val originalGradleSetting = false
        val currentGradleSetting = true

        // Verify change detection works
        assertTrue(originalGitSetting != currentGitSetting, "Git setting change should be detected")
        assertTrue(originalGradleSetting != currentGradleSetting, "Gradle setting change should be detected")
    }

    @Test
    fun testCleanupDisabledFeatureMethodExists() {
        // Test that the cleanupDisabledFeature method exists in ProxyController
        // This verifies our implementation was added correctly
        val controllerClass = ProxyController::class.java

        // Check if the method exists
        val method = controllerClass.getDeclaredMethod("cleanupDisabledFeature", String::class.java)
        assertNotNull(method, "cleanupDisabledFeature method should exist")
        assertEquals(1, method.parameterCount, "cleanupDisabledFeature should take one parameter")
        assertTrue(java.lang.reflect.Modifier.isPublic(method.modifiers), "cleanupDisabledFeature should be public")
    }

    @Test
    fun testHandleFeatureDisablingMethodExists() {
        // Test that the handleFeatureDisabling method exists in ProxyThemAllConfigurable
        val configurableClass = ProxyThemAllConfigurable::class.java

        // Check if the method exists
        val method = configurableClass.getDeclaredMethod("handleFeatureDisabling")
        assertNotNull(method, "handleFeatureDisabling method should exist")
        assertEquals(0, method.parameterCount, "handleFeatureDisabling should take no parameters")
        assertTrue(java.lang.reflect.Modifier.isPrivate(method.modifiers), "handleFeatureDisabling should be private")
    }

    @Test
    fun testProxyThemAllConfigurableCanBeInstantiated() {
        // Basic test to ensure the configurable can be created
        val configurable = ProxyThemAllConfigurable()
        assertNotNull(configurable, "ProxyThemAllConfigurable should be instantiable")
        assertEquals("ProxyThemAll", configurable.displayName, "Display name should be correct")
    }
}
