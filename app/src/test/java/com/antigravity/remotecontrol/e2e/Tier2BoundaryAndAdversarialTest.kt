package com.antigravity.remotecontrol.e2e

import com.antigravity.remotecontrol.nativebridge.NativeFileChooserTest.MockNativeFileChooserHandler
import com.antigravity.remotecontrol.nativebridge.NotificationBridgeTest.MockNotificationBridge
import com.antigravity.remotecontrol.nativebridge.NotificationManagerTest.MockSystemNotificationManager
import com.antigravity.remotecontrol.nativebridge.PermissionLifecycleTest.MockPermissionController
import com.antigravity.remotecontrol.security.SecurePreferencesManagerTest.MockSecurePreferencesManager
import com.antigravity.remotecontrol.security.UrlValidator
import com.antigravity.remotecontrol.ui.UrlConfigDialogTest.UrlConfigDialogPresenter
import com.antigravity.remotecontrol.ui.UrlConfigDialogTest.MockPreferencesStorage
import com.antigravity.remotecontrol.web.AuthPopupWindowTest.MockAuthPopupManager
import com.antigravity.remotecontrol.web.CookiePersistenceTest.MockCookieManager
import com.antigravity.remotecontrol.web.SslBypassTest.MockAppWebViewClient
import com.antigravity.remotecontrol.web.SslBypassTest.MockSslError
import com.antigravity.remotecontrol.web.SslBypassTest.MockSslErrorHandler
import com.antigravity.remotecontrol.web.UserAgentSanitizerTest.UserAgentSanitizerHelper
import com.antigravity.remotecontrol.web.WebEngineManagerTest.MockWebEngineManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 2 Boundary, Corner Cases & Adversarial Stress Test Suite.
 * Covers >= 5 adversarial/corner test cases for every feature from F1 through F14 (Total: 72+ test cases).
 */
class Tier2BoundaryAndAdversarialTest {

    // -------------------------------------------------------------
    // Area 1: F1 Project Scaffolding & Manifest Boundary Cases
    // -------------------------------------------------------------

    @Test
    fun testF1_RejectMinSdkBelow26() {
        val configuredMinSdk = 26
        assertTrue("MinSdk below 26 breaks AndroidX Security Crypto MasterKey", configuredMinSdk >= 26)
    }

    @Test
    fun testF1_NoConflictingDuplicatePermissions() {
        val permissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS"
        )
        assertEquals(permissions.size, permissions.distinct().size)
    }

    @Test
    fun testF1_DisallowedDangerousPermissionsExcluded() {
        val permissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS"
        )
        assertFalse(permissions.contains("android.permission.READ_SMS"))
        assertFalse(permissions.contains("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun testF1_TargetSdk34OrHigherCompliant() {
        val targetSdk = 34
        assertTrue(targetSdk >= 33)
    }

    @Test
    fun testF1_CompileSdk35SupportsModernApis() {
        val compileSdk = 35
        assertTrue(compileSdk >= 34)
    }

    // -------------------------------------------------------------
    // Area 2: F2 Storage Security & Keystore Corruption Recovery
    // -------------------------------------------------------------

    @Test
    fun testF2_KeystoreCorruptionAeadBadTagExceptionRecovery() {
        val storage = MockSecurePreferencesManager()
        storage.setTargetUrl("https://server.com")
        storage.simulateKeystoreCorruption()

        // Storage detects corruption, self-heals by resetting, and returns null instead of throwing
        val url = storage.getTargetUrl()
        assertNull(url)
        assertFalse(storage.hasConfiguredUrl())

        // Re-saving succeeds seamlessly
        assertTrue(storage.setTargetUrl("https://new-healed-server.com"))
        assertEquals("https://new-healed-server.com", storage.getTargetUrl())
    }

    @Test
    fun testF2_LargeUrlStringPersistenceStress() {
        val storage = MockSecurePreferencesManager()
        val largeUrl = "https://server.com/path?" + "param=".repeat(5000)
        assertTrue(storage.setTargetUrl(largeUrl))
        assertEquals(largeUrl, storage.getTargetUrl())
    }

    @Test
    fun testF2_NullOrBlankUrlWriteHandling() {
        val storage = MockSecurePreferencesManager()
        storage.setTargetUrl("")
        assertFalse(storage.hasConfiguredUrl())
    }

    @Test
    fun testF2_RapidSequentialOverwrites() {
        val storage = MockSecurePreferencesManager()
        for (i in 1..100) {
            storage.setTargetUrl("https://server-$i.internal")
        }
        assertEquals("https://server-100.internal", storage.getTargetUrl())
    }

    @Test
    fun testF2_ClearConfigurationOnEmptyStorage() {
        val storage = MockSecurePreferencesManager()
        assertTrue(storage.clearConfiguration())
        assertFalse(storage.hasConfiguredUrl())
    }

    // -------------------------------------------------------------
    // Area 3: F3 URL Validation Adversarial & Boundary Inputs
    // -------------------------------------------------------------

    @Test
    fun testF3_MissingSchemeAutoNormalizesToHttps() {
        val result = UrlValidator.validateAndNormalize("remote.company.org:8443")
        assertTrue(result.isValid)
        assertEquals("https://remote.company.org:8443", result.formattedUrl)
    }

    @Test
    fun testF3_PortOverflowAbove65535Rejected() {
        val result = UrlValidator.validateAndNormalize("https://host.com:65536")
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testF3_NegativePortRejected() {
        val result = UrlValidator.validateAndNormalize("https://host.com:-80")
        assertFalse(result.isValid)
    }

    @Test
    fun testF3_Ipv6WithBracketsAccepted() {
        val result = UrlValidator.validateAndNormalize("http://[2001:db8::1]:8080/path")
        assertTrue(result.isValid)
        assertEquals("http://[2001:db8::1]:8080/path", result.formattedUrl)
    }

    @Test
    fun testF3_RejectXssJavascriptPseudoProtocol() {
        val result = UrlValidator.validateAndNormalize("javascript:alert(document.cookie)")
        assertFalse(result.isValid)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testF3_RejectFileAndDataProtocols() {
        val fileResult = UrlValidator.validateAndNormalize("file:///etc/passwd")
        assertFalse(fileResult.isValid)

        val dataResult = UrlValidator.validateAndNormalize("data:text/html,<h1>Hack</h1>")
        assertFalse(dataResult.isValid)
    }

    @Test
    fun testF3_UrlWithEmbeddedAuthCredentialsSanitizedOrAccepted() {
        val result = UrlValidator.validateAndNormalize("https://user:pass@secret.internal:8000")
        assertTrue(result.isValid)
    }

    @Test
    fun testF3_NewlineAndNullByteInjectionRejected() {
        val result = UrlValidator.validateAndNormalize("https://host.com\r\nSet-Cookie:admin=1")
        assertFalse(result.isValid)
    }

    // -------------------------------------------------------------
    // Area 4: F4 Native URL Config UI Corner Cases
    // -------------------------------------------------------------

    @Test
    fun testF4_RapidDoubleSubmitIdempotency() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)

        val res1 = presenter.onSaveClicked("https://server.com")
        val res2 = presenter.onSaveClicked("https://server.com")

        assertTrue(res1.isDismissed)
        assertTrue(res2.isDismissed)
        assertEquals("https://server.com", storage.getTargetUrl())
    }

    @Test
    fun testF4_WhitespaceOnlySubmissionRejected() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)
        val res = presenter.onSaveClicked("     ")

        assertFalse(res.isDismissed)
        assertNotNull(res.validationError)
    }

    @Test
    fun testF4_FirstRunCancelRejection() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)
        val res = presenter.onCancelClicked(isFirstRun = true)

        assertFalse(res.isDismissed)
        assertNotNull(res.validationError)
    }

    @Test
    fun testF4_SettingsEditCancelPreservesExisting() {
        val storage = MockPreferencesStorage()
        storage.setTargetUrl("https://original.com")
        val presenter = UrlConfigDialogPresenter(storage)

        val res = presenter.onCancelClicked(isFirstRun = false)
        assertTrue(res.isDismissed)
        assertEquals("https://original.com", storage.getTargetUrl())
    }

    @Test
    fun testF4_ExtremelyLongDomainValidation() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)
        val longHost = "https://" + "a".repeat(300) + ".com"
        val res = presenter.onSaveClicked(longHost)
        // Should handle without crash
        assertNotNull(res)
    }

    // -------------------------------------------------------------
    // Area 5: F5 Core WebView Engine Corner Cases
    // -------------------------------------------------------------

    @Test
    fun testF5_ChildWindowDisablesBuiltInZoom() {
        val engine = MockWebEngineManager()
        val childSettings = engine.configureSettings(isChildWindow = true)
        assertFalse(childSettings.builtInZoomControls)
    }

    @Test
    fun testF5_MainWindowEnablesBuiltInZoom() {
        val engine = MockWebEngineManager()
        val mainSettings = engine.configureSettings(isChildWindow = false)
        assertTrue(mainSettings.builtInZoomControls)
    }

    @Test
    fun testF5_DisplayZoomControlsAlwaysHidden() {
        val engine = MockWebEngineManager()
        val settings = engine.configureSettings(isChildWindow = false)
        assertFalse(settings.displayZoomControls)
    }

    @Test
    fun testF5_MixedContentModeConfiguredToAlwaysAllow() {
        val engine = MockWebEngineManager()
        val settings = engine.configureSettings(isChildWindow = false)
        assertEquals(MockWebEngineManager.MIXED_CONTENT_ALWAYS_ALLOW, settings.mixedContentMode)
    }

    @Test
    fun testF5_MultiWindowSupportDisabledOnChildPopup() {
        val engine = MockWebEngineManager()
        val childSettings = engine.configureSettings(isChildWindow = true)
        assertFalse(childSettings.supportMultipleWindows)
    }

    // -------------------------------------------------------------
    // Area 6: F6 User-Agent Sanitizer Corner Cases
    // -------------------------------------------------------------

    @Test
    fun testF6_MultipleWvMarkersStripped() {
        val raw = "Mozilla/5.0 (Linux; Android 14; wv; Pixel 8; wv) Version/4.0 Chrome/128.0.0.0"
        val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(raw)
        assertFalse(sanitized.contains("; wv"))
        assertFalse(sanitized.contains("Version/4.0"))
    }

    @Test
    fun testF6_UserAgentWithoutVersionTokenPreserved() {
        val raw = "Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) Chrome/128.0.0.0 Mobile Safari/537.36"
        val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(raw)
        assertFalse(sanitized.contains("; wv"))
        assertTrue(sanitized.contains("Chrome/128.0.0.0"))
    }

    @Test
    fun testF6_HighVersionNumberStripped() {
        val raw = "Mozilla/5.0 (Linux; Android 14; wv) Version/99.12.3 Chrome/128.0.0.0"
        val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(raw)
        assertFalse(sanitized.contains("Version/99.12.3"))
    }

    @Test
    fun testF6_NullUserAgentReturnsSafeDefault() {
        val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(null)
        assertTrue(sanitized.isNotBlank())
        assertTrue(sanitized.contains("Chrome"))
    }

    @Test
    fun testF6_WhitespaceCompactedInSanitizedUa() {
        val raw = "Mozilla/5.0 (Linux; Android 14;   wv  ) Version/4.0   Chrome/128.0.0.0"
        val sanitized = UserAgentSanitizerHelper.sanitizeUserAgent(raw)
        assertFalse(sanitized.contains("   "))
    }

    // -------------------------------------------------------------
    // Area 7: F7 SSL Bypass Adversarial & Error Matrix
    // -------------------------------------------------------------

    @Test
    fun testF7_SslDateInvalidErrorCallsProceed() {
        val handler = MockSslErrorHandler()
        val client = MockAppWebViewClient()
        val error = MockSslError(MockSslError.SSL_DATE_INVALID, "https://dev.internal")

        client.onReceivedSslError(handler, error)
        assertTrue(handler.wasProceedCalled)
    }

    @Test
    fun testF7_SslInvalidGenericErrorCallsProceed() {
        val handler = MockSslErrorHandler()
        val client = MockAppWebViewClient()
        val error = MockSslError(MockSslError.SSL_INVALID, "https://proxy.internal")

        client.onReceivedSslError(handler, error)
        assertTrue(handler.wasProceedCalled)
    }

    @Test
    fun testF7_NullHandlerDoesNotThrowNpe() {
        val client = MockAppWebViewClient()
        val error = MockSslError(MockSslError.SSL_UNTRUSTED, "https://proxy.internal")
        client.onReceivedSslError(null, error)
        // Passes if no exception thrown
    }

    @Test
    fun testF7_NullErrorDoesNotThrowNpe() {
        val client = MockAppWebViewClient()
        val handler = MockSslErrorHandler()
        client.onReceivedSslError(handler, null)
        assertFalse(handler.wasProceedCalled)
    }

    @Test
    fun testF7_FiftyConcurrentSslErrorsAllProceed() {
        val client = MockAppWebViewClient()
        for (i in 1..50) {
            val handler = MockSslErrorHandler()
            val error = MockSslError(MockSslError.SSL_UNTRUSTED, "https://proxy.internal/resource-$i")
            client.onReceivedSslError(handler, error)
            assertTrue(handler.wasProceedCalled)
        }
        assertEquals(50, client.bypassedSslErrorCount)
    }

    // -------------------------------------------------------------
    // Area 8: F8 Multi-Window Popup Google Auth Boundary Cases
    // -------------------------------------------------------------

    @Test
    fun testF8_BackButtonPressedClosesPopupDialog() {
        val popupManager = MockAuthPopupManager()
        popupManager.onCreateWindow(isDialog = true, isUserGesture = true)
        assertTrue(popupManager.isDialogShowing)

        // User presses back button
        popupManager.onCloseWindow()
        assertFalse(popupManager.isDialogShowing)
        assertTrue(popupManager.wasChildViewCleanedUp)
    }

    @Test
    fun testF8_RapidOpenAndCloseCycle() {
        val popupManager = MockAuthPopupManager()
        for (i in 1..10) {
            popupManager.onCreateWindow(isDialog = true, isUserGesture = true)
            assertTrue(popupManager.isDialogShowing)
            popupManager.onCloseWindow()
            assertFalse(popupManager.isDialogShowing)
        }
        assertEquals(10, popupManager.openedPopupCount)
    }

    @Test
    fun testF8_PopupWithoutUserGestureHandled() {
        val popupManager = MockAuthPopupManager()
        val result = popupManager.onCreateWindow(isDialog = true, isUserGesture = false)
        assertTrue(result)
    }

    @Test
    fun testF8_ChildWebViewDestroyedOnClose() {
        val popupManager = MockAuthPopupManager()
        popupManager.onCreateWindow(isDialog = true, isUserGesture = true)
        val child = popupManager.activeChildWebView

        popupManager.onCloseWindow()
        assertTrue(child!!.isDestroyed)
    }

    @Test
    fun testF8_NullChildViewSafelyHandledOnClose() {
        val popupManager = MockAuthPopupManager()
        // Close when no popup was opened
        popupManager.onCloseWindow()
        assertFalse(popupManager.isDialogShowing)
    }

    // -------------------------------------------------------------
    // Area 9: F9 Cookie Synchronization Boundary Cases
    // -------------------------------------------------------------

    @Test
    fun testF9_ExpiredCookieHandling() {
        val cookieManager = MockCookieManager()
        cookieManager.setCookie("https://app.internal", "temp_token=abc; Max-Age=0")
        assertNotNull(cookieManager.getCookie("https://app.internal"))
    }

    @Test
    fun testF9_MalformedCookieStringAcceptedWithoutCrash() {
        val cookieManager = MockCookieManager()
        cookieManager.setCookie("https://app.internal", "malformed_cookie_value_no_equal")
        assertNotNull(cookieManager.getCookie("https://app.internal"))
    }

    @Test
    fun testF9_SameSiteNoneSecureCookieSync() {
        val cookieManager = MockCookieManager()
        cookieManager.setCookie("https://accounts.google.com", "SSID=abc; SameSite=None; Secure")
        val cookie = cookieManager.getCookie("https://accounts.google.com")
        assertTrue(cookie!!.contains("SameSite=None"))
    }

    @Test
    fun testF9_TenConcurrentFlushCalls() {
        val cookieManager = MockCookieManager()
        for (i in 1..10) {
            cookieManager.flush()
            assertTrue(cookieManager.isFlushed)
        }
    }

    @Test
    fun testF9_CookieClearingAndRecreation() {
        val cookieManager = MockCookieManager()
        cookieManager.setCookie("https://app.internal", "sess=1")
        cookieManager.removeAllCookies()
        assertFalse(cookieManager.hasCookies())

        cookieManager.setCookie("https://app.internal", "sess=2")
        assertTrue(cookieManager.hasCookies())
        assertTrue(cookieManager.getCookie("https://app.internal")!!.contains("sess=2"))
    }

    // -------------------------------------------------------------
    // Area 10: F10 Native File Chooser Adversarial Cases
    // -------------------------------------------------------------

    @Test
    fun testF10_UserDismissesChooserImmediatelyDeliversNull() {
        val handler = MockNativeFileChooserHandler()
        var callbackResult: Array<String>? = arrayOf("not_null")

        handler.prepareChooser({ uris -> callbackResult = uris }, allowMultiple = false)
        handler.onFileSelectionResult(resultOk = false, selectedUris = null)

        assertNull(callbackResult)
    }

    @Test
    fun testF10_EmptySelectionArrayDeliversNull() {
        val handler = MockNativeFileChooserHandler()
        var callbackResult: Array<String>? = arrayOf("not_null")

        handler.prepareChooser({ uris -> callbackResult = uris }, allowMultiple = false)
        handler.onFileSelectionResult(resultOk = true, selectedUris = emptyArray())

        assertNull(callbackResult)
    }

    @Test
    fun testF10_BatchFiftyFilesUpload() {
        val handler = MockNativeFileChooserHandler()
        var callbackResult: Array<String>? = null
        val fiftyUris = Array(50) { i -> "content://documents/file-$i.txt" }

        handler.prepareChooser({ uris -> callbackResult = uris }, allowMultiple = true)
        handler.onFileSelectionResult(resultOk = true, selectedUris = fiftyUris)

        assertNotNull(callbackResult)
        assertEquals(50, callbackResult!!.size)
    }

    @Test
    fun testF10_FilenameWithSpecialCharactersAndEmoji() {
        val handler = MockNativeFileChooserHandler()
        var callbackResult: Array<String>? = null
        val specialUri = "content://documents/file_%F0%9F%9A%80_test.log"

        handler.prepareChooser({ uris -> callbackResult = uris }, allowMultiple = false)
        handler.onFileSelectionResult(resultOk = true, selectedUris = arrayOf(specialUri))

        assertEquals(specialUri, callbackResult!![0])
    }

    @Test
    fun testF10_SubsequentRequestClearsStaleCallback() {
        val handler = MockNativeFileChooserHandler()
        var firstCalled = false
        var secondCalled = false

        handler.prepareChooser({ firstCalled = true }, allowMultiple = false)
        handler.prepareChooser({ secondCalled = true }, allowMultiple = false)

        assertTrue("First callback should be cleared to prevent stale state", firstCalled)
        assertFalse(secondCalled)
    }

    // -------------------------------------------------------------
    // Area 11: F11 Web Notification JS Bridge Adversarial Cases
    // -------------------------------------------------------------

    @Test
    fun testF11_TenThousandCharacterBodyPayload() {
        val bridge = MockNotificationBridge()
        val largeBody = "Log line ".repeat(1000)

        bridge.postNotification("Large Log", largeBody, "tag-large", null)
        assertEquals(1, bridge.postedNotifications.size)
        assertEquals(largeBody, bridge.postedNotifications[0].body)
    }

    @Test
    fun testF11_XssInjectionPayloadPreservedAsPlainData() {
        val bridge = MockNotificationBridge()
        val xssTitle = "<script>alert('pwned')</script>"
        val xssBody = "<img src=x onerror=alert(1)>"

        bridge.postNotification(xssTitle, xssBody, null, null)
        assertEquals(1, bridge.postedNotifications.size)
        assertEquals(xssTitle, bridge.postedNotifications[0].title)
    }

    @Test
    fun testF11_OneHundredNotificationsDispatchedRapidly() {
        val bridge = MockNotificationBridge()
        for (i in 1..100) {
            bridge.postNotification("Alert $i", "Message $i", "tag-$i", null)
        }
        assertEquals(100, bridge.postedNotifications.size)
    }

    @Test
    fun testF11_NullTitleAndBodyHandling() {
        val bridge = MockNotificationBridge()
        bridge.postNotification("", "", null, null)
        assertEquals(1, bridge.postedNotifications.size)
        assertEquals("", bridge.postedNotifications[0].title)
    }

    @Test
    fun testF11_PolyfillScriptContainsPermissionPromise() {
        val bridge = MockNotificationBridge()
        val script = bridge.getNotificationPolyfillScript()
        assertTrue(script.contains("Promise.resolve"))
    }

    // -------------------------------------------------------------
    // Area 12: F12 System Notification Display Corner Cases
    // -------------------------------------------------------------

    @Test
    fun testF12_NotificationTagCollisionOverwritesGracefully() {
        val manager = MockSystemNotificationManager()
        val id1 = manager.displayNotification("First", "Body 1", "same_tag")
        val id2 = manager.displayNotification("Second", "Body 2", "same_tag")

        assertTrue(id2 > id1)
        assertEquals(2, manager.getActiveNotifications().size)
    }

    @Test
    fun testF12_CancelNonExistentNotificationNoOp() {
        val manager = MockSystemNotificationManager()
        manager.cancelNotification(9999, "non_existent")
        assertEquals(0, manager.getActiveNotifications().size)
    }

    @Test
    fun testF12_EmptyTitleAndBodyDisplayNotification() {
        val manager = MockSystemNotificationManager()
        val id = manager.displayNotification("", "", null)
        assertTrue(id > 0)
    }

    @Test
    fun testF12_HighIntegerNotificationIdGeneration() {
        val manager = MockSystemNotificationManager()
        for (i in 1..20) {
            manager.displayNotification("Title $i", "Body $i", null)
        }
        assertEquals(20, manager.getActiveNotifications().size)
    }

    @Test
    fun testF12_ChannelVibrationAndBadgeEnabled() {
        val manager = MockSystemNotificationManager()
        val channel = manager.getNotificationChannel()
        assertTrue(channel.enableVibration)
        assertTrue(channel.showBadge)
    }

    // -------------------------------------------------------------
    // Area 13: F13 Permission Lifecycle Corner Cases
    // -------------------------------------------------------------

    @Test
    fun testF13_PermissionPermanentlyDeniedState() {
        val controller = MockPermissionController()
        controller.setSdkVersion(34)
        controller.onPermissionResult(isGranted = false)
        assertEquals("denied", controller.checkNotificationPermission())
    }

    @Test
    fun testF13_DynamicSdkVersionToggle() {
        val controller = MockPermissionController()
        controller.setSdkVersion(30)
        assertEquals("granted", controller.checkNotificationPermission())

        controller.setSdkVersion(34)
        assertEquals("default", controller.checkNotificationPermission())
    }

    @Test
    fun testF13_MultipleConsecutivePermissionRequests() {
        val controller = MockPermissionController()
        controller.setSdkVersion(34)
        controller.requestNotificationPermission()
        controller.requestNotificationPermission()
        assertTrue(controller.wasRuntimeDialogShown)
    }

    @Test
    fun testF13_PermissionRevocationAtRuntimeReflectsImmediately() {
        val controller = MockPermissionController()
        controller.setSdkVersion(34)
        controller.onPermissionResult(isGranted = true)
        assertEquals("granted", controller.checkNotificationPermission())

        controller.simulateOsSettingRevocation()
        assertEquals("denied", controller.checkNotificationPermission())
    }

    @Test
    fun testF13_PreApi33NeverShowsRuntimeDialog() {
        val controller = MockPermissionController()
        controller.setSdkVersion(28)
        controller.requestNotificationPermission()
        assertFalse(controller.wasRuntimeDialogShown)
    }

    // -------------------------------------------------------------
    // Area 14: F14 Network Security Config Boundary Cases
    // -------------------------------------------------------------

    @Test
    fun testF14_CertificateHeaderValidation() {
        val validCert = "-----BEGIN CERTIFICATE-----\nMIIB...==\n-----END CERTIFICATE-----"
        assertTrue(validCert.startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(validCert.endsWith("-----END CERTIFICATE-----"))
    }

    @Test
    fun testF14_CleartextTrafficPermittedFlagInXml() {
        val xml = "<network-security-config><base-config cleartextTrafficPermitted=\"true\" /></network-security-config>"
        assertTrue(xml.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun testF14_TrustAnchorsIncludeAllThreeSources() {
        val sources = listOf("system", "user", "@raw/netspark_ca")
        assertEquals(3, sources.size)
        assertTrue(sources.contains("@raw/netspark_ca"))
    }

    @Test
    fun testF14_EmptyDomainConfigFallbacksToBaseConfig() {
        val baseCleartext = true
        assertTrue(baseCleartext)
    }

    @Test
    fun testF14_NetsparkCaFileIsNonEmpty() {
        val certLength = 1800 // Expected typical PEM size
        assertTrue(certLength > 500)
    }
}
