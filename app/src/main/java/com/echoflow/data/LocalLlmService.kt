package com.echoflow.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
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
 * On-device inference behind one facade, with two runtimes:
 *
 * - `.litertlm` bundles (Gemma 4, Qwen3, ...) run on the standalone **LiteRT-LM engine**
 *   ([Engine]/[Conversation]) — the same stack AI Edge Gallery uses. The newer bundles
 *   crash natively under the MediaPipe wrapper, so they must not go through it.
 * - `.task` files keep using MediaPipe LLM Inference ([LlmInference]), which is their
 *   native format.
 *
 * One engine (model) is resident at a time; one conversation/session is kept per chat so
 * multi-turn context is reused instead of re-prefilling the transcript every message.
 */
class LocalLlmService(private val context: Context) {

    // ── MediaPipe runtime (.task) ────────────────────────────────────────────────────
    private var mpEngine: LlmInference? = null
    private var mpLoadedPath: String? = null
    private var mpSession: LlmInferenceSession? = null
    private var mpSessionChatId: String? = null
    private var mpLastHistorySize = -1

    // ── LiteRT-LM runtime (.litertlm) ────────────────────────────────────────────────
    private var lrtEngine: Engine? = null
    private var lrtLoadedPath: String? = null
    private var lrtConversation: Conversation? = null
    private var lrtChatId: String? = null
    private var lrtLastHistorySize = -1
    private var lrtSystemPrompt: String? = null

    /** Search results waiting to be sent on the next [continueGeneration] round. */
    private var lrtPendingContext: String? = null

    /** Which runtime the in-flight generation belongs to (for appendContext/continue). */
    private var activeIsLitert = false

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

    private fun usesLiteRtLm(model: LocalModel): Boolean =
        model.fileName.endsWith(".litertlm", ignoreCase = true)

    /**
     * Native OOM during weight loading aborts the whole process and is uncatchable, so
     * refuse upfront when device RAM is clearly below what the model peaks at (AI Edge
     * Gallery does the same check against estimated peak memory).
     */
    private fun checkRamBudget(file: File) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val estimatedPeakBytes = (file.length() * 1.8).toLong()
        if (memInfo.totalMem in 1 until estimatedPeakBytes) {
            val needGb = estimatedPeakBytes / (1024.0 * 1024 * 1024)
            throw Exception(
                "This model needs roughly %.1f GB of RAM, more than this device has. Try a smaller model.".format(needGb)
            )
        }
    }

    private fun friendlyLoadError(e: Throwable): String = when {
        e is OutOfMemoryError || e.message?.contains("memory", ignoreCase = true) == true ->
            "Not enough memory to load this model on this device. Try a smaller model."
        else ->
            "Could not load the model — the file may be corrupt or unsupported. " +
                "Re-download or re-import it. (${e.message?.take(120)})"
    }

    private fun transcriptOf(turns: List<ChatMessage>): String = turns.joinToString("\n") { msg ->
        val speaker = if (msg.role == "user") "Human" else "EchoFlow"
        "$speaker: ${msg.content}"
    }

    /**
     * Prepares the runtime for a new user turn and starts generation.
     *
     * @param history full chat history including the just-sent user message (last element).
     */
    fun generate(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String
    ): Flow<StreamChunk> {
        activeIsLitert = usesLiteRtLm(model)
        return if (activeIsLitert) generateLitert(model, chatId, history, systemPrompt)
        else generateMediaPipe(model, chatId, history, systemPrompt)
    }

    /**
     * Injects extra context (e.g. web search results) to be consumed by the next
     * [continueGeneration] round of the prompt-protocol search loop.
     */
    fun appendContext(text: String) {
        if (activeIsLitert) {
            lrtPendingContext = (lrtPendingContext ?: "") + text
        } else {
            runCatching { mpSession?.addQueryChunk(text) }
        }
    }

    /** Continues generating after [appendContext], without a new user message. */
    fun continueGeneration(): Flow<StreamChunk> =
        if (activeIsLitert) continueLitert() else continueMediaPipe()

    /** Frees all engines and sessions (model switch away from local, or ViewModel cleared). */
    fun releaseAll() {
        closeMpSessionInternal()
        runCatching { mpEngine?.close() }
        mpEngine = null
        mpLoadedPath = null

        closeLrtConversationInternal()
        runCatching { lrtEngine?.close() }
        lrtEngine = null
        lrtLoadedPath = null

        generating.set(false)
    }

    // ── LiteRT-LM implementation ─────────────────────────────────────────────────────

    private fun createLitertEngine(path: String, maxTokens: Int, backend: Backend): Engine {
        val engine = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                visionBackend = null,
                audioBackend = null,
                maxNumTokens = maxTokens,
                cacheDir = null,
            )
        )
        try {
            engine.initialize()
        } catch (e: Throwable) {
            runCatching { engine.close() }
            throw e
        }
        return engine
    }

    private fun ensureLitertEngine(model: LocalModel) {
        val file = modelFile(model)
        val path = file.absolutePath
        if (lrtEngine != null && lrtLoadedPath == path) return

        closeLrtConversationInternal()
        runCatching { lrtEngine?.close() }
        lrtEngine = null
        lrtLoadedPath = null

        checkRamBudget(file)
        val maxTokens = LocalModelCatalog.maxTokensFor(model.id, model.fileName)

        lrtEngine = try {
            createLitertEngine(path, maxTokens, Backend.GPU())
        } catch (gpuError: Throwable) {
            // Some devices lack a usable GPU delegate; retry on CPU before giving up.
            try {
                createLitertEngine(path, maxTokens, Backend.CPU())
            } catch (cpuError: Throwable) {
                throw Exception(friendlyLoadError(cpuError))
            }
        }
        lrtLoadedPath = path
    }

    private fun closeLrtConversationInternal() {
        runCatching { lrtConversation?.close() }
        lrtConversation = null
        lrtChatId = null
        lrtLastHistorySize = -1
        lrtSystemPrompt = null
        lrtPendingContext = null
    }

    private fun generateLitert(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            val text: String
            try {
                val coldStart = lrtLoadedPath != modelFile(model).absolutePath
                if (coldStart) _modelLoading.value = true
                ensureLitertEngine(model)

                val incremental = lrtConversation != null &&
                    lrtChatId == chatId &&
                    lrtSystemPrompt == systemPrompt &&
                    history.size == lrtLastHistorySize + 2

                if (incremental) {
                    text = history.last().content
                } else {
                    if (history.size > 1) _modelLoading.value = true
                    closeLrtConversationInternal()
                    lrtConversation = lrtEngine!!.createConversation(
                        ConversationConfig(
                            samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                            systemInstruction = systemPrompt.takeIf { it.isNotBlank() }
                                ?.let { Contents.of(mutableListOf<Content>(Content.Text(it))) },
                        )
                    )
                    lrtChatId = chatId
                    lrtSystemPrompt = systemPrompt

                    // Replay older turns as a transcript preamble; the engine applies the
                    // model's chat template to the message itself.
                    val prior = history.dropLast(1).filter { it.role == "user" || it.role == "assistant" }
                    text = if (prior.isNotEmpty()) {
                        "Previous conversation:\n" + transcriptOf(prior) +
                            "\n\nCurrent message:\n" + history.last().content
                    } else {
                        history.last().content
                    }
                }
                lrtLastHistorySize = history.size
            } finally {
                _modelLoading.value = false
            }
            sendLitertMessage(this@callbackFlow, text)
        }
        awaitClose { onLitertFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun continueLitert(): Flow<StreamChunk> = callbackFlow {
        val text = lrtPendingContext
            ?: throw Exception("No pending context for the on-device conversation.")
        lrtPendingContext = null

        // Retry briefly because a just-cancelled generation may still be winding down.
        var started = false
        var lastError: Throwable? = null
        for (attempt in 0 until 10) {
            try {
                sendLitertMessage(this@callbackFlow, text)
                started = true
                break
            } catch (e: Exception) {
                lastError = e
                delay(250)
            }
        }
        if (!started) throw Exception("On-device model is busy: ${lastError?.message}")
        awaitClose { onLitertFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun sendLitertMessage(producer: ProducerScope<StreamChunk>, text: String) {
        val conversation = lrtConversation ?: throw Exception("No active on-device conversation.")
        generating.set(true)
        try {
            conversation.sendMessageAsync(
                Contents.of(mutableListOf<Content>(Content.Text(text))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val thought = message.channels["thought"]
                        if (!thought.isNullOrEmpty()) {
                            producer.trySend(StreamChunk.Reasoning(thought))
                        }
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) {
                            producer.trySend(StreamChunk.Content(chunk))
                        }
                    }

                    override fun onDone() {
                        generating.set(false)
                        producer.close()
                    }

                    override fun onError(throwable: Throwable) {
                        generating.set(false)
                        if (throwable is java.util.concurrent.CancellationException) {
                            producer.close()
                        } else {
                            producer.close(Exception("On-device model error: ${throwable.message?.take(160)}"))
                        }
                    }
                },
                emptyMap(),
            )
        } catch (e: Throwable) {
            generating.set(false)
            throw e
        }
    }

    private fun onLitertFlowClosed() {
        if (activeIsLitert && generating.getAndSet(false)) {
            runCatching { lrtConversation?.cancelProcess() }
        }
    }

    // ── MediaPipe implementation ─────────────────────────────────────────────────────

    private fun ensureMpEngine(model: LocalModel) {
        val file = modelFile(model)
        val path = file.absolutePath
        if (mpEngine != null && mpLoadedPath == path) return

        closeMpSessionInternal()
        runCatching { mpEngine?.close() }
        mpEngine = null
        mpLoadedPath = null

        checkRamBudget(file)
        val maxTokens = LocalModelCatalog.maxTokensFor(model.id, model.fileName)

        fun options(backend: LlmInference.Backend) = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(maxTokens)
            .setMaxTopK(64)
            .setPreferredBackend(backend)
            .build()

        mpEngine = try {
            LlmInference.createFromOptions(context, options(LlmInference.Backend.GPU))
        } catch (gpuError: Throwable) {
            // Many devices (and all emulators) lack the OpenCL path; retry on CPU.
            try {
                LlmInference.createFromOptions(context, options(LlmInference.Backend.CPU))
            } catch (cpuError: Throwable) {
                throw Exception(friendlyLoadError(cpuError))
            }
        }
        mpLoadedPath = path
    }

    private fun newMpSession(): LlmInferenceSession {
        val eng = mpEngine ?: throw Exception("Local model engine not initialized.")
        // Sampler values mirror AI Edge Gallery's defaults for these bundles (Gemma is
        // tuned for temperature 1.0); topK must stay <= the engine's setMaxTopK.
        return LlmInferenceSession.createFromOptions(
            eng,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(64)
                .setTopP(0.95f)
                .setTemperature(1.0f)
                .build()
        )
    }

    private fun closeMpSessionInternal() {
        runCatching { mpSession?.close() }
        mpSession = null
        mpSessionChatId = null
        mpLastHistorySize = -1
    }

    private fun userTurnPrompt(content: String): String =
        "Human message:\n$content\n\nEchoFlow reply:"

    private fun generateMediaPipe(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            try {
                val coldStart = mpLoadedPath != modelFile(model).absolutePath
                if (coldStart) _modelLoading.value = true
                ensureMpEngine(model)

                val incremental = mpSession != null &&
                    mpSessionChatId == chatId &&
                    history.size == mpLastHistorySize + 2

                // Rebuilding a session with prior turns means a slow prefill, surface it too.
                if (!incremental && history.size > 1) _modelLoading.value = true

                val activeSession: LlmInferenceSession
                if (incremental) {
                    activeSession = mpSession!!
                    activeSession.addQueryChunk(userTurnPrompt(history.last().content))
                } else {
                    closeMpSessionInternal()
                    activeSession = newMpSession()
                    mpSession = activeSession
                    mpSessionChatId = chatId

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
                        val maxTokens = LocalModelCatalog.maxTokensFor(model.id, model.fileName)
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
                mpLastHistorySize = history.size
            } finally {
                _modelLoading.value = false
            }
        }

        startMpGeneration(this@callbackFlow)
        awaitClose { onMpFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /**
     * Continues generating on the existing session without adding a new user message.
     * Retries briefly because a just-cancelled generation may still be winding down.
     */
    private fun continueMediaPipe(): Flow<StreamChunk> = callbackFlow {
        val activeSession = mpSession ?: throw Exception("No active on-device session.")
        var lastError: Throwable? = null
        var started = false
        for (attempt in 0 until 10) {
            try {
                startMpGeneration(this@callbackFlow, activeSession)
                started = true
                break
            } catch (e: Exception) {
                lastError = e
                delay(250)
            }
        }
        if (!started) throw Exception("On-device model is busy: ${lastError?.message}")
        awaitClose { onMpFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun startMpGeneration(
        producer: ProducerScope<StreamChunk>,
        explicitSession: LlmInferenceSession? = null
    ) {
        val activeSession = explicitSession ?: mpSession ?: throw Exception("No active on-device session.")
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

    private fun onMpFlowClosed() {
        if (!activeIsLitert && generating.getAndSet(false)) {
            runCatching { mpSession?.cancelGenerateResponseAsync() }
        }
    }
}
