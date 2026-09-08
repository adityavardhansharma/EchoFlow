package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal object SettingsPreferenceStorage {
    const val LEGACY_FILE = "settings_prefs"
    const val SECURE_FILE = "secure_settings_prefs"
    const val MIGRATED_KEY = "secure_prefs_migrated_v1"

    fun legacy(context: Context): SharedPreferences =
        context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)

    fun secureOrNull(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    fun migrateLegacyIfNeeded(legacy: SharedPreferences, destination: SharedPreferences) {
        if (destination === legacy || destination.getBoolean(MIGRATED_KEY, false)) return
        val edit = destination.edit()
        legacy.all.forEach { (key, value) ->
            if (destination.contains(key)) return@forEach
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    edit.putStringSet(key, value as Set<String>)
                }
            }
        }
        edit.putBoolean(MIGRATED_KEY, true).apply()
    }
}
