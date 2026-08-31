package com.antigravity.remotecontrol.nativebridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher

class NativeFileChooser(
    var launcher: ActivityResultLauncher<Intent>? = null
) : INativeFileChooser {

    private var pendingCallback: ValueCallback<Array<Uri>>? = null

    override fun handleShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean {
        cancelPendingCallback()
        pendingCallback = filePathCallback

        return try {
            val intent = fileChooserParams.createIntent() ?: createFallbackFileChooserIntent(fileChooserParams)
            val currentLauncher = launcher
            if (currentLauncher != null) {
                currentLauncher.launch(intent)
                true
            } else {
                Log.e(TAG, "File picker ActivityResultLauncher is not initialized")
                cancelPendingCallback()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file chooser intent", e)
            cancelPendingCallback()
            false
        }
    }

    override fun onFileChooserResult(resultCode: Int, data: Intent?) {
        val callback = pendingCallback ?: return
        pendingCallback = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onReceiveValue(null)
            return
        }

        val results: Array<Uri>? = try {
            val clipData = data.clipData
            val dataUri = data.data

            when {
                clipData != null && clipData.itemCount > 0 -> {
                    Array(clipData.itemCount) { index -> clipData.getItemAt(index).uri }
                }
                dataUri != null -> {
                    arrayOf(dataUri)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing file chooser result", e)
            null
        }

        callback.onReceiveValue(results)
    }

    fun cancelPendingCallback() {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = null
    }

    private fun createFallbackFileChooserIntent(params: WebChromeClient.FileChooserParams): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        val acceptTypes = params.acceptTypes?.filter { it.isNotBlank() }
        if (!acceptTypes.isNullOrEmpty()) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
            if (acceptTypes.size == 1) {
                intent.type = acceptTypes[0]
            }
        }

        if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        return Intent.createChooser(intent, "Select File")
    }

    companion object {
        private const val TAG = "NativeFileChooser"
    }
}
