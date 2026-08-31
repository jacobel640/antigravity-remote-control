package com.antigravity.remotecontrol.web

import android.R as AndroidR
import android.app.Dialog
import android.content.Context
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import com.antigravity.remotecontrol.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator

class AuthPopupWindow(private val context: Context) {

    fun showAuthPopup(resultMsg: Message?): Boolean {
        if (resultMsg == null) {
            return false
        }

        val authDialog = Dialog(context, AndroidR.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen)
        authDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dialog_auth_popup, null)
        authDialog.setContentView(dialogView)

        authDialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val popupToolbar = dialogView.findViewById<MaterialToolbar>(R.id.popupToolbar)
        val popupProgressBar = dialogView.findViewById<LinearProgressIndicator>(R.id.popupProgressBar)
        val popupWebViewContainer = dialogView.findViewById<FrameLayout>(R.id.popupWebViewContainer)

        val childWebView = WebView(context)
        popupWebViewContainer.addView(
            childWebView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        WebEngineManager.setupWebView(childWebView, isChildWindow = true)

        childWebView.webViewClient = AppWebViewClient(
            onPageStartedCallback = {
                popupProgressBar.visibility = View.VISIBLE
            },
            onPageFinishedCallback = {
                popupProgressBar.visibility = View.GONE
                WebEngineManager.flushCookies()
            }
        )

        childWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                popupProgressBar.progress = newProgress
                popupProgressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onCloseWindow(window: WebView?) {
                super.onCloseWindow(window)
                authDialog.dismiss()
            }
        }

        popupToolbar.setNavigationOnClickListener {
            authDialog.dismiss()
        }

        authDialog.setOnDismissListener {
            popupWebViewContainer.removeAllViews()
            childWebView.destroy()
        }

        authDialog.show()

        val transport = resultMsg.obj as? WebView.WebViewTransport
        transport?.webView = childWebView
        resultMsg.sendToTarget()

        return true
    }
}
