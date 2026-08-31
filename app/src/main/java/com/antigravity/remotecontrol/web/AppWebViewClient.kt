package com.antigravity.remotecontrol.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.antigravity.remotecontrol.nativebridge.NotificationBridge

class AppWebViewClient(
    private val isSslBypassEnabled: () -> Boolean = { true },
    private val onPageStartedCallback: ((String?) -> Unit)? = null,
    private val onPageFinishedCallback: ((String?) -> Unit)? = null,
    private val onErrorCallback: ((String) -> Unit)? = null
) : WebViewClient() {

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        if (isSslBypassEnabled()) {
            Log.w(TAG, "Bypassing SSL Certificate error: ${error?.primaryError} for URL: ${error?.url}")
            handler?.proceed()
        } else {
            super.onReceivedSslError(view, handler, error)
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.evaluateJavascript(NotificationBridge.getPolyfillScript(), null)
        onPageStartedCallback?.invoke(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        CookieManager.getInstance().flush()
        view?.evaluateJavascript(NotificationBridge.getPolyfillScript(), null)
        view?.evaluateJavascript(com.antigravity.remotecontrol.nativebridge.UIBridge.getScrollDetectionScript(), null)
        onPageFinishedCallback?.invoke(url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val description = error?.description?.toString() ?: "Connection failed"
            Log.e(TAG, "Main frame navigation error: $description (code: ${error?.errorCode})")
            onErrorCallback?.invoke(description)
        }
    }

    companion object {
        private const val TAG = "AppWebViewClient"
    }
}
