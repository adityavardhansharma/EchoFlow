package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoGenSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    @Test fun `a fresh install lands on the cheap Veo default, landscape and silent`() {
        val repository = SettingsRepository(context)
        assertEquals(SettingsRepository.DEFAULT_VIDEO_MODEL_ID, repository.getVideoGenModelDirect())
        assertEquals("16:9", repository.getVideoAspectRatioDirect())
        assertEquals("720p", repository.getVideoResolutionDirect())
        // Audio roughly doubles the per-second price, so it must be opt-in.
        assertEquals(false, repository.getVideoAudioEnabledDirect())
    }

    @Test fun `framing settings persist across instances`() {
        val repository = SettingsRepository(context)
        repository.saveVideoGenModel("openai/sora-2-pro")
        repository.saveVideoAspectRatio("9:16")
        repository.saveVideoResolution("1080p")
        repository.saveVideoAudioEnabled(true)

        val reloaded = SettingsRepository(context)
        assertEquals("openai/sora-2-pro", reloaded.getVideoGenModelDirect())
        assertEquals("9:16", reloaded.getVideoAspectRatioDirect())
        assertEquals("1080p", reloaded.getVideoResolutionDirect())
        assertEquals(true, reloaded.getVideoAudioEnabledDirect())
        assertEquals("9:16", reloaded.videoAspectRatio.value)
    }

    @Test fun `unknown framing values normalize instead of reaching the API`() {
        val repository = SettingsRepository(context)
        repository.saveVideoAspectRatio("banana")
        assertEquals("16:9", repository.getVideoAspectRatioDirect())
        repository.saveVideoResolution("8K")
        assertEquals("720p", repository.getVideoResolutionDirect())
    }

    @Test fun `a blank stored model falls back rather than submitting an empty id`() {
        SettingsPreferenceStorage.legacy(context).edit().putString("video_gen_model", "").commit()
        assertEquals(SettingsRepository.DEFAULT_VIDEO_MODEL_ID, SettingsRepository(context).getVideoGenModelDirect())
    }
}
