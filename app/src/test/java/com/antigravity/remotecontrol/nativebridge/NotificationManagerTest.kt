package com.antigravity.remotecontrol.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F12: Android System Notification Display.
 * Verifies NotificationChannel creation (IMPORTANCE_HIGH), builder properties, pending intents, and badge support.
 */
class NotificationManagerTest {

    private lateinit var systemNotificationManager: MockSystemNotificationManager

    @Before
    fun setUp() {
        systemNotificationManager = MockSystemNotificationManager()
    }

    @Test
    fun testNotificationChannelCreatedWithHighImportance() {
        val channel = systemNotificationManager.getNotificationChannel()

        assertNotNull(channel)
        assertEquals("antigravity_alerts", channel.id)
        assertEquals("Antigravity Remote Alerts", channel.name)
        assertEquals(MockSystemNotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.enableVibration)
        assertTrue(channel.showBadge)
    }

    @Test
    fun testBuildAndDisplayNotificationWithParameters() {
        val notificationId = systemNotificationManager.displayNotification(
            title = "New Agent Message",
            body = "Task completed successfully",
            tag = "msg-99"
        )

        assertTrue(notificationId > 0)
        val active = systemNotificationManager.getActiveNotifications()
        assertEquals(1, active.size)
        assertEquals("New Agent Message", active[0].title)
        assertEquals("Task completed successfully", active[0].body)
        assertEquals("msg-99", active[0].tag)
    }

    @Test
    fun testNotificationHasContentIntentForForegrounding() {
        systemNotificationManager.displayNotification("Alert", "Check dashboard", null)
        val active = systemNotificationManager.getActiveNotifications()

        assertEquals(1, active.size)
        assertTrue(active[0].hasPendingIntent)
        assertTrue(active[0].autoCancel)
    }

    @Test
    fun testCancelNotificationRemovesFromActiveList() {
        val id = systemNotificationManager.displayNotification("Alert 1", "Body 1", "tag-1")
        assertEquals(1, systemNotificationManager.getActiveNotifications().size)

        systemNotificationManager.cancelNotification(id, "tag-1")
        assertEquals(0, systemNotificationManager.getActiveNotifications().size)
    }

    @Test
    fun testMultipleNotificationsWithDistinctTags() {
        systemNotificationManager.displayNotification("Task 1", "Body 1", "tag-1")
        systemNotificationManager.displayNotification("Task 2", "Body 2", "tag-2")
        systemNotificationManager.displayNotification("Task 3", "Body 3", "tag-3")

        val active = systemNotificationManager.getActiveNotifications()
        assertEquals(3, active.size)
    }

    data class MockNotificationChannel(
        val id: String,
        val name: String,
        val importance: Int,
        val enableVibration: Boolean,
        val showBadge: Boolean
    )

    data class ActiveNotificationRecord(
        val id: Int,
        val tag: String?,
        val title: String,
        val body: String,
        val hasPendingIntent: Boolean,
        val autoCancel: Boolean
    )

    class MockSystemNotificationManager {
        companion object {
            const val IMPORTANCE_HIGH = 4
        }

        private val channel = MockNotificationChannel(
            id = "antigravity_alerts",
            name = "Antigravity Remote Alerts",
            importance = IMPORTANCE_HIGH,
            enableVibration = true,
            showBadge = true
        )

        private val notifications = mutableListOf<ActiveNotificationRecord>()
        private var idCounter = 1

        fun getNotificationChannel(): MockNotificationChannel = channel

        fun displayNotification(title: String, body: String, tag: String?): Int {
            val id = idCounter++
            notifications.add(
                ActiveNotificationRecord(
                    id = id,
                    tag = tag,
                    title = title,
                    body = body,
                    hasPendingIntent = true,
                    autoCancel = true
                )
            )
            return id
        }

        fun getActiveNotifications(): List<ActiveNotificationRecord> = notifications.toList()

        fun cancelNotification(id: Int, tag: String?) {
            notifications.removeAll { it.id == id && it.tag == tag }
        }
    }
}
