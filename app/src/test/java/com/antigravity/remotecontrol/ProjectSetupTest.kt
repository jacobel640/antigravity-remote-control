package com.antigravity.remotecontrol

import com.antigravity.remotecontrol.security.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Milestone 1 Project Setup & Infrastructure Verification Tests.
 * Verifies core application constants, package configuration, and initial component wiring.
 */
class ProjectSetupTest {

    @Test
    fun verifyNotificationChannelConstants() {
        assertNotNull(RemoteControlApplication.DEFAULT_NOTIFICATION_CHANNEL_ID)
        assertEquals("antigravity_remote_control_channel", RemoteControlApplication.DEFAULT_NOTIFICATION_CHANNEL_ID)
        assertEquals("antigravity_alerts", RemoteControlApplication.ALERTS_NOTIFICATION_CHANNEL_ID)
    }

    @Test
    fun verifyPackageNamespaceIntegrity() {
        val expectedPackage = "com.antigravity.remotecontrol"
        val actualPackage = RemoteControlApplication::class.java.`package`?.name
        assertEquals(expectedPackage, actualPackage)
    }

    @Test
    fun verifyUrlValidatorIntegration() {
        val validationResult = UrlValidator.validateAndNormalize("https://remote.antigravity.internal:8443")
        assertTrue(validationResult.isValid)
        assertEquals("https://remote.antigravity.internal:8443", validationResult.formattedUrl)
    }

    @Test
    fun verifyMainActivityClassDefinition() {
        assertNotNull(MainActivity::class.java)
    }
}
