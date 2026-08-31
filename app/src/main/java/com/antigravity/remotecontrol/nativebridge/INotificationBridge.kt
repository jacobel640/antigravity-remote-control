package com.antigravity.remotecontrol.nativebridge

import android.webkit.JavascriptInterface

interface INotificationBridge {

    @JavascriptInterface
    fun postNotification(title: String, body: String, tag: String?, iconUrl: String?)

    @JavascriptInterface
    fun getPermissionStatus(): String

    @JavascriptInterface
    fun requestPermission()
}
