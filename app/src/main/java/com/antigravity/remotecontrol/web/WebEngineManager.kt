package com.antigravity.remotecontrol.web

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebEngineManager : IWebEngineManager {

    private val WV_REGEX = Regex(";\\s*wv")
    private val VERSION_REGEX = Regex("Version/[0-9.]+\\s*")
    private const val DEFAULT_FALLBACK_UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun setupWebView(webView: WebView, isChildWindow: Boolean) {
        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(!isChildWindow)

        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.setSupportZoom(!isChildWindow)
        settings.builtInZoomControls = !isChildWindow
        settings.displayZoomControls = false

        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val defaultUa = settings.userAgentString
        settings.userAgentString = sanitizeUserAgent(defaultUa)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    override fun sanitizeUserAgent(defaultUserAgent: String): String {
        if (defaultUserAgent.isBlank()) {
            return DEFAULT_FALLBACK_UA
        }
        var sanitized = defaultUserAgent
            .replace(WV_REGEX, "")
            .replace(VERSION_REGEX, "")
            .replace(Regex("\\s+"), " ")
            .trim()

        sanitized = sanitized.replace("; )", ")").replace("( ", "(")
        return sanitized
    }

    override fun syncCookies(url: String) {
        CookieManager.getInstance().flush()
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }
}
