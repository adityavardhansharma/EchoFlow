package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(getApiKeyDirect())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(getSelectedModelDirect())
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _themeColor = MutableStateFlow(getThemeColorDirect())
    val themeColor: StateFlow<String> = _themeColor.asStateFlow()

    private val _darkMode = MutableStateFlow(getDarkModeDirect())
    val darkMode: StateFlow<String> = _darkMode.asStateFlow() // "system", "dark", "light"

    private val _webSearchEnabled = MutableStateFlow(getWebSearchEnabledDirect())
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

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

    fun getWebSearchEnabledDirect(): Boolean {
        return prefs.getBoolean("web_search_enabled", false)
    }

    fun saveWebSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("web_search_enabled", enabled).apply()
        _webSearchEnabled.value = enabled
    }
}
