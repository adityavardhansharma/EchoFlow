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
        com.echoflow.testSettings(context)?.edit()?.clear()?.commit()
    }

    @Test fun `the cloud image model preference survives the on-device removal`() {
        val repository = SettingsRepository(context, com.echoflow.testSettings(context))
        assertEquals(SettingsRepository.DEFAULT_IMAGE_MODEL_ID, repository.getImageGenModelDirect())

        repository.saveImageGenModel("google/gemini-3-flash-image")
        assertEquals("google/gemini-3-flash-image", SettingsRepository(context, com.echoflow.testSettings(context)).getImageGenModelDirect())
    }
}
