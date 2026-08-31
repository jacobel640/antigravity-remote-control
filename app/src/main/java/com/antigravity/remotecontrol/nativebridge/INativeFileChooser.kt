package com.antigravity.remotecontrol.nativebridge

import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

interface INativeFileChooser {

    fun handleShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean

    fun onFileChooserResult(resultCode: Int, data: Intent?)
}
