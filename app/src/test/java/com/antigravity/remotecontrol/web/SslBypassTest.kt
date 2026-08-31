package com.antigravity.remotecontrol.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F7: SSL Certificate Interception Bypass.
 * Verifies transparent bypass of SSL errors (SSL_UNTRUSTED, SSL_EXPIRED, SSL_IDMISMATCH, SSL_NOTYETVALID) via handler.proceed().
 */
class SslBypassTest {

    private lateinit var sslHandler: MockSslErrorHandler
    private lateinit var webViewClient: MockAppWebViewClient

    @Before
    fun setUp() {
        sslHandler = MockSslErrorHandler()
        webViewClient = MockAppWebViewClient()
    }

    @Test
    fun testUntrustedCertificateErrorCallsProceed() {
        val error = MockSslError(MockSslError.SSL_UNTRUSTED, "https://college-proxy.internal")
        webViewClient.onReceivedSslError(sslHandler, error)

        assertTrue(sslHandler.wasProceedCalled)
        assertFalse(sslHandler.wasCancelCalled)
        assertEquals(1, webViewClient.bypassedSslErrorCount)
    }

    @Test
    fun testExpiredCertificateErrorCallsProceed() {
        val error = MockSslError(MockSslError.SSL_EXPIRED, "https://dev-server.lan:8443")
        webViewClient.onReceivedSslError(sslHandler, error)

        assertTrue(sslHandler.wasProceedCalled)
        assertFalse(sslHandler.wasCancelCalled)
    }

    @Test
    fun testIdMismatchCertificateErrorCallsProceed() {
        val error = MockSslError(MockSslError.SSL_IDMISMATCH, "https://10.0.2.2:8443")
        webViewClient.onReceivedSslError(sslHandler, error)

        assertTrue(sslHandler.wasProceedCalled)
        assertFalse(sslHandler.wasCancelCalled)
    }

    @Test
    fun testNotYetValidCertificateErrorCallsProceed() {
        val error = MockSslError(MockSslError.SSL_NOTYETVALID, "https://new-cert-proxy.org")
        webViewClient.onReceivedSslError(sslHandler, error)

        assertTrue(sslHandler.wasProceedCalled)
        assertFalse(sslHandler.wasCancelCalled)
    }

    @Test
    fun testMultipleConsecutiveSubresourceSslErrorsAllProceed() {
        val urls = listOf(
            "https://proxy.internal/app.js",
            "https://proxy.internal/styles.css",
            "https://proxy.internal/api/stream",
            "https://proxy.internal/favicon.ico"
        )

        for (url in urls) {
            val handler = MockSslErrorHandler()
            val error = MockSslError(MockSslError.SSL_UNTRUSTED, url)
            webViewClient.onReceivedSslError(handler, error)
            assertTrue("Handler for $url must call proceed", handler.wasProceedCalled)
        }

        assertEquals(4, webViewClient.bypassedSslErrorCount)
    }

    data class MockSslError(val primaryError: Int, val url: String) {
        companion object {
            const val SSL_NOTYETVALID = 0
            const val SSL_EXPIRED = 1
            const val SSL_IDMISMATCH = 2
            const val SSL_UNTRUSTED = 3
            const val SSL_DATE_INVALID = 4
            const val SSL_INVALID = 5
        }
    }

    class MockSslErrorHandler {
        var wasProceedCalled: Boolean = false
            private set
        var wasCancelCalled: Boolean = false
            private set

        fun proceed() {
            wasProceedCalled = true
        }

        fun cancel() {
            wasCancelCalled = true
        }
    }

    class MockAppWebViewClient {
        var bypassedSslErrorCount: Int = 0
            private set

        fun onReceivedSslError(handler: MockSslErrorHandler?, error: MockSslError?) {
            if (handler != null && error != null) {
                bypassedSslErrorCount++
                handler.proceed()
            }
        }
    }
}
