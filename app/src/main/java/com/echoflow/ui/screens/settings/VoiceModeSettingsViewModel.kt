package com.echoflow.ui.screens.settings

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoflow.data.voice.ElevenLabsVoiceApi
import com.echoflow.data.voice.VoiceLlm
import com.echoflow.data.voice.VoiceLlms
import com.echoflow.data.voice.VoiceModeAuth
import com.echoflow.data.voice.VoiceModeSettings
import com.echoflow.data.voice.VoiceOption
import com.echoflow.data.voice.VoiceProvider
import com.echoflow.data.voice.VoiceStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the voice picker is looking: the user's own voices, or ElevenLabs' public library. */
enum class VoiceSource { Mine, Library }

data class VoicePickerState(
    val source: VoiceSource = VoiceSource.Mine,
    val query: String = "",
    val voices: List<VoiceOption> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
    /** The voice being saved (a library voice is copied into My Voices first). */
    val picking: String? = null,
)

/**
 * State for the Voice mode settings page: checks the ElevenLabs key, loads the plan and the
 * LLMs ElevenLabs offers, and runs the searchable voice picker with previews.
 */
class VoiceModeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = VoiceModeSettings.get(application)
    private val api = ElevenLabsVoiceApi()
    private val auth = VoiceModeAuth(settings, api)

    val config = settings.config
    val account = settings.account
    val verifiedKeyHash = settings.verifiedKeyHash

    private val _verifying = MutableStateFlow(false)
    val verifying: StateFlow<Boolean> = _verifying.asStateFlow()

    private val _llms = MutableStateFlow<List<VoiceLlm>>(emptyList())
    val llms: StateFlow<List<VoiceLlm>> = _llms.asStateFlow()

    private val _llmsLoading = MutableStateFlow(false)
    val llmsLoading: StateFlow<Boolean> = _llmsLoading.asStateFlow()

    private val _picker = MutableStateFlow(VoicePickerState())
    val picker: StateFlow<VoicePickerState> = _picker.asStateFlow()

    /** The voice whose preview is playing, if any. */
    private val _previewing = MutableStateFlow<String?>(null)
    val previewing: StateFlow<String?> = _previewing.asStateFlow()

    private var key = ""
    private var bindJob: Job? = null
    private var nextToken: String? = null
    private var libraryPage = 0
    private var searchJob: Job? = null
    private var player: MediaPlayer? = null

    /** Called with the saved ElevenLabs key (blank when it's off): verifies it once per key. */
    fun bind(apiKey: String) {
        val clean = apiKey.trim()
        if (clean == key && (clean.isEmpty() || _llms.value.isNotEmpty())) return
        key = clean
        // A newer key replaces any check still running for the old one.
        bindJob?.cancel()
        if (clean.isEmpty()) {
            _verifying.value = false
            settings.markUnverified()
            return
        }
        bindJob = viewModelScope.launch {
            _verifying.value = true
            try {
                auth.verify(clean) { key == clean }
            } finally {
                if (key == clean) _verifying.value = false
            }
            if (key == clean && settings.isVerified(clean)) loadLlms()
        }
    }

    fun refresh() {
        val current = key
        key = ""
        bind(current)
    }

    private suspend fun loadLlms() {
        _llmsLoading.value = true
        val fetched = api.llms(key).getOrNull()?.let(VoiceLlms::selectable)?.takeIf { it.isNotEmpty() }
        _llmsLoading.value = false
        _llms.value = fetched ?: VoiceLlms.FALLBACK
        // A model ElevenLabs stopped offering can't run the agent: move to the best one it has.
        val saved = settings.config.value.llm
        if (fetched != null && fetched.none { it.id == saved }) settings.saveLlm(VoiceLlms.defaultFrom(fetched))
    }

    fun saveLlm(id: String) = settings.saveLlm(id)
    fun saveStyle(style: VoiceStyle) = settings.saveStyle(style)
    fun saveLanguage(code: String) = settings.saveLanguage(code)
    fun saveProvider(provider: VoiceProvider) = settings.saveProvider(provider)
    fun saveRealtimeVoice(provider: VoiceProvider, voice: String) = settings.saveRealtimeVoice(provider, voice)
    fun saveRealtimeModel(provider: VoiceProvider, model: String) = settings.saveRealtimeModel(provider, model)

    // ── Voice picker ────────────────────────────────────────────────────────────────────

    fun openPicker() {
        _picker.value = VoicePickerState()
        load(reset = true)
    }

    fun setSource(source: VoiceSource) {
        if (source == _picker.value.source) return
        _picker.update { it.copy(source = source, voices = emptyList(), error = null, loading = true, canLoadMore = false) }
        load(reset = true)
    }

    fun setQuery(query: String) {
        // The old results' paging no longer applies: block "load more" until page one is in.
        nextToken = null
        libraryPage = 0
        _picker.update { it.copy(query = query, loading = true, canLoadMore = false) }
        load(reset = true, debounceMs = 350)
    }

    fun loadMore() {
        if (_picker.value.loading || !_picker.value.canLoadMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean, debounceMs: Long = 0) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val state = _picker.value
            if (reset) {
                nextToken = null
                libraryPage = 0
            }
            _picker.update { it.copy(loading = true, error = null) }
            val result = when (state.source) {
                VoiceSource.Mine -> api.myVoices(key, state.query, if (reset) null else nextToken)
                VoiceSource.Library -> api.libraryVoices(key, state.query, if (reset) 0 else libraryPage + 1)
            }
            result.onSuccess { page ->
                if (state.source == VoiceSource.Mine) nextToken = page.next
                else if (!reset) libraryPage += 1
                _picker.update {
                    it.copy(
                        voices = (if (reset) page.voices else it.voices + page.voices).distinctBy { v -> v.voiceId },
                        loading = false,
                        canLoadMore = page.next != null,
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _picker.update { it.copy(loading = false, error = error.message ?: "Couldn't load voices.") }
            }
        }
    }

    /** Saves [voice] as the voice-mode voice; [onDone] runs once it's saved. */
    fun pickVoice(voice: VoiceOption, onDone: () -> Unit) {
        if (_picker.value.picking != null) return
        _picker.update { it.copy(picking = voice.voiceId, error = null) }
        viewModelScope.launch {
            api.addLibraryVoice(key, voice)
                .onSuccess { id ->
                    settings.saveVoice(id, voice.name, voice.category)
                    _picker.update { it.copy(picking = null) }
                    stopPreview()
                    onDone()
                }
                .onFailure { error ->
                    _picker.update { it.copy(picking = null, error = error.message ?: "Couldn't add that voice.") }
                }
        }
    }

    fun useDefaultVoice() = settings.saveVoice("", "", null)

    // ── Previews ────────────────────────────────────────────────────────────────────────

    fun togglePreview(voice: VoiceOption) {
        val url = voice.previewUrl ?: return
        if (_previewing.value == voice.voiceId) {
            stopPreview()
            return
        }
        stopPreview()
        _previewing.value = voice.voiceId
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { it.start() }
            setOnCompletionListener { stopPreview() }
            setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Preview failed ($what/$extra)")
                stopPreview()
                true
            }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure {
                Log.w(TAG, "Preview failed", it)
                stopPreview()
            }
        }
    }

    fun stopPreview() {
        player?.let { runCatching { it.reset(); it.release() } }
        player = null
        _previewing.value = null
    }

    override fun onCleared() {
        stopPreview()
        super.onCleared()
    }

    private companion object {
        const val TAG = "VoiceModeSettings"
    }
}
