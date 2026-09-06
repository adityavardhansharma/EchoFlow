package com.echoflow.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-time upgrade that adds the shipped chat models to `custom_models` without disturbing
 * existing selections.
 *
 * - **New installs** keep the implicit [DefaultChatModels.DEFAULT_MODEL_ID] default.
 * - **Existing installs** that never persisted `selected_model` but already have chat history
 *   stay on the legacy Gemini default so an app update does not silently switch models mid-thread.
 * - **Everyone else** keeps whatever model they had selected; Luna and Echo Lumen are merely added
 *   to the picker and Settings list when missing.
 */
object DefaultChatModelsSeed {
    private const val KEY_DONE = "default_chat_models_seeded_v1"
    private const val SELECTED_MODEL_KEY = "selected_model"

    suspend fun run(
        context: Context, database: AppDatabase,
        securePrefs: SharedPreferences = SettingsPreferenceStorage.secure(context),
    ) = withContext(Dispatchers.IO) {
        val legacyPrefs = SettingsPreferenceStorage.legacy(context)
        SettingsPreferenceStorage.migrateLegacyIfNeeded(legacyPrefs, securePrefs)
        val prefs = securePrefs
        if (prefs.getBoolean(KEY_DONE, false)) return@withContext

        val customModelDao = database.customModelDao()
        DefaultChatModels.SHIPPED.forEach { (id, name) ->
            ensureCustomModel(customModelDao, id, name)
        }

        val selectedModel = readSelectedModel(legacyPrefs, securePrefs)
        when {
            selectedModel == DefaultChatModels.LEGACY_DEFAULT_MODEL_ID ->
                ensureCustomModel(
                    customModelDao,
                    DefaultChatModels.LEGACY_DEFAULT_MODEL_ID,
                    DefaultChatModels.LEGACY_DEFAULT_MODEL_NAME,
                )
            selectedModel == null && database.chatDao().hasAnyThreads() -> {
                ensureCustomModel(
                    customModelDao,
                    DefaultChatModels.LEGACY_DEFAULT_MODEL_ID,
                    DefaultChatModels.LEGACY_DEFAULT_MODEL_NAME,
                )
                writeSelectedModel(legacyPrefs, securePrefs, DefaultChatModels.LEGACY_DEFAULT_MODEL_ID)
            }
        }

        prefs.edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun readSelectedModel(
        legacyPrefs: SharedPreferences,
        securePrefs: SharedPreferences?,
    ): String? = when {
        legacyPrefs.contains(SELECTED_MODEL_KEY) ->
            legacyPrefs.getString(SELECTED_MODEL_KEY, null)
        securePrefs?.contains(SELECTED_MODEL_KEY) == true ->
            securePrefs.getString(SELECTED_MODEL_KEY, null)
        else -> null
    }

    private suspend fun ensureCustomModel(dao: CustomModelDao, id: String, name: String) {
        if (dao.getCustomModelById(id) == null) {
            dao.insertCustomModel(CustomModel(id, name))
        }
    }

    private fun writeSelectedModel(
        legacyPrefs: SharedPreferences,
        securePrefs: SharedPreferences?,
        modelId: String,
    ) {
        securePrefs?.edit()?.putString(SELECTED_MODEL_KEY, modelId)?.apply()
    }
}
