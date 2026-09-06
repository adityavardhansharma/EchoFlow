package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureSettingsMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val legacy = context.getSharedPreferences("migration-legacy",Context.MODE_PRIVATE)
    private val secure = context.getSharedPreferences("migration-secure",Context.MODE_PRIVATE)
    @Before fun reset() { legacy.edit().clear().commit(); secure.edit().clear().commit() }
    @Test fun `migration preserves newer secure values and removes plaintext`() {
        legacy.edit().putString("openrouter_api_key","old").putString("dark_mode","dark").commit()
        secure.edit().putString("openrouter_api_key","new").commit()
        SettingsPreferenceStorage.migrateLegacyIfNeeded(legacy,secure)
        assertTrue(legacy.all.isEmpty())
        assertEquals("new",secure.getString("openrouter_api_key",null))
        assertEquals("dark",secure.getString("dark_mode",null))
    }
    @Test fun `failed durable write retains legacy data for retry`() {
        legacy.edit().putString("openrouter_api_key","keep-me").commit()
        val failed = object : SharedPreferences by secure {
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor by secure.edit() {
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
                override fun commit() = false
            }
        }
        assertThrows(IllegalStateException::class.java) { SettingsPreferenceStorage.migrateLegacyIfNeeded(legacy,failed) }
        assertEquals("keep-me",legacy.getString("openrouter_api_key",null))
    }
    @Test fun `old migrated installations remove leftovers without restoring deleted keys`() {
        secure.edit().putBoolean(SettingsPreferenceStorage.MIGRATED_KEY,true).commit()
        legacy.edit().putString("openrouter_api_key","deleted-secret").commit()
        SettingsPreferenceStorage.migrateLegacyIfNeeded(legacy,secure)
        assertTrue(legacy.all.isEmpty())
        assertFalse(secure.contains("openrouter_api_key"))
    }
    @Test fun `plaintext destination cannot be used as secure storage`() {
        assertThrows(IllegalArgumentException::class.java) { SettingsPreferenceStorage.migrateLegacyIfNeeded(legacy,legacy) }
    }
}
