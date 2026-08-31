package com.antigravity.remotecontrol.security

import androidx.security.crypto.MasterKey

interface ISecurePreferencesManager {
    fun getTargetUrl(): String?
    fun setTargetUrl(url: String): Boolean
    fun hasConfiguredUrl(): Boolean
    fun clearConfiguration(): Boolean
    fun getOrCreateMasterKey(): MasterKey? = null
}
