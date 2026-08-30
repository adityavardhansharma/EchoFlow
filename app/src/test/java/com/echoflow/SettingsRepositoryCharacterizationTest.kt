package com.echoflow

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.InferenceParams
import com.echoflow.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryCharacterizationTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("secure_settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshInstallDefaultsRemainStable() {
        val repository = SettingsRepository(context)

        assertEquals("system", repository.darkMode.value)
        assertEquals("off", repository.webSearchProvider.value)
        assertFalse(repository.echoCrawlIntroDismissed.value)
        assertFalse(repository.ggufEnabled.value)
        assertFalse(repository.browserFlowEnabled.value)
        assertTrue(repository.echoAdviserEnabled.value)
        assertTrue(repository.echoFusionEnabled.value)
        assertTrue(repository.echoAgentEnabled.value)
    }

    @Test
    fun savedValuesRoundTripThroughARecreatedRepository() {
        SettingsRepository(context).apply {
            saveDarkMode("dark")
            saveWebSearchProvider("exa")
            saveSearchApiKey("exa", "secret")
            saveGgufEnabled(true)
            saveBrowserIdleMinutes(42)
            saveInferenceParams(true, InferenceParams(0.25f, 17, 0.8f, 2048))
        }

        val restored = SettingsRepository(context)
        assertEquals("dark", restored.getDarkModeDirect())
        assertEquals("exa", restored.getWebSearchProviderDirect())
        assertEquals("secret", restored.getSearchApiKeyDirect("exa"))
        assertTrue(restored.getGgufEnabledDirect())
        assertEquals(42, restored.getBrowserIdleMinutesDirect())
        assertEquals(InferenceParams(0.25f, 17, 0.8f, 2048), restored.getInferenceParamsDirect(true))
    }

    @Test
    fun legacyWebSearchBooleanMigratesToProviderSetting() {
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("web_search_enabled", true).commit()

        val repository = SettingsRepository(context)

        assertEquals("openrouter", repository.getWebSearchProviderDirect())
    }

    @Test
    fun removedMonidSearchSelectionsMigrateOff() {
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit()
            .putString("web_search_provider", "monid")
            .putString("last_search_provider", "monid")
            .putString("deep_research_search_provider", "monid")
            .putString("monid_api_key", "mnid-stale")
            .commit()

        val repository = SettingsRepository(context)

        assertEquals("off", repository.getWebSearchProviderDirect())
        assertEquals("auto", repository.getDeepResearchSearchProviderDirect())
        assertEquals(null, repository.resolveChipSearchProvider())
        assertEquals("", repository.getSearchApiKeyDirect("monid"))
    }

    @Test
    fun echoCrawlIsReadyWithoutAKeyAndDoesNotChangeExistingProviders() {
        SettingsRepository(context).apply {
            saveWebSearchProvider("exa")
            saveSearchApiKey("exa", "secret")
        }
        val existing = SettingsRepository(context)
        assertEquals("exa", existing.getWebSearchProviderDirect())

        val fresh = SettingsRepository(context).also { it.saveWebSearchProvider("echocrawl") }
        assertEquals("echocrawl", fresh.getWebSearchProviderDirect())
        assertEquals("echocrawl", fresh.resolveChipSearchProvider())
        assertEquals("", fresh.getSearchApiKeyDirect("echocrawl"))
        assertTrue(com.echoflow.data.ClientSearchProviders.isReady("echocrawl", ""))
        assertFalse(com.echoflow.data.ClientSearchProviders.requiresApiKey("echocrawl"))
    }

    @Test
    fun echoCrawlIntroDismissSticksAcrossRepositoryRecreation() {
        assertFalse(SettingsRepository(context).getEchoCrawlIntroDismissedDirect())
        SettingsRepository(context).dismissEchoCrawlIntro()
        assertTrue(SettingsRepository(context).getEchoCrawlIntroDismissedDirect())
        SettingsRepository(context).dismissEchoCrawlIntro()
        assertTrue(SettingsRepository(context).echoCrawlIntroDismissed.value)
    }

    @Test
    fun echoCrawlIntroV1DismissDoesNotHideRedesignedBanner() {
        listOf("settings_prefs", "secure_settings_prefs").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("echocrawl_intro_dismissed", true)
                .commit()
        }
        assertFalse(SettingsRepository(context).getEchoCrawlIntroDismissedDirect())
    }

    @Test
    fun xAiProviderSettingsRoundTripThroughARecreatedRepository() {
        val repository = SettingsRepository(context)
        repository.saveCustomProviderConfig(
            repository.getCustomProviderConfigDirect().copy(
                cloudApisEnabled = true,
                xAiEnabled = true,
                xAiApiKey = "  xai-test-key  ",
                xAiModel = "  grok-4.5  ",
                xAiModels = "grok-4.5\ngrok-4.20",
                xAiSelectedModels = "grok-4.5",
            )
        )

        val restored = SettingsRepository(context).getCustomProviderConfigDirect()
        assertTrue(restored.cloudApisEnabled)
        assertTrue(restored.xAiEnabled)
        assertEquals("xai-test-key", restored.xAiApiKey)
        assertEquals("grok-4.5", restored.xAiModel)
        assertEquals("grok-4.5\ngrok-4.20", restored.xAiModels)
        assertEquals("grok-4.5", restored.xAiSelectedModels)
    }
}
