package com.antigravity.remotecontrol.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

class SecurePreferencesManager(private val context: Context) : ISecurePreferencesManager {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(false)
            .build()
    }

    private var sharedPreferences: SharedPreferences = createEncryptedSharedPreferences()

    private fun createEncryptedSharedPreferences(): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            when (e) {
                is GeneralSecurityException, is IOException -> {
                    Log.e(TAG, "EncryptedSharedPreferences initialization failed. Recovering...", e)
                    recoverCorruptedPreferences()
                }
                else -> {
                    Log.e(TAG, "Unexpected error initializing EncryptedSharedPreferences. Recovering...", e)
                    recoverCorruptedPreferences()
                }
            }
        }
    }

    private fun recoverCorruptedPreferences(): SharedPreferences {
        return try {
            context.deleteSharedPreferences(PREFS_FILE_NAME)
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recover EncryptedSharedPreferences, falling back to private SharedPreferences", e)
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun <T> safePreferencesAccess(block: (SharedPreferences) -> T, fallback: T): T {
        return try {
            block(sharedPreferences)
        } catch (e: Exception) {
            Log.e(TAG, "SharedPreferences access exception. Triggering self-healing recovery...", e)
            sharedPreferences = recoverCorruptedPreferences()
            try {
                block(sharedPreferences)
            } catch (inner: Exception) {
                Log.e(TAG, "SharedPreferences recovery access failed", inner)
                fallback
            }
        }
    }

    override fun getTargetUrl(): String? {
        return safePreferencesAccess({ it.getString(KEY_TARGET_URL, "https://antigravity.google.com/") }, "https://antigravity.google.com/")
    }

    override fun setTargetUrl(url: String): Boolean {
        if (url.isBlank()) {
            return false
        }
        return safePreferencesAccess({
            it.edit().putString(KEY_TARGET_URL, url).commit()
        }, false)
    }

    override fun hasConfiguredUrl(): Boolean {
        val url = getTargetUrl()
        return !url.isNullOrBlank()
    }

    override fun clearConfiguration(): Boolean {
        return safePreferencesAccess({
            it.edit().remove(KEY_TARGET_URL).commit()
        }, false)
    }

    fun clearAll(): Boolean {
        return safePreferencesAccess({
            it.edit().clear().commit()
        }, false)
    }

    override fun getOrCreateMasterKey(): MasterKey {
        return masterKey
    }

    fun getMasterKeyAlias(): String {
        return MasterKey.DEFAULT_MASTER_KEY_ALIAS
    }

    fun isSslBypassEnabled(): Boolean {
        return safePreferencesAccess({ it.getBoolean(KEY_SSL_BYPASS_ENABLED, true) }, true)
    }

    fun setSslBypassEnabled(enabled: Boolean): Boolean {
        return safePreferencesAccess({
            it.edit().putBoolean(KEY_SSL_BYPASS_ENABLED, enabled).commit()
        }, false)
    }

    companion object {
        private const val TAG = "SecurePrefsManager"
        private const val PREFS_FILE_NAME = "antigravity_secure_prefs"
        private const val KEY_TARGET_URL = "key_target_url"
        private const val KEY_SSL_BYPASS_ENABLED = "key_ssl_bypass_enabled"
    }
}
