package com.antigravity.remotecontrol.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 Feature Tests for F6: Sanitized User-Agent Engine.
 * Verifies removal of WebView markers (; wv and Version/4.0) to prevent Google OAuth 403 disallowed_useragent blocks.
 */
class UserAgentSanitizerTest {

    private val sanitizer = UserAgentSanitizerHelper

    @Test
    fun testStandardAndroidWebViewUserAgentSanitization() {
        val rawUa = "Mozilla/5.0 (Linux; U; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.88 Mobile Safari/537.36"
        val sanitized = sanitizer.sanitizeUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
        assertTrue(sanitized.contains("Chrome/128.0.6613.88"))
        assertTrue(sanitized.contains("Mobile Safari/537.36"))
        assertTrue(sanitized.contains("Android 14"))
    }

    @Test
    fun testUserAgentWithDifferentVersionDigits() {
        val rawUa = "Mozilla/5.0 (Linux; Android 13; SM-S918B; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0.0 Chrome/115.0.5790.166 Mobile Safari/537.36"
        val sanitized = sanitizer.sanitizeUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0.0"))
        assertTrue(sanitized.contains("Chrome/115.0.5790.166"))
    }

    @Test
    fun testUserAgentAlreadySanitizedRemainsUnchanged() {
        val cleanUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.88 Mobile Safari/537.36"
        val sanitized = sanitizer.sanitizeUserAgent(cleanUa)

        assertEquals(cleanUa, sanitized)
    }

    @Test
    fun testPreservesDesktopAndCustomTokens() {
        val rawUa = "Mozilla/5.0 (X11; Linux x86_64; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Safari/537.36"
        val sanitized = sanitizer.sanitizeUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
        assertTrue(sanitized.contains("Chrome/120.0.0.0"))
        assertTrue(sanitized.contains("Linux x86_64"))
    }

    @Test
    fun testUserAgentWithParenthesisSpacingCorrectness() {
        val rawUa = "Mozilla/5.0 (Linux; Android 12; Pixel 6; wv) AppleWebKit/537.36"
        val sanitized = sanitizer.sanitizeUserAgent(rawUa)

        assertFalse(sanitized.contains("; wv"))
        assertTrue(sanitized.contains("(Linux; Android 12; Pixel 6)"))
    }

    @Test
    fun testEmptyOrBlankUserAgentFallback() {
        val blankResult = sanitizer.sanitizeUserAgent("   ")
        assertTrue(blankResult.isNotBlank())
        assertTrue(blankResult.contains("Mozilla/5.0"))
    }

    object UserAgentSanitizerHelper {
        private val WV_REGEX = Regex(";\\s*wv")
        private val VERSION_REGEX = Regex("Version/[0-9.]+\\s*")
        private const val DEFAULT_FALLBACK_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        fun sanitizeUserAgent(rawUa: String?): String {
            if (rawUa.isNullOrBlank()) {
                return DEFAULT_FALLBACK_UA
            }
            return rawUa
                .replace(WV_REGEX, "")
                .replace(VERSION_REGEX, "")
                .replace("  ", " ")
                .trim()
        }
    }
}
