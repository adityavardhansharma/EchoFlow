package com.echoflow.data.memory

import android.content.Context
import com.echoflow.data.SettingsPreferenceStorage
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject

/** Immutable consent boundary captured before a turn starts, never after it finishes. */
data class MemorySession(val generation: Long, val since: Long)

data class MemoryPreferences(
    val connected: Boolean,
    val recall: Boolean,
    val learn: Boolean,
    val allowLocal: Boolean,
    val generation: Long,
    val learningNote: String,
)

/** Secrets never leave encrypted storage or enter the model's tool arguments. */
class MemorySettings internal constructor(
    private val prefs: SharedPreferences?,
    private val privacyPrefs: SharedPreferences? = prefs,
) {
    constructor(context: Context) : this(SettingsPreferenceStorage.secureOrNull(context),
        context.getSharedPreferences("memory_privacy", Context.MODE_PRIVATE))
    val key: String get() = prefs?.getString("memory_key", "").orEmpty()
    val space: String get() = prefs?.getString("memory_space", "echoflow-personal") ?: "echoflow-personal"
    val connected get() = key.isNotBlank()
    val accessRevision: Long get() = prefs?.getLong("memory_access_revision", 0) ?: 0
    var recall: Boolean
        get() = prefs?.getBoolean("memory_recall", true) ?: true
        set(value) { updateAccess("memory_recall", value) }
    var learn: Boolean
        get() = prefs?.getBoolean("memory_learn", false) ?: false
        set(value) {
            synchronized(lock) {
                if (value == learn) return@synchronized
                val store = prefs ?: error("Secure storage is unavailable.")
                check(store.edit().putBoolean("memory_learn", value)
                    .putLong("memory_generation", generation + 1)
                    .putLong("memory_since", System.currentTimeMillis())
                    .remove("memory_pending_queue").remove("memory_learning_note").remove("memory_learning_blocked").commit()) {
                    "Couldn't save memory consent. Please try again."
                }
            }
        }
    var allowLocal: Boolean
        get() = prefs?.getBoolean("memory_local", false) ?: false
        set(value) { updateAccess("memory_local", value) }
    private fun updateAccess(name: String, value: Boolean) = synchronized(lock) {
        check(prefs?.edit()?.putBoolean(name, value)?.putLong("memory_access_revision", accessRevision + 1)?.commit() == true) {
            "Couldn't save memory permissions. Please try again."
        }
    }
    var plan: String
        get() = prefs?.getString("memory_plan", "unknown") ?: "unknown"
        set(value) { prefs?.edit()?.putString("memory_plan", value)?.apply() }
    val since: Long get() = prefs?.getLong("memory_since", Long.MAX_VALUE) ?: Long.MAX_VALUE
    val generation: Long get() = prefs?.getLong("memory_generation", 0) ?: 0
    var learningNote: String
        get() = prefs?.getString("memory_learning_note", "").orEmpty()
        set(value) { prefs?.edit()?.putString("memory_learning_note", value)?.apply() }
    fun exclude(chatId: String) {
        synchronized(lock) {
            val excluded = privacyPrefs?.getStringSet("memory_excluded", emptySet()).orEmpty() + chatId
            check(privacyPrefs?.edit()?.putStringSet("memory_excluded", excluded)?.commit() == true) {
                "Couldn't save this conversation's privacy choice."
            }
        }
    }
    fun excluded(chatId: String) = privacyPrefs?.getStringSet("memory_excluded", emptySet())?.contains(chatId) == true ||
        prefs?.getStringSet("memory_excluded", emptySet())?.contains(chatId) == true
    fun connect(key: String, space: String) {
        require(key.isNotBlank()) { "Enter your Supermemory API key." }
        require(space.matches(Regex("[a-zA-Z0-9_:-]{1,100}"))) { "Use letters, numbers, hyphens, underscores or colons for the memory space." }
        val store = prefs ?: error("Secure storage is unavailable. Try restarting your device.")
        synchronized(lock) {
        check(store.edit().putString("memory_key", key.trim()).putString("memory_space", space)
            .putString("memory_plan", "unknown").putLong("memory_since", System.currentTimeMillis())
            .putLong("memory_generation", generation + 1).putBoolean("memory_learn", false)
            .remove("memory_pending_queue").remove("memory_learning_note").remove("memory_learning_blocked")
            .remove("memory_profile_cache").remove("memory_profile_cached_at").commit()) { "Couldn't securely save the API key." }
        }
    }
    fun disconnect() {
        synchronized(lock) {
            check(prefs?.edit()?.remove("memory_key")?.putBoolean("memory_learn", false)
                ?.putLong("memory_generation", generation + 1)?.remove("memory_pending_queue")
                ?.remove("memory_learning_note")?.remove("memory_learning_blocked")
                ?.remove("memory_profile_cache")?.remove("memory_profile_cached_at")?.commit() != false) { "Couldn't remove the key. Please try again." }
        }
    }

    /** Called before persisting local user text, including cancelled or failed replies. */
    fun recordLocalTurn(chatId: String) = synchronized(lock) {
        // Provenance isn't a secret. Keep it independently of Keystore availability so a
        // temporarily locked Keystore cannot make a local conversation eligible later.
        val store = privacyPrefs ?: error("Conversation privacy storage is unavailable.")
        check(store.edit().putStringSet("memory_local_chats",
            store.getStringSet("memory_local_chats", emptySet()).orEmpty() + chatId).commit()) {
            "Couldn't record local conversation privacy."
        }
        if (!allowLocal) exclude(chatId)
    }

    fun includesLocal(chatId: String) = privacyPrefs?.getStringSet("memory_local_chats", emptySet())?.contains(chatId) == true
    fun session(): MemorySession? = if (connected && learn) MemorySession(generation, since) else null
    fun permits(session: MemorySession): Boolean = connected && learn && generation == session.generation && since == session.since

    /** Journal IDs and a completion cutoff before Room work; never journal message text. */
    fun journal(chatId: String, session: MemorySession): Long = synchronized(lock) {
        check(permits(session)) { "Memory consent changed." }
        val cutoff = System.currentTimeMillis()
        val entries = pendingQueue().toMutableMap().apply { put(chatId, cutoff) }
        check(prefs?.edit()?.putString("memory_pending_queue", org.json.JSONObject(entries as Map<*, *>).toString())?.commit() == true) {
            "Couldn't queue memory for this conversation."
        }
        cutoff
    }

    fun pendingQueue(): Map<String, Long> = synchronized(lock) {
        val json = org.json.JSONObject(prefs?.getString("memory_pending_queue", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getLong(it) }
    }

    fun acknowledgeQueue(chatId: String, cutoff: Long, session: MemorySession) = synchronized(lock) {
        if (!permits(session)) return@synchronized
        val entries = pendingQueue().toMutableMap()
        if (entries[chatId] != cutoff) return@synchronized
        entries.remove(chatId)
        check(prefs?.edit()?.putString("memory_pending_queue", org.json.JSONObject(entries as Map<*, *>).toString())?.commit() == true) {
            "Couldn't update the memory queue."
        }
    }

    var learningBlocked: Boolean
        get() = prefs?.getBoolean("memory_learning_blocked", false) ?: false
        set(value) { prefs?.edit()?.putBoolean("memory_learning_blocked", value)?.apply() }

    /** Small encrypted cache: avoids a profile network call on every obvious identity question. */
    fun cachedProfile(maxAgeMs: Long = PROFILE_CACHE_TTL_MS): MemoryProfile? = synchronized(lock) {
        val savedAt = prefs?.getLong("memory_profile_cached_at", 0) ?: 0
        if (savedAt <= 0 || System.currentTimeMillis() - savedAt > maxAgeMs) return@synchronized null
        runCatching {
            val json = JSONObject(prefs?.getString("memory_profile_cache", "") ?: "")
            MemoryProfile(json.getJSONArray("stable").strings(), json.getJSONArray("recent").strings())
        }.getOrNull()
    }

    fun cacheProfile(profile: MemoryProfile) = synchronized(lock) {
        val json = JSONObject().put("stable", JSONArray(profile.stable)).put("recent", JSONArray(profile.recent))
        check(prefs?.edit()?.putString("memory_profile_cache", json.toString())
            ?.putLong("memory_profile_cached_at", System.currentTimeMillis())?.commit() == true) {
            "Couldn't cache the memory profile."
        }
    }

    fun invalidateProfile() = synchronized(lock) {
        prefs?.edit()?.remove("memory_profile_cache")?.remove("memory_profile_cached_at")?.apply()
    }

    /** Observe non-secret state only; API keys never enter Compose state or a flow. */
    fun snapshot() = MemoryPreferences(connected, recall, learn, allowLocal, generation, learningNote)
    fun changes() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("memory_")) trySend(snapshot())
        }
        prefs?.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { prefs?.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    companion object {
        private val lock = Any()
        internal const val PROFILE_CACHE_TTL_MS = 15 * 60 * 1000L
        fun batchSize(plan: String) = when (plan.lowercase().removePrefix("api_")) {
            "pro" -> 3
            "max", "scale", "enterprise" -> 1
            else -> 5
        }
    }
}

private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
