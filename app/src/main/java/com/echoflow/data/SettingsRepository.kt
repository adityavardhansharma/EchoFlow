package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    init {
        // One-time migration from the legacy boolean toggle to the provider-based setting.
        if (!prefs.contains(KEY_SEARCH_PROVIDER) && prefs.getBoolean("web_search_enabled", false)) {
            prefs.edit().putString(KEY_SEARCH_PROVIDER, "openrouter").apply()
        }
    }

    private val _apiKey = MutableStateFlow(getApiKeyDirect())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(getSelectedModelDirect())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _themeColor = MutableStateFlow(getThemeColorDirect())
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    private val _darkMode = MutableStateFlow(getDarkModeDirect())
    val darkMode: StateFlow<String> = _darkMode.asStateFlow() // "system", "dark", "light"

    private val _webSearchProvider = MutableStateFlow(getWebSearchProviderDirect())
    val webSearchProvider: StateFlow<String> = _webSearchProvider.asStateFlow() // "off", "openrouter", "exa", "parallel", "firecrawl"

    private val _webSearchScope = MutableStateFlow(getWebSearchScopeDirect())
    val webSearchScope: StateFlow<String> = _webSearchScope.asStateFlow() // "both", "cloud", "local"

    private val _exaApiKey = MutableStateFlow(getSearchApiKeyDirect("exa"))
    val exaApiKey: StateFlow<String> = _exaApiKey.asStateFlow()

    private val _parallelApiKey = MutableStateFlow(getSearchApiKeyDirect("parallel"))
    val parallelApiKey: StateFlow<String> = _parallelApiKey.asStateFlow()

    private val _firecrawlApiKey = MutableStateFlow(getSearchApiKeyDirect("firecrawl"))
    val firecrawlApiKey: StateFlow<String> = _firecrawlApiKey.asStateFlow()

    private val _localModelsEnabled = MutableStateFlow(getLocalModelsEnabledDirect())
    val localModelsEnabled: StateFlow<Boolean> = _localModelsEnabled.asStateFlow()

    // Deep Research
    private val _deepResearchModel = MutableStateFlow(getDeepResearchModelDirect())
    val deepResearchModel: StateFlow<String> = _deepResearchModel.asStateFlow()

    private val _deepResearchSearchProvider = MutableStateFlow(getDeepResearchSearchProviderDirect())
    val deepResearchSearchProvider: StateFlow<String> = _deepResearchSearchProvider.asStateFlow() // "auto"|"exa"|"parallel"|"firecrawl"

    private val _deepResearchMaxSearches = MutableStateFlow(getDeepResearchMaxSearchesDirect())
    val deepResearchMaxSearches: StateFlow<Int> = _deepResearchMaxSearches.asStateFlow()

    private val _deepResearchMaxSources = MutableStateFlow(getDeepResearchMaxSourcesDirect())
    val deepResearchMaxSources: StateFlow<Int> = _deepResearchMaxSources.asStateFlow()

    private val _deepResearchExaEffort = MutableStateFlow(getDeepResearchExaEffortDirect())
    val deepResearchExaEffort: StateFlow<String> = _deepResearchExaEffort.asStateFlow() // auto|low|medium|high|xhigh

    // Data Agent (Firecrawl-only extraction mode; off by default)
    private val _dataAgentEnabled = MutableStateFlow(getDataAgentEnabledDirect())
    val dataAgentEnabled: StateFlow<Boolean> = _dataAgentEnabled.asStateFlow()

    private val _dataAgentEngine = MutableStateFlow(getDataAgentEngineDirect())
    val dataAgentEngine: StateFlow<String> = _dataAgentEngine.asStateFlow()

    private val _dataAgentMaxCredits = MutableStateFlow(getDataAgentMaxCreditsDirect())
    val dataAgentMaxCredits: StateFlow<Int> = _dataAgentMaxCredits.asStateFlow()

    private val _hfAccessToken = MutableStateFlow(getHfAccessTokenDirect())
    val hfAccessToken: StateFlow<String> = _hfAccessToken.asStateFlow()

    fun getApiKeyDirect(): String {
        return prefs.getString("openrouter_api_key", "").orEmpty()
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("openrouter_api_key", key).apply()
        _apiKey.value = key
    }

    fun getSelectedModelDirect(): String {
        // Defaulting to "google/gemini-2.0-flash" which is standard and rapid
        return prefs.getString("selected_model", "google/gemini-2.0-flash").orEmpty()
    }

    fun saveSelectedModel(modelId: String) {
        prefs.edit().putString("selected_model", modelId).apply()
        _selectedModel.value = modelId
    }

    fun getThemeColorDirect(): String {
        // Default to Material You wallpaper-sampled dynamic color; Theme.kt falls back to the
        // monochrome palette automatically on devices older than Android 12.
        return prefs.getString("theme_color", "dynamic").orEmpty()
    }

    fun saveThemeColor(colorName: String) {
        prefs.edit().putString("theme_color", colorName).apply()
        _themeColor.value = colorName
    }

    fun getDarkModeDirect(): String {
        return prefs.getString("dark_mode", "system").orEmpty()
    }

    fun saveDarkMode(mode: String) {
        prefs.edit().putString("dark_mode", mode).apply()
        _darkMode.value = mode
    }

    fun getWebSearchProviderDirect(): String {
        return prefs.getString(KEY_SEARCH_PROVIDER, "off").orEmpty()
    }

    fun saveWebSearchProvider(provider: String) {
        prefs.edit().putString(KEY_SEARCH_PROVIDER, provider).apply()
        _webSearchProvider.value = provider
        // Remember the last real provider so the in-chat "+ → Web Search" toggle can turn
        // search on without the user reopening Settings.
        if (provider != "off") {
            prefs.edit().putString(KEY_LAST_SEARCH_PROVIDER, provider).apply()
        }
    }

    /**
     * The provider the in-chat Web Search toggle should use: the active provider when one
     * is on, otherwise the last one the user picked, otherwise any client provider that
     * still has a key saved. Returns null when nothing is configured.
     */
    fun resolveChipSearchProvider(): String? {
        val active = getWebSearchProviderDirect()
        if (active != "off") return active
        val last = prefs.getString(KEY_LAST_SEARCH_PROVIDER, null)
        if (!last.isNullOrBlank()) {
            if (last == "openrouter" || getSearchApiKeyDirect(last).isNotBlank()) return last
        }
        return listOf("exa", "parallel", "firecrawl").firstOrNull { getSearchApiKeyDirect(it).isNotBlank() }
    }

    fun getWebSearchScopeDirect(): String {
        return prefs.getString(KEY_SEARCH_SCOPE, "both").orEmpty()
    }

    fun saveWebSearchScope(scope: String) {
        prefs.edit().putString(KEY_SEARCH_SCOPE, scope).apply()
        _webSearchScope.value = scope
    }

    fun getSearchApiKeyDirect(provider: String): String {
        val key = when (provider) {
            "exa" -> "exa_api_key"
            "parallel" -> "parallel_api_key"
            "firecrawl" -> "firecrawl_api_key"
            else -> return ""
        }
        return prefs.getString(key, "").orEmpty()
    }

    fun saveSearchApiKey(provider: String, value: String) {
        val key = when (provider) {
            "exa" -> "exa_api_key"
            "parallel" -> "parallel_api_key"
            "firecrawl" -> "firecrawl_api_key"
            else -> return
        }
        prefs.edit().putString(key, value).apply()
        when (provider) {
            "exa" -> _exaApiKey.value = value
            "parallel" -> _parallelApiKey.value = value
            "firecrawl" -> _firecrawlApiKey.value = value
        }
    }

    fun getLocalModelsEnabledDirect(): Boolean {
        return prefs.getBoolean("local_models_enabled", false)
    }

    fun saveLocalModelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("local_models_enabled", enabled).apply()
        _localModelsEnabled.value = enabled
    }

    fun getHfAccessTokenDirect(): String {
        return prefs.getString("hf_access_token", "").orEmpty()
    }

    fun saveHfAccessToken(token: String) {
        prefs.edit().putString("hf_access_token", token).apply()
        _hfAccessToken.value = token
    }

    // ── Deep Research ────────────────────────────────────────────────────────────────

    fun getDeepResearchModelDirect(): String =
        prefs.getString("deep_research_model", "").orEmpty()

    fun saveDeepResearchModel(id: String) {
        prefs.edit().putString("deep_research_model", id).apply()
        _deepResearchModel.value = id
    }

    fun getDeepResearchSearchProviderDirect(): String =
        prefs.getString("deep_research_search_provider", "auto").orEmpty()

    fun saveDeepResearchSearchProvider(provider: String) {
        prefs.edit().putString("deep_research_search_provider", provider).apply()
        _deepResearchSearchProvider.value = provider
    }

    fun getDeepResearchMaxSearchesDirect(): Int =
        prefs.getInt("deep_research_max_searches", 5)

    fun saveDeepResearchMaxSearches(value: Int) {
        prefs.edit().putInt("deep_research_max_searches", value).apply()
        _deepResearchMaxSearches.value = value
    }

    fun getDeepResearchMaxSourcesDirect(): Int =
        prefs.getInt("deep_research_max_sources", 20)

    fun saveDeepResearchMaxSources(value: Int) {
        prefs.edit().putInt("deep_research_max_sources", value).apply()
        _deepResearchMaxSources.value = value
    }

    fun getDeepResearchExaEffortDirect(): String =
        prefs.getString("deep_research_exa_effort", "auto").orEmpty()

    fun saveDeepResearchExaEffort(value: String) {
        prefs.edit().putString("deep_research_exa_effort", value).apply()
        _deepResearchExaEffort.value = value
    }

    // ── Data Agent ───────────────────────────────────────────────────────────────────

    fun getDataAgentEnabledDirect(): Boolean =
        prefs.getBoolean("data_agent_enabled", false)

    fun saveDataAgentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("data_agent_enabled", enabled).apply()
        _dataAgentEnabled.value = enabled
    }

    fun getDataAgentEngineDirect(): String =
        prefs.getString("data_agent_engine", "").orEmpty()

    fun saveDataAgentEngine(id: String) {
        prefs.edit().putString("data_agent_engine", id).apply()
        _dataAgentEngine.value = id
    }

    fun getDataAgentMaxCreditsDirect(): Int =
        prefs.getInt("data_agent_max_credits", 2500)

    fun saveDataAgentMaxCredits(value: Int) {
        prefs.edit().putInt("data_agent_max_credits", value).apply()
        _dataAgentMaxCredits.value = value
    }

    companion object {
        private const val KEY_SEARCH_PROVIDER = "web_search_provider"
        private const val KEY_SEARCH_SCOPE = "web_search_scope"
        private const val KEY_LAST_SEARCH_PROVIDER = "last_search_provider"
        const val DEFAULT_MODEL_ID = "google/gemini-2.0-flash"
    }
}
