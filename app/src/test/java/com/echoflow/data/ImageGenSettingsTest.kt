package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageGenSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    @Test fun `existing users default to the OpenRouter engine and sane local defaults`() {
        val repository = SettingsRepository(context)
        assertEquals(SettingsRepository.IMAGE_ENGINE_OPENROUTER, repository.getImageGenEngineDirect())
        assertEquals("", repository.getLocalImageModelDirect())
        assertEquals(20, repository.getLocalImageIterationsDirect())
        assertEquals("random", repository.getLocalImageSeedModeDirect())
        assertEquals(1, repository.getLocalImageFixedSeedDirect())
        assertEquals(false, repository.getExperimentalImageModelsEnabledDirect())
        // The existing cloud image model preference is untouched by the engine split.
        assertEquals(SettingsRepository.DEFAULT_IMAGE_MODEL_ID, repository.getImageGenModelDirect())
    }

    @Test fun `engine, model, seed settings persist and unknown values normalize`() {
        val repository = SettingsRepository(context)
        repository.saveImageGenEngine(SettingsRepository.IMAGE_ENGINE_LOCAL)
        repository.saveLocalImageModel("local-image/dreamshaper-8")
        repository.saveLocalImageSeedMode("fixed")
        repository.saveLocalImageFixedSeed(1234)
        repository.saveExperimentalImageModelsEnabled(true)

        val reloaded = SettingsRepository(context)
        assertEquals(SettingsRepository.IMAGE_ENGINE_LOCAL, reloaded.getImageGenEngineDirect())
        assertEquals("local-image/dreamshaper-8", reloaded.getLocalImageModelDirect())
        assertEquals("fixed", reloaded.getLocalImageSeedModeDirect())
        assertEquals(1234, reloaded.getLocalImageFixedSeedDirect())
        assertEquals(true, reloaded.getExperimentalImageModelsEnabledDirect())
        assertEquals(true, reloaded.experimentalImageModelsEnabled.value)

        reloaded.saveImageGenEngine("nonsense")
        assertEquals(SettingsRepository.IMAGE_ENGINE_OPENROUTER, reloaded.getImageGenEngineDirect())
        reloaded.saveLocalImageSeedMode("nonsense")
        assertEquals("random", reloaded.getLocalImageSeedModeDirect())
    }

    @Test fun `local iterations clamp to 10-30 on save and on read`() {
        val repository = SettingsRepository(context)
        repository.saveLocalImageIterations(1)
        assertEquals(10, repository.getLocalImageIterationsDirect())
        repository.saveLocalImageIterations(500)
        assertEquals(30, repository.getLocalImageIterationsDirect())
        repository.saveLocalImageIterations(25)
        assertEquals(25, repository.getLocalImageIterationsDirect())
    }
}
