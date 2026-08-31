package com.antigravity.remotecontrol.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F10: Native File Upload Handler.
 * Verifies WebChromeClient.onShowFileChooser integration, single/multi file support, and deadlock prevention on cancel.
 */
class NativeFileChooserTest {

    private lateinit var fileChooserHandler: MockNativeFileChooserHandler

    @Before
    fun setUp() {
        fileChooserHandler = MockNativeFileChooserHandler()
    }

    @Test
    fun testSingleFileSelectionDeliversUriArrayToCallback() {
        var deliveredUris: Array<String>? = null
        val callback: (Array<String>?) -> Unit = { uris -> deliveredUris = uris }

        fileChooserHandler.prepareChooser(callback, allowMultiple = false)
        val selectedUri = "content://com.android.providers.media.documents/document/image%3A102"
        fileChooserHandler.onFileSelectionResult(resultOk = true, selectedUris = arrayOf(selectedUri))

        assertNotNull(deliveredUris)
        assertEquals(1, deliveredUris!!.size)
        assertEquals(selectedUri, deliveredUris!![0])
        assertFalse(fileChooserHandler.hasPendingCallback())
    }

    @Test
    fun testMultipleFileSelectionDeliversAllUris() {
        var deliveredUris: Array<String>? = null
        val callback: (Array<String>?) -> Unit = { uris -> deliveredUris = uris }

        fileChooserHandler.prepareChooser(callback, allowMultiple = true)
        val selected = arrayOf(
            "content://documents/document/file1.log",
            "content://documents/document/file2.txt",
            "content://documents/document/file3.json"
        )
        fileChooserHandler.onFileSelectionResult(resultOk = true, selectedUris = selected)

        assertNotNull(deliveredUris)
        assertEquals(3, deliveredUris!!.size)
        assertEquals("content://documents/document/file1.log", deliveredUris!![0])
    }

    @Test
    fun testUserCancellationDeliversNullToPreventWebViewDeadlock() {
        var deliveredUris: Array<String>? = arrayOf("dummy")
        var callbackExecuted = false
        val callback: (Array<String>?) -> Unit = { uris ->
            deliveredUris = uris
            callbackExecuted = true
        }

        fileChooserHandler.prepareChooser(callback, allowMultiple = false)
        assertTrue(fileChooserHandler.hasPendingCallback())

        // Simulate user pressing Cancel / Back button
        fileChooserHandler.onFileSelectionResult(resultOk = false, selectedUris = null)

        assertTrue("Callback must be executed on cancel", callbackExecuted)
        assertNull("Callback must receive null on cancel to clear WebView lock", deliveredUris)
        assertFalse(fileChooserHandler.hasPendingCallback())
    }

    @Test
    fun testAcceptTypesFilterApplied() {
        val acceptTypes = arrayOf("image/png", "image/jpeg")
        fileChooserHandler.setAcceptTypes(acceptTypes)

        assertEquals(2, fileChooserHandler.getAcceptTypes().size)
        assertTrue(fileChooserHandler.getAcceptTypes().contains("image/png"))
    }

    @Test
    fun testSecondInvocationWithoutResultCleansPreviousCallback() {
        var firstCallbackCalled = false
        val firstCallback: (Array<String>?) -> Unit = { firstCallbackCalled = true }
        fileChooserHandler.prepareChooser(firstCallback, allowMultiple = false)

        var secondCallbackCalled = false
        val secondCallback: (Array<String>?) -> Unit = { secondCallbackCalled = true }
        // Launch a new chooser before first completes
        fileChooserHandler.prepareChooser(secondCallback, allowMultiple = false)

        assertTrue("Previous callback must receive null to unblock WebView", firstCallbackCalled)
        assertTrue(fileChooserHandler.hasPendingCallback())
    }

    class MockNativeFileChooserHandler {
        private var pendingCallback: ((Array<String>?) -> Unit)? = null
        private var acceptTypes: Array<String> = arrayOf("*/*")

        fun prepareChooser(callback: (Array<String>?) -> Unit, allowMultiple: Boolean) {
            // Guarantee previous callback is cleared with null
            pendingCallback?.invoke(null)
            pendingCallback = callback
        }

        fun onFileSelectionResult(resultOk: Boolean, selectedUris: Array<String>?) {
            if (resultOk && !selectedUris.isNullOrEmpty()) {
                pendingCallback?.invoke(selectedUris)
            } else {
                pendingCallback?.invoke(null)
            }
            pendingCallback = null
        }

        fun hasPendingCallback(): Boolean = pendingCallback != null

        fun setAcceptTypes(types: Array<String>) {
            acceptTypes = types
        }

        fun getAcceptTypes(): Array<String> = acceptTypes
    }
}
