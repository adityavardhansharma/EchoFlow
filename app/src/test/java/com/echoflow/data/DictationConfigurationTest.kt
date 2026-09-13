package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DictationConfigurationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Before fun reset() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    @Test fun `another repository changing a key immediately invalidates service configuration`() = runTest {
        val service = SettingsRepository(context)
        val ui = SettingsRepository(context)
        var invalidations = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.dictationConfigurationChanges.collect { invalidations++ }
        }
        runCurrent()
        val initial = invalidations
        ui.saveApiKey("test-key")
        runCurrent()
        assertTrue(invalidations > initial)
        assertEquals("test-key", service.getDictationConfiguration().key)
        val beforeRemoval = invalidations
        ui.saveApiKey("")
        runCurrent()
        assertTrue(invalidations > beforeRemoval)
        assertFalse(service.getDictationConfiguration().ready)
        job.cancel()
    }

    @Test fun `encrypted backing store invalidates without needing decrypted key names`() = runTest {
        val service = SettingsRepository(context)
        var invalidations = 0
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.dictationConfigurationChanges.collect { invalidations++ }
        }
        runCurrent()
        val initial = invalidations
        context.getSharedPreferences(SettingsPreferenceStorage.SECURE_FILE, Context.MODE_PRIVATE)
            .edit().putString("opaque-encrypted-key-for-test", "opaque-value").commit()
        runCurrent()
        assertTrue(invalidations > initial)
        job.cancel()
        context.getSharedPreferences(SettingsPreferenceStorage.SECURE_FILE, Context.MODE_PRIVATE)
            .edit().remove("opaque-encrypted-key-for-test").commit()
    }

    @Test fun `snapshot matches provider availability and stays read only`() {
        val repository = SettingsRepository(context)
        val prefs = SettingsPreferenceStorage.secureOrNull(context) ?: SettingsPreferenceStorage.legacy(context)
        repository.saveApiKey("openrouter-test")
        repository.saveSttMode(SttMode.Cloud)
        prefs.edit().putString("stt_cloud_model", SttCatalog.SARVAM_MODEL_ID)
            .putBoolean("labs_cloud_apis_enabled", true).putBoolean("sarvam_enabled", true)
            .putString("sarvam_api_key", "sarvam-test").commit()
        val sarvam = repository.getDictationConfiguration()
        assertEquals(SttCatalog.SARVAM_MODEL_ID, sarvam.model)
        assertEquals("sarvam-test", sarvam.key)
        assertTrue(sarvam.ready)
        assertTrue(sarvam.hinglish)
        prefs.edit().putBoolean("sarvam_enabled", false).commit()
        val fallback = repository.getDictationConfiguration()
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, fallback.model)
        assertEquals("openrouter-test", fallback.key)
        assertFalse(fallback.hinglish)
        assertEquals(SttCatalog.SARVAM_MODEL_ID, prefs.getString("stt_cloud_model", null))
        repository.saveApiKey("")
        assertFalse(repository.getDictationConfiguration().ready)
        prefs.edit().putString("stt_mode", SttMode.OnDevice.storageKey).putBoolean("sarvam_enabled", true).commit()
        assertFalse(repository.getDictationConfiguration().ready)
    }
}
