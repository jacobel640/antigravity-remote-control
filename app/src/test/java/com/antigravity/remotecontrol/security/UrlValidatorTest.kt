package com.antigravity.remotecontrol.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 Feature Tests for F3: URL Validation and Sanitization.
 * Verifies RFC-compliant URL parsing, normalization, protocol inference, and domain validation.
 */
class UrlValidatorTest {

    @Test
    fun testValidHttpsUrlWithDomain() {
        val input = "https://remote.antigravity.internal:8443/chat"
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("https://remote.antigravity.internal:8443/chat", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testValidHttpUrlWithLocalhost() {
        val input = "http://localhost:3000"
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("http://localhost:3000", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testValidAndroidEmulatorAliasIp() {
        val input = "http://10.0.2.2:8080/app"
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("http://10.0.2.2:8080/app", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testValidIpv4Address() {
        val input = "https://192.168.1.100:9000"
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("https://192.168.1.100:9000", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testAutoPrependHttpsWhenSchemeMissing() {
        val input = "antigravity.example.com:8443"
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("https://antigravity.example.com:8443", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testTrimWhitespaceAroundInputUrl() {
        val input = "   https://my-server.org/dashboard   "
        val result = UrlValidator.validateAndNormalize(input)
        assertTrue(result.isValid)
        assertEquals("https://my-server.org/dashboard", result.formattedUrl)
        assertNull(result.errorMessage)
    }

    @Test
    fun testNullOrEmptyInputReturnsInvalid() {
        val nullResult = UrlValidator.validateAndNormalize(null)
        assertFalse(nullResult.isValid)
        assertNull(nullResult.formattedUrl)
        assertNotNull(nullResult.errorMessage)

        val emptyResult = UrlValidator.validateAndNormalize("   ")
        assertFalse(emptyResult.isValid)
        assertNull(emptyResult.formattedUrl)
        assertNotNull(emptyResult.errorMessage)
    }
}
