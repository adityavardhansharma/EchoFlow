package com.echoflow.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AdvisorProfileDao
import com.echoflow.data.CatalogEntry
import com.echoflow.data.CustomModel
import com.echoflow.data.CustomModelDao
import com.echoflow.data.DeepResearchModel
import com.echoflow.data.DeepResearchModelDao
import com.echoflow.data.FusionPanel
import com.echoflow.data.FusionPanelDao
import com.echoflow.data.DownloadState
import com.echoflow.data.HuggingFaceModelSearch
import com.echoflow.data.InferenceParams
import com.echoflow.data.LocalModel
import com.echoflow.data.LocalModelDao
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.OpenRouterModelDirectory
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val fusionPanelDao: FusionPanelDao
) : ViewModel() {
    private val hfModelSearch = HuggingFaceModelSearch()

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

    // Echo Adviser / Echo Fusion
    val echoAdviserProfileId: StateFlow<String> = repository.echoAdviserProfileId
    val echoFusionPanelId: StateFlow<String> = repository.echoFusionPanelId
    val advisorProfiles: StateFlow<List<AdvisorProfile>> = advisorProfileDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val fusionPanels: StateFlow<List<FusionPanel>> = fusionPanelDao.getAll()
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

    // ── Deep Research ────────────────────────────────────────────────────────────────

    fun saveDeepResearchModel(id: String) = repository.saveDeepResearchModel(id)
    fun saveDeepResearchSearchProvider(provider: String) = repository.saveDeepResearchSearchProvider(provider)
    fun saveDeepResearchMaxSearches(value: Int) = repository.saveDeepResearchMaxSearches(value)
    fun saveDeepResearchMaxSources(value: Int) = repository.saveDeepResearchMaxSources(value)
    fun saveDeepResearchExaEffort(value: String) = repository.saveDeepResearchExaEffort(value)

    fun saveDataAgentEnabled(enabled: Boolean) = repository.saveDataAgentEnabled(enabled)
    fun saveDataAgentEngine(id: String) = repository.saveDataAgentEngine(id)
    fun saveDataAgentMaxCredits(value: Int) = repository.saveDataAgentMaxCredits(value)

    // ── Echo Adviser ───────────────────────────────────────────────────────────────────

    fun saveEchoAdviserProfile(id: String) = repository.saveEchoAdviserProfileId(id)

    fun addAdvisorProfile(name: String, modelId: String, modelName: String) {
        viewModelScope.launch {
            val cleanName = name.trim().ifEmpty { "Advisor" }
            val cleanModel = modelId.trim()
            if (cleanModel.isEmpty()) return@launch
            val id = java.util.UUID.randomUUID().toString()
            advisorProfileDao.insert(
                AdvisorProfile(
                    id = id,
                    name = cleanName,
                    modelId = cleanModel,
                    modelName = modelName.trim().ifEmpty { cleanModel.substringAfterLast("/") },
                    createdAt = System.currentTimeMillis(),
                )
            )
            // First profile becomes the active selection automatically.
            if (repository.getEchoAdviserProfileIdDirect().isBlank()) {
                repository.saveEchoAdviserProfileId(id)
            }
        }
    }

    fun deleteAdvisorProfile(id: String) {
        viewModelScope.launch {
            advisorProfileDao.delete(id)
            if (repository.getEchoAdviserProfileIdDirect() == id) {
                repository.saveEchoAdviserProfileId(advisorProfileDao.getAllSync().firstOrNull()?.id.orEmpty())
            }
        }
    }

    // ── Echo Fusion ────────────────────────────────────────────────────────────────────

    fun saveEchoFusionPanel(id: String) = repository.saveEchoFusionPanelId(id)

    fun addFusionPanel(name: String, models: List<Pair<String, String>>, judgeModelId: String?) {
        viewModelScope.launch {
            val cleanName = name.trim().ifEmpty { "Panel" }
            val ids = models.map { it.first.trim() }.filter { it.isNotEmpty() }
            if (ids.size < 2) return@launch // a panel needs at least two models
            val id = java.util.UUID.randomUUID().toString()
            fusionPanelDao.upsert(
                FusionPanel(
                    id = id,
                    name = cleanName,
                    modelIds = ids.joinToString("\n"),
                    modelNames = models.map { it.second.trim().ifEmpty { it.first.substringAfterLast("/") } }.joinToString("\n"),
                    judgeModelId = judgeModelId?.trim()?.takeIf { it.isNotEmpty() && it in ids },
                    createdAt = System.currentTimeMillis(),
                )
            )
            if (repository.getEchoFusionPanelIdDirect().isBlank()) {
                repository.saveEchoFusionPanelId(id)
            }
        }
    }

    fun deleteFusionPanel(id: String) {
        viewModelScope.launch {
            fusionPanelDao.delete(id)
            if (repository.getEchoFusionPanelIdDirect() == id) {
                repository.saveEchoFusionPanelId(fusionPanelDao.getAllSync().firstOrNull()?.id.orEmpty())
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
            fusionPanelDao: FusionPanelDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, customModelDao, localModelDao, downloadManager, deepResearchModelDao, advisorProfileDao, fusionPanelDao) as T
            }
        }
    }
}
