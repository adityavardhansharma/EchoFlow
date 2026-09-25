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
 *
 * A second one-time pass swaps the retired GPT 5.6 Luna row for GPT-6 Luna on installs the first
 * pass already seeded, unless GPT 5.6 Luna is still the selected model.
 */
object DefaultChatModelsSeed {
    private const val KEY_DONE = "default_chat_models_seeded_v1"
    private const val KEY_GPT6_LUNA_DONE = "default_chat_models_gpt6_luna_v2"
    private const val SELECTED_MODEL_KEY = "selected_model"
    private const val RETIRED_LUNA_ID = "openai/gpt-5.6-luna"
    private const val RETIRED_LUNA_NAME = "GPT 5.6 Luna"

    suspend fun run(context: Context, database: AppDatabase) = withContext(Dispatchers.IO) {
        val legacyPrefs = SettingsPreferenceStorage.legacy(context)
        val securePrefs = SettingsPreferenceStorage.secureOrNull(context)
        val prefs = securePrefs ?: legacyPrefs
        if (!prefs.getBoolean(KEY_DONE, false)) seedShippedModels(database, prefs, legacyPrefs, securePrefs)
        if (!prefs.getBoolean(KEY_GPT6_LUNA_DONE, false)) retireGpt56Luna(database, prefs, legacyPrefs, securePrefs)
    }

    private suspend fun seedShippedModels(
        database: AppDatabase,
        prefs: SharedPreferences,
        legacyPrefs: SharedPreferences,
        securePrefs: SharedPreferences?,
    ) {
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

    private suspend fun retireGpt56Luna(
        database: AppDatabase,
        prefs: SharedPreferences,
        legacyPrefs: SharedPreferences,
        securePrefs: SharedPreferences?,
    ) {
        val customModelDao = database.customModelDao()
        ensureCustomModel(customModelDao, DefaultChatModels.DEFAULT_MODEL_ID, DefaultChatModels.DEFAULT_MODEL_NAME)
        // Only the row the first pass seeded; a GPT-5.6 Luna added from the directory is the user's.
        val retired = customModelDao.getCustomModelById(RETIRED_LUNA_ID)
        if (retired?.name == RETIRED_LUNA_NAME && readSelectedModel(legacyPrefs, securePrefs) != RETIRED_LUNA_ID) {
            customModelDao.deleteCustomModel(RETIRED_LUNA_ID)
        }
        prefs.edit().putBoolean(KEY_GPT6_LUNA_DONE, true).apply()
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
        legacyPrefs.edit().putString(SELECTED_MODEL_KEY, modelId).apply()
        securePrefs?.edit()?.putString(SELECTED_MODEL_KEY, modelId)?.apply()
    }
}
