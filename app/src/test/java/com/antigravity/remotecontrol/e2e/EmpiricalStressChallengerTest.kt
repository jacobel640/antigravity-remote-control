package com.antigravity.remotecontrol.e2e

import com.antigravity.remotecontrol.nativebridge.NativeFileChooserTest.MockNativeFileChooserHandler
import com.antigravity.remotecontrol.nativebridge.NotificationBridgeTest.MockNotificationBridge
import com.antigravity.remotecontrol.nativebridge.NotificationManagerTest.MockSystemNotificationManager
import com.antigravity.remotecontrol.nativebridge.PermissionLifecycleTest.MockPermissionController
import com.antigravity.remotecontrol.security.ISecurePreferencesManager
import com.antigravity.remotecontrol.security.SecurePreferencesManagerTest.MockSecurePreferencesManager
import com.antigravity.remotecontrol.security.UrlValidator
import com.antigravity.remotecontrol.web.AuthPopupWindowTest.MockAuthPopupManager
import com.antigravity.remotecontrol.web.CookiePersistenceTest.MockCookieManager
import com.antigravity.remotecontrol.web.SslBypassTest.MockAppWebViewClient
import com.antigravity.remotecontrol.web.SslBypassTest.MockSslError
import com.antigravity.remotecontrol.web.SslBypassTest.MockSslErrorHandler
import com.antigravity.remotecontrol.web.UserAgentSanitizerTest.UserAgentSanitizerHelper
import com.antigravity.remotecontrol.web.WebEngineManagerTest.MockWebEngineManager
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EMPIRICAL STRESS CHALLENGER TEST SUITE (Milestone 5)
 *
 * Executes exhaustive empirical verification across all critical vectors:
 * 1. Fuzzing & Rapid URL Mutations (IPv6, custom ports, invalid hosts, protocol injection)
 * 2. Multi-threaded Keystore Resets & AEADBadTagException recovery
 * 3. File Chooser cancellation flood, duplicate triggers, and lifecycle resilience
 * 4. High-frequency push notification flood, large payload fuzzing, and permission toggling
 * 5. SSL Proxy MITM interception stress and cross-origin cookie sync
 * 6. User-Agent sanitizer fuzzing across diverse browser strings
 */
class EmpiricalStressChallengerTest {

    // =========================================================================
    // 1. URL VALIDATOR FUZZING & CORNER CASES
    // =========================================================================

    @Test
    fun testIpv6AddressWithPortAndPath() {
        val validIpv6Urls = listOf(
            "http://[::1]:8080",
            "https://[::1]:8443/chat",
            "http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8000/app",
            "https://[fe80::1]:443",
            "http://[::ffff:192.0.2.128]:3000/api"
        )
        for (url in validIpv6Urls) {
            val result = UrlValidator.validateAndNormalize(url)
            assertTrue("Expected valid for IPv6 URL: $url", result.isValid)
            assertEquals(url, result.formattedUrl)
            assertNull(result.errorMessage)
        }
    }

    @Test
    fun testIpv6AddressMissingBracketsHandled() {
        val unbracketedIpv6 = "http://2001:db8::1:8080"
        val result = UrlValidator.validateAndNormalize(unbracketedIpv6)
        assertNotNull(result)
        assertFalse("Unbracketed IPv6 must be rejected", result.isValid)
    }

    @Test
    fun testPortBoundaryValues() {
        // Port 1 (min valid)
        val minPort = UrlValidator.validateAndNormalize("https://server.internal:1")
        assertTrue(minPort.isValid)

        // Port 65535 (max valid)
        val maxPort = UrlValidator.validateAndNormalize("https://server.internal:65535")
        assertTrue(maxPort.isValid)

        // Port 0 (invalid)
        val zeroPort = UrlValidator.validateAndNormalize("https://server.internal:0")
        assertFalse(zeroPort.isValid)

        // Port 65536 (overflow)
        val overflowPort = UrlValidator.validateAndNormalize("https://server.internal:65536")
        assertFalse(overflowPort.isValid)

        // Huge integer port overflow
        val hugePort = UrlValidator.validateAndNormalize("https://server.internal:99999999999999999999")
        assertFalse(hugePort.isValid)

        // Negative port
        val negPort = UrlValidator.validateAndNormalize("https://server.internal:-443")
        assertFalse(negPort.isValid)

        // Non-numeric port / reg-name authority
        val alphaPort = UrlValidator.validateAndNormalize("https://server.internal:abc")
        assertFalse(alphaPort.isValid)

        // Multiple colons authority in URI
        val multiColonPort = UrlValidator.validateAndNormalize("https://server.internal:80:80")
        assertFalse(multiColonPort.isValid)
    }


    @Test
    fun testAdversarialProtocolsRejected() {
        val forbiddenSchemes = listOf(
            "javascript:alert(document.cookie)",
            "JAVASCRIPT:alert(1)",
            "file:///data/data/com.antigravity.remotecontrol/shared_prefs/prefs.xml",
            "FILE:///etc/hosts",
            "data:text/html,<script>alert(1)</script>",
            "mailto:attacker@evil.com",
            "tel:911",
            "ftp://ftp.example.com/files",
            "about:blank"
        )
        for (scheme in forbiddenSchemes) {
            val result = UrlValidator.validateAndNormalize(scheme)
            assertFalse("Scheme $scheme must be rejected", result.isValid)
            assertNotNull(result.errorMessage)
        }
    }

    @Test
    fun testControlCharacterAndNullByteInjectionRejected() {
        val maliciousInputs = listOf(
            "https://server.internal\r\nInjected-Header: evil",
            "https://server.internal\nSet-Cookie: admin=true",
            "https://server.internal\u0000/path",
            "https://server.internal\r/index.html"
        )
        for (input in maliciousInputs) {
            val result = UrlValidator.validateAndNormalize(input)
            assertFalse("Input with control character must be rejected: $input", result.isValid)
            assertTrue(result.errorMessage!!.contains("control characters"))
        }
    }

    @Test
    fun testFiftyThousandCharUrlStress() {
        val longParam = "key=" + "v".repeat(50000)
        val longUrl = "https://server.internal/query?$longParam"
        val result = UrlValidator.validateAndNormalize(longUrl)
        assertTrue(result.isValid)
        assertEquals(longUrl, result.formattedUrl)
    }

    @Test
    fun testHostWithoutSchemeAutoPrependsHttps() {
        val testCases = mapOf(
            "10.0.2.2:8000" to "https://10.0.2.2:8000",
            "localhost:3000" to "https://localhost:3000",
            "my-remote-app.corp.net" to "https://my-remote-app.corp.net",
            "192.168.1.50:9090/ws" to "https://192.168.1.50:9090/ws"
        )
        for ((raw, expected) in testCases) {
            val result = UrlValidator.validateAndNormalize(raw)
            assertTrue("Expected valid for raw input: $raw", result.isValid)
            assertEquals(expected, result.formattedUrl)
        }
    }

    // =========================================================================
    // 2. CONCURRENT KEYSTORE RESETS & AEAD RECOVERY STRESS
    // =========================================================================

    @Test
    fun testMultiThreadedConcurrentAccessWithKeystoreCorruption() {
        val storage = MockSecurePreferencesManager()
        val threadCount = 20
        val operationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = mutableListOf<Throwable>()

        storage.setTargetUrl("https://initial-safe-url.com")

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        if (i == 25 && t % 4 == 0) {
                            // Inject random keystore corruption event
                            storage.simulateKeystoreCorruption()
                        }
                        storage.setTargetUrl("https://thread-$t-op-$i.internal")
                        val readBack = storage.getTargetUrl()
                        if (readBack != null) {
                            assertTrue(readBack.startsWith("https://"))
                        }
                    }
                } catch (e: Throwable) {
                    synchronized(errors) {
                        errors.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Timeout during concurrency test", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("No exceptions should escape concurrent operations: $errors", 0, errors.size)
    }

    // =========================================================================
    // 3. FILE CHOOSER FLOOD & LIFECYCLE DEADLOCK PREVENTION
    // =========================================================================

    @Test
    fun testRapidDuplicateFileChooserRequestsCancelPreviousCallbacks() {
        val handler = MockNativeFileChooserHandler()
        val receivedResults = mutableListOf<Array<String>?>()
        val totalRequests = 25

        for (i in 0 until totalRequests) {
            handler.prepareChooser({ result ->
                receivedResults.add(result)
            }, allowMultiple = false)
        }

        // Only the 25th request remains pending, 24 earlier requests received null to prevent deadlocks
        assertEquals(totalRequests - 1, receivedResults.size)
        for (res in receivedResults) {
            assertNull("Previous cancelled callback must receive null", res)
        }
        assertTrue(handler.hasPendingCallback())

        // Fulfill the final 25th request
        handler.onFileSelectionResult(resultOk = true, selectedUris = arrayOf("content://docs/final_selection.pdf"))
        assertEquals(totalRequests, receivedResults.size)
        assertEquals("content://docs/final_selection.pdf", receivedResults.last()?.get(0))
        assertFalse(handler.hasPendingCallback())
    }

    @Test
    fun testFileChooserHandlesFiftyBatchFilesSelection() {
        val handler = MockNativeFileChooserHandler()
        var batchResult: Array<String>? = null

        handler.prepareChooser({ result ->
            batchResult = result
        }, allowMultiple = true)

        val files = Array(50) { index -> "content://media/external/file_$index.log" }
        handler.onFileSelectionResult(resultOk = true, selectedUris = files)

        assertNotNull(batchResult)
        assertEquals(50, batchResult!!.size)
        assertEquals("content://media/external/file_0.log", batchResult!![0])
        assertEquals("content://media/external/file_49.log", batchResult!![49])
    }

    // =========================================================================
    // 4. PUSH NOTIFICATION FLOOD & PERMISSION LIFECYCLE
    // =========================================================================

    @Test
    fun testOneThousandNotificationFloodStress() {
        val bridge = MockNotificationBridge()
        val notificationManager = MockSystemNotificationManager()
        val totalNotifications = 1000

        for (i in 1..totalNotifications) {
            bridge.postNotification(
                title = "Notification #$i",
                body = "System stress test payload message body for item $i",
                tag = "tag-$i",
                iconUrl = null
            )
            val posted = bridge.postedNotifications.last()
            notificationManager.displayNotification(posted.title, posted.body, posted.tag)
        }

        assertEquals(totalNotifications, bridge.postedNotifications.size)
        assertEquals(totalNotifications, notificationManager.getActiveNotifications().size)
    }

    @Test
    fun testPermissionStateTransitionsAndJsSync() {
        val controller = MockPermissionController()
        val bridge = MockNotificationBridge()

        controller.setSdkVersion(34) // Android 14

        // 1. Initial state
        assertEquals("default", controller.checkNotificationPermission())

        // 2. Grant permission
        controller.requestNotificationPermission()
        controller.onPermissionResult(isGranted = true)
        bridge.setPermissionGranted(true)
        assertEquals("granted", controller.checkNotificationPermission())
        assertEquals("granted", bridge.getPermissionStatus())

        // 3. User revokes in OS Settings
        controller.simulateOsSettingRevocation()
        bridge.setPermissionGranted(false)
        assertEquals("denied", controller.checkNotificationPermission())
        assertEquals("denied", bridge.getPermissionStatus())

        // 4. User re-grants permission
        controller.onPermissionResult(isGranted = true)
        bridge.setPermissionGranted(true)
        assertEquals("granted", controller.checkNotificationPermission())
        assertEquals("granted", bridge.getPermissionStatus())
    }

    // =========================================================================
    // 5. PROXY SSL MITM INTERCEPTION & COOKIE PERSISTENCE
    // =========================================================================

    @Test
    fun testAllSslErrorCodesBypassedCleanly() {
        val client = MockAppWebViewClient()
        val errorCodes = listOf(
            MockSslError.SSL_NOTYETVALID,
            MockSslError.SSL_EXPIRED,
            MockSslError.SSL_IDMISMATCH,
            MockSslError.SSL_UNTRUSTED,
            MockSslError.SSL_DATE_INVALID,
            MockSslError.SSL_INVALID
        )

        for (code in errorCodes) {
            val handler = MockSslErrorHandler()
            val error = MockSslError(code, "https://mitm-proxy.internal/api")
            client.onReceivedSslError(handler, error)
            assertTrue("Handler proceed() must be called for error code $code", handler.wasProceedCalled)
        }

        assertEquals(errorCodes.size, client.bypassedSslErrorCount)
    }

    @Test
    fun testCrossOriginCookiePersistenceDuringGoogleOAuth() {
        val cookieManager = MockCookieManager()
        val authDomain = "https://accounts.google.com"
        val targetAppDomain = "https://remote.antigravity.internal:8443"

        cookieManager.setAcceptThirdPartyCookies(true)

        // Set Google OAuth session cookies
        cookieManager.setCookie(authDomain, "SID=google_sid_12345; Path=/; Domain=google.com; Secure; HttpOnly")
        cookieManager.setCookie(authDomain, "HSID=google_hsid_67890; Path=/; Domain=google.com; Secure; HttpOnly")
        cookieManager.setCookie(authDomain, "SSID=google_ssid_abcdef; SameSite=None; Secure")

        // Set Antigravity target domain auth token
        cookieManager.setCookie(targetAppDomain, "ag_auth_token=jwt_payload_998877; Secure; HttpOnly")

        cookieManager.flush()
        assertTrue(cookieManager.isFlushed)

        // Verify cookies accessible across origins
        val googleCookies = cookieManager.getCookie(authDomain)
        val appCookies = cookieManager.getCookie(targetAppDomain)

        assertNotNull(googleCookies)
        assertTrue(googleCookies!!.contains("google_sid_12345"))
        assertTrue(googleCookies.contains("SameSite=None"))

        assertNotNull(appCookies)
        assertTrue(appCookies!!.contains("jwt_payload_998877"))
    }

    // =========================================================================
    // 6. USER-AGENT SANITIZER ADVERSARIAL FUZZING
    // =========================================================================

    @Test
    fun testUserAgentSanitizerFuzzing() {
        val userAgents = listOf(
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.6613.88 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; U; Android 13; en-us; SM-S918B Build/TP1A.220624.014; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/115.0.5790.166 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; wv; wv) Version/4.0.0.0 Chrome/100.0.0.0",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        )

        for (ua in userAgents) {
            val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(ua)
            assertFalse("Sanitized UA must not contain '; wv': $sanitized", sanitized.contains("; wv"))
            assertFalse("Sanitized UA must not contain 'Version/4.0': $sanitized", sanitized.contains("Version/4.0"))
            assertTrue("Sanitized UA must retain Chrome token: $sanitized", sanitized.contains("Chrome/"))
        }
    }
}
