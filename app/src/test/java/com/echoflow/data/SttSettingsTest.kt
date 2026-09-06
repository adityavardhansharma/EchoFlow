package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SttSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        com.echoflow.testSettings(context)?.edit()?.clear()?.commit()
    }

    @Test fun `a fresh install dictates with GPT Transcribe`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        assertEquals("openai/gpt-transcribe", repository.getSttCloudModelDirect())
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, repository.sttCloudModel.value)
    }

    @Test fun `a leftover Fish id is resolved to GPT Transcribe before it can be sent`() {
        val stored = com.echoflow.testSettings(context)
            ?: SettingsPreferenceStorage.legacy(context)
        stored.edit().putString("stt_cloud_model", "fish-audio/transcribe-1").commit()

        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        assertEquals("openai/gpt-transcribe", repository.getSttCloudModelDirect())
    }

    @Test fun `Nemotron ASR is a selectable cloud model`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        val id = "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b"
        repository.saveSttCloudModel(id)
        assertEquals(id, repository.getSttCloudModelDirect())
        assertEquals(id, repository.sttCloudModel.value)
    }
}
