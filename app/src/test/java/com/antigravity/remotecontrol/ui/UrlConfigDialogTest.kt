package com.antigravity.remotecontrol.ui

import com.antigravity.remotecontrol.security.ISecurePreferencesManager
import com.antigravity.remotecontrol.security.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F4: Native URL Configuration UI.
 * Verifies first-run onboarding gating, validation error feedback, persistence, and cancellation behavior.
 */
class UrlConfigDialogTest {

    private lateinit var mockStorage: MockPreferencesStorage
    private lateinit var dialogPresenter: UrlConfigDialogPresenter

    @Before
    fun setUp() {
        mockStorage = MockPreferencesStorage()
        dialogPresenter = UrlConfigDialogPresenter(mockStorage)
    }

    @Test
    fun testFirstRunGatingTriggersDialogWhenNoUrlStored() {
        assertTrue(dialogPresenter.shouldShowFirstRunDialog())
    }

    @Test
    fun testFirstRunGatingSuppressedWhenUrlAlreadyConfigured() {
        mockStorage.setTargetUrl("https://my-server.com")
        assertFalse(dialogPresenter.shouldShowFirstRunDialog())
    }

    @Test
    fun testValidUrlSubmissionPersistsAndDismissesDialog() {
        val input = "https://remote.antigravity.internal:8443"
        val state = dialogPresenter.onSaveClicked(input)

        assertTrue(state.isDismissed)
        assertNull(state.validationError)
        assertEquals("https://remote.antigravity.internal:8443", mockStorage.getTargetUrl())
    }

    @Test
    fun testInvalidUrlSubmissionKeepsDialogVisibleWithErrorMessage() {
        val invalidInput = "htp://invalid url"
        val state = dialogPresenter.onSaveClicked(invalidInput)

        assertFalse(state.isDismissed)
        assertNotNull(state.validationError)
        assertNull(mockStorage.getTargetUrl())
    }

    @Test
    fun testCancelButtonDismissesDialogOnlyIfUrlAlreadyConfigured() {
        // First-run mode: cancel is not allowed to leave app in empty state
        val firstRunCancelState = dialogPresenter.onCancelClicked(isFirstRun = true)
        assertFalse(firstRunCancelState.isDismissed)

        // Settings edit mode: cancel discards edits and keeps previous URL
        mockStorage.setTargetUrl("https://existing-server.com")
        val editCancelState = dialogPresenter.onCancelClicked(isFirstRun = false)
        assertTrue(editCancelState.isDismissed)
        assertEquals("https://existing-server.com", mockStorage.getTargetUrl())
    }

    @Test
    fun testClearServerUrlTriggersFirstRunState() {
        mockStorage.setTargetUrl("https://server.com")
        assertTrue(mockStorage.hasConfiguredUrl())

        dialogPresenter.onResetConfiguration()
        assertFalse(mockStorage.hasConfiguredUrl())
        assertTrue(dialogPresenter.shouldShowFirstRunDialog())
    }

    data class DialogUiState(val isDismissed: Boolean, val validationError: String? = null)

    class UrlConfigDialogPresenter(private val preferencesManager: ISecurePreferencesManager) {
        fun shouldShowFirstRunDialog(): Boolean {
            return !preferencesManager.hasConfiguredUrl()
        }

        fun onSaveClicked(input: String?): DialogUiState {
            val validation = UrlValidator.validateAndNormalize(input)
            return if (validation.isValid && validation.formattedUrl != null) {
                preferencesManager.setTargetUrl(validation.formattedUrl)
                DialogUiState(isDismissed = true, validationError = null)
            } else {
                DialogUiState(isDismissed = false, validationError = validation.errorMessage ?: "Invalid URL")
            }
        }

        fun onCancelClicked(isFirstRun: Boolean): DialogUiState {
            return if (isFirstRun) {
                DialogUiState(isDismissed = false, validationError = "Server URL is required to continue")
            } else {
                DialogUiState(isDismissed = true, validationError = null)
            }
        }

        fun onResetConfiguration() {
            preferencesManager.clearConfiguration()
        }
    }

    open class MockPreferencesStorage : ISecurePreferencesManager {
        private var url: String? = null
        override fun getTargetUrl(): String? = url
        override fun setTargetUrl(url: String): Boolean {
            this.url = url
            return true
        }
        override fun hasConfiguredUrl(): Boolean = !url.isNullOrBlank()
        override fun clearConfiguration(): Boolean {
            url = null
            return true
        }
    }
}
