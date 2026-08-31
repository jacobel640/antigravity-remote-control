package com.antigravity.remotecontrol

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class RemoteControlApplication : Application() {

    companion object {
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "antigravity_remote_control_channel"
        const val ALERTS_NOTIFICATION_CHANNEL_ID = "antigravity_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.notification_channel_name)
            val channelDescription = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH

            val defaultChannel = NotificationChannel(
                DEFAULT_NOTIFICATION_CHANNEL_ID,
                channelName,
                importance
            ).apply {
                description = channelDescription
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val alertsChannel = NotificationChannel(
                ALERTS_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                importance
            ).apply {
                description = channelDescription
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(defaultChannel)
            notificationManager?.createNotificationChannel(alertsChannel)
        }
    }
}
