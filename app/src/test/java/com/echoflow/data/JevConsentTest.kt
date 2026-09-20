package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JevConsentTest {
    @Test fun `old opt in stays inactive until contextual sharing is enabled again`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
        val prefs = SettingsPreferenceStorage.secureOrNull(context) ?: SettingsPreferenceStorage.legacy(context)
        prefs.edit().putBoolean("jev_enabled", true).putString("jev_api_key", "fixture").commit()
        val repository = SettingsRepository(context)
        assertFalse(repository.getJevEnabledDirect())
        assertFalse(repository.jevEnabled.value)
        assertEquals("fixture", repository.getJevApiKeyDirect())
        repository.saveJevEnabled(true)
        assertTrue(SettingsRepository(context).getJevEnabledDirect())
        repository.saveJevEnabled(false)
        assertFalse(SettingsRepository(context).getJevEnabledDirect())
    }
}
