package com.echoflow.data.voice

import android.content.Context
import android.content.SharedPreferences
import com.echoflow.data.usage.UsageKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice mode's own settings, apart from the big SettingsRepository: the voice-mode LLM, style,
 * voice and language, plus two facts about the ElevenLabs key — whether it authenticated, and
 * which agent EchoFlow created on that account. Nothing here is secret (the key itself stays in
 * the encrypted settings store); key-bound facts are stored against the key's one-way hash, so a
 * different key starts fresh instead of reusing another account's agent.
 *
 * One instance per process ([get]) so Settings and Chat see the same state.
 */
class VoiceModeSettings private constructor(private val prefs: SharedPreferences) {

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<VoiceModeConfig> = _config.asStateFlow()

    /** Hash of the key that last authenticated with ElevenLabs; null until one has. */
    private val _verifiedKeyHash = MutableStateFlow(prefs.getString(KEY_VERIFIED, null))
    val verifiedKeyHash: StateFlow<String?> = _verifiedKeyHash.asStateFlow()

    private val _account = MutableStateFlow<VoiceAccount?>(readAccount())
    /** The plan behind the verified key, as last fetched; null when unknown. */
    val account: StateFlow<VoiceAccount?> = _account.asStateFlow()

    fun isVerified(apiKey: String): Boolean =
        apiKey.isNotBlank() && _verifiedKeyHash.value == UsageKeys.hash(apiKey)

    fun markVerified(apiKey: String, account: VoiceAccount?) {
        val hash = UsageKeys.hash(apiKey)
        val keyChanged = _verifiedKeyHash.value != hash
        prefs.edit().putString(KEY_VERIFIED, hash).apply()
        _verifiedKeyHash.value = hash
        when {
            account != null -> saveAccount(account)
            // A new key that can't read its plan must not show the previous key's plan.
            keyChanged -> clearAccount()
        }
    }

    private fun clearAccount() {
        prefs.edit().remove(KEY_ACCOUNT_TIER).apply()
        _account.value = null
    }

    fun markUnverified() {
        prefs.edit().remove(KEY_VERIFIED).remove(KEY_ACCOUNT_TIER).apply()
        _verifiedKeyHash.value = null
        _account.value = null
    }

    fun saveAccount(account: VoiceAccount) {
        prefs.edit()
            .putString(KEY_ACCOUNT_TIER, account.tier)
            .putString(KEY_ACCOUNT_STATUS, account.status)
            .putLong(KEY_ACCOUNT_USED, account.creditsUsed)
            .putLong(KEY_ACCOUNT_LIMIT, account.creditLimit)
            .putLong(KEY_ACCOUNT_RESET, account.resetsAtUnix ?: 0L)
            .apply()
        _account.value = account
    }

    fun saveLlm(llm: String) = update { it.copy(llm = llm) }
    fun saveStyle(style: VoiceStyle) = update { it.copy(style = style) }
    fun saveLanguage(code: String) = update { it.copy(language = code) }
    /** A blank [voiceId] goes back to the agent's default voice. */
    fun saveVoice(voiceId: String, name: String, category: String?) = update {
        val id = voiceId.takeIf { v -> v.isNotBlank() }
        it.copy(voiceId = id, voiceName = name.takeIf { id != null && name.isNotBlank() }, voiceCategory = category.takeIf { id != null })
    }

    /** The agent EchoFlow made on this key's account, and the config hash it was last synced to. */
    fun agentFor(apiKey: String): StoredAgent? {
        val id = prefs.getString(KEY_AGENT_ID, null) ?: return null
        if (prefs.getString(KEY_AGENT_KEY, null) != UsageKeys.hash(apiKey)) return null
        return StoredAgent(id, prefs.getString(KEY_AGENT_HASH, null))
    }

    fun saveAgent(apiKey: String, agentId: String, configHash: String) {
        prefs.edit()
            .putString(KEY_AGENT_ID, agentId)
            .putString(KEY_AGENT_KEY, UsageKeys.hash(apiKey))
            .putString(KEY_AGENT_HASH, configHash)
            .apply()
    }

    fun forgetAgent() {
        prefs.edit().remove(KEY_AGENT_ID).remove(KEY_AGENT_KEY).remove(KEY_AGENT_HASH).apply()
    }

    private fun update(change: (VoiceModeConfig) -> VoiceModeConfig) {
        val next = change(_config.value)
        prefs.edit()
            .putString(KEY_LLM, next.llm)
            .putString(KEY_STYLE, next.style.name)
            .putString(KEY_VOICE_ID, next.voiceId)
            .putString(KEY_VOICE_NAME, next.voiceName)
            .putString(KEY_VOICE_CATEGORY, next.voiceCategory)
            .putString(KEY_LANGUAGE, next.language)
            .apply()
        _config.value = next
    }

    private fun readConfig() = VoiceModeConfig(
        llm = prefs.getString(KEY_LLM, null)?.takeIf { it.isNotBlank() } ?: VoiceLlms.DEFAULT,
        style = VoiceStyle.fromStorage(prefs.getString(KEY_STYLE, null)),
        voiceId = prefs.getString(KEY_VOICE_ID, null)?.takeIf { it.isNotBlank() },
        voiceName = prefs.getString(KEY_VOICE_NAME, null),
        voiceCategory = prefs.getString(KEY_VOICE_CATEGORY, null),
        language = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() } ?: VoiceLanguages.DEFAULT,
    )

    private fun readAccount(): VoiceAccount? {
        val tier = prefs.getString(KEY_ACCOUNT_TIER, null) ?: return null
        return VoiceAccount(
            tier = tier,
            status = prefs.getString(KEY_ACCOUNT_STATUS, null),
            creditsUsed = prefs.getLong(KEY_ACCOUNT_USED, 0L),
            creditLimit = prefs.getLong(KEY_ACCOUNT_LIMIT, 0L),
            resetsAtUnix = prefs.getLong(KEY_ACCOUNT_RESET, 0L).takeIf { it > 0L },
        )
    }

    data class StoredAgent(val agentId: String, val configHash: String?)

    companion object {
        private const val FILE = "voice_mode"
        private const val KEY_LLM = "llm"
        private const val KEY_STYLE = "style"
        private const val KEY_VOICE_ID = "voice_id"
        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_VOICE_CATEGORY = "voice_category"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_VERIFIED = "verified_key_hash"
        private const val KEY_AGENT_ID = "agent_id"
        private const val KEY_AGENT_KEY = "agent_key_hash"
        private const val KEY_AGENT_HASH = "agent_config_hash"
        private const val KEY_ACCOUNT_TIER = "account_tier"
        private const val KEY_ACCOUNT_STATUS = "account_status"
        private const val KEY_ACCOUNT_USED = "account_used"
        private const val KEY_ACCOUNT_LIMIT = "account_limit"
        private const val KEY_ACCOUNT_RESET = "account_reset"

        @Volatile private var instance: VoiceModeSettings? = null

        fun get(context: Context): VoiceModeSettings = instance ?: synchronized(this) {
            instance ?: VoiceModeSettings(
                context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

/**
 * Checks an ElevenLabs key once and remembers the answer, so voice mode appears only for a key
 * that works. A scoped key without the "user read" permission still counts as valid: it simply
 * can't report its plan, and the app then treats the plan as unknown.
 */
class VoiceModeAuth(
    private val settings: VoiceModeSettings,
    private val api: ElevenLabsVoiceApi = ElevenLabsVoiceApi(),
) {
    enum class Outcome { Valid, Invalid, Offline }

    /**
     * Checks [apiKey] and records the result. [stillCurrent] is asked just before writing, so a
     * slow check for a key the user has since replaced can't overwrite the newer key's result.
     */
    suspend fun verify(apiKey: String, stillCurrent: () -> Boolean = { true }): Outcome {
        if (apiKey.isBlank()) {
            if (stillCurrent()) settings.markUnverified()
            return Outcome.Invalid
        }
        val result = api.account(apiKey)
        result.onSuccess {
            if (stillCurrent()) settings.markVerified(apiKey, it)
            return Outcome.Valid
        }
        val error = result.exceptionOrNull()
        return when {
            error is ElevenLabsApiException && error.missingPermission -> {
                if (stillCurrent()) settings.markVerified(apiKey, null)
                Outcome.Valid
            }
            error is ElevenLabsApiException && (error.unauthorized || error.code == 403) -> {
                if (stillCurrent()) settings.markUnverified()
                Outcome.Invalid
            }
            // A network failure says nothing about the key; keep whatever was known.
            else -> Outcome.Offline
        }
    }
}

/**
 * Makes sure the user's account has an agent matching [VoiceModeConfig]: creates it the first
 * time, updates it when a setting changed, and recreates it if it was deleted on ElevenLabs.
 * An unchanged config costs no request at all.
 */
class VoiceAgentProvisioner(
    private val settings: VoiceModeSettings,
    private val api: ElevenLabsVoiceApi = ElevenLabsVoiceApi(),
) {
    suspend fun ensureAgent(apiKey: String, config: VoiceModeConfig): Result<String> = runCatching {
        var agentId = settings.agentFor(apiKey)?.also { stored ->
            if (stored.configHash == VoiceAgentSpec.configHash(config)) return@runCatching stored.agentId
        }?.agentId
        // Expressive asks for v4 Turbo; an account that doesn't take it yet gets v3 Conversational.
        val models = buildList {
            add(config.effectiveStyle.modelId)
            if (config.effectiveStyle == VoiceStyle.Expressive) add(VoiceStyle.EXPRESSIVE_FALLBACK_MODEL_ID)
        }
        // Each model is tried with the card tool first, then without it, so an account that
        // rejects the tool still gets a working agent (just without the live card).
        val attempts = models.flatMap { model -> listOf(model to true, model to false) }
        var lastError: Throwable? = null
        for ((model, withTools) in attempts) {
            val body = VoiceAgentSpec.agentBody(config, model, withTools)
            // The hash always names the requested model, so a fallback agent isn't re-patched on
            // every session; picking a different style or voice still changes it.
            val hash = VoiceAgentSpec.configHash(config)
            val existing = agentId
            val result = if (existing != null) {
                api.updateAgent(apiKey, existing, body).map { existing }
            } else {
                api.createAgent(apiKey, body)
            }
            result.onSuccess { id ->
                settings.saveAgent(apiKey, id, hash)
                return@runCatching id
            }
            val error = result.exceptionOrNull()
            lastError = error
            when {
                // Deleted on the ElevenLabs side: make a new one with the same model.
                existing != null && error is ElevenLabsApiException && error.code == 404 -> {
                    settings.forgetAgent()
                    agentId = null
                    api.createAgent(apiKey, body).onSuccess { id ->
                        settings.saveAgent(apiKey, id, hash)
                        return@runCatching id
                    }.onFailure { lastError = it }
                    if ((lastError as? ElevenLabsApiException)?.code != 422) throw lastError!!
                }
                // 422 is a rejected field (the TTS model or the tool): try the next combination.
                error is ElevenLabsApiException && error.code == 422 -> Unit
                else -> throw error ?: IllegalStateException("Couldn't set up the voice agent.")
            }
        }
        throw lastError ?: IllegalStateException("Couldn't set up the voice agent.")
    }
}
