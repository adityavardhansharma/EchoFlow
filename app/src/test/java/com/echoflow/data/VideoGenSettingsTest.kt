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
        com.echoflow.testSettings(context)?.edit()?.clear()?.commit()
    }

    @Test fun `a fresh install lands on the cheap Veo default, landscape and silent`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        assertEquals(SettingsRepository.DEFAULT_VIDEO_MODEL_ID, repository.getVideoGenModelDirect())
        assertEquals("16:9", repository.getVideoAspectRatioDirect())
        assertEquals("720p", repository.getVideoResolutionDirect())
        // Audio roughly doubles the per-second price, so it must be opt-in.
        assertEquals(false, repository.getVideoAudioEnabledDirect())
    }

    @Test fun `framing settings persist across instances`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        repository.saveVideoGenModel("openai/sora-2-pro")
        repository.saveVideoAspectRatio("9:16")
        repository.saveVideoResolution("1080p")
        repository.saveVideoAudioEnabled(true)

        val reloaded = SettingsRepository(context, com.echoflow.testSettings(context))
        assertEquals("openai/sora-2-pro", reloaded.getVideoGenModelDirect())
        assertEquals("9:16", reloaded.getVideoAspectRatioDirect())
        assertEquals("1080p", reloaded.getVideoResolutionDirect())
        assertEquals(true, reloaded.getVideoAudioEnabledDirect())
        assertEquals("9:16", reloaded.videoAspectRatio.value)
    }

    @Test fun `a resolution the house list has never heard of is still kept`() {
        // This used to assert the opposite, and that assumption was the bug: the picker offers
        // whatever the *model* declares, so tapping 4K on a model that offers 4K wrote nothing
        // and snapped the selection back to 720p with no error and no explanation.
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        repository.saveVideoResolution("4K")
        assertEquals("4K", repository.getVideoResolutionDirect())
        assertEquals("4K", SettingsRepository(context, com.echoflow.testSettings(context)).getVideoResolutionDirect())

        repository.saveVideoAspectRatio("5:4")
        assertEquals("5:4", SettingsRepository(context, com.echoflow.testSettings(context)).getVideoAspectRatioDirect())
    }

    @Test fun `blank framing values fall back rather than storing nothing`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        repository.saveVideoResolution("   ")
        assertEquals("720p", repository.getVideoResolutionDirect())
        repository.saveVideoAspectRatio("")
        assertEquals("16:9", repository.getVideoAspectRatioDirect())
    }

    @Test fun `a stored resolution the model cannot do is reconciled at request time`() {
        // The store keeps the preference; the policy is what protects the API, because it is
        // the only layer that knows which model is about to run.
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        repository.saveVideoResolution("4K")

        val supported = listOf("720p", "1080p")
        assertEquals(
            "1080p",
            VideoRequestPolicy.resolveResolution(repository.getVideoResolutionDirect(), supported),
        )
    }

    @Test fun `a blank stored model falls back rather than submitting an empty id`() {
        SettingsPreferenceStorage.legacy(context).edit().putString("video_gen_model", "").commit()
        assertEquals(SettingsRepository.DEFAULT_VIDEO_MODEL_ID, SettingsRepository(context, com.echoflow.testSettings(context)).getVideoGenModelDirect())
    }
}
