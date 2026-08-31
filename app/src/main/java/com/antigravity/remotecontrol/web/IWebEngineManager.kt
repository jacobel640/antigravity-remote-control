package com.antigravity.remotecontrol.web

import android.webkit.WebView

interface IWebEngineManager {

    fun setupWebView(webView: WebView, isChildWindow: Boolean = false)

    fun sanitizeUserAgent(defaultUserAgent: String): String

    fun syncCookies(url: String)
}
