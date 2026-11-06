package com.echoflow.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeatureSettingsStoresTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("feature-stores-test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun inferenceParamsFallBackToEachSidesDefaults() {
        val store = InferenceParamsStore(prefs)
        assertEquals(InferenceLimits.LOCAL_DEFAULTS, store.read(local = true))
        assertEquals(InferenceLimits.CLOUD_DEFAULTS, store.read(local = false))
    }

    @Test
    fun inferenceParamsSidesAreKeptApart() {
        val store = InferenceParamsStore(prefs)
        val tuned = InferenceLimits.LOCAL_DEFAULTS.copy(temperature = 0.2f, maxTokens = 321)
        store.write(local = true, tuned)
        assertEquals(tuned, store.read(local = true))
        assertEquals(InferenceLimits.CLOUD_DEFAULTS, store.read(local = false))
        // Same keys as before the split, so upgrades keep the user's values.
        assertEquals(321, prefs.getInt("ip_local_max_tokens", -1))
    }

    @Test
    fun deepResearchDefaultsAndRoundTrip() {
        val store = DeepResearchSettingsStore(prefs)
        assertEquals("", store.model())
        assertEquals("auto", store.searchProvider())
        assertEquals(5, store.maxSearches())
        assertEquals(20, store.maxSources())
        assertEquals("auto", store.exaEffort())

        store.setModel("m")
        store.setSearchProvider("exa")
        store.setMaxSearches(9)
        store.setMaxSources(40)
        store.setExaEffort("high")
        assertEquals("m", store.model())
        assertEquals("exa", store.searchProvider())
        assertEquals(9, store.maxSearches())
        assertEquals(40, store.maxSources())
        assertEquals("high", store.exaEffort())
    }

    @Test
    fun unknownDeepResearchProviderReadsAsAuto() {
        prefs.edit().putString("deep_research_search_provider", "monid").commit()
        assertEquals("auto", DeepResearchSettingsStore(prefs).searchProvider())
    }
}
