package com.echoflow.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device inference via MediaPipe LLM Inference (AI Edge Gallery style). One engine
 * (model) is resident at a time; one session is kept per chat so multi-turn context is
 * reused instead of re-prefilling the whole transcript on every message.
 */
class LocalLlmService(private val context: Context) {

    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    private var session: LlmInferenceSession? = null
    private var sessionChatId: String? = null
    private var lastHistorySize = -1

    private val setupMutex = Mutex()
    private val generating = AtomicBoolean(false)

    /**
     * True while the model is being loaded into memory or a long context is being
     * prefilled — the slow part of on-device inference. The UI shows a loading state
     * until this drops back to false and tokens start streaming.
     */
    private val _modelLoading = MutableStateFlow(false)
    val modelLoading: StateFlow<Boolean> = _modelLoading.asStateFlow()

    private fun modelFile(model: LocalModel): File =
        File(File(context.filesDir, "models"), model.fileName)

    fun modelFileExists(model: LocalModel): Boolean = modelFile(model).exists()

    private fun ensureEngine(model: LocalModel) {
        val path = modelFile(model).absolutePath
        if (engine != null && loadedPath == path) return

        closeSessionInternal()
        runCatching { engine?.close() }
        engine = null
        loadedPath = null

        val maxTokens = LocalModelCatalog.maxTokensFor(model.id)

        fun options(backend: LlmInference.Backend) = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(maxTokens)
            .setMaxTopK(64)
            .setPreferredBackend(backend)
            .build()

        engine = try {
            LlmInference.createFromOptions(context, options(LlmInference.Backend.GPU))
        } catch (gpuError: Throwable) {
            // Many devices (and all emulators) lack the OpenCL path; retry on CPU.
            try {
                LlmInference.createFromOptions(context, options(LlmInference.Backend.CPU))
            } catch (cpuError: Throwable) {
                throw Exception(friendlyLoadError(cpuError))
            }
        }
        loadedPath = path
    }

    private fun friendlyLoadError(e: Throwable): String = when {
        e is OutOfMemoryError || e.message?.contains("memory", ignoreCase = true) == true ->
            "Not enough memory to load this model on this device. Try a smaller model."
        else ->
            "Could not load the model — the file may be corrupt or unsupported. " +
                "Re-download or re-import it. (${e.message?.take(120)})"
    }

    private fun newSession(): LlmInferenceSession {
        val eng = engine ?: throw Exception("Local model engine not initialized.")
        return LlmInferenceSession.createFromOptions(
            eng,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(40)
                .setTopP(0.95f)
                .setTemperature(0.8f)
                .build()
        )
    }

    private fun closeSessionInternal() {
        runCatching { session?.close() }
        session = null
        sessionChatId = null
        lastHistorySize = -1
    }

    private fun transcriptOf(turns: List<ChatMessage>): String = turns.joinToString("\n") { msg ->
        val speaker = if (msg.role == "user") "Human" else "EchoFlow"
        "$speaker: ${msg.content}"
    }

    private fun userTurnPrompt(content: String): String =
        "Human message:\n$content\n\nEchoFlow reply:"

    /**
     * Prepares the session for a new user turn and starts generation.
     *
     * @param history full chat history including the just-sent user message (last element).
     */
    fun generate(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            try {
                val coldStart = loadedPath != modelFile(model).absolutePath
                if (coldStart) _modelLoading.value = true
                ensureEngine(model)

                val incremental = session != null &&
                    sessionChatId == chatId &&
                    history.size == lastHistorySize + 2

                // Rebuilding a session with prior turns means a slow prefill, surface it too.
                if (!incremental && history.size > 1) _modelLoading.value = true

                val activeSession: LlmInferenceSession
                if (incremental) {
                    activeSession = session!!
                    activeSession.addQueryChunk(userTurnPrompt(history.last().content))
                } else {
                    closeSessionInternal()
                    activeSession = newSession()
                    session = activeSession
                    sessionChatId = chatId

                    if (systemPrompt.isNotBlank()) {
                        activeSession.addQueryChunk(
                            "System instructions for EchoFlow:\n$systemPrompt\n\n" +
                                "End of instructions. Continue only as EchoFlow."
                        )
                    }

                    val prior = history.dropLast(1).filter { it.role == "user" || it.role == "assistant" }
                    if (prior.isNotEmpty()) {
                        // Replay older turns as one transcript chunk, trimming the oldest turns
                        // when they would crowd out room for the answer.
                        val maxTokens = LocalModelCatalog.maxTokensFor(model.id)
                        var turns = prior
                        var transcript = transcriptOf(turns)
                        while (turns.size > 1 &&
                            runCatching { activeSession.sizeInTokens(transcript) }.getOrDefault(0) > maxTokens / 2
                        ) {
                            turns = turns.drop(2).ifEmpty { turns.drop(1) }
                            transcript = transcriptOf(turns)
                        }
                        if (turns.isNotEmpty()) {
                            activeSession.addQueryChunk("Previous conversation:\n$transcript")
                        }
                    }
                    activeSession.addQueryChunk(userTurnPrompt(history.last().content))
                }
                lastHistorySize = history.size
            } finally {
                _modelLoading.value = false
            }
        }

        startGeneration(this@callbackFlow)
        awaitClose { onFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /**
     * Injects extra context (e.g. web search results) into the live session. Used by the
     * prompt-protocol search loop between rounds.
     */
    fun appendContext(text: String) {
        runCatching { session?.addQueryChunk(text) }
    }

    /**
     * Continues generating on the existing session without adding a new user message.
     * Retries briefly because a just-cancelled generation may still be winding down.
     */
    fun continueGeneration(): Flow<StreamChunk> = callbackFlow {
        val activeSession = session ?: throw Exception("No active on-device session.")
        var lastError: Throwable? = null
        var started = false
        for (attempt in 0 until 10) {
            try {
                startGeneration(this@callbackFlow, activeSession)
                started = true
                break
            } catch (e: Exception) {
                lastError = e
                delay(250)
            }
        }
        if (!started) throw Exception("On-device model is busy: ${lastError?.message}")
        awaitClose { onFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun startGeneration(
        producer: kotlinx.coroutines.channels.ProducerScope<StreamChunk>,
        explicitSession: LlmInferenceSession? = null
    ) {
        val activeSession = explicitSession ?: session ?: throw Exception("No active on-device session.")
        generating.set(true)
        try {
            activeSession.generateResponseAsync { partialResult, done ->
                if (!partialResult.isNullOrEmpty()) {
                    producer.trySend(StreamChunk.Content(partialResult))
                }
                if (done) {
                    generating.set(false)
                    producer.close()
                }
            }
        } catch (e: Throwable) {
            generating.set(false)
            throw e
        }
    }

    private fun onFlowClosed() {
        if (generating.getAndSet(false)) {
            runCatching { session?.cancelGenerateResponseAsync() }
        }
    }

    /** Frees the engine and session (model switch away from local, or ViewModel cleared). */
    fun releaseAll() {
        closeSessionInternal()
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
        generating.set(false)
    }
}
