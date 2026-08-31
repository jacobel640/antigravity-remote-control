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
 * Tier 4 Real-World Application Workloads & End-to-End User Journey Tests.
 * Simulates complete multi-step workflows from onboarding, proxy interception, Google authentication, file upload, to background push notifications.
 */
class Tier4RealWorldWorkloadsTest {

    /**
     * User Journey 1: First-Run Onboarding & Google OAuth Login Workflow
     * Steps: Fresh Install -> First-run URL Config -> Secure Storage -> Web Loading with Sanitized UA ->
     * Popup Google Sign-In -> Cross-Origin Cookie Capture -> Popup Dismissal -> Remote Control Dashboard.
     */
    @Test
    fun testJourney1_FirstRunOnboardingAndGoogleOAuthWorkflow() {
        val storage = MockPreferencesStorage()
        val presenter = UrlConfigDialogPresenter(storage)
        val webEngine = MockWebEngineManager()
        val popupManager = MockAuthPopupManager()
        val cookieManager = MockCookieManager()

        // Step 1: First launch detection
        assertTrue(presenter.shouldShowFirstRunDialog())

        // Step 2: User enters target server URL
        val targetInput = "https://remote.antigravity.internal:8443"
        val saveResult = presenter.onSaveClicked(targetInput)
        assertTrue(saveResult.isDismissed)
        assertEquals(targetInput, storage.getTargetUrl())
        assertFalse(presenter.shouldShowFirstRunDialog())

        // Step 3: Main WebView initialization with sanitized User-Agent
        val rawUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UQ1A.240205.004; wv) AppleWebKit/537.36 Version/4.0 Chrome/128.0.6613.88"
        val sanitizedUa = UserAgentSanitizerHelper.sanitizeUserAgent(rawUa)
        assertFalse(sanitizedUa.contains("; wv"))
        assertFalse(sanitizedUa.contains("Version/4.0"))

        val webSettings = webEngine.configureSettings(isChildWindow = false)
        assertTrue(webSettings.javaScriptEnabled)
        assertTrue(webSettings.domStorageEnabled)

        // Step 4: User clicks "Sign in with Google", triggering multi-window popup
        val popupCreated = popupManager.onCreateWindow(isDialog = true, isUserGesture = true)
        assertTrue(popupCreated)
        assertTrue(popupManager.isDialogShowing)

        // Step 5: Child popup completes authentication and sets session cookies
        cookieManager.setAcceptThirdPartyCookies(true)
        cookieManager.setCookie("https://accounts.google.com", "ACCOUNT_CHOOSER=user@gmail.com; Secure")
        cookieManager.setCookie(targetInput, "SESSION_ID=sess_prod_998877; Secure; HttpOnly")
        cookieManager.flush()
        assertTrue(cookieManager.isFlushed)

        // Step 6: OAuth flow completes -> window.close() invoked
        popupManager.onCloseWindow()
        assertFalse(popupManager.isDialogShowing)
        assertTrue(popupManager.wasChildViewCleanedUp)

        // Step 7: Main WebView now has valid authenticated session cookie
        val activeCookie = cookieManager.getCookie(targetInput)
        assertNotNull(activeCookie)
        assertTrue(activeCookie!!.contains("SESSION_ID=sess_prod_998877"))
    }

    /**
     * User Journey 2: College/Corporate Interception Proxy Network Traversal
     * Steps: App connects through proxy (Netspark) -> SSL_UNTRUSTED intercepted -> Automatic proceed() ->
     * Subresources bypassed -> WebSocket connection established -> Web UI operational.
     */
    @Test
    fun testJourney2_InterceptionProxyNetworkTraversal() {
        val webClient = MockAppWebViewClient()
        val baseUrl = "https://college-lab.antigravity.internal"

        // Step 1: Main document request hits proxy MITM cert
        val docHandler = MockSslErrorHandler()
        val docError = MockSslError(MockSslError.SSL_UNTRUSTED, "$baseUrl/")
        webClient.onReceivedSslError(docHandler, docError)
        assertTrue(docHandler.wasProceedCalled)

        // Step 2: Static assets (JS/CSS/Fonts) hit proxy cert
        val assetUrls = listOf(
            "$baseUrl/static/bundle.js",
            "$baseUrl/static/theme.css",
            "$baseUrl/static/logo.svg"
        )
        for (assetUrl in assetUrls) {
            val assetHandler = MockSslErrorHandler()
            val assetError = MockSslError(MockSslError.SSL_UNTRUSTED, assetUrl)
            webClient.onReceivedSslError(assetHandler, assetError)
            assertTrue(assetHandler.wasProceedCalled)
        }

        // Step 3: Real-time SSE / Streaming API hits proxy cert
        val streamHandler = MockSslErrorHandler()
        val streamError = MockSslError(MockSslError.SSL_UNTRUSTED, "$baseUrl/api/stream")
        webClient.onReceivedSslError(streamHandler, streamError)
        assertTrue(streamHandler.wasProceedCalled)

        // Total 5 bypassed SSL events handled cleanly with zero aborts
        assertEquals(5, webClient.bypassedSslErrorCount)
    }

    /**
     * User Journey 3: Interactive Chat Session & Multi-File Attachment
     * Steps: Chat opened -> File picker requested -> Multi-file selection delivered ->
     * Subsequent file picker requested -> Cancel button pressed -> Deadlock prevented.
     */
    @Test
    fun testJourney3_InteractiveChatAndMultiFileAttachment() {
        val fileChooser = MockNativeFileChooserHandler()

        // Step 1: User attaches 2 files to chat
        var deliveredUris: Array<String>? = null
        fileChooser.prepareChooser({ uris -> deliveredUris = uris }, allowMultiple = true)

        val selectedFiles = arrayOf(
            "content://com.android.providers.media/documents/agent_log.txt",
            "content://com.android.providers.media/documents/screenshot.png"
        )
        fileChooser.onFileSelectionResult(resultOk = true, selectedUris = selectedFiles)

        assertNotNull(deliveredUris)
        assertEquals(2, deliveredUris!!.size)
        assertEquals("content://com.android.providers.media/documents/agent_log.txt", deliveredUris!![0])
        assertFalse(fileChooser.hasPendingCallback())

        // Step 2: User opens file chooser again but cancels
        var secondDelivery: Array<String>? = arrayOf("previous")
        fileChooser.prepareChooser({ uris -> secondDelivery = uris }, allowMultiple = false)
        fileChooser.onFileSelectionResult(resultOk = false, selectedUris = null)

        assertNull("Callback must receive null on cancel to release WebView thread lock", secondDelivery)
        assertFalse(fileChooser.hasPendingCallback())

        // Step 3: Third attempt immediately works without thread deadlock
        var thirdDelivery: Array<String>? = null
        fileChooser.prepareChooser({ uris -> thirdDelivery = uris }, allowMultiple = false)
        fileChooser.onFileSelectionResult(resultOk = true, selectedUris = arrayOf("content://documents/third.json"))
        assertNotNull(thirdDelivery)
        assertEquals(1, thirdDelivery!!.size)
    }

    /**
     * User Journey 4: Background Message Event & Native Push Notification Lifecycle
     * Steps: Web app requests notification permission -> API 33+ permission granted -> Remote agent fires notification ->
     * JS polyfill routes to bridge -> System notification channel displays high priority notification -> User clicks.
     */
    @Test
    fun testJourney4_BackgroundMessageAndNativeNotificationLifecycle() {
        val jsBridge = MockNotificationBridge()
        val permissionController = MockPermissionController()
        val notificationManager = MockSystemNotificationManager()

        // Step 1: SDK 34 runtime permission check
        permissionController.setSdkVersion(34)
        assertEquals("default", permissionController.checkNotificationPermission())

        // Step 2: Web app invokes Notification.requestPermission()
        jsBridge.requestPermission()
        permissionController.requestNotificationPermission()
        assertTrue(permissionController.wasRuntimeDialogShown)

        // Step 3: User approves notification dialog
        permissionController.onPermissionResult(isGranted = true)
        jsBridge.setPermissionGranted(true)
        assertEquals("granted", jsBridge.getPermissionStatus())

        // Step 4: Web background task posts notification
        jsBridge.postNotification(
            title = "Task Run: Completed",
            body = "12 tests passed, 0 failures.",
            tag = "task-run-456",
            iconUrl = "https://server.com/icon.png"
        )
        assertEquals(1, jsBridge.postedNotifications.size)

        // Step 5: System notification posted
        val item = jsBridge.postedNotifications[0]
        val notifId = notificationManager.displayNotification(item.title, item.body, item.tag)
        assertTrue(notifId > 0)

        val active = notificationManager.getActiveNotifications()
        assertEquals(1, active.size)
        assertEquals("Task Run: Completed", active[0].title)
        assertEquals("12 tests passed, 0 failures.", active[0].body)
        assertTrue(active[0].hasPendingIntent)
    }

    /**
     * User Journey 5: Hardware Keystore Reset & Disaster Recovery Lifecycle
     * Steps: Device lockscreen reset -> Decryption fails (AEADBadTagException) -> Self-healing clears corrupted state ->
     * App recovers to first-run setup -> Re-enters server URL -> Saves with new MasterKey -> Seamless recovery.
     */
    @Test
    fun testJourney5_HardwareKeystoreResetAndDisasterRecovery() {
        val secureStorage = MockSecurePreferencesManager()
        secureStorage.setTargetUrl("https://production-server.internal")
        assertTrue(secureStorage.hasConfiguredUrl())

        // Step 1: Disaster event — lock screen changed, AndroidKeyStore key invalidated
        secureStorage.simulateKeystoreCorruption()

        // Step 2: App launch detects corruption, self-heals by resetting corrupted entries
        val restoredUrl = secureStorage.getTargetUrl()
        assertNull(restoredUrl)
        assertFalse(secureStorage.hasConfiguredUrl())

        // Step 3: UI gracefully presents onboarding dialog without throwing crash
        val storageBridge = object : MockPreferencesStorage() {
            override fun hasConfiguredUrl(): Boolean = secureStorage.hasConfiguredUrl()
            override fun setTargetUrl(url: String): Boolean = secureStorage.setTargetUrl(url)
            override fun getTargetUrl(): String? = secureStorage.getTargetUrl()
        }
        val presenter = UrlConfigDialogPresenter(storageBridge)
        assertTrue(presenter.shouldShowFirstRunDialog())

        // Step 4: User enters server URL again
        val validation = UrlValidator.validateAndNormalize("https://production-server.internal")
        assertTrue(validation.isValid)

        val reSaveResult = presenter.onSaveClicked("https://production-server.internal")
        assertTrue(reSaveResult.isDismissed)

        // Step 5: Verified securely saved under fresh key
        assertEquals("https://production-server.internal", secureStorage.getTargetUrl())
        assertTrue(secureStorage.hasConfiguredUrl())
    }
}
