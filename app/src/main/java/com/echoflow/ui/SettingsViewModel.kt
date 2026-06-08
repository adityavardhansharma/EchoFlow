package com.echoflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.CustomModel
import com.echoflow.data.CustomModelDao
import com.echoflow.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val customModelDao: CustomModelDao
) : ViewModel() {

    val apiKey: StateFlow<String> = repository.apiKey
    val selectedModel: StateFlow<String> = repository.selectedModel
    val themeColor: StateFlow<String> = repository.themeColor
    val darkMode: StateFlow<String> = repository.darkMode
    val webSearchEnabled: StateFlow<Boolean> = repository.webSearchEnabled

    val customModels: StateFlow<List<CustomModel>> = customModelDao.getAllCustomModels()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    fun saveWebSearchEnabled(enabled: Boolean) {
        repository.saveWebSearchEnabled(enabled)
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
                saveSelectedModel("google/gemini-2.0-flash")
            }
        }
    }

    companion object {
        fun provideFactory(
            repository: SettingsRepository,
            customModelDao: CustomModelDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository, customModelDao) as T
            }
        }
    }
}
