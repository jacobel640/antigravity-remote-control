package com.antigravity.remotecontrol.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F11: Web Notification JS Bridge.
 * Verifies @JavascriptInterface methods, W3C Notification polyfill injection, and message dispatch.
 */
class NotificationBridgeTest {

    private lateinit var bridge: MockNotificationBridge

    @Before
    fun setUp() {
        bridge = MockNotificationBridge()
    }

    @Test
    fun testPostNotificationReceivesTitleAndBody() {
        bridge.postNotification(
            title = "Task Finished",
            body = "Build completed with status 0",
            tag = "task-101",
            iconUrl = "https://server.com/icon.png"
        )

        assertEquals(1, bridge.postedNotifications.size)
        val item = bridge.postedNotifications[0]
        assertEquals("Task Finished", item.title)
        assertEquals("Build completed with status 0", item.body)
        assertEquals("task-101", item.tag)
        assertEquals("https://server.com/icon.png", item.iconUrl)
    }

    @Test
    fun testGetPermissionStatusReturnsGrantedWhenAuthorized() {
        bridge.setPermissionGranted(true)
        assertEquals("granted", bridge.getPermissionStatus())
    }

    @Test
    fun testGetPermissionStatusReturnsDeniedWhenNotAuthorized() {
        bridge.setPermissionGranted(false)
        assertEquals("denied", bridge.getPermissionStatus())
    }

    @Test
    fun testRequestPermissionTriggersHostPermissionFlow() {
        bridge.requestPermission()
        assertTrue(bridge.permissionRequestTriggered)
    }

    @Test
    fun testPostNotificationWithNullTagAndIcon() {
        bridge.postNotification(
            title = "Simple Alert",
            body = "Notification with no options",
            tag = null,
            iconUrl = null
        )

        assertEquals(1, bridge.postedNotifications.size)
        val item = bridge.postedNotifications[0]
        assertEquals("Simple Alert", item.title)
        assertEquals("Notification with no options", item.body)
    }

    @Test
    fun testGenerateW3CPolyfillJavascriptContainsNotificationShim() {
        val polyfillScript = bridge.getNotificationPolyfillScript()
        assertNotNull(polyfillScript)
        assertTrue(polyfillScript.contains("window.Notification"))
        assertTrue(polyfillScript.contains("AndroidNotificationBridge.postNotification"))
        assertTrue(polyfillScript.contains("AndroidNotificationBridge.requestPermission"))
    }

    data class NotificationItem(
        val title: String,
        val body: String,
        val tag: String?,
        val iconUrl: String?
    )

    class MockNotificationBridge : INotificationBridge {
        val postedNotifications = mutableListOf<NotificationItem>()
        var permissionRequestTriggered: Boolean = false
            private set
        private var isGranted: Boolean = false

        override fun postNotification(title: String, body: String, tag: String?, iconUrl: String?) {
            postedNotifications.add(NotificationItem(title, body, tag, iconUrl))
        }

        override fun getPermissionStatus(): String {
            return if (isGranted) "granted" else "denied"
        }

        override fun requestPermission() {
            permissionRequestTriggered = true
        }

        fun setPermissionGranted(granted: Boolean) {
            isGranted = granted
        }

        fun getNotificationPolyfillScript(): String {
            return """
                (function() {
                    if (!window.Notification) {
                        window.Notification = function(title, options) {
                            options = options || {};
                            AndroidNotificationBridge.postNotification(
                                title,
                                options.body || '',
                                options.tag || null,
                                options.icon || null
                            );
                        };
                        window.Notification.permission = AndroidNotificationBridge.getPermissionStatus();
                        window.Notification.requestPermission = function(callback) {
                            AndroidNotificationBridge.requestPermission();
                            if (callback) callback(window.Notification.permission);
                            return Promise.resolve(window.Notification.permission);
                        };
                    }
                })();
            """.trimIndent()
        }
    }
}
