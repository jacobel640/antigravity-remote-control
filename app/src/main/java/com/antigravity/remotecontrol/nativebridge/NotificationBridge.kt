package com.antigravity.remotecontrol.nativebridge

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antigravity.remotecontrol.MainActivity
import com.antigravity.remotecontrol.R
import com.antigravity.remotecontrol.RemoteControlApplication

class NotificationBridge(
    private val context: Context,
    private val onPermissionRequested: (() -> Unit)? = null
) : INotificationBridge {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    override fun postNotification(title: String, body: String, tag: String?, iconUrl: String?) {
        if (title.isBlank()) {
            return
        }
        mainHandler.post {
            displaySystemNotification(title, body, tag, iconUrl)
        }
    }

    @JavascriptInterface
    override fun getPermissionStatus(): String {
        return if (hasNotificationPermission()) {
            "granted"
        } else {
            "denied"
        }
    }

    @JavascriptInterface
    override fun requestPermission() {
        mainHandler.post {
            onPermissionRequested?.invoke()
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun displaySystemNotification(
        title: String,
        body: String,
        tag: String?,
        iconUrl: String?
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification skipped: POST_NOTIFICATIONS permission not granted")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            RemoteControlApplication.DEFAULT_NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = tag?.hashCode() ?: (System.currentTimeMillis().toInt() and 0x7FFFFFFF)
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while posting notification", e)
        }
    }

    companion object {
        const val TAG = "NotificationBridge"
        const val JAVASCRIPT_OBJ_NAME = "AndroidNotificationBridge"

        fun getPolyfillScript(): String {
            return """
                (function() {
                    if (window._antigravityNotificationInjected) return;
                    window._antigravityNotificationInjected = true;

                    function CustomNotification(title, options) {
                        options = options || {};
                        var body = options.body || "";
                        var tag = options.tag || null;
                        var icon = options.icon || null;
                        if (window.AndroidNotificationBridge) {
                            window.AndroidNotificationBridge.postNotification(title, body, tag, icon);
                        }
                    }

                    Object.defineProperty(CustomNotification, 'permission', {
                        get: function() {
                            return window.AndroidNotificationBridge ? window.AndroidNotificationBridge.getPermissionStatus() : "default";
                        },
                        configurable: true
                    });

                    CustomNotification.requestPermission = function(callback) {
                        return new Promise(function(resolve) {
                            if (window.AndroidNotificationBridge) {
                                window.AndroidNotificationBridge.requestPermission();
                                var status = window.AndroidNotificationBridge.getPermissionStatus();
                                resolve(status);
                            } else {
                                resolve("denied");
                            }
                        }).then(function(result) {
                            if (typeof callback === "function") {
                                callback(result);
                            }
                            return result;
                        });
                    };

                    window.Notification = CustomNotification;
                })();
            """.trimIndent()
        }
    }
}
