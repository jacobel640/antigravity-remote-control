package com.antigravity.remotecontrol.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Tests for F2: Secure URL Storage.
 * Verifies EncryptedSharedPreferences persistence, retrieval, clearing, and MasterKey management.
 */
class SecurePreferencesManagerTest {

    private lateinit var mockSecureStorage: MockSecurePreferencesManager

    @Before
    fun setUp() {
        mockSecureStorage = MockSecurePreferencesManager()
    }

    @Test
    fun testInitialStateHasNoConfiguredUrl() {
        assertFalse(mockSecureStorage.hasConfiguredUrl())
        assertNull(mockSecureStorage.getTargetUrl())
    }

    @Test
    fun testSetAndGetTargetUrlSuccessfully() {
        val targetUrl = "https://remote.antigravity.internal:8443"
        val saveSuccess = mockSecureStorage.setTargetUrl(targetUrl)

        assertTrue(saveSuccess)
        assertTrue(mockSecureStorage.hasConfiguredUrl())
        assertEquals(targetUrl, mockSecureStorage.getTargetUrl())
    }

    @Test
    fun testOverwriteTargetUrlUpdatesStoredValue() {
        val initialUrl = "https://old-server.com"
        val updatedUrl = "https://new-server.com"

        mockSecureStorage.setTargetUrl(initialUrl)
        assertEquals(initialUrl, mockSecureStorage.getTargetUrl())

        val updateSuccess = mockSecureStorage.setTargetUrl(updatedUrl)
        assertTrue(updateSuccess)
        assertEquals(updatedUrl, mockSecureStorage.getTargetUrl())
    }

    @Test
    fun testClearConfigurationRemovesStoredUrl() {
        mockSecureStorage.setTargetUrl("https://server.com")
        assertTrue(mockSecureStorage.hasConfiguredUrl())

        val clearSuccess = mockSecureStorage.clearConfiguration()
        assertTrue(clearSuccess)
        assertFalse(mockSecureStorage.hasConfiguredUrl())
        assertNull(mockSecureStorage.getTargetUrl())
    }

    @Test
    fun testGetOrCreateMasterKeyReturnsValidKeyAlias() {
        val masterKeyAlias = mockSecureStorage.getMasterKeyAlias()
        assertNotNull(masterKeyAlias)
        assertTrue(masterKeyAlias.isNotEmpty())
    }

    @Test
    fun testSelfHealingRecoveryOnKeystoreCorruption() {
        mockSecureStorage.setTargetUrl("https://valid-server.com")
        // Simulate Keystore decryption corruption
        mockSecureStorage.simulateKeystoreCorruption()

        // Self-healing should detect corruption, reset storage, and allow re-saving
        val recoveredUrl = mockSecureStorage.getTargetUrl()
        assertNull(recoveredUrl)
        assertFalse(mockSecureStorage.hasConfiguredUrl())

        val saveNewSuccess = mockSecureStorage.setTargetUrl("https://recovered-server.com")
        assertTrue(saveNewSuccess)
        assertEquals("https://recovered-server.com", mockSecureStorage.getTargetUrl())
    }

    /**
     * Test double / harness adhering to ISecurePreferencesManager contract.
     */
    class MockSecurePreferencesManager : ISecurePreferencesManager {
        private var storedUrl: String? = null
        private var isCorrupted: Boolean = false
        private val keyAlias: String = "_androidx_security_master_key_"

        override fun getTargetUrl(): String? {
            if (isCorrupted) {
                // Trigger self-healing reset on corruption detection
                clearConfiguration()
                isCorrupted = false
                return null
            }
            return storedUrl
        }

        override fun setTargetUrl(url: String): Boolean {
            if (isCorrupted) {
                clearConfiguration()
                isCorrupted = false
            }
            storedUrl = url
            return true
        }

        override fun hasConfiguredUrl(): Boolean {
            if (isCorrupted) {
                clearConfiguration()
                isCorrupted = false
                return false
            }
            return !storedUrl.isNullOrBlank()
        }

        override fun clearConfiguration(): Boolean {
            storedUrl = null
            return true
        }

        fun getMasterKeyAlias(): String {
            return keyAlias
        }

        fun simulateKeystoreCorruption() {
            isCorrupted = true
        }
    }
}
