package com.echoflow.data.memory

import android.content.Context
import com.echoflow.data.SettingsPreferenceStorage

/** Secrets never leave encrypted storage or enter the model's tool arguments. */
class MemorySettings internal constructor(private val prefs: android.content.SharedPreferences?) {
    constructor(context: Context) : this(SettingsPreferenceStorage.secureOrNull(context))
    val key: String get() = prefs?.getString("memory_key", "").orEmpty()
    val space: String get() = prefs?.getString("memory_space", "echoflow-personal") ?: "echoflow-personal"
    val connected get() = key.isNotBlank()
    var recall: Boolean
        get() = prefs?.getBoolean("memory_recall", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("memory_recall", value)?.apply() }
    var learn: Boolean
        get() = prefs?.getBoolean("memory_learn", false) ?: false
        set(value) {
            prefs?.edit()?.apply {
                putBoolean("memory_learn", value)
                if (value && !learn) putLong("memory_since", System.currentTimeMillis())
            }?.apply()
        }
    var allowLocal: Boolean
        get() = prefs?.getBoolean("memory_local", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("memory_local", value)?.apply() }
    var plan: String
        get() = prefs?.getString("memory_plan", "unknown") ?: "unknown"
        set(value) { prefs?.edit()?.putString("memory_plan", value)?.apply() }
    val since: Long get() = prefs?.getLong("memory_since", Long.MAX_VALUE) ?: Long.MAX_VALUE
    val generation: Long get() = prefs?.getLong("memory_generation", 0) ?: 0
    var learningNote: String
        get() = prefs?.getString("memory_learning_note", "").orEmpty()
        set(value) { prefs?.edit()?.putString("memory_learning_note", value)?.apply() }
    fun exclude(chatId: String) {
        val excluded = prefs?.getStringSet("memory_excluded", emptySet()).orEmpty() + chatId
        prefs?.edit()?.putStringSet("memory_excluded", excluded)?.apply()
    }
    fun excluded(chatId: String) = prefs?.getStringSet("memory_excluded", emptySet())?.contains(chatId) == true
    fun connect(key: String, space: String) {
        require(key.isNotBlank()) { "Enter your Supermemory API key." }
        require(space.matches(Regex("[a-zA-Z0-9_:-]{1,100}"))) { "Use letters, numbers, hyphens, underscores or colons for the memory space." }
        val store = prefs ?: error("Secure storage is unavailable. Try restarting your device.")
        store.edit().putString("memory_key", key.trim()).putString("memory_space", space)
            .putString("memory_plan", "unknown").putLong("memory_since", System.currentTimeMillis())
            .putLong("memory_generation", generation + 1).putBoolean("memory_learn", false).commit()
    }
    fun disconnect() {
        prefs?.edit()?.remove("memory_key")?.putBoolean("memory_learn", false)
            ?.putLong("memory_generation", generation + 1)?.commit()
    }
    companion object {
        fun batchSize(plan: String) = when (plan.lowercase().removePrefix("api_")) {
            "pro" -> 3
            "max", "scale", "enterprise" -> 1
            else -> 5
        }
    }
}
