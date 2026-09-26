package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPrefs: SharedPreferences = SettingsPreferenceStorage.legacy(context)
    private val prefs: SharedPreferences = SettingsPreferenceStorage.secureOrNull(context) ?: legacyPrefs

    init {
        SettingsPreferenceStorage.migrateLegacyIfNeeded(legacyPrefs, prefs)
        // One-time migration from the legacy boolean toggle to the provider-based setting.
        if (!prefs.contains(KEY_SEARCH_PROVIDER) && prefs.getBoolean("web_search_enabled", false)) {
            prefs.edit().putString(KEY_SEARCH_PROVIDER, "openrouter").apply()
        }
        migrateRemovedMonidSearchSelections()
    }

    // Non-secret, shared across repository instances: service auto-off reaches Settings immediately.
    private val dictationPrefs = context.getSharedPreferences("system_dictation", Context.MODE_PRIVATE).also {
        if (it.getBoolean("system_wide_dictation", false) && !SystemDictationPermissions.granted(context)) {
            it.edit().putBoolean("system_wide_dictation", false).apply()
        }
    }
    val systemWideDictation = kotlinx.coroutines.flow.callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "system_wide_dictation") trySend(getSystemWideDictationDirect())
        }
        dictationPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(getSystemWideDictationDirect())
        awaitClose { dictationPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun getSystemWideDictationDirect() = dictationPrefs.getBoolean("system_wide_dictation", false)
    fun saveSystemWideDictation(enabled: Boolean) {
        dictationPrefs.edit().putBoolean("system_wide_dictation", enabled).apply()
    }

    // Observe the backing stores, not an EncryptedSharedPreferences wrapper: the UI and service
    // own different wrappers. Their encrypted-key notifications are invalidations, never key names
    // to decrypt on the UI thread. Consumers read only the small STT snapshot on Dispatchers.IO.
    internal val dictationConfigurationChanges = kotlinx.coroutines.flow.callbackFlow {
        val stores = listOf(legacyPrefs, appContext.getSharedPreferences(
            SettingsPreferenceStorage.SECURE_FILE, Context.MODE_PRIVATE))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        stores.forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        trySend(Unit)
        awaitClose { stores.forEach { it.unregisterOnSharedPreferenceChangeListener(listener) } }
    }.buffer(kotlinx.coroutines.channels.Channel.CONFLATED)

    /** A read-only snapshot: no full provider catalog, unrelated keys, or migration writes. */
    internal fun getDictationConfiguration(): DictationConfiguration {
        val cloudApis = prefs.getBoolean("labs_cloud_apis_enabled", false)
        val sarvam = cloudApis && prefs.getBoolean("direct_sarvam_enabled", false)
        val deepgram = cloudApis && prefs.getBoolean("direct_deepgram_enabled", false)
        val stored = prefs.getString("stt_cloud_model", SttCatalog.DEFAULT_MODEL_ID).orEmpty()
        // Direct-provider models read only their own key, and only while that provider is on.
        val directKey = when {
            stored == SttCatalog.SARVAM_MODEL_ID && sarvam ->
                prefs.getString("direct_sarvam_api_key", "").orEmpty()
            stored == SttCatalog.DEEPGRAM_MODEL_ID && deepgram ->
                prefs.getString("direct_deepgram_api_key", "").orEmpty()
            else -> ""
        }
        val model = if (SttCatalog.usesDirectKey(stored) && directKey.isBlank())
            SttCatalog.DEFAULT_MODEL_ID else SttCatalog.resolve(stored).id
        val key = if (SttCatalog.usesDirectKey(model)) directKey else getApiKeyDirect()
        // System-wide dictation remains cloud-backed while on-device transcription is only a
        // settings preview. Switching that preview must not disable the user's system-wide opt-in.
        // Hinglish romanizes any model's output, so it needs only a live Sarvam key.
        val sarvamKey = if (sarvam) prefs.getString("direct_sarvam_api_key", "").orEmpty() else ""
        return DictationConfiguration(model, key,
            sarvamKey.isNotBlank() && getSarvamHinglishEnabledDirect(),
            if (SttCatalog.supportsCustomVocabulary(model)) getSttVocabularyDirect() else emptyList(),
            sarvamKey)
    }
    fun getDictationBubbleRight() = dictationPrefs.getBoolean("bubble_right", true)
    fun getDictationBubbleY() = dictationPrefs.getFloat("bubble_y", 0.5f).let {
        if (it.isFinite()) it.coerceIn(0f, 1f) else 0.5f
    }
    fun saveDictationBubblePosition(right: Boolean, y: Float) {
        dictationPrefs.edit().putBoolean("bubble_right", right)
            .putFloat("bubble_y", if (y.isFinite()) y.coerceIn(0f, 1f) else 0.5f).apply()
    }

    private val _apiKey = MutableStateFlow(getApiKeyDirect())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(getSelectedModelDirect())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _defaultModel = MutableStateFlow(getDefaultModelDirect())
    /** The model every new chat starts on, or null (the out-of-box state) to keep the last one used. */
    val defaultModel: StateFlow<String?> = _defaultModel.asStateFlow()

    private val _themeColor = MutableStateFlow(getThemeColorDirect())
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    private val _darkMode = MutableStateFlow(getDarkModeDirect())
    val darkMode: StateFlow<String> = _darkMode.asStateFlow() // "system", "dark", "light"

    private val _webSearchProvider = MutableStateFlow(getWebSearchProviderDirect())
    val webSearchProvider: StateFlow<String> = _webSearchProvider.asStateFlow() // "off", "openrouter", plus [ClientSearchProviders.chatIds]

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

    // GGUF (llama.cpp) is opt-in: it's a community format with variable quality and a
    // separate CPU-only runtime, so it stays off until the user enables it.
    private val _ggufEnabled = MutableStateFlow(getGgufEnabledDirect())
    val ggufEnabled: StateFlow<Boolean> = _ggufEnabled.asStateFlow()

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

    // Browser Flow (Firecrawl Interact stateful browser; gated additionally on a Firecrawl key)
    private val _browserFlowEnabled = MutableStateFlow(getBrowserFlowEnabledDirect())
    val browserFlowEnabled: StateFlow<Boolean> = _browserFlowEnabled.asStateFlow()

    private val _browserIdleMinutes = MutableStateFlow(getBrowserIdleMinutesDirect())
    val browserIdleMinutes: StateFlow<Int> = _browserIdleMinutes.asStateFlow()

    // Artifacts: when on, the artifact prompt forbids CDNs so generated HTML renders fully offline.
    private val _artifactsOffline = MutableStateFlow(getArtifactsOfflineDirect())
    val artifactsOffline: StateFlow<Boolean> = _artifactsOffline.asStateFlow()

    // Speech to text (composer dictation). Cloud only for now; on-device is "coming soon".
    // Runs on OpenRouter with the same key as Cloud models — see [SttCatalog].
    private val _sttMode = MutableStateFlow(getSttModeDirect())
    val sttMode: StateFlow<SttMode> = _sttMode.asStateFlow()

    private val _sttCloudModel = MutableStateFlow(getSttCloudModelDirect())
    val sttCloudModel: StateFlow<String> = _sttCloudModel.asStateFlow()

    // Hinglish: when on and a Sarvam key is saved, any dictation model's Devanagari output is
    // romanized to Latin script via Sarvam's `/transliterate`. Other scripts are untouched.
    private val _sarvamHinglishEnabled = MutableStateFlow(getSarvamHinglishEnabledDirect())
    val sarvamHinglishEnabled: StateFlow<Boolean> = _sarvamHinglishEnabled.asStateFlow()

    // Custom vocabulary: names and terms sent to dictation models that accept keyword biasing.
    // Kept independently of the selected model so switching away and back never loses the list.
    private val _sttVocabulary = MutableStateFlow(getSttVocabularyDirect())
    val sttVocabulary: StateFlow<List<String>> = _sttVocabulary.asStateFlow()

    // Which surface the app is on. Persisted so a relaunch resumes where the user left off.
    private val _appMode = MutableStateFlow(getAppModeDirect())
    val appMode: StateFlow<AppMode> = _appMode.asStateFlow()

    // What Imagine is making. Sticky, because a run of clips should not need re-choosing.
    private val _imagineMedia = MutableStateFlow(getImagineMediaDirect())
    val imagineMedia: StateFlow<ImagineMedia> = _imagineMedia.asStateFlow()

    // Framing for image turns. Video keeps its own, since the two have different model sets
    // and an aspect ratio a video model accepts is not one an image model necessarily does.
    private val _imageAspectRatio = MutableStateFlow(getImageAspectRatioDirect())
    val imageAspectRatio: StateFlow<String> = _imageAspectRatio.asStateFlow()

    // Which image-output OpenRouter model an image turn uses.
    private val _imageGenModel = MutableStateFlow(getImageGenModelDirect())
    val imageGenModel: StateFlow<String> = _imageGenModel.asStateFlow()

    // Video generation: OpenRouter-only. Framing and audio are the user's; length is the
    // model's — there is no duration preference here on purpose.
    private val _videoGenModel = MutableStateFlow(getVideoGenModelDirect())
    val videoGenModel: StateFlow<String> = _videoGenModel.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(getVideoAspectRatioDirect())
    val videoAspectRatio: StateFlow<String> = _videoAspectRatio.asStateFlow()

    private val _videoResolution = MutableStateFlow(getVideoResolutionDirect())
    val videoResolution: StateFlow<String> = _videoResolution.asStateFlow()

    private val _videoAudioEnabled = MutableStateFlow(getVideoAudioEnabledDirect())
    val videoAudioEnabled: StateFlow<Boolean> = _videoAudioEnabled.asStateFlow()

    // Echo Adviser / Echo Fusion: which saved profile/panel is currently active in chat.
    private val _echoAdviserProfileId = MutableStateFlow(getEchoAdviserProfileIdDirect())
    val echoAdviserProfileId: StateFlow<String> = _echoAdviserProfileId.asStateFlow()

    private val _echoFusionPanelId = MutableStateFlow(getEchoFusionPanelIdDirect())
    val echoFusionPanelId: StateFlow<String> = _echoFusionPanelId.asStateFlow()

    private val _echoAgentProfileId = MutableStateFlow(getEchoAgentProfileIdDirect())
    val echoAgentProfileId: StateFlow<String> = _echoAgentProfileId.asStateFlow()

    // Echo Labs master switches: each experimental mode can be turned off entirely (hides it
    // from the in-chat "+" menu). Stable modes default on; the beta one (Browser Flow) is opt-in.
    private val _echoAdviserEnabled = MutableStateFlow(getEchoAdviserEnabledDirect())
    val echoAdviserEnabled: StateFlow<Boolean> = _echoAdviserEnabled.asStateFlow()

    private val _echoFusionEnabled = MutableStateFlow(getEchoFusionEnabledDirect())
    val echoFusionEnabled: StateFlow<Boolean> = _echoFusionEnabled.asStateFlow()

    private val _echoAgentEnabled = MutableStateFlow(getEchoAgentEnabledDirect())
    val echoAgentEnabled: StateFlow<Boolean> = _echoAgentEnabled.asStateFlow()

    // Jev Router (TypeSafe System One classifier; Echo Labs opt-in, cloud chats only).
    private val _jevEnabled = MutableStateFlow(getJevEnabledDirect())
    val jevEnabled: StateFlow<Boolean> = _jevEnabled.asStateFlow()

    private val _jevApiKey = MutableStateFlow(getJevApiKeyDirect())
    val jevApiKey: StateFlow<String> = _jevApiKey.asStateFlow()

    // Inference parameters: one global set for on-device models, one for OpenRouter models.
    private val _localInferenceParams = MutableStateFlow(getInferenceParamsDirect(local = true))
    val localInferenceParams: StateFlow<InferenceParams> = _localInferenceParams.asStateFlow()

    private val _cloudInferenceParams = MutableStateFlow(getInferenceParamsDirect(local = false))
    val cloudInferenceParams: StateFlow<InferenceParams> = _cloudInferenceParams.asStateFlow()

    private val _customProviderConfig = MutableStateFlow(getCustomProviderConfigDirect())
    val customProviderConfig: StateFlow<CustomProviderConfig> = _customProviderConfig.asStateFlow()

    fun getApiKeyDirect(): String {
        return prefs.getString("openrouter_api_key", "").orEmpty()
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("openrouter_api_key", key).apply()
        _apiKey.value = key
    }

    fun getSelectedModelDirect(): String {
        return prefs.getString("selected_model", DefaultChatModels.DEFAULT_MODEL_ID).orEmpty()
    }

    fun saveSelectedModel(modelId: String) {
        prefs.edit().putString("selected_model", modelId).apply()
        _selectedModel.value = modelId
    }

    fun getDefaultModelDirect(): String? =
        prefs.getString("default_model", null)?.takeIf { it.isNotBlank() }

    fun saveDefaultModel(modelId: String?) {
        prefs.edit().apply { if (modelId == null) remove("default_model") else putString("default_model", modelId) }.apply()
        _defaultModel.value = modelId
    }

    fun getThemeColorDirect(): String {
        // Default to Material You wallpaper-sampled dynamic color; Theme.kt falls back to Ocean
        // on devices older than Android 12.
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
        val stored = prefs.getString(KEY_SEARCH_PROVIDER, "off").orEmpty()
        return if (stored in WEB_SEARCH_PROVIDERS) stored else "off"
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
        if (!last.isNullOrBlank() && last in WEB_SEARCH_PROVIDERS) {
            if (last == "openrouter" ||
                last == ClientSearchProviders.ECHOCRAWL ||
                getSearchApiKeyDirect(last).isNotBlank()
            ) {
                return last
            }
        }
        return ClientSearchProviders.keyedIds.firstOrNull { getSearchApiKeyDirect(it).isNotBlank() }
    }

    fun getWebSearchScopeDirect(): String {
        return prefs.getString(KEY_SEARCH_SCOPE, "both").orEmpty()
    }

    fun saveWebSearchScope(scope: String) {
        prefs.edit().putString(KEY_SEARCH_SCOPE, scope).apply()
        _webSearchScope.value = scope
    }

    private val _echoCrawlIntroDismissed = MutableStateFlow(getEchoCrawlIntroDismissedDirect())
    val echoCrawlIntroDismissed: StateFlow<Boolean> = _echoCrawlIntroDismissed.asStateFlow()

    fun getEchoCrawlIntroDismissedDirect(): Boolean =
        prefs.getBoolean(KEY_ECHOCRAWL_INTRO_DISMISSED, false)

    /** Either intro action (open Settings or Don't show again) calls this — this banner version never returns. */
    fun dismissEchoCrawlIntro() {
        prefs.edit().putBoolean(KEY_ECHOCRAWL_INTRO_DISMISSED, true).apply()
        _echoCrawlIntroDismissed.value = true
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

    fun getGgufEnabledDirect(): Boolean {
        return prefs.getBoolean("gguf_enabled", false)
    }

    fun saveGgufEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("gguf_enabled", enabled).apply()
        _ggufEnabled.value = enabled
    }

    // Lives in its own prefs file so the process-wide local engine sees changes immediately.
    val keepLocalModelLoaded: Flow<Boolean> = LocalModelResidency.keepLoadedChanges(appContext)
    fun getKeepLocalModelLoadedDirect(): Boolean = LocalModelResidency.keepLoaded(appContext)
    fun saveKeepLocalModelLoaded(enabled: Boolean) = LocalModelResidency.setKeepLoaded(appContext, enabled)

    fun getHfAccessTokenDirect(): String {
        return prefs.getString("hf_access_token", "").orEmpty()
    }

    fun saveHfAccessToken(token: String) {
        prefs.edit().putString("hf_access_token", token).apply()
        _hfAccessToken.value = token
    }

    // ── Echo Adviser / Echo Fusion selection ───────────────────────────────────────────

    fun getEchoAdviserProfileIdDirect(): String =
        prefs.getString("echo_adviser_profile_id", "").orEmpty()

    fun saveEchoAdviserProfileId(id: String) {
        prefs.edit().putString("echo_adviser_profile_id", id).apply()
        _echoAdviserProfileId.value = id
    }

    fun getEchoFusionPanelIdDirect(): String =
        prefs.getString("echo_fusion_panel_id", "").orEmpty()

    fun saveEchoFusionPanelId(id: String) {
        prefs.edit().putString("echo_fusion_panel_id", id).apply()
        _echoFusionPanelId.value = id
    }

    fun getEchoAgentProfileIdDirect(): String =
        prefs.getString("echo_agent_profile_id", "").orEmpty()

    fun saveEchoAgentProfileId(id: String) {
        prefs.edit().putString("echo_agent_profile_id", id).apply()
        _echoAgentProfileId.value = id
    }

    // ── Inference parameters ───────────────────────────────────────────────────────────

    /**
     * Reads the stored sampler params for one side, falling back to the shipped defaults for
     * any value never set. [local] picks the on-device set; otherwise the OpenRouter set.
     */
    fun getInferenceParamsDirect(local: Boolean): InferenceParams {
        val defaults = if (local) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS
        val p = if (local) "local" else "cloud"
        return InferenceParams(
            temperature = prefs.getFloat("ip_${p}_temperature", defaults.temperature),
            topK = prefs.getInt("ip_${p}_top_k", defaults.topK),
            topP = prefs.getFloat("ip_${p}_top_p", defaults.topP),
            maxTokens = prefs.getInt("ip_${p}_max_tokens", defaults.maxTokens),
        )
    }

    fun saveInferenceParams(local: Boolean, params: InferenceParams) {
        val p = if (local) "local" else "cloud"
        prefs.edit()
            .putFloat("ip_${p}_temperature", params.temperature)
            .putInt("ip_${p}_top_k", params.topK)
            .putFloat("ip_${p}_top_p", params.topP)
            .putInt("ip_${p}_max_tokens", params.maxTokens)
            .apply()
        if (local) _localInferenceParams.value = params else _cloudInferenceParams.value = params
    }

    fun resetInferenceParams(local: Boolean) {
        saveInferenceParams(local, if (local) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS)
    }

    // ── Custom provider (Echo Labs prerelease) ────────────────────────────────────────

    fun getCustomProviderConfigDirect(): CustomProviderConfig =
        CustomProviderConfig(
            cloudApisEnabled = prefs.getBoolean("labs_cloud_apis_enabled", false),
            ollamaEnabled = prefs.getBoolean("labs_ollama_enabled", false),
            openAiCompatibleEnabled = prefs.getBoolean("labs_openai_compatible_enabled", false),
            openAiEnabled = prefs.getBoolean("direct_openai_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            openAiApiKey = prefs.getString("direct_openai_api_key", "").orEmpty(),
            openAiModel = prefs.getString("direct_openai_model", "").orEmpty(),
            openAiModels = prefs.getString("direct_openai_models", "").orEmpty(),
            openAiSelectedModels = prefs.getString("direct_openai_selected_models", "").orEmpty(),
            claudeEnabled = prefs.getBoolean("direct_claude_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            claudeApiKey = prefs.getString("direct_claude_api_key", "").orEmpty(),
            claudeModel = prefs.getString("direct_claude_model", "").orEmpty(),
            claudeModels = prefs.getString("direct_claude_models", "").orEmpty(),
            claudeSelectedModels = prefs.getString("direct_claude_selected_models", "").orEmpty(),
            geminiEnabled = prefs.getBoolean("direct_gemini_enabled", prefs.getBoolean("labs_cloud_apis_enabled", false)),
            geminiApiKey = prefs.getString("direct_gemini_api_key", "").orEmpty(),
            geminiModel = prefs.getString("direct_gemini_model", "").orEmpty(),
            geminiModels = prefs.getString("direct_gemini_models", "").orEmpty(),
            geminiSelectedModels = prefs.getString("direct_gemini_selected_models", "").orEmpty(),
            cerebrasEnabled = prefs.getBoolean("direct_cerebras_enabled", false),
            cerebrasApiKey = prefs.getString("direct_cerebras_api_key", "").orEmpty(),
            cerebrasModel = prefs.getString("direct_cerebras_model", "").orEmpty(),
            cerebrasModels = prefs.getString("direct_cerebras_models", "").orEmpty(),
            cerebrasSelectedModels = prefs.getString("direct_cerebras_selected_models", "").orEmpty(),
            sarvamEnabled = prefs.getBoolean("direct_sarvam_enabled", false),
            sarvamApiKey = prefs.getString("direct_sarvam_api_key", "").orEmpty(),
            sarvamModel = prefs.getString("direct_sarvam_model", "").orEmpty(),
            sarvamModels = prefs.getString("direct_sarvam_models", "sarvam-105b\nsarvam-105b-conversations").orEmpty(),
            sarvamSelectedModels = prefs.getString("direct_sarvam_selected_models", "sarvam-105b").orEmpty(),
            deepgramEnabled = prefs.getBoolean("direct_deepgram_enabled", false),
            deepgramApiKey = prefs.getString("direct_deepgram_api_key", "").orEmpty(),
            xAiEnabled = prefs.getBoolean("direct_xai_enabled", false),
            xAiApiKey = prefs.getString("direct_xai_api_key", "").orEmpty(),
            xAiModel = prefs.getString("direct_xai_model", "").orEmpty(),
            xAiModels = prefs.getString("direct_xai_models", "").orEmpty(),
            xAiSelectedModels = prefs.getString("direct_xai_selected_models", "").orEmpty(),
            vercelEnabled = prefs.getBoolean("direct_vercel_enabled", false),
            vercelApiKey = prefs.getString("direct_vercel_api_key", "").orEmpty(),
            vercelModel = prefs.getString("direct_vercel_model", "").orEmpty(),
            vercelModels = prefs.getString("direct_vercel_models", "").orEmpty(),
            vercelSelectedModels = prefs.getString("direct_vercel_selected_models", "").orEmpty(),
            groqEnabled = prefs.getBoolean("direct_groq_enabled", false),
            groqApiKey = prefs.getString("direct_groq_api_key", "").orEmpty(),
            groqModel = prefs.getString("direct_groq_model", "").orEmpty(),
            groqModels = prefs.getString("direct_groq_models", "").orEmpty(),
            groqSelectedModels = prefs.getString("direct_groq_selected_models", "").orEmpty(),
            togetherEnabled = prefs.getBoolean("direct_together_enabled", false),
            togetherApiKey = prefs.getString("direct_together_api_key", "").orEmpty(),
            togetherModel = prefs.getString("direct_together_model", "").orEmpty(),
            togetherModels = prefs.getString("direct_together_models", "").orEmpty(),
            togetherSelectedModels = prefs.getString("direct_together_selected_models", "").orEmpty(),
            cloudflareEnabled = prefs.getBoolean("direct_cloudflare_enabled", false),
            cloudflareApiKey = prefs.getString("direct_cloudflare_api_key", "").orEmpty(),
            cloudflareModel = prefs.getString("direct_cloudflare_model", "").orEmpty(),
            cloudflareModels = prefs.getString("direct_cloudflare_models", "").orEmpty(),
            cloudflareSelectedModels = prefs.getString("direct_cloudflare_selected_models", "").orEmpty(),
            cloudflareAccountId = prefs.getString("direct_cloudflare_account_id", "").orEmpty(),
            ollamaBaseUrl = prefs.getString("ollama_base_url", "http://localhost:11434").orEmpty(),
            ollamaModel = prefs.getString("ollama_model", "").orEmpty(),
            ollamaModels = prefs.getString("ollama_models", "").orEmpty(),
            ollamaSelectedModels = prefs.getString("ollama_selected_models", "").orEmpty(),
            ollamaImagesEnabled = prefs.getBoolean("ollama_images_enabled", false),
            ollamaPdfsEnabled = prefs.getBoolean("ollama_pdfs_enabled", false),
            ollamaToolCallingEnabled = prefs.getBoolean("ollama_tool_calling_enabled", false),
            openAiBaseUrl = prefs.getString("openai_compatible_base_url", "http://localhost:1234/v1").orEmpty(),
            openAiCompatibleApiKey = prefs.getString("openai_compatible_api_key", "").orEmpty(),
            openAiCompatibleModel = prefs.getString("openai_compatible_model", "").orEmpty(),
            openAiCompatibleModels = prefs.getString("openai_compatible_models", "").orEmpty(),
            openAiCompatibleSelectedModels = prefs.getString("openai_compatible_selected_models", "").orEmpty(),
            openAiCompatibleImagesEnabled = prefs.getBoolean("openai_compatible_images_enabled", false),
            openAiCompatiblePdfsEnabled = prefs.getBoolean("openai_compatible_pdfs_enabled", false),
            openAiCompatibleToolCallingEnabled = prefs.getBoolean("openai_compatible_tool_calling_enabled", false),
        )

    fun saveCustomProviderConfig(config: CustomProviderConfig) {
        val clean = config.copy(
            openAiApiKey = config.openAiApiKey.trim(),
            openAiModel = config.openAiModel.trim(),
            claudeApiKey = config.claudeApiKey.trim(),
            claudeModel = config.claudeModel.trim(),
            geminiApiKey = config.geminiApiKey.trim(),
            geminiModel = config.geminiModel.trim(),
            cerebrasApiKey = config.cerebrasApiKey.trim(),
            cerebrasModel = config.cerebrasModel.trim(),
            sarvamApiKey = config.sarvamApiKey.trim(),
            sarvamModel = config.sarvamModel.trim(),
            deepgramApiKey = config.deepgramApiKey.trim(),
            xAiApiKey = config.xAiApiKey.trim(),
            xAiModel = config.xAiModel.trim(),
            vercelApiKey = config.vercelApiKey.trim(),
            vercelModel = config.vercelModel.trim(),
            groqApiKey = config.groqApiKey.trim(),
            groqModel = config.groqModel.trim(),
            togetherApiKey = config.togetherApiKey.trim(),
            togetherModel = config.togetherModel.trim(),
            cloudflareApiKey = config.cloudflareApiKey.trim(),
            cloudflareModel = config.cloudflareModel.trim(),
            cloudflareAccountId = config.cloudflareAccountId.trim(),
            ollamaBaseUrl = config.ollamaBaseUrl.trim(),
            ollamaModel = config.ollamaModel.trim(),
            openAiBaseUrl = config.openAiBaseUrl.trim(),
            openAiCompatibleApiKey = config.openAiCompatibleApiKey.trim(),
            openAiCompatibleModel = config.openAiCompatibleModel.trim(),
        )
        prefs.edit()
            .putBoolean("labs_cloud_apis_enabled", clean.cloudApisEnabled)
            .putBoolean("labs_ollama_enabled", clean.ollamaEnabled)
            .putBoolean("labs_openai_compatible_enabled", clean.openAiCompatibleEnabled)
            .putBoolean("direct_openai_enabled", clean.openAiEnabled)
            .putString("direct_openai_api_key", clean.openAiApiKey)
            .putString("direct_openai_model", clean.openAiModel)
            .putString("direct_openai_models", clean.openAiModels)
            .putString("direct_openai_selected_models", clean.openAiSelectedModels)
            .putBoolean("direct_claude_enabled", clean.claudeEnabled)
            .putString("direct_claude_api_key", clean.claudeApiKey)
            .putString("direct_claude_model", clean.claudeModel)
            .putString("direct_claude_models", clean.claudeModels)
            .putString("direct_claude_selected_models", clean.claudeSelectedModels)
            .putBoolean("direct_gemini_enabled", clean.geminiEnabled)
            .putString("direct_gemini_api_key", clean.geminiApiKey)
            .putString("direct_gemini_model", clean.geminiModel)
            .putString("direct_gemini_models", clean.geminiModels)
            .putString("direct_gemini_selected_models", clean.geminiSelectedModels)
            .putBoolean("direct_cerebras_enabled", clean.cerebrasEnabled)
            .putString("direct_cerebras_api_key", clean.cerebrasApiKey)
            .putString("direct_cerebras_model", clean.cerebrasModel)
            .putString("direct_cerebras_models", clean.cerebrasModels)
            .putString("direct_cerebras_selected_models", clean.cerebrasSelectedModels)
            .putBoolean("direct_sarvam_enabled", clean.sarvamEnabled)
            .putString("direct_sarvam_api_key", clean.sarvamApiKey)
            .putString("direct_sarvam_model", clean.sarvamModel)
            .putString("direct_sarvam_models", clean.sarvamModels)
            .putString("direct_sarvam_selected_models", clean.sarvamSelectedModels)
            .putBoolean("direct_deepgram_enabled", clean.deepgramEnabled)
            .putString("direct_deepgram_api_key", clean.deepgramApiKey)
            .putBoolean("direct_xai_enabled", clean.xAiEnabled)
            .putString("direct_xai_api_key", clean.xAiApiKey)
            .putString("direct_xai_model", clean.xAiModel)
            .putString("direct_xai_models", clean.xAiModels)
            .putString("direct_xai_selected_models", clean.xAiSelectedModels)
            .putBoolean("direct_vercel_enabled", clean.vercelEnabled)
            .putString("direct_vercel_api_key", clean.vercelApiKey)
            .putString("direct_vercel_model", clean.vercelModel)
            .putString("direct_vercel_models", clean.vercelModels)
            .putString("direct_vercel_selected_models", clean.vercelSelectedModels)
            .putBoolean("direct_groq_enabled", clean.groqEnabled)
            .putString("direct_groq_api_key", clean.groqApiKey)
            .putString("direct_groq_model", clean.groqModel)
            .putString("direct_groq_models", clean.groqModels)
            .putString("direct_groq_selected_models", clean.groqSelectedModels)
            .putBoolean("direct_together_enabled", clean.togetherEnabled)
            .putString("direct_together_api_key", clean.togetherApiKey)
            .putString("direct_together_model", clean.togetherModel)
            .putString("direct_together_models", clean.togetherModels)
            .putString("direct_together_selected_models", clean.togetherSelectedModels)
            .putBoolean("direct_cloudflare_enabled", clean.cloudflareEnabled)
            .putString("direct_cloudflare_api_key", clean.cloudflareApiKey)
            .putString("direct_cloudflare_model", clean.cloudflareModel)
            .putString("direct_cloudflare_models", clean.cloudflareModels)
            .putString("direct_cloudflare_selected_models", clean.cloudflareSelectedModels)
            .putString("direct_cloudflare_account_id", clean.cloudflareAccountId)
            .putString("ollama_base_url", clean.ollamaBaseUrl)
            .putString("ollama_model", clean.ollamaModel)
            .putString("ollama_models", clean.ollamaModels)
            .putString("ollama_selected_models", clean.ollamaSelectedModels)
            .putBoolean("ollama_images_enabled", clean.ollamaImagesEnabled)
            .putBoolean("ollama_pdfs_enabled", clean.ollamaPdfsEnabled)
            .putBoolean("ollama_tool_calling_enabled", clean.ollamaToolCallingEnabled)
            .putString("openai_compatible_base_url", clean.openAiBaseUrl)
            .putString("openai_compatible_api_key", clean.openAiCompatibleApiKey)
            .putString("openai_compatible_model", clean.openAiCompatibleModel)
            .putString("openai_compatible_models", clean.openAiCompatibleModels)
            .putString("openai_compatible_selected_models", clean.openAiCompatibleSelectedModels)
            .putBoolean("openai_compatible_images_enabled", clean.openAiCompatibleImagesEnabled)
            .putBoolean("openai_compatible_pdfs_enabled", clean.openAiCompatiblePdfsEnabled)
            .putBoolean("openai_compatible_tool_calling_enabled", clean.openAiCompatibleToolCallingEnabled)
            .apply()
        saveSttCloudModel(_sttCloudModel.value)
        _customProviderConfig.value = clean
    }

    // ── Deep Research ────────────────────────────────────────────────────────────────

    fun getDeepResearchModelDirect(): String =
        prefs.getString("deep_research_model", "").orEmpty()

    fun saveDeepResearchModel(id: String) {
        prefs.edit().putString("deep_research_model", id).apply()
        _deepResearchModel.value = id
    }

    fun getDeepResearchSearchProviderDirect(): String {
        val stored = prefs.getString(KEY_DEEP_RESEARCH_SEARCH_PROVIDER, "auto").orEmpty()
        return if (stored in DEEP_RESEARCH_SEARCH_PROVIDERS) stored else "auto"
    }

    fun saveDeepResearchSearchProvider(provider: String) {
        prefs.edit().putString(KEY_DEEP_RESEARCH_SEARCH_PROVIDER, provider).apply()
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

    // ── Echo Labs master switches ──────────────────────────────────────────────────────

    fun getEchoAdviserEnabledDirect(): Boolean = prefs.getBoolean("echo_adviser_enabled", true)
    fun saveEchoAdviserEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("echo_adviser_enabled", enabled).apply()
        _echoAdviserEnabled.value = enabled
    }

    fun getEchoFusionEnabledDirect(): Boolean = prefs.getBoolean("echo_fusion_enabled", true)
    fun saveEchoFusionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("echo_fusion_enabled", enabled).apply()
        _echoFusionEnabled.value = enabled
    }

    fun getEchoAgentEnabledDirect(): Boolean = prefs.getBoolean("echo_agent_enabled", true)
    fun saveEchoAgentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("echo_agent_enabled", enabled).apply()
        _echoAgentEnabled.value = enabled
    }

    // ── Jev Router ───────────────────────────────────────────────────────────────────

    /** Contextual routing requires a new opt-in after the original prompt-only version. */
    fun getJevEnabledDirect(): Boolean = prefs.getBoolean("jev_enabled", false) &&
        prefs.getInt("jev_consent_version", 0) == 2
    fun saveJevEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("jev_enabled", enabled)
            .putInt("jev_consent_version", if (enabled) 2 else 0).apply()
        _jevEnabled.value = enabled
    }

    fun getJevApiKeyDirect(): String = prefs.getString("jev_api_key", "").orEmpty()
    fun saveJevApiKey(key: String) {
        val clean = key.trim()
        prefs.edit().putString("jev_api_key", clean).apply()
        _jevApiKey.value = clean
    }

    // ── Browser Flow (beta) ────────────────────────────────────────────────────────────

    fun getBrowserFlowEnabledDirect(): Boolean =
        prefs.getBoolean("browser_flow_enabled", false) // beta — opt-in

    fun saveBrowserFlowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("browser_flow_enabled", enabled).apply()
        _browserFlowEnabled.value = enabled
    }

    // ── Artifacts ──────────────────────────────────────────────────────────────────────

    /** When true, artifact HTML must be fully self-contained (no CDN) so it renders offline. */
    fun getArtifactsOfflineDirect(): Boolean =
        prefs.getBoolean("artifacts_offline", false)

    fun saveArtifactsOffline(enabled: Boolean) {
        prefs.edit().putBoolean("artifacts_offline", enabled).apply()
        _artifactsOffline.value = enabled
    }

    // ── Speech to text ───────────────────────────────────────────────────────────────────

    fun getSttModeDirect(): SttMode = SttMode.fromStorage(prefs.getString("stt_mode", null))

    fun saveSttMode(mode: SttMode) {
        prefs.edit().putString("stt_mode", mode.storageKey).apply()
        _sttMode.value = mode
    }

    fun getSttCloudModelDirect(): String {
        val stored = prefs.getString("stt_cloud_model", SttCatalog.DEFAULT_MODEL_ID).orEmpty()
        val resolved = SttCatalog.resolveAvailable(stored, getCustomProviderConfigDirect()).id
        if (stored != resolved) prefs.edit().putString("stt_cloud_model", resolved).apply()
        return resolved
    }

    fun saveSttCloudModel(id: String) {
        val resolvedId = SttCatalog.resolveAvailable(id, getCustomProviderConfigDirect()).id
        prefs.edit().putString("stt_cloud_model", resolvedId).apply()
        _sttCloudModel.value = resolvedId
    }

    fun getSarvamHinglishEnabledDirect(): Boolean =
        prefs.getBoolean("sarvam_hinglish_enabled", true)

    fun saveSarvamHinglishEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sarvam_hinglish_enabled", enabled).apply()
        _sarvamHinglishEnabled.value = enabled
    }

    fun getSttVocabularyDirect(): List<String> =
        DictationVocabulary.decode(prefs.getString(KEY_STT_VOCABULARY, null))

    fun saveSttVocabulary(terms: List<String>) {
        val normalized = DictationVocabulary.normalize(terms)
        prefs.edit().putString(KEY_STT_VOCABULARY, DictationVocabulary.encode(normalized)).apply()
        _sttVocabulary.value = normalized
    }

    // ── App mode ───────────────────────────────────────────────────────────────────────

    fun getAppModeDirect(): AppMode = AppMode.fromStorage(prefs.getString(KEY_APP_MODE, null))

    fun saveAppMode(mode: AppMode) {
        prefs.edit().putString(KEY_APP_MODE, mode.storageKey).apply()
        _appMode.value = mode
    }

    fun getImagineMediaDirect(): ImagineMedia =
        ImagineMedia.fromStorage(prefs.getString(KEY_IMAGINE_MEDIA, null))

    fun saveImagineMedia(media: ImagineMedia) {
        prefs.edit().putString(KEY_IMAGINE_MEDIA, media.storageKey).apply()
        _imagineMedia.value = media
    }

    /**
     * Image framing. Unlike video, OpenRouter image models take their shape from the prompt
     * rather than a parameter, so this is passed to the model as an instruction — which is why
     * it is stored as one of our own labels rather than a provider enum.
     */
    fun getImageAspectRatioDirect(): String =
        prefs.getString(KEY_IMAGE_ASPECT, VideoRequestPolicy.DEFAULT_ASPECT_RATIO).orEmpty()
            .takeIf { it in VideoRequestPolicy.ASPECT_RATIOS } ?: VideoRequestPolicy.DEFAULT_ASPECT_RATIO

    fun saveImageAspectRatio(ratio: String) {
        val clean = ratio.takeIf { it in VideoRequestPolicy.ASPECT_RATIOS }
            ?: VideoRequestPolicy.DEFAULT_ASPECT_RATIO
        prefs.edit().putString(KEY_IMAGE_ASPECT, clean).apply()
        _imageAspectRatio.value = clean
    }

    /**
     * Where each mode was left, remembered separately, as one of three states:
     *
     *  - [ModePosition.Thread] — a conversation was open
     *  - [ModePosition.Blank]  — a blank composer was open. That is a place the user chose to
     *                            be, so it has to be returned to like any other
     *  - [ModePosition.Unset]  — this mode has never been visited
     *
     * Collapsing Blank into Unset is precisely the bug this shape exists to prevent: leaving a
     * fresh composer, switching modes and coming back would silently reopen an old
     * conversation the user had already navigated away from.
     */
    fun getLastPositionDirect(mode: AppMode): ModePosition =
        when (val stored = prefs.getString(positionKey(mode), null)) {
            null, "" -> ModePosition.Unset
            BLANK_POSITION -> ModePosition.Blank
            else -> ModePosition.Thread(stored)
        }

    fun saveLastPosition(mode: AppMode, position: ModePosition) {
        val stored = when (position) {
            is ModePosition.Thread -> position.chatId
            ModePosition.Blank -> BLANK_POSITION
            ModePosition.Unset -> null
        }
        prefs.edit().putString(positionKey(mode), stored).apply()
    }

    private fun positionKey(mode: AppMode) = "last_thread_${mode.storageKey}"

    // ── Image generation ───────────────────────────────────────────────────────────────

    fun getImageGenModelDirect(): String =
        prefs.getString("image_gen_model", DEFAULT_IMAGE_MODEL_ID).orEmpty()

    fun saveImageGenModel(id: String) {
        prefs.edit().putString("image_gen_model", id).apply()
        _imageGenModel.value = id
    }

    // ── Video generation ───────────────────────────────────────────────────────────────
    // Cloud only: OpenRouter is the sole route. There is deliberately no duration setting —
    // the model decides how long each clip runs.

    fun getVideoGenModelDirect(): String =
        prefs.getString("video_gen_model", DEFAULT_VIDEO_MODEL_ID).orEmpty()
            .ifBlank { DEFAULT_VIDEO_MODEL_ID }

    fun saveVideoGenModel(id: String) {
        prefs.edit().putString("video_gen_model", id).apply()
        _videoGenModel.value = id
    }

    fun getVideoAspectRatioDirect(): String =
        prefs.getString("video_aspect_ratio", null).orEmpty().trim()
            .ifEmpty { VideoRequestPolicy.DEFAULT_ASPECT_RATIO }

    fun saveVideoAspectRatio(ratio: String) = saveVideoFraming(
        key = "video_aspect_ratio",
        value = ratio,
        fallback = VideoRequestPolicy.DEFAULT_ASPECT_RATIO,
        state = _videoAspectRatio,
    )

    fun getVideoResolutionDirect(): String =
        prefs.getString("video_resolution", null).orEmpty().trim()
            .ifEmpty { VideoRequestPolicy.DEFAULT_RESOLUTION }

    fun saveVideoResolution(resolution: String) = saveVideoFraming(
        key = "video_resolution",
        value = resolution,
        fallback = VideoRequestPolicy.DEFAULT_RESOLUTION,
        state = _videoResolution,
    )

    /**
     * Stores a framing preference as given, checking only that it is not blank.
     *
     * These used to be validated against [VideoRequestPolicy]'s house lists, which turned a
     * stale constant into a silent veto: the picker offers whatever the *model* declares, so
     * choosing 4K on a model that offers it wrote nothing and snapped the selection back to
     * 720p, with no error and no way to tell why.
     *
     * A store cannot know what any given provider supports, and it does not need to. The
     * reconciliation belongs at request time in [VideoRequestPolicy.resolveResolution], which
     * runs against the actual model's capability set and already downgrades gracefully.
     */
    private fun saveVideoFraming(
        key: String,
        value: String,
        fallback: String,
        state: MutableStateFlow<String>,
    ) {
        val clean = value.trim().ifEmpty { fallback }
        prefs.edit().putString(key, clean).apply()
        state.value = clean
    }

    /** Audio roughly doubles the per-second price, so it starts off and is opt-in. */
    fun getVideoAudioEnabledDirect(): Boolean = prefs.getBoolean("video_generate_audio", false)

    fun saveVideoAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("video_generate_audio", enabled).apply()
        _videoAudioEnabled.value = enabled
    }

    /** Minutes of inactivity before Browser Flow auto-stops the session (cost guard). 0 = off. */
    fun getBrowserIdleMinutesDirect(): Int =
        prefs.getInt("browser_idle_minutes", 3)

    fun saveBrowserIdleMinutes(value: Int) {
        prefs.edit().putInt("browser_idle_minutes", value).apply()
        _browserIdleMinutes.value = value
    }

    /**
     * PR #147 stored `monid` as a search backend. After that merge is reverted, a leftover
     * selection would look like search is on while every key lookup returns empty.
     */
    private fun migrateRemovedMonidSearchSelections() {
        val edit = prefs.edit()
        var changed = false
        if (prefs.getString(KEY_SEARCH_PROVIDER, null) == "monid") {
            edit.putString(KEY_SEARCH_PROVIDER, "off")
            changed = true
        }
        if (prefs.getString(KEY_LAST_SEARCH_PROVIDER, null) == "monid") {
            edit.remove(KEY_LAST_SEARCH_PROVIDER)
            changed = true
        }
        if (prefs.getString(KEY_DEEP_RESEARCH_SEARCH_PROVIDER, null) == "monid") {
            edit.putString(KEY_DEEP_RESEARCH_SEARCH_PROVIDER, "auto")
            changed = true
        }
        if (prefs.contains(KEY_MONID_API_KEY)) {
            edit.remove(KEY_MONID_API_KEY)
            changed = true
        }
        if (changed) edit.apply()
    }

    companion object {
        private const val KEY_APP_MODE = "app_mode"
        private const val KEY_STT_VOCABULARY = "stt_custom_vocabulary"

        /**
         * Marks "a blank composer was open". A reserved token rather than an empty string,
         * because empty is indistinguishable from never-written — and thread ids are UUIDs,
         * so this can never collide with a real one.
         */
        private const val BLANK_POSITION = "__blank__"
        private const val KEY_IMAGINE_MEDIA = "imagine_media"
        private const val KEY_IMAGE_ASPECT = "image_aspect_ratio"
        private const val KEY_SEARCH_PROVIDER = "web_search_provider"
        private const val KEY_SEARCH_SCOPE = "web_search_scope"
        private const val KEY_LAST_SEARCH_PROVIDER = "last_search_provider"
        private const val KEY_DEEP_RESEARCH_SEARCH_PROVIDER = "deep_research_search_provider"
        // v3: stacked copy + labeled buttons. Earlier dismiss keys do not hide this once.
        private const val KEY_ECHOCRAWL_INTRO_DISMISSED = "echocrawl_intro_dismissed_v3"
        private const val KEY_MONID_API_KEY = "monid_api_key"
        private val WEB_SEARCH_PROVIDERS = setOf(
            "off",
            "openrouter",
            ClientSearchProviders.EXA,
            ClientSearchProviders.PARALLEL,
            ClientSearchProviders.FIRECRAWL,
            ClientSearchProviders.ECHOCRAWL,
        )
        private val DEEP_RESEARCH_SEARCH_PROVIDERS = setOf("auto", "exa", "parallel", "firecrawl")
        const val DEFAULT_MODEL_ID = DefaultChatModels.DEFAULT_MODEL_ID
        const val DEFAULT_IMAGE_MODEL_ID = "google/gemini-2.5-flash-image"

        /** Veo 3.1 Fast: good quality without the flagship's per-second price. */
        const val DEFAULT_VIDEO_MODEL_ID = "google/veo-3.1-fast"
        const val DEFAULT_VIDEO_MODEL_NAME = "Veo 3.1 Fast"
    }
}
