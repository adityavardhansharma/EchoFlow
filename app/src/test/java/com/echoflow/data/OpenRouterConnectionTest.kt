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
class OpenRouterConnectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("openrouter-connection-test", Context.MODE_PRIVATE)
    private val legacy = SettingsPreferenceStorage.legacy(context)
    @Before fun reset() { prefs.edit().clear().commit(); legacy.edit().clear().commit() }
    private fun repository() = SettingsRepository(context, prefs)

    @Test fun `existing encrypted key is recognized as manual without rewriting it`() {
        prefs.edit().putString("openrouter_api_key", "existing-user-key").commit()
        val settings = repository()
        assertEquals("existing-user-key", settings.apiKey.value)
        assertTrue(settings.openRouterConnection.value.connected)
        assertFalse(settings.openRouterConnection.value.signedIn)
        assertFalse(prefs.contains("openrouter_signed_in"))
    }

    @Test fun `legacy plaintext key migrates before connection state is read`() {
        legacy.edit().putString("openrouter_api_key", "legacy-key").commit()
        val settings = repository()
        assertEquals("legacy-key", settings.apiKey.value)
        assertTrue(settings.openRouterConnection.value.connected)
        assertFalse(settings.openRouterConnection.value.signedIn)
        assertTrue(legacy.all.isEmpty())
    }

    @Test fun `successful sign in retains manual key across restart and account switch`() {
        val settings = repository()
        settings.saveApiKey("manual-secret")
        settings.saveOpenRouterSignIn("oauth-secret-one")
        settings.saveOpenRouterSignIn("oauth-secret-two")
        val restarted = repository()
        assertTrue(restarted.openRouterConnection.value.signedIn)
        assertTrue(restarted.openRouterConnection.value.hasSavedManualKey)
        assertEquals("oauth-secret-two", restarted.apiKey.value)
        restarted.restoreOpenRouterManualKey()
        assertEquals("manual-secret", restarted.apiKey.value)
        assertFalse(restarted.openRouterConnection.value.signedIn)
        assertFalse(restarted.openRouterConnection.value.hasSavedManualKey)
    }

    @Test fun `manual replacement becomes the only credential`() {
        val settings = repository()
        settings.saveApiKey("original-manual")
        settings.saveOpenRouterSignIn("signed-in-secret")
        settings.saveApiKey("  replacement-manual  ")
        assertEquals("replacement-manual", repository().apiKey.value)
        assertFalse(settings.openRouterConnection.value.hasSavedManualKey)
        assertFalse(settings.openRouterConnection.value.signedIn)
    }

    @Test fun `disconnect removes active and saved keys and survives restart`() {
        val settings = repository()
        settings.saveApiKey("original-manual")
        settings.saveOpenRouterSignIn("signed-in-secret")
        settings.saveApiKey("")
        assertEquals(OpenRouterConnection(), repository().openRouterConnection.value)
        assertEquals("", repository().apiKey.value)
        assertFalse(prefs.contains("openrouter_manual_key"))
    }

    @Test fun `empty sign in result cannot replace existing connection`() {
        val settings = repository()
        settings.saveApiKey("existing-key")
        assertThrows(IllegalArgumentException::class.java) { settings.saveOpenRouterSignIn("") }
        assertEquals("existing-key", repository().apiKey.value)
    }

    @Test fun `missing manual backup does not erase active key`() {
        val settings = repository()
        settings.saveOpenRouterSignIn("signed-in-secret")
        assertThrows(IllegalStateException::class.java) { settings.restoreOpenRouterManualKey() }
        assertEquals("signed-in-secret", settings.apiKey.value)
    }

    @Test fun `failed durable save restores direct readers and leaves flows unchanged`() {
        prefs.edit().putString("openrouter_api_key", "existing-key").commit()
        var failNext = true
        val failing = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val delegate = prefs.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        delegate.putString(key, value); return this
                    }
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                        delegate.putBoolean(key, value); return this
                    }
                    override fun remove(key: String?): SharedPreferences.Editor {
                        delegate.remove(key); return this
                    }
                    override fun commit(): Boolean {
                        val result = delegate.commit()
                        if (failNext) { failNext = false; return false }
                        return result
                    }
                }
            }
        }
        val settings = SettingsRepository(context, failing)
        assertThrows(IllegalStateException::class.java) { settings.saveOpenRouterSignIn("new-sign-in-key") }
        assertEquals("existing-key", settings.getApiKeyDirect())
        assertEquals("existing-key", settings.apiKey.value)
        assertFalse(settings.openRouterConnection.value.signedIn)
        assertFalse(repository().openRouterConnection.value.hasSavedManualKey)
    }
}
