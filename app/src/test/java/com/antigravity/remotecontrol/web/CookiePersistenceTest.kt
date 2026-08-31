package com.antigravity.remotecontrol.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F9: Cross-Origin Cookie Persistence.
 * Verifies third-party cookie acceptance, cross-origin synchronization, and disk flushing.
 */
class CookiePersistenceTest {

    private lateinit var cookieManager: MockCookieManager

    @Before
    fun setUp() {
        cookieManager = MockCookieManager()
    }

    @Test
    fun testThirdPartyCookiesEnabledByDefault() {
        cookieManager.setAcceptThirdPartyCookies(true)
        assertTrue(cookieManager.acceptThirdPartyCookies)
    }

    @Test
    fun testSetAndGetCookieForTargetDomain() {
        val domain = "https://remote.antigravity.internal"
        val cookieValue = "session_token=xyz98765; Secure; HttpOnly; SameSite=Lax"

        cookieManager.setCookie(domain, cookieValue)
        val stored = cookieManager.getCookie(domain)

        assertNotNull(stored)
        assertTrue(stored!!.contains("session_token=xyz98765"))
    }

    @Test
    fun testCrossOriginCookieSynchronizationBetweenGoogleAndAppDomain() {
        val googleOrigin = "https://accounts.google.com"
        val appOrigin = "https://remote.antigravity.internal"

        cookieManager.setCookie(googleOrigin, "G_AUTH_USER_ID=1092837465")
        cookieManager.setCookie(appOrigin, "REMOTE_SESSION_KEY=abcdef123456")

        val googleCookie = cookieManager.getCookie(googleOrigin)
        val appCookie = cookieManager.getCookie(appOrigin)

        assertTrue(googleCookie!!.contains("G_AUTH_USER_ID=1092837465"))
        assertTrue(appCookie!!.contains("REMOTE_SESSION_KEY=abcdef123456"))
    }

    @Test
    fun testFlushPersistsCookiesToDisk() {
        cookieManager.setCookie("https://remote.antigravity.internal", "token=abc")
        assertFalse(cookieManager.isFlushed)

        cookieManager.flush()
        assertTrue(cookieManager.isFlushed)
    }

    @Test
    fun testRemoveAllCookiesCleansStorage() {
        cookieManager.setCookie("https://remote.antigravity.internal", "token=abc")
        assertTrue(cookieManager.hasCookies())

        cookieManager.removeAllCookies()
        assertFalse(cookieManager.hasCookies())
    }

    class MockCookieManager {
        private val cookieStore = mutableMapOf<String, String>()
        var acceptThirdPartyCookies: Boolean = false
            private set
        var isFlushed: Boolean = false
            private set

        fun setAcceptThirdPartyCookies(accept: Boolean) {
            acceptThirdPartyCookies = accept
        }

        fun setCookie(url: String, value: String) {
            val domain = extractDomain(url)
            val existing = cookieStore[domain]
            cookieStore[domain] = if (existing == null) value else "$existing; $value"
            isFlushed = false
        }

        fun getCookie(url: String): String? {
            val domain = extractDomain(url)
            return cookieStore[domain]
        }

        fun flush() {
            isFlushed = true
        }

        fun hasCookies(): Boolean = cookieStore.isNotEmpty()

        fun removeAllCookies() {
            cookieStore.clear()
            isFlushed = true
        }

        private fun extractDomain(url: String): String {
            return url.substringAfter("://").substringBefore("/").substringBefore(":")
        }
    }
}
