package com.lola.pro

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object LolaVault {
    private const val PREFS_NAME = "lola_secure_vault"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSecret(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getSecret(context: Context, key: String): String? {
        return getPrefs(context).getString(key, null)
    }

    fun saveKeyPool(context: Context, keys: List<String>) {
        saveSecret(context, "gemini_keys", keys.joinToString(","))
    }

    fun getKeyPool(context: Context): List<String> {
        return getSecret(context, "gemini_keys")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }
}