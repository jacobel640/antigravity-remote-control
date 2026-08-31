package com.antigravity.remotecontrol.web

import android.content.Context
import android.net.Uri
import android.os.Message
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.antigravity.remotecontrol.nativebridge.INativeFileChooser

class AppWebChromeClient(
    private val context: Context,
    private val fileChooser: INativeFileChooser? = null,
    private val onProgressChangedCallback: ((Int) -> Unit)? = null,
    private val onWindowCloseCallback: (() -> Unit)? = null
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback?.invoke(newProgress)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val popupWindow = AuthPopupWindow(context)
        return popupWindow.showAuthPopup(resultMsg)
    }

    override fun onCloseWindow(window: WebView?) {
        super.onCloseWindow(window)
        window?.destroy()
        onWindowCloseCallback?.invoke()
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        if (webView != null && filePathCallback != null && fileChooserParams != null && fileChooser != null) {
            return fileChooser.handleShowFileChooser(webView, filePathCallback, fileChooserParams)
        }
        filePathCallback?.onReceiveValue(null)
        return false
    }
}
