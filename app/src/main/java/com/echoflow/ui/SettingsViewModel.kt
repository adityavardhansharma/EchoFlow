package com.echoflow.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AdvisorProfileDao
import com.echoflow.data.AgentProfile
import com.echoflow.data.AgentProfileDao
import com.echoflow.data.CatalogEntry
import com.echoflow.data.CustomProviderConfig
import com.echoflow.data.CustomModelProvider
import com.echoflow.data.CustomProviderModel
import com.echoflow.data.CustomProviderService
import com.echoflow.data.CustomModel
import com.echoflow.data.CustomModelDao
import com.echoflow.data.DeepResearchModel
import com.echoflow.data.DeepResearchModelDao
import com.echoflow.data.FusionPanel
import com.echoflow.data.FusionPanelDao
import com.echoflow.data.ImageModel
import com.echoflow.data.ImageModelDao
import com.echoflow.data.LocalImageCatalogEntry
import com.echoflow.data.LocalImageDownloadState
import com.echoflow.data.LocalImageModel
import com.echoflow.data.LocalImageModelDao
import com.echoflow.data.LocalImageModelManager
import com.echoflow.data.LocalInferenceGate
import com.echoflow.data.DownloadState
import com.echoflow.data.HuggingFaceModelSearch
import com.echoflow.data.InferenceParams
import com.echoflow.data.LocalModel
import com.echoflow.data.LocalModelDao
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.OpenRouterModelDirectory
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.data.OpenRouterVideoModelDirectory
import com.echoflow.data.OpenRouterVideoModelInfo
import com.echoflow.data.SettingsRepository
import com.echoflow.data.VideoModel
import com.echoflow.data.VideoModelDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val customModelDao: CustomModelDao,
    private val localModelDao: LocalModelDao,
    private val downloadManager: ModelDownloadManager,
    private val deepResearchModelDao: DeepResearchModelDao,
    private val advisorProfileDao: AdvisorProfileDao,
    private val fusionPanelDao: FusionPanelDao,
    private val agentProfileDao: AgentProfileDao,
    private val imageModelDao: ImageModelDao,
    private val localImageModelDao: LocalImageModelDao,
    private val localImageModelManager: LocalImageModelManager,
    private val videoModelDao: VideoModelDao,
    private val localInferenceGate: LocalInferenceGate
) : ViewModel() {
    private val hfModelSearch = HuggingFaceModelSearch()
    private val customProviderService = CustomProviderService()
    private val profileManager = SettingsProfileManager(repository, advisorProfileDao, fusionPanelDao, agentProfileDao)

    val apiKey: StateFlow<String> = repository.apiKey
    val selectedModel: StateFlow<String> = repository.selectedModel
    val themeColor: StateFlow<String> = repository.themeColor
    val darkMode: StateFlow<String> = repository.darkMode

    // Web search
    val webSearchProvider: StateFlow<String> = repository.webSearchProvider
    val webSearchScope: StateFlow<String> = repository.webSearchScope
    val exaApiKey: StateFlow<String> = repository.exaApiKey
    val parallelApiKey: StateFlow<String> = repository.parallelApiKey
    val firecrawlApiKey: StateFlow<String> = repository.firecrawlApiKey

    // Local models
    val localModelsEnabled: StateFlow<Boolean> = repository.localModelsEnabled
    val ggufEnabled: StateFlow<Boolean> = repository.ggufEnabled
    val hfAccessToken: StateFlow<String> = repository.hfAccessToken
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.states

    // Inference parameters (global, one set per side)
    val localInferenceParams: StateFlow<InferenceParams> = repository.localInferenceParams
    val cloudInferenceParams: StateFlow<InferenceParams> = repository.cloudInferenceParams
    val customProviderConfig: StateFlow<CustomProviderConfig> = repository.customProviderConfig

    private val _customProviderTestLoading = MutableStateFlow(false)
    val customProviderTestLoading: StateFlow<Boolean> = _customProviderTestLoading.asStateFlow()

    private val _customProviderTestMessage = MutableStateFlow<String?>(null)
    val customProviderTestMessage: StateFlow<String?> = _customProviderTestMessage.asStateFlow()

    private val _customProviderFetchLoading = MutableStateFlow<CustomModelProvider?>(null)
    val customProviderFetchLoading: StateFlow<CustomModelProvider?> = _customProviderFetchLoading.asStateFlow()

    private val _customProviderFetchMessage = MutableStateFlow<String?>(null)
    val customProviderFetchMessage: StateFlow<String?> = _customProviderFetchMessage.asStateFlow()

    val customProviderModels: StateFlow<List<CustomProviderModel>> = repository.customProviderConfig
        .map { it.toModelEntries() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Deep Research
    val deepResearchModelId: StateFlow<String> = repository.deepResearchModel
    val deepResearchSearchProvider: StateFlow<String> = repository.deepResearchSearchProvider
    val deepResearchMaxSearches: StateFlow<Int> = repository.deepResearchMaxSearches
    val deepResearchMaxSources: StateFlow<Int> = repository.deepResearchMaxSources
    val deepResearchExaEffort: StateFlow<String> = repository.deepResearchExaEffort
    val deepResearchModels: StateFlow<List<DeepResearchModel>> = deepResearchModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Data Agent
    val dataAgentEnabled: StateFlow<Boolean> = repository.dataAgentEnabled
    val dataAgentEngine: StateFlow<String> = repository.dataAgentEngine
    val dataAgentMaxCredits: StateFlow<Int> = repository.dataAgentMaxCredits

    // Echo Labs master switches
    val echoAdviserEnabled: StateFlow<Boolean> = repository.echoAdviserEnabled
    val echoFusionEnabled: StateFlow<Boolean> = repository.echoFusionEnabled
    val echoAgentEnabled: StateFlow<Boolean> = repository.echoAgentEnabled

    // Browser Flow (beta)
    val browserFlowEnabled: StateFlow<Boolean> = repository.browserFlowEnabled
    val browserIdleMinutes: StateFlow<Int> = repository.browserIdleMinutes

    // Artifacts
    val artifactsOffline: StateFlow<Boolean> = repository.artifactsOffline

    // Image generation
    val imageGenModelId: StateFlow<String> = repository.imageGenModel
    val imageModels: StateFlow<List<ImageModel>> = imageModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // On-device image generation
    val imageGenEngine: StateFlow<String> = repository.imageGenEngine
    val localImageModelId: StateFlow<String> = repository.localImageModel
    val localImageIterations: StateFlow<Int> = repository.localImageIterations
    val localImageSeedMode: StateFlow<String> = repository.localImageSeedMode
    val localImageFixedSeed: StateFlow<Int> = repository.localImageFixedSeed
    val experimentalImageModelsEnabled: StateFlow<Boolean> = repository.experimentalImageModelsEnabled
    val localImageModels: StateFlow<List<LocalImageModel>> = localImageModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val localImageDownloadStates: StateFlow<Map<String, LocalImageDownloadState>> = localImageModelManager.states

    private val _localImageMessage = MutableStateFlow<String?>(null)
    val localImageMessage: StateFlow<String?> = _localImageMessage.asStateFlow()

    // Video generation (OpenRouter only)
    val videoGenModelId: StateFlow<String> = repository.videoGenModel
    val videoAspectRatio: StateFlow<String> = repository.videoAspectRatio
    val videoResolution: StateFlow<String> = repository.videoResolution
    val videoAudioEnabled: StateFlow<Boolean> = repository.videoAudioEnabled
    val videoModels: StateFlow<List<VideoModel>> = videoModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Echo Adviser / Echo Fusion
    val echoAdviserProfileId: StateFlow<String> = repository.echoAdviserProfileId
    val echoFusionPanelId: StateFlow<String> = repository.echoFusionPanelId
    val advisorProfiles: StateFlow<List<AdvisorProfile>> = advisorProfileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fusionPanels: StateFlow<List<FusionPanel>> = fusionPanelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Echo Agent
    val echoAgentProfileId: StateFlow<String> = repository.echoAgentProfileId
    val agentProfiles: StateFlow<List<AgentProfile>> = agentProfileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    // A picked file the user must confirm before we copy it (too big to load on this device).
    private var pendingImportUri: Uri? = null
    private val _importWarning = MutableStateFlow<String?>(null)
    val importWarning: StateFlow<String?> = _importWarning.asStateFlow()

    // Starts empty on purpose: the search sheet must open idle, never replaying a stale
    // query or kicking off a search by itself.
    private val _hfModelQuery = MutableStateFlow("")
    val hfModelQuery: StateFlow<String> = _hfModelQuery.asStateFlow()

    private val _hfSearchResults = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val hfSearchResults: StateFlow<List<CatalogEntry>> = _hfSearchResults.asStateFlow()

    private val _hfSearchLoading = MutableStateFlow(false)
    val hfSearchLoading: StateFlow<Boolean> = _hfSearchLoading.asStateFlow()

    private val _hfSearchError = MutableStateFlow<String?>(null)
    val hfSearchError: StateFlow<String?> = _hfSearchError.asStateFlow()

    // OpenRouter model directory (add-cloud-model search)
    private val orDirectory = OpenRouterModelDirectory()

    private val _orAllModels = MutableStateFlow<List<OpenRouterModelInfo>>(emptyList())

    private val _orModelQuery = MutableStateFlow("")
    val orModelQuery: StateFlow<String> = _orModelQuery.asStateFlow()

    private val _orDirectoryLoading = MutableStateFlow(false)
    val orDirectoryLoading: StateFlow<Boolean> = _orDirectoryLoading.asStateFlow()

    private val _orDirectoryError = MutableStateFlow<String?>(null)
    val orDirectoryError: StateFlow<String?> = _orDirectoryError.asStateFlow()

    /** Directory filtered live as the user types; capped so the sheet stays snappy. */
    val orModelResults: StateFlow<List<OpenRouterModelInfo>> =
        combine(_orAllModels, _orModelQuery) { all, query ->
            val q = query.trim()
            if (q.isEmpty()) all.take(40)
            else all.filter { it.id.contains(q, true) || it.name.contains(q, true) }.take(60)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Same directory, restricted to models whose output modalities include images. */
    val orImageModelResults: StateFlow<List<OpenRouterModelInfo>> =
        combine(_orAllModels, _orModelQuery) { all, query ->
            val imageCapable = all.filter { it.outputsImage }
            val q = query.trim()
            if (q.isEmpty()) imageCapable.take(40)
            else imageCapable.filter { it.id.contains(q, true) || it.name.contains(q, true) }.take(60)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // OpenRouter video-model directory. Separate from the chat directory: video models live on
    // their own endpoint and carry capability sets (ratios, resolutions, audio) instead of
    // context lengths and token pricing.
    private val videoDirectory = OpenRouterVideoModelDirectory()

    private val _orVideoModels = MutableStateFlow<List<OpenRouterVideoModelInfo>>(emptyList())

    private val _videoModelQuery = MutableStateFlow("")
    val videoModelQuery: StateFlow<String> = _videoModelQuery.asStateFlow()

    private val _videoDirectoryLoading = MutableStateFlow(false)
    val videoDirectoryLoading: StateFlow<Boolean> = _videoDirectoryLoading.asStateFlow()

    private val _videoDirectoryError = MutableStateFlow<String?>(null)
    val videoDirectoryError: StateFlow<String?> = _videoDirectoryError.asStateFlow()

    /** Directory filtered live as the user types; capped so the sheet stays snappy. */
    val videoModelResults: StateFlow<List<OpenRouterVideoModelInfo>> =
        combine(_orVideoModels, _videoModelQuery) { all, query ->
            val q = query.trim()
            if (q.isEmpty()) all
            else all.filter { it.id.contains(q, true) || it.name.contains(q, true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Capabilities of the selected video model, so the settings page can grey out ratios and
     * resolutions it does not support instead of letting the user pick a guaranteed 400.
     */
    val selectedVideoModelCapabilities: StateFlow<OpenRouterVideoModelInfo?> =
        combine(_orVideoModels, repository.videoGenModel) { all, selected ->
            all.firstOrNull { it.id == selected }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val customModels: StateFlow<List<CustomModel>> = customModelDao.getAllCustomModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val localModels: StateFlow<List<LocalModel>> = localModelDao.getAllLocalModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch { downloadManager.pruneOrphans() }
        viewModelScope.launch {
            // The background Worker can finish in a recreated process with its own repository
            // instance. Reconcile this ViewModel's hot flow with the durable preference and the
            // files that are actually runnable whenever Room changes.
            localImageModelDao.getAll().collect { models ->
                val installed = models.filter(localImageModelManager::isInstalled)
                val persisted = repository.getLocalImageModelDirect()
                val desired = persisted.takeIf { selected -> installed.any { it.id == selected } }
                    ?: installed.firstOrNull()?.id.orEmpty()
                if (persisted != desired || repository.localImageModel.value != desired) {
                    repository.saveLocalImageModel(desired)
                }
            }
        }
    }

    fun saveApiKey(key: String) {
        repository.saveApiKey(key)
    }

    fun saveSelectedModel(modelId: String) {
        repository.saveSelectedModel(modelId)
    }

    fun saveThemeColor(colorName: String) {
        repository.saveThemeColor(colorName)
    }

    fun saveDarkMode(mode: String) {
        repository.saveDarkMode(mode)
    }

    fun saveWebSearchProvider(provider: String) {
        repository.saveWebSearchProvider(provider)
    }

    fun saveWebSearchScope(scope: String) {
        repository.saveWebSearchScope(scope)
    }

    fun saveSearchApiKey(provider: String, key: String) {
        repository.saveSearchApiKey(provider, key.trim())
    }

    fun saveLocalModelsEnabled(enabled: Boolean) {
        repository.saveLocalModelsEnabled(enabled)
    }

    fun saveGgufEnabled(enabled: Boolean) {
        repository.saveGgufEnabled(enabled)
    }

    fun saveHfAccessToken(token: String) {
        repository.saveHfAccessToken(token.trim())
    }

    fun saveInferenceParams(local: Boolean, params: InferenceParams) {
        repository.saveInferenceParams(local, params)
    }

    fun resetInferenceParams(local: Boolean) {
        repository.resetInferenceParams(local)
    }

    fun saveCustomProviderConfig(config: CustomProviderConfig) {
        repository.saveCustomProviderConfig(config)
        _customProviderTestMessage.value = null
    }

    fun testCustomProvider(config: CustomProviderConfig = customProviderConfig.value) {
        if (_customProviderTestLoading.value) return
        viewModelScope.launch {
            _customProviderTestLoading.value = true
            _customProviderTestMessage.value = null
            try {
                val result = when {
                    config.ollamaEnabled ->
                        customProviderService.testOllama(config.ollamaBaseUrl, config.ollamaModel)
                    config.openAiCompatibleEnabled ->
                        customProviderService.testOpenAiCompatible(config.openAiBaseUrl, config.openAiCompatibleApiKey, config.openAiCompatibleModel)
                    else -> null
                }
                _customProviderTestMessage.value = result?.message
            } finally {
                _customProviderTestLoading.value = false
            }
        }
    }

    fun fetchCustomProviderModels(provider: CustomModelProvider, config: CustomProviderConfig = customProviderConfig.value) {
        if (_customProviderFetchLoading.value != null) return
        viewModelScope.launch {
            _customProviderFetchLoading.value = provider
            _customProviderFetchMessage.value = null
            try {
                val result = when (provider) {
                    CustomModelProvider.OpenAi -> customProviderService.fetchModels(provider, apiKey = config.openAiApiKey)
                    CustomModelProvider.Claude -> customProviderService.fetchModels(provider, apiKey = config.claudeApiKey)
                    CustomModelProvider.Gemini -> customProviderService.fetchModels(provider, apiKey = config.geminiApiKey)
                    CustomModelProvider.Cerebras -> customProviderService.fetchModels(provider, apiKey = config.cerebrasApiKey)
                    CustomModelProvider.XAi -> customProviderService.fetchModels(provider, apiKey = config.xAiApiKey)
                    CustomModelProvider.Ollama -> customProviderService.fetchModels(provider, baseUrl = config.ollamaBaseUrl)
                    CustomModelProvider.OpenAiCompatible -> customProviderService.fetchModels(
                        provider,
                        baseUrl = config.openAiBaseUrl,
                        apiKey = config.openAiCompatibleApiKey,
                    )
                }
                if (result.ok) {
                    val updated = when (provider) {
                        CustomModelProvider.OpenAi -> config.copy(openAiModels = result.message)
                        CustomModelProvider.Claude -> config.copy(claudeModels = result.message)
                        CustomModelProvider.Gemini -> config.copy(geminiModels = result.message)
                        CustomModelProvider.Cerebras -> config.copy(cerebrasModels = result.message)
                        CustomModelProvider.XAi -> config.copy(xAiModels = result.message)
                        CustomModelProvider.Ollama -> config.copy(ollamaModels = result.message)
                        CustomModelProvider.OpenAiCompatible -> config.copy(openAiCompatibleModels = result.message)
                    }
                    saveCustomProviderConfig(updated)
                    _customProviderFetchMessage.value = "Fetched ${result.message.lineSequence().filter { it.isNotBlank() }.count()} models."
                } else {
                    _customProviderFetchMessage.value = result.message
                }
            } finally {
                _customProviderFetchLoading.value = null
            }
        }
    }

    // ── Deep Research ────────────────────────────────────────────────────────────────

    fun saveDeepResearchModel(id: String) = repository.saveDeepResearchModel(id)
    fun saveDeepResearchSearchProvider(provider: String) = repository.saveDeepResearchSearchProvider(provider)
    fun saveDeepResearchMaxSearches(value: Int) = repository.saveDeepResearchMaxSearches(value)
    fun saveDeepResearchMaxSources(value: Int) = repository.saveDeepResearchMaxSources(value)
    fun saveDeepResearchExaEffort(value: String) = repository.saveDeepResearchExaEffort(value)

    fun saveDataAgentEnabled(enabled: Boolean) = repository.saveDataAgentEnabled(enabled)
    fun saveBrowserFlowEnabled(enabled: Boolean) = repository.saveBrowserFlowEnabled(enabled)
    fun saveBrowserIdleMinutes(value: Int) = repository.saveBrowserIdleMinutes(value)
    fun saveArtifactsOffline(enabled: Boolean) = repository.saveArtifactsOffline(enabled)
    fun saveEchoAdviserEnabled(enabled: Boolean) = repository.saveEchoAdviserEnabled(enabled)
    fun saveEchoFusionEnabled(enabled: Boolean) = repository.saveEchoFusionEnabled(enabled)
    fun saveEchoAgentEnabled(enabled: Boolean) = repository.saveEchoAgentEnabled(enabled)
    fun saveDataAgentEngine(id: String) = repository.saveDataAgentEngine(id)
    fun saveDataAgentMaxCredits(value: Int) = repository.saveDataAgentMaxCredits(value)

    // ── Echo Adviser ───────────────────────────────────────────────────────────────────

    fun saveEchoAdviserProfile(id: String) = repository.saveEchoAdviserProfileId(id)

    fun addAdvisorProfile(name: String, modelId: String, modelName: String) {
        viewModelScope.launch { profileManager.addAdvisor(name, modelId, modelName) }
    }

    fun deleteAdvisorProfile(id: String) {
        viewModelScope.launch { profileManager.deleteAdvisor(id) }
    }

    // ── Echo Fusion ────────────────────────────────────────────────────────────────────

    fun saveEchoFusionPanel(id: String) = repository.saveEchoFusionPanelId(id)

    fun addFusionPanel(name: String, models: List<Pair<String, String>>, judgeModelId: String?) {
        viewModelScope.launch { profileManager.addPanel(name, models, judgeModelId) }
    }

    fun deleteFusionPanel(id: String) {
        viewModelScope.launch { profileManager.deletePanel(id) }
    }

    // ── Echo Agent ───────────────────────────────────────────────────────────────────────

    fun saveEchoAgentProfile(id: String) = repository.saveEchoAgentProfileId(id)

    fun addAgentProfile(name: String, workerModelId: String, workerModelName: String, maxToolCalls: Int) {
        viewModelScope.launch { profileManager.addAgent(name, workerModelId, workerModelName, maxToolCalls) }
    }

    fun deleteAgentProfile(id: String) {
        viewModelScope.launch { profileManager.deleteAgent(id) }
    }

    // ── Image generation ─────────────────────────────────────────────────────────────────

    fun saveImageGenModel(id: String) = repository.saveImageGenModel(id)

    fun saveImageGenEngine(engine: String) = repository.saveImageGenEngine(engine)
    fun saveLocalImageModel(id: String) = repository.saveLocalImageModel(id)
    fun saveLocalImageIterations(value: Int) = repository.saveLocalImageIterations(value)
    fun saveLocalImageSeedMode(mode: String) = repository.saveLocalImageSeedMode(mode)
    fun saveLocalImageFixedSeed(seed: Int) = repository.saveLocalImageFixedSeed(seed)
    fun saveExperimentalImageModelsEnabled(enabled: Boolean) {
        repository.saveExperimentalImageModelsEnabled(enabled)
        if (enabled) _localImageMessage.value = null
    }

    fun localImageRuntimeSupported(entry: LocalImageCatalogEntry): Boolean =
        localImageModelManager.deviceSupportsRuntime(entry)

    /** Picker path: choosing an on-device model also switches the engine to local. */
    fun selectLocalImageModel(id: String) {
        repository.saveLocalImageModel(id)
        repository.saveImageGenEngine(com.echoflow.data.SettingsRepository.IMAGE_ENGINE_LOCAL)
    }

    /** Picker path: choosing a cloud image model also switches the engine to OpenRouter. */
    fun selectCloudImageModel(id: String) {
        repository.saveImageGenModel(id)
        repository.saveImageGenEngine(com.echoflow.data.SettingsRepository.IMAGE_ENGINE_OPENROUTER)
    }

    fun downloadLocalImageModel(entry: LocalImageCatalogEntry) {
        val experimentalEnabled = repository.getExperimentalImageModelsEnabledDirect()
        if (!localImageModelManager.deviceSupportsRuntime(entry)) {
            _localImageMessage.value = "${entry.name} needs Android 8.0 or newer."
            return
        }
        if (!entry.canDownload(experimentalEnabled)) {
            _localImageMessage.value = if (entry.experimental && !experimentalEnabled) {
                "Turn on Show experimental models to download ${entry.name}."
            } else {
                "${entry.name} isn't ready to download yet."
            }
            return
        }
        _localImageMessage.value = null
        localImageModelManager.download(entry)
    }

    fun cancelLocalImageDownload(entryId: String) = localImageModelManager.cancel(entryId)

    fun retryLocalImageDownload(entry: LocalImageCatalogEntry) {
        localImageModelManager.clearFailed(entry.id)
        downloadLocalImageModel(entry)
    }

    fun clearLocalImageMessage() {
        _localImageMessage.value = null
    }

    fun deleteLocalImageModel(model: LocalImageModel) {
        viewModelScope.launch {
            if (localInferenceGate.isBusy) {
                _localImageMessage.value = "Wait for the current on-device task to finish before deleting a model."
                return@launch
            }
            try {
                localImageModelManager.delete(model)
                if (repository.getLocalImageModelDirect() == model.id) {
                    // Fall over to another installed model, or clear the selection entirely.
                    val remaining = localImageModelDao.getAllSync()
                        .firstOrNull { it.id != model.id && localImageModelManager.isInstalled(it) }
                    repository.saveLocalImageModel(remaining?.id.orEmpty())
                }
            } catch (error: Exception) {
                _localImageMessage.value = error.message ?: "Couldn't delete ${model.name}."
            }
        }
    }

    fun addImageModel(id: String, name: String) {
        viewModelScope.launch {
            val cleanId = id.trim()
            val cleanName = name.trim().ifEmpty { cleanId.substringAfterLast("/") }
            if (cleanId.isNotEmpty()) {
                imageModelDao.insert(ImageModel(cleanId, cleanName, System.currentTimeMillis()))
            }
        }
    }

    fun deleteImageModel(id: String) {
        viewModelScope.launch {
            imageModelDao.delete(id)
            if (repository.getImageGenModelDirect() == id) {
                repository.saveImageGenModel(SettingsRepository.DEFAULT_IMAGE_MODEL_ID)
            }
        }
    }

    // ── Video generation ─────────────────────────────────────────────────────────────────

    fun saveVideoGenModel(id: String) = repository.saveVideoGenModel(id)
    fun saveVideoAspectRatio(ratio: String) = repository.saveVideoAspectRatio(ratio)
    fun saveVideoResolution(resolution: String) = repository.saveVideoResolution(resolution)
    fun saveVideoAudioEnabled(enabled: Boolean) = repository.saveVideoAudioEnabled(enabled)

    fun updateVideoModelQuery(query: String) {
        _videoModelQuery.value = query
    }

    /** Loads the video directory once; safe to call every time the sheet or page opens. */
    fun loadVideoModelDirectory() {
        if (_orVideoModels.value.isNotEmpty() || _videoDirectoryLoading.value) return
        viewModelScope.launch {
            _videoDirectoryLoading.value = true
            _videoDirectoryError.value = null
            try {
                _orVideoModels.value = videoDirectory.allModels()
            } catch (e: Exception) {
                _videoDirectoryError.value = e.message ?: "Could not load the video model directory."
            } finally {
                _videoDirectoryLoading.value = false
            }
        }
    }

    fun addVideoModel(id: String, name: String) {
        viewModelScope.launch {
            val cleanId = id.trim()
            val cleanName = name.trim().ifEmpty { cleanId.substringAfterLast("/") }
            if (cleanId.isNotEmpty()) {
                videoModelDao.insert(VideoModel(cleanId, cleanName, System.currentTimeMillis()))
            }
        }
    }

    fun deleteVideoModel(id: String) {
        viewModelScope.launch {
            videoModelDao.delete(id)
            if (repository.getVideoGenModelDirect() == id) {
                repository.saveVideoGenModel(SettingsRepository.DEFAULT_VIDEO_MODEL_ID)
            }
        }
    }

    fun addDeepResearchModel(id: String, name: String) {
        viewModelScope.launch {
            val cleanId = id.trim()
            val cleanName = name.trim().ifEmpty { cleanId.substringAfterLast("/") }
            if (cleanId.isNotEmpty()) {
                deepResearchModelDao.insert(DeepResearchModel(cleanId, cleanName, System.currentTimeMillis()))
            }
        }
    }

    fun deleteDeepResearchModel(id: String) {
        viewModelScope.launch {
            deepResearchModelDao.delete(id)
            if (repository.getDeepResearchModelDirect() == id) {
                repository.saveDeepResearchModel("")
            }
        }
    }

    fun downloadModel(entry: CatalogEntry) {
        downloadManager.download(entry, repository.getHfAccessTokenDirect())
    }

    fun updateHfModelQuery(query: String) {
        _hfModelQuery.value = query
    }

    fun searchHfModels() {
        viewModelScope.launch {
            _hfSearchLoading.value = true
            _hfSearchError.value = null
            try {
                _hfSearchResults.value = hfModelSearch.search(
                    query = _hfModelQuery.value,
                    hfToken = repository.getHfAccessTokenDirect()
                )
            } catch (e: Exception) {
                _hfSearchError.value = e.message ?: "Search failed."
            } finally {
                _hfSearchLoading.value = false
            }
        }
    }

    fun updateOrModelQuery(query: String) {
        _orModelQuery.value = query
    }

    /** Loads the OpenRouter directory once; safe to call every time the sheet opens. */
    fun loadOpenRouterDirectory() {
        if (_orAllModels.value.isNotEmpty() || _orDirectoryLoading.value) return
        viewModelScope.launch {
            _orDirectoryLoading.value = true
            _orDirectoryError.value = null
            try {
                _orAllModels.value = orDirectory.allModels()
            } catch (e: Exception) {
                _orDirectoryError.value = e.message ?: "Could not load the model directory."
            } finally {
                _orDirectoryLoading.value = false
            }
        }
    }

    fun cancelDownload(entryId: String) {
        downloadManager.cancel(entryId)
    }

    fun retryDownload(entry: CatalogEntry) {
        downloadManager.clearFailed(entry.id)
        downloadModel(entry)
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _importError.value = null
            val allow = repository.getGgufEnabledDirect()
            val assessment = downloadManager.assessImport(uri)
            if (assessment.isGguf && !allow) {
                _importError.value = "GGUF support is off. Turn on “Allow GGUF models” below to import .gguf files."
                return@launch
            }
            if (assessment.tooBig) {
                // Hold the file and ask first — don't copy multiple GB only to fail at load.
                pendingImportUri = uri
                _importWarning.value = "“${assessment.displayName}” needs roughly ${gb(assessment.estimatedPeakBytes)} " +
                    "of RAM to run — more than this device's ${gb(assessment.deviceTotalRamBytes)}. " +
                    "It will very likely fail to load. Import anyway?"
                return@launch
            }
            runImport(uri, allow)
        }
    }

    /** User chose to import despite the size warning. */
    fun confirmImport() {
        val uri = pendingImportUri ?: return
        pendingImportUri = null
        _importWarning.value = null
        runImport(uri, repository.getGgufEnabledDirect())
    }

    fun dismissImportWarning() {
        pendingImportUri = null
        _importWarning.value = null
    }

    private fun runImport(uri: Uri, allowGguf: Boolean) {
        viewModelScope.launch {
            try {
                downloadManager.importFromUri(uri, allowGguf)
            } catch (e: Exception) {
                _importError.value = e.message ?: "Import failed."
            }
        }
    }

    private fun gb(bytes: Long): String = "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))

    private fun CustomProviderConfig.toModelEntries(): List<CustomProviderModel> {
        return CustomProviderModelCatalog.entries(this)
    }

    fun clearImportError() {
        _importError.value = null
    }

    fun deleteLocalModel(model: LocalModel) {
        viewModelScope.launch {
            downloadManager.delete(model)
            // If the deleted model was selected, fall back to default
            if (selectedModel.value == model.id) {
                saveSelectedModel(SettingsRepository.DEFAULT_MODEL_ID)
            }
        }
    }

    fun addCustomModel(id: String, name: String) {
        viewModelScope.launch {
            val cleanId = id.trim()
            val cleanName = name.trim().ifEmpty { id.substringAfterLast("/") }
            if (cleanId.isNotEmpty()) {
                customModelDao.insertCustomModel(CustomModel(cleanId, cleanName))
            }
        }
    }

    fun deleteCustomModel(id: String) {
        viewModelScope.launch {
            customModelDao.deleteCustomModel(id)
            // If the deleted model was selected, fall back to default
            if (selectedModel.value == id) {
                saveSelectedModel(SettingsRepository.DEFAULT_MODEL_ID)
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: SettingsRepository,
            customModelDao: CustomModelDao,
            localModelDao: LocalModelDao,
            downloadManager: ModelDownloadManager,
            deepResearchModelDao: DeepResearchModelDao,
            advisorProfileDao: AdvisorProfileDao,
            fusionPanelDao: FusionPanelDao,
            agentProfileDao: AgentProfileDao,
            imageModelDao: ImageModelDao,
            localImageModelDao: LocalImageModelDao,
            localImageModelManager: LocalImageModelManager,
            videoModelDao: VideoModelDao,
            localInferenceGate: LocalInferenceGate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, customModelDao, localModelDao, downloadManager, deepResearchModelDao, advisorProfileDao, fusionPanelDao, agentProfileDao, imageModelDao, localImageModelDao, localImageModelManager, videoModelDao, localInferenceGate) as T
            }
        }
    }
}
