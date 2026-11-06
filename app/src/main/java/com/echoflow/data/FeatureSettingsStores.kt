package com.echoflow.data

import android.content.SharedPreferences

/*
 * Plain read/write stores for groups of settings, split out of [SettingsRepository] the way
 * [CustomProviderSettingsStore] was. Each owns its preference keys and defaults; the repository
 * keeps the flows and calls these. They read on demand and hold no state, so creating one before
 * the repository's migrations have run is safe. Keys are unchanged, so existing installs read the
 * same values.
 */

/** The sampler settings: one set for on-device models, one for cloud models. */
internal class InferenceParamsStore(private val prefs: SharedPreferences) {
    fun read(local: Boolean): InferenceParams {
        val defaults = defaults(local)
        val p = prefix(local)
        return InferenceParams(
            temperature = prefs.getFloat("ip_${p}_temperature", defaults.temperature),
            topK = prefs.getInt("ip_${p}_top_k", defaults.topK),
            topP = prefs.getFloat("ip_${p}_top_p", defaults.topP),
            maxTokens = prefs.getInt("ip_${p}_max_tokens", defaults.maxTokens),
        )
    }

    fun write(local: Boolean, params: InferenceParams) {
        val p = prefix(local)
        prefs.edit()
            .putFloat("ip_${p}_temperature", params.temperature)
            .putInt("ip_${p}_top_k", params.topK)
            .putFloat("ip_${p}_top_p", params.topP)
            .putInt("ip_${p}_max_tokens", params.maxTokens)
            .apply()
    }

    fun defaults(local: Boolean): InferenceParams =
        if (local) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS

    private fun prefix(local: Boolean) = if (local) "local" else "cloud"
}

/** Deep Research's model, search provider and budgets. */
internal class DeepResearchSettingsStore(private val prefs: SharedPreferences) {
    fun model(): String = prefs.getString(KEY_MODEL, "").orEmpty()
    fun setModel(id: String) = prefs.edit().putString(KEY_MODEL, id).apply()

    /** The saved provider, or "auto" for anything this build no longer offers. */
    fun searchProvider(): String {
        val stored = prefs.getString(KEY_SEARCH_PROVIDER, "auto").orEmpty()
        return if (stored in SEARCH_PROVIDERS) stored else "auto"
    }
    fun setSearchProvider(provider: String) = prefs.edit().putString(KEY_SEARCH_PROVIDER, provider).apply()

    fun maxSearches(): Int = prefs.getInt(KEY_MAX_SEARCHES, 5)
    fun setMaxSearches(value: Int) = prefs.edit().putInt(KEY_MAX_SEARCHES, value).apply()

    fun maxSources(): Int = prefs.getInt(KEY_MAX_SOURCES, 20)
    fun setMaxSources(value: Int) = prefs.edit().putInt(KEY_MAX_SOURCES, value).apply()

    fun exaEffort(): String = prefs.getString(KEY_EXA_EFFORT, "auto").orEmpty()
    fun setExaEffort(value: String) = prefs.edit().putString(KEY_EXA_EFFORT, value).apply()

    companion object {
        const val KEY_SEARCH_PROVIDER = "deep_research_search_provider"
        private const val KEY_MODEL = "deep_research_model"
        private const val KEY_MAX_SEARCHES = "deep_research_max_searches"
        private const val KEY_MAX_SOURCES = "deep_research_max_sources"
        private const val KEY_EXA_EFFORT = "deep_research_exa_effort"
        val SEARCH_PROVIDERS = setOf("auto", "exa", "parallel", "firecrawl")
    }
}
