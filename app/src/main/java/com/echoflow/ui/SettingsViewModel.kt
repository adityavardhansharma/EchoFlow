package com.echoflow.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.CatalogEntry
import com.echoflow.data.CustomModel
import com.echoflow.data.CustomModelDao
import com.echoflow.data.DownloadState
import com.echoflow.data.HuggingFaceModelSearch
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
    private val downloadManager: ModelDownloadManager
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
    val hfAccessToken: StateFlow<String> = repository.hfAccessToken
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.states

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

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

    fun saveHfAccessToken(token: String) {
        repository.saveHfAccessToken(token.trim())
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
            try {
                downloadManager.importFromUri(uri)
            } catch (e: Exception) {
                _importError.value = e.message ?: "Import failed."
            }
        }
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
            downloadManager: ModelDownloadManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, customModelDao, localModelDao, downloadManager) as T
            }
        }
    }
}
