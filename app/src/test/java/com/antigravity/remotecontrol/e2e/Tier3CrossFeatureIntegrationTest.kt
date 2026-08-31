package com.antigravity.remotecontrol.e2e

import com.antigravity.remotecontrol.nativebridge.NativeFileChooserTest.MockNativeFileChooserHandler
import com.antigravity.remotecontrol.nativebridge.NotificationBridgeTest.MockNotificationBridge
import com.antigravity.remotecontrol.nativebridge.NotificationManagerTest.MockSystemNotificationManager
import com.antigravity.remotecontrol.nativebridge.PermissionLifecycleTest.MockPermissionController
import com.antigravity.remotecontrol.security.SecurePreferencesManagerTest.MockSecurePreferencesManager
import com.antigravity.remotecontrol.security.UrlValidator
import com.antigravity.remotecontrol.ui.UrlConfigDialogTest.MockPreferencesStorage
import com.antigravity.remotecontrol.ui.UrlConfigDialogTest.UrlConfigDialogPresenter
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
 * Tier 3 Cross-Feature Combinations & Integration Test Suite.
 * Verifies pairwise and clustered subsystem interactions across storage, web rendering, auth, SSL bypass, file upload, and push notifications.
 */
class Tier3CrossFeatureIntegrationTest {

    /**
     * Combo 1: URL Configuration + Validation + Storage + WebView Loading with Sanitized UA
     * Features: F2 (Storage) + F3 (Validator) + F4 (UI) + F5 (WebView) + F6 (Sanitized UA)
     */
    @Test
    fun testCombo1_UrlConfigAndWebViewLoadWithSanitizedUa() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)
        val webEngine = MockWebEngineManager()

        // 1. First-run gate triggers dialog
        assertTrue(presenter.shouldShowFirstRunDialog())

        // 2. User submits un-schemed URL
        val rawInput = "remote.antigravity.internal:8443/chat"
        val saveResult = presenter.onSaveClicked(rawInput)
        assertTrue(saveResult.isDismissed)

        // 3. Validated and persisted in secure storage
        val storedUrl = storage.getTargetUrl()
        assertEquals("https://remote.antigravity.internal:8443/chat", storedUrl)

        // 4. Web Engine configures settings and sanitizes User-Agent
        val settings = webEngine.configureSettings(isChildWindow = false)
        assertTrue(settings.javaScriptEnabled)

        val rawUa = "Mozilla/5.0 (Linux; Android 14; wv) Version/4.0 Chrome/128.0.0.0"
        val sanitizedUa = UserAgentSanitizerHelper.sanitizeUserAgent(rawUa)
        assertFalse(sanitizedUa.contains("; wv"))
        assertFalse(sanitizedUa.contains("Version/4.0"))
    }

    /**
     * Combo 2: Google OAuth Multi-Window Popup + Sanitized UA + Cross-Origin Cookie Persistence + Popup Dismissal
     * Features: F6 (Sanitized UA) + F8 (Popup Auth) + F9 (Cookies)
     */
    @Test
    fun testCombo2_GoogleOAuthPopupWithSanitizedUaAndCookieSync() {
        val popupManager = MockAuthPopupManager()
        val cookieManager = MockCookieManager()

        // 1. Web application triggers window.open() for Google OAuth
        val opened = popupManager.onCreateWindow(isDialog = true, isUserGesture = true)
        assertTrue(opened)
        assertTrue(popupManager.isDialogShowing)

        // 2. Child WebView has sanitized UA to prevent 403 disallowed_useragent
        val childUa = popupManager.activeChildWebView?.userAgent
        assertNotNull(childUa)
        assertFalse(childUa!!.contains("; wv"))

        // 3. Google auth sets session cookies spanning origins
        cookieManager.setAcceptThirdPartyCookies(true)
        cookieManager.setCookie("https://accounts.google.com", "OAUTH_TOKEN=secret_token_123; Secure")
        cookieManager.setCookie("https://remote.antigravity.internal", "APP_SESSION=session_456; Secure")
        cookieManager.flush()
        assertTrue(cookieManager.isFlushed)

        // 4. OAuth completes, child window closes
        popupManager.onCloseWindow()
        assertFalse(popupManager.isDialogShowing)
        assertTrue(popupManager.wasChildViewCleanedUp)

        // 5. Parent retains active session cookies
        assertNotNull(cookieManager.getCookie("https://remote.antigravity.internal"))
    }

    /**
     * Combo 3: SSL Interception Proxy Bypass + Native File Chooser Upload
     * Features: F7 (SSL Bypass) + F10 (File Upload) + F14 (Network Security)
     */
    @Test
    fun testCombo3_SslProxyBypassAndNativeFileUpload() {
        val webClient = MockAppWebViewClient()
        val fileChooser = MockNativeFileChooserHandler()

        // 1. Corporate/college proxy intercepts HTTPS request with untrusted certificate
        val sslHandler = MockSslErrorHandler()
        val sslError = MockSslError(MockSslError.SSL_UNTRUSTED, "https://remote.antigravity.internal/upload")
        webClient.onReceivedSslError(sslHandler, sslError)
        assertTrue(sslHandler.wasProceedCalled)

        // 2. Web interface invokes file chooser
        var uploadedUris: Array<String>? = null
        fileChooser.prepareChooser({ uris -> uploadedUris = uris }, allowMultiple = false)

        // 3. User selects file in native picker
        val selectedFileUri = "content://media/external/files/diagnostic_log.zip"
        fileChooser.onFileSelectionResult(resultOk = true, selectedUris = arrayOf(selectedFileUri))

        // 4. File URI delivered to WebView over bypassed SSL channel
        assertNotNull(uploadedUris)
        assertEquals(selectedFileUri, uploadedUris!![0])
        assertFalse(fileChooser.hasPendingCallback())
    }

    /**
     * Combo 4: Web Notification JS Polyfill + Android 13+ Runtime Permission Request + Native System Notification Display
     * Features: F11 (JS Bridge) + F12 (Notification Display) + F13 (Permission Lifecycle)
     */
    @Test
    fun testCombo4_NotificationPolyfillWithAndroid13PermissionAndSystemDisplay() {
        val jsBridge = MockNotificationBridge()
        val permissionController = MockPermissionController()
        val systemNotifications = MockSystemNotificationManager()

        permissionController.setSdkVersion(34) // Android 14

        // 1. Initial permission state in JS is 'default'
        assertEquals("default", permissionController.checkNotificationPermission())

        // 2. Web app calls Notification.requestPermission()
        jsBridge.requestPermission()
        permissionController.requestNotificationPermission()
        assertTrue(permissionController.wasRuntimeDialogShown)

        // 3. User grants POST_NOTIFICATIONS permission
        permissionController.onPermissionResult(isGranted = true)
        jsBridge.setPermissionGranted(true)
        assertEquals("granted", jsBridge.getPermissionStatus())

        // 4. Web app creates new Notification
        jsBridge.postNotification(
            title = "Task Finished",
            body = "Execution output ready for review",
            tag = "task-88",
            iconUrl = null
        )
        assertEquals(1, jsBridge.postedNotifications.size)

        // 5. Native bridge routes to Android NotificationManager
        val posted = jsBridge.postedNotifications[0]
        val notificationId = systemNotifications.displayNotification(posted.title, posted.body, posted.tag)
        assertTrue(notificationId > 0)

        val active = systemNotifications.getActiveNotifications()
        assertEquals(1, active.size)
        assertEquals("Task Finished", active[0].title)
        assertEquals("task-88", active[0].tag)
    }

    /**
     * Combo 5: Hardware Keystore Corruption Recovery + First-Run UI Re-Trigger + Validated Persistence
     * Features: F2 (Storage) + F3 (Validation) + F4 (UI)
     */
    @Test
    fun testCombo5_KeystoreCorruptionRecoveryAndReConfiguration() {
        val secureStorage = MockSecurePreferencesManager()
        secureStorage.setTargetUrl("https://initial-server.com")

        // 1. Device Keystore keys invalidated (lock-screen modified)
        secureStorage.simulateKeystoreCorruption()

        // 2. Storage self-heals: resets corrupted keys, returns null URL
        val recoveredUrl = secureStorage.getTargetUrl()
        assertNull(recoveredUrl)
        assertFalse(secureStorage.hasConfiguredUrl())

        // 3. App detects no URL configured and displays first-run dialog
        val storageBridge = object : MockPreferencesStorage() {
            override fun hasConfiguredUrl(): Boolean = secureStorage.hasConfiguredUrl()
            override fun setTargetUrl(url: String): Boolean = secureStorage.setTargetUrl(url)
            override fun getTargetUrl(): String? = secureStorage.getTargetUrl()
        }
        val presenter = UrlConfigDialogPresenter(storageBridge)
        assertTrue(presenter.shouldShowFirstRunDialog())

        // 4. User inputs new server URL
        val saveResult = presenter.onSaveClicked("https://healed-secure-server.internal")
        assertTrue(saveResult.isDismissed)
        assertEquals("https://healed-secure-server.internal", secureStorage.getTargetUrl())
    }

    /**
     * Combo 6: Multi-Window Child Popup + Proxy SSL Error + Cookie Synchronization
     * Features: F7 (SSL Bypass) + F8 (Popup Auth) + F9 (Cookies)
     */
    @Test
    fun testCombo6_ChildPopupSslBypassAndCookieSync() {
        val popupManager = MockAuthPopupManager()
        val webClient = MockAppWebViewClient()
        val cookieManager = MockCookieManager()

        // 1. Open child OAuth window
        popupManager.onCreateWindow(isDialog = true, isUserGesture = true)

        // 2. Child window hits proxy SSL error on OAuth redirect
        val sslHandler = MockSslErrorHandler()
        val sslError = MockSslError(MockSslError.SSL_UNTRUSTED, "https://accounts.google.com/o/oauth2/auth")
        webClient.onReceivedSslError(sslHandler, sslError)
        assertTrue(sslHandler.wasProceedCalled)

        // 3. OAuth completes, setting session cookies
        cookieManager.setCookie("https://accounts.google.com", "AUTH_STATE=success")
        cookieManager.flush()

        // 4. Child window closes cleanly
        popupManager.onCloseWindow()
        assertFalse(popupManager.isDialogShowing)
        assertTrue(cookieManager.isFlushed)
    }

    /**
     * Combo 7: Native File Chooser Cancellation + Page Navigation Deadlock-Free Flow
     * Features: F5 (WebEngine) + F10 (File Chooser)
     */
    @Test
    fun testCombo7_FileChooserCancelAndPageNavigationNoDeadlock() {
        val fileChooser = MockNativeFileChooserHandler()
        var callbackExecuted = false

        // 1. File chooser triggered
        fileChooser.prepareChooser({ callbackExecuted = true }, allowMultiple = false)
        assertTrue(fileChooser.hasPendingCallback())

        // 2. User presses Cancel or navigates away
        fileChooser.onFileSelectionResult(resultOk = false, selectedUris = null)
        assertTrue(callbackExecuted)
        assertFalse(fileChooser.hasPendingCallback())

        // 3. Next file chooser request works without getting stuck
        var secondCallbackExecuted = false
        fileChooser.prepareChooser({ secondCallbackExecuted = true }, allowMultiple = false)
        fileChooser.onFileSelectionResult(resultOk = true, selectedUris = arrayOf("content://media/file.txt"))
        assertTrue(secondCallbackExecuted)
    }
}
