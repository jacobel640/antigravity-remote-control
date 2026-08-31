package com.antigravity.remotecontrol

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.antigravity.remotecontrol.nativebridge.NativeFileChooser
import com.antigravity.remotecontrol.nativebridge.NotificationBridge
import com.antigravity.remotecontrol.security.SecurePreferencesManager
import com.antigravity.remotecontrol.ui.UrlConfigDialog
import com.antigravity.remotecontrol.web.AppWebChromeClient
import com.antigravity.remotecontrol.web.AppWebViewClient
import com.antigravity.remotecontrol.web.WebEngineManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var securePreferences: SecurePreferencesManager
    private lateinit var nativeFileChooser: NativeFileChooser
    private lateinit var notificationBridge: NotificationBridge

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var webContainer: android.widget.FrameLayout
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var webView: com.antigravity.remotecontrol.web.NestedScrollingWebView
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var btnRetry: MaterialButton
    private lateinit var fabSecurity: com.google.android.material.floatingactionbutton.FloatingActionButton

    private var isJsSwipeEnabled = false

    /** Frames that currently have the settings button placed in the site's own top bar. */
    private val framesWithSettingsButton = mutableSetOf<String>()

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val showFallbackButton = Runnable {
        if (framesWithSettingsButton.isEmpty()) {
            fabSecurity.alpha = 1f
            fabSecurity.visibility = View.VISIBLE
        }
    }

    private val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            nativeFileChooser.onFileChooserResult(result.resultCode, result.data)
        }

    private val notificationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updateJsNotificationPermission(if (isGranted) "granted" else "denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        securePreferences = SecurePreferencesManager(this)
        nativeFileChooser = NativeFileChooser(filePickerLauncher)
        notificationBridge = NotificationBridge(this) {
            requestNotificationPermission()
        }

        bindViews()
        setupWebView()
        setupListeners()
        setupBackNavigation()
        setupWindowInsets()

        loadConfiguredUrl()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.coordinatorLayout)) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            // Combine system bars and cutout for safe areas
            val topInset = kotlin.math.max(systemBars.top, displayCutout.top)
            val bottomInset = kotlin.math.max(systemBars.bottom, displayCutout.bottom)
            val leftInset = kotlin.math.max(systemBars.left, displayCutout.left)
            val rightInset = kotlin.math.max(systemBars.right, displayCutout.right)

            // Apply padding to the web container to ensure content stays within safe areas
            webContainer.updatePadding(
                top = topInset,
                bottom = bottomInset,
                left = leftInset,
                right = rightInset
            )
            
            // Progress bar stays at the very top of the safe area
            progressBar.updatePadding(top = topInset)
            
            // Floating button adjustments
            val fabParams = fabSecurity.layoutParams as android.view.ViewGroup.MarginLayoutParams
            fabParams.topMargin = topInset + (16 * resources.displayMetrics.density).toInt()
            fabParams.leftMargin = leftInset + (16 * resources.displayMetrics.density).toInt()
            fabSecurity.layoutParams = fabParams

            windowInsets
        }
    }

    override fun onResume() {
        super.onResume()
        // Visibility is owned by updateFallbackButton(): the native button is only for
        // when the page could not host the settings button itself.
        updateFallbackButton()
    }

    private fun bindViews() {
        progressBar = findViewById(R.id.progressBar)
        webContainer = findViewById(R.id.webContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        webView = findViewById(R.id.webView)
        layoutError = findViewById(R.id.layoutError)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        btnRetry = findViewById(R.id.btnRetry)
        fabSecurity = findViewById(R.id.fabSecurity)

        fabSecurity.setOnClickListener {
            showUrlConfigDialog(isFirstRun = false)
        }
    }

    private fun setupWebView() {
        WebEngineManager.setupWebView(webView)

        swipeRefreshLayout.setOnRefreshListener {
            hideErrorState()
            webView.reload()
        }

        // Stays enabled for the whole session. Toggling isEnabled used to race the touch
        // stream: SwipeRefreshLayout only accepts a nested scroll at ACTION_DOWN, so a
        // layout that was disabled at that moment could never receive the pull, while one
        // that happened to be enabled kept the connection open for the entire gesture.
        // The actual decision now lives in NestedScrollingWebView, which withholds the
        // pull-down delta unless the gesture qualifies.
        swipeRefreshLayout.isEnabled = true

        setupScrollDetection()

        // Native callback: the FINAL authority for SwipeRefreshLayout.
        // Returns true if the child CAN scroll up (thus blocking refresh).
        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            webView.canScrollVertically(-1)
        }

        webView.webViewClient = AppWebViewClient(
            isSslBypassEnabled = { securePreferences.isSslBypassEnabled() },
            onPageStartedCallback = {
                progressBar.visibility = View.VISIBLE
                hideErrorState()
            },
            onPageFinishedCallback = {
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            },
            onErrorCallback = { errorMessage ->
                showErrorState(errorMessage)
                swipeRefreshLayout.isRefreshing = false
            }
        )

        webView.webChromeClient = AppWebChromeClient(
            context = this,
            fileChooser = nativeFileChooser,
            onProgressChangedCallback = { progress ->
                progressBar.progress = progress
                progressBar.visibility = if (progress < 100) View.VISIBLE else View.GONE
            }
        )

        webView.addJavascriptInterface(
            notificationBridge,
            NotificationBridge.JAVASCRIPT_OBJ_NAME
        )
    }

    private fun setupListeners() {
        btnRetry.setOnClickListener {
            hideErrorState()
            loadConfiguredUrl()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun loadConfiguredUrl() {
        val targetUrl = securePreferences.getTargetUrl() ?: DEFAULT_URL
        hideErrorState()
        webView.loadUrl(targetUrl)
    }

    private fun showErrorState(message: String) {
        tvErrorMessage.text = message
        layoutError.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progressBar.visibility = View.GONE
    }

    private fun hideErrorState() {
        layoutError.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    /**
     * Installs the page-side scroll detector.
     *
     * The Antigravity UI runs inside an iframe, and `addJavascriptInterface` /
     * `evaluateJavascript` only ever reach the main frame — which is why a main-frame-only
     * detector reported "at top" forever no matter how far the content had scrolled.
     * `addDocumentStartJavaScript` runs the detector in every frame and
     * `addWebMessageListener` gives subframes a way to answer.
     */
    private fun setupScrollDetection() {
        // Any frame that says it is scrolled vetoes the pull, so the combined answer is
        // the AND over frames. Entries expire so a frame that goes away cannot veto forever.
        val frameStates = HashMap<String, Pair<Boolean, Long>>()

        fun applyReport(frameKey: String, atTop: Boolean) {
            val now = android.os.SystemClock.uptimeMillis()
            frameStates[frameKey] = atTop to now
            var combined = true
            val entries = frameStates.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                if (now - entry.value.second > FRAME_REPORT_STALE_MS) {
                    entries.remove()
                } else if (!entry.value.first) {
                    combined = false
                }
            }
            isJsSwipeEnabled = combined
            webView.updateBridgeConfirmation(combined, now)
        }

        val script = com.antigravity.remotecontrol.nativebridge.UIBridge.getScrollDetectionScript()
        val allOrigins = setOf("*")

        val canListen = androidx.webkit.WebViewFeature
            .isFeatureSupported(androidx.webkit.WebViewFeature.WEB_MESSAGE_LISTENER)
        val canInjectEverywhere = androidx.webkit.WebViewFeature
            .isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)

        if (canListen) {
            // The only thing a frame can say here is "at top" or "not at top", and the
            // payload is validated below, so accepting every origin costs nothing beyond
            // letting an iframe influence whether a pull gesture may refresh.
            androidx.webkit.WebViewCompat.addWebMessageListener(
                webView,
                com.antigravity.remotecontrol.nativebridge.UIBridge.MESSAGE_OBJ_NAME,
                allOrigins
            ) { _, message, sourceOrigin, isMainFrame, _ ->
                val payload = message.data
                val bridge = com.antigravity.remotecontrol.nativebridge.UIBridge
                val key = if (isMainFrame) "main" else "sub:$sourceOrigin"
                when {
                    payload == "1" || payload == "0" -> {
                        applyReport(key, payload == bridge.MSG_AT_TOP)
                    }
                    payload == bridge.MSG_OPEN_SETTINGS -> {
                        // A command, not a status, so it needs a guard. Origin is not usable
                        // as one: the app's own UI is served from a different host than the
                        // configured URL. Requiring a fresh touch is, and it is the guard
                        // that matches the actual risk — a frame acting on its own.
                        if (webView.hasRecentUserGesture(SETTINGS_GESTURE_WINDOW_MS)) {
                            showUrlConfigDialog(isFirstRun = false)
                        }
                    }
                    payload != null && payload.startsWith(bridge.MSG_BUTTON_PREFIX) -> {
                        // "B:0" is the only state that means the page could not host the
                        // button; "B:2" means it chose not to on this screen, which is not
                        // a reason to bring the native one back.
                        onSettingsButtonPlaced(key, !payload.startsWith("B:0"))
                    }
                }
            }
        } else {
            // Fallback: main frame only. Subframe scrolling stays invisible, so the
            // detector is degraded but never wrong in the dangerous direction.
            val uiBridge = com.antigravity.remotecontrol.nativebridge.UIBridge { atTop, _ ->
                applyReport("main", atTop)
            }
            webView.addJavascriptInterface(
                uiBridge,
                com.antigravity.remotecontrol.nativebridge.UIBridge.JAVASCRIPT_OBJ_NAME
            )
        }

        if (canInjectEverywhere) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(webView, script, allOrigins)
        }
        // When document-start injection is unavailable, AppWebViewClient's onPageFinished
        // injection is the only path, and it reaches the main frame alone.
    }

    /**
     * Tracks whether the page managed to put a settings button in its own top bar.
     *
     * The native floating button is the fallback for exactly this: if Antigravity's markup
     * changes and no frame can place the button, the user must still be able to reach the
     * URL and SSL settings.
     */
    private fun onSettingsButtonPlaced(frameKey: String, handled: Boolean) {
        if (handled) {
            framesWithSettingsButton.add(frameKey)
        } else {
            framesWithSettingsButton.remove(frameKey)
        }
        updateFallbackButton()
    }

    private fun updateFallbackButton() {
        uiHandler.removeCallbacks(showFallbackButton)
        if (framesWithSettingsButton.isEmpty()) {
            // Frames report independently and the shell frame has no top bar of its own,
            // so a "not placed" almost always arrives before the real frame's "placed".
            // Waiting keeps the native button from flashing on every load.
            uiHandler.postDelayed(showFallbackButton, FALLBACK_BUTTON_DELAY_MS)
        } else {
            fabSecurity.visibility = View.GONE
        }
    }

    private fun showUrlConfigDialog(isFirstRun: Boolean) {
        val dialog = UrlConfigDialog(this, securePreferences)
        dialog.show(
            isFirstRun = isFirstRun,
            onUrlSaved = { savedUrl ->
                hideErrorState()
                webView.loadUrl(savedUrl)
            }
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!notificationBridge.hasNotificationPermission()) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun updateJsNotificationPermission(status: String) {
        val script = "if (window.Notification) { window.Notification.permission = '$status'; }"
        webView.evaluateJavascript(script, null)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(showFallbackButton)
        nativeFileChooser.cancelPendingCallback()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        /**
         * How long a frame's scroll report stays authoritative. Frames re-report every
         * 250ms, so anything older than this belongs to a frame that has gone away and
         * must not keep vetoing pull-to-refresh.
         */
        private const val FRAME_REPORT_STALE_MS = 1500L

        /** Grace period before falling back to the native settings button. */
        private const val FALLBACK_BUTTON_DELAY_MS = 2500L

        private const val DEFAULT_URL = "https://antigravity.google.com/"

        /** How fresh a touch must be for a page-issued settings command to be honoured. */
        private const val SETTINGS_GESTURE_WINDOW_MS = 1500L
    }
}
