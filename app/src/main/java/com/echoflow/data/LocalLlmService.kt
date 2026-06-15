package com.echoflow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device inference behind one facade, with three runtimes:
 *
 * - `.litertlm` bundles (Gemma 4, Qwen3, ...) run on the standalone **LiteRT-LM engine**
 *   ([Engine]/[Conversation]) — the same stack AI Edge Gallery uses. The newer bundles
 *   crash natively under the MediaPipe wrapper, so they must not go through it.
 * - `.task` files keep using MediaPipe LLM Inference ([LlmInference]), which is their
 *   native format.
 * - `.gguf` files run on the **llama.cpp engine** ([LlamaAndroid]) — the standard format
 *   for community quantized models pulled from Hugging Face.
 *
 * One engine (model) is resident at a time; one conversation/session is kept per chat so
 * multi-turn context is reused instead of re-prefilling the transcript every message.
 *
 * Every generation runs the user's global [InferenceParams] (already coerced to the active
 * model's limits by the caller) through the runtime's native sampler.
 */
class LocalLlmService(private val context: Context) {

    private enum class Runtime { MEDIAPIPE, LITERT, GGUF }

    // ── MediaPipe runtime (.task) ────────────────────────────────────────────────────
    private var mpEngine: LlmInference? = null
    private var mpLoadedPath: String? = null
    private var mpLoadedMaxTokens = -1
    private var mpLoadedMaxTopK = -1
    private var mpSession: LlmInferenceSession? = null
    private var mpSessionChatId: String? = null
    private var mpLastHistorySize = -1
    private var mpParams: InferenceParams? = null

    // ── LiteRT-LM runtime (.litertlm) ────────────────────────────────────────────────
    private var lrtEngine: Engine? = null
    private var lrtLoadedPath: String? = null
    private var lrtLoadedMaxTokens = -1
    private var lrtConversation: Conversation? = null
    private var lrtChatId: String? = null
    private var lrtLastHistorySize = -1
    private var lrtSystemPrompt: String? = null
    private var lrtParams: InferenceParams? = null

    /** Search results waiting to be sent on the next [continueGeneration] round. */
    private var lrtPendingContext: String? = null

    // ── llama.cpp runtime (.gguf) ─────────────────────────────────────────────────────
    private var ggufEngine: LlamaAndroid? = null
    private var ggufContextId: Int? = null
    private var ggufLoadedPath: String? = null
    private var ggufLoadedMaxTokens = -1
    private var ggufChatId: String? = null
    /** Conversation text the next completion continues from (full reprompt per turn). */
    private var ggufBasePrompt: String = ""
    /** Tokens streamed so far in the in-flight completion (for search continuation). */
    private var ggufFullText = StringBuilder()
    private var ggufPendingContext: String? = null
    private var ggufJob: Job? = null
    private val ggufScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Which runtime the in-flight generation belongs to (for appendContext/continue). */
    private var activeRuntime = Runtime.MEDIAPIPE
    /** Params of the in-flight generation, reused by [continueGeneration]. */
    private var activeParams: InferenceParams = InferenceLimits.LOCAL_DEFAULTS

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

    private fun runtimeFor(model: LocalModel): Runtime = when {
        LocalModelCatalog.isGguf(model.fileName) -> Runtime.GGUF
        LocalModelCatalog.isLiteRtLm(model.fileName) -> Runtime.LITERT
        else -> Runtime.MEDIAPIPE
    }

    /** Resolves the effective token budget: the coerced param, or the model's own default. */
    private fun effectiveMaxTokens(model: LocalModel, params: InferenceParams): Int =
        params.maxTokens.takeIf { it > 0 } ?: LocalModelCatalog.maxTokensFor(model.id, model.fileName)

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
     * Decodes an attachment URI into a downscaled JPEG [Content.ImageBytes] for multimodal
     * .litertlm bundles. Returns null when there's no image or it can't be read.
     */
    private fun imageContentFromUri(uriString: String?): Content? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val raw = context.contentResolver.openInputStream(Uri.parse(uriString))?.use { it.readBytes() }
                ?: return null
            var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
            val max = 768
            val longest = maxOf(bmp.width, bmp.height)
            if (longest > max) {
                val scale = max.toFloat() / longest
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            }
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            Content.ImageBytes(out.toByteArray())
        } catch (e: Exception) {
            null
        }
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
        systemPrompt: String,
        params: InferenceParams = InferenceLimits.LOCAL_DEFAULTS
    ): Flow<StreamChunk> {
        activeRuntime = runtimeFor(model)
        activeParams = params
        return when (activeRuntime) {
            Runtime.GGUF -> generateGguf(model, chatId, history, systemPrompt, params)
            Runtime.LITERT -> generateLitert(model, chatId, history, systemPrompt, params)
            Runtime.MEDIAPIPE -> generateMediaPipe(model, chatId, history, systemPrompt, params)
        }
    }

    /**
     * Injects extra context (e.g. web search results) to be consumed by the next
     * [continueGeneration] round of the prompt-protocol search loop.
     */
    fun appendContext(text: String) {
        when (activeRuntime) {
            Runtime.LITERT -> lrtPendingContext = (lrtPendingContext ?: "") + text
            Runtime.GGUF -> ggufPendingContext = (ggufPendingContext ?: "") + text
            Runtime.MEDIAPIPE -> runCatching { mpSession?.addQueryChunk(text) }
        }
    }

    /** Continues generating after [appendContext], without a new user message. */
    fun continueGeneration(): Flow<StreamChunk> = when (activeRuntime) {
        Runtime.LITERT -> continueLitert()
        Runtime.GGUF -> continueGguf()
        Runtime.MEDIAPIPE -> continueMediaPipe()
    }

    /** Frees all engines and sessions (model switch away from local, or ViewModel cleared). */
    fun releaseAll() {
        closeMpSessionInternal()
        runCatching { mpEngine?.close() }
        mpEngine = null
        mpLoadedPath = null
        mpLoadedMaxTokens = -1
        mpLoadedMaxTopK = -1

        closeLrtConversationInternal()
        runCatching { lrtEngine?.close() }
        lrtEngine = null
        lrtLoadedPath = null
        lrtLoadedMaxTokens = -1

        releaseGgufInternal()

        generating.set(false)
    }

    // ── LiteRT-LM implementation ─────────────────────────────────────────────────────

    private fun createLitertEngine(path: String, maxTokens: Int, backend: Backend, visionBackend: Backend?): Engine {
        val engine = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                // Vision is enabled for every .litertlm engine so image attachments work on
                // multimodal bundles (Gemma 4). Text-only bundles simply ignore the image.
                visionBackend = visionBackend,
                audioBackend = null,
                maxNumTokens = maxTokens,
                maxNumImages = if (visionBackend != null) 1 else null,
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

    private fun ensureLitertEngine(model: LocalModel, maxTokens: Int) {
        val file = modelFile(model)
        val path = file.absolutePath
        // The kv-cache size is baked in at engine creation, so a changed token budget needs
        // a fresh engine, not just a fresh conversation.
        if (lrtEngine != null && lrtLoadedPath == path && lrtLoadedMaxTokens == maxTokens) return

        closeLrtConversationInternal()
        runCatching { lrtEngine?.close() }
        lrtEngine = null
        lrtLoadedPath = null
        lrtLoadedMaxTokens = -1

        checkRamBudget(file)

        lrtEngine = try {
            createLitertEngine(path, maxTokens, Backend.GPU(), visionBackend = Backend.GPU())
        } catch (gpuError: Throwable) {
            // Some devices lack a usable GPU delegate; retry on CPU before giving up.
            try {
                createLitertEngine(path, maxTokens, Backend.CPU(), visionBackend = Backend.CPU())
            } catch (cpuError: Throwable) {
                throw Exception(friendlyLoadError(cpuError))
            }
        }
        lrtLoadedPath = path
        lrtLoadedMaxTokens = maxTokens
    }

    private fun closeLrtConversationInternal() {
        runCatching { lrtConversation?.close() }
        lrtConversation = null
        lrtChatId = null
        lrtLastHistorySize = -1
        lrtSystemPrompt = null
        lrtPendingContext = null
        lrtParams = null
    }

    private fun generateLitert(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            val text: String
            try {
                val maxTokens = effectiveMaxTokens(model, params)
                val coldStart = lrtLoadedPath != modelFile(model).absolutePath || lrtLoadedMaxTokens != maxTokens
                if (coldStart) _modelLoading.value = true
                ensureLitertEngine(model, maxTokens)

                val incremental = lrtConversation != null &&
                    lrtChatId == chatId &&
                    lrtSystemPrompt == systemPrompt &&
                    lrtParams == params &&
                    history.size == lrtLastHistorySize + 2

                if (incremental) {
                    text = history.last().content
                } else {
                    if (history.size > 1) _modelLoading.value = true
                    closeLrtConversationInternal()
                    lrtConversation = lrtEngine!!.createConversation(
                        ConversationConfig(
                            samplerConfig = SamplerConfig(
                                topK = params.topK.coerceAtLeast(1),
                                topP = params.topP.toDouble(),
                                temperature = params.temperature.toDouble(),
                            ),
                            systemInstruction = systemPrompt.takeIf { it.isNotBlank() }
                                ?.let { Contents.of(mutableListOf<Content>(Content.Text(it))) },
                        )
                    )
                    lrtChatId = chatId
                    lrtSystemPrompt = systemPrompt
                    lrtParams = params

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
            // An image on the latest user turn is sent alongside the text (multimodal bundles).
            val image = history.lastOrNull()?.takeIf { it.role == "user" }?.let { imageContentFromUri(it.localAttachmentUri) }
            sendLitertMessage(this@callbackFlow, text, image)
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

    private fun sendLitertMessage(producer: ProducerScope<StreamChunk>, text: String, image: Content? = null) {
        val conversation = lrtConversation ?: throw Exception("No active on-device conversation.")
        generating.set(true)
        try {
            val parts = mutableListOf<Content>(Content.Text(text))
            if (image != null) parts.add(image)
            conversation.sendMessageAsync(
                Contents.of(parts),
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
        if (activeRuntime == Runtime.LITERT && generating.getAndSet(false)) {
            runCatching { lrtConversation?.cancelProcess() }
        }
    }

    // ── MediaPipe implementation ─────────────────────────────────────────────────────

    private fun ensureMpEngine(model: LocalModel, maxTokens: Int, maxTopK: Int) {
        val file = modelFile(model)
        val path = file.absolutePath
        // Token budget and the top-k ceiling are fixed at engine creation, so a change in
        // either forces a rebuild.
        if (mpEngine != null && mpLoadedPath == path && mpLoadedMaxTokens == maxTokens && mpLoadedMaxTopK == maxTopK) return

        closeMpSessionInternal()
        runCatching { mpEngine?.close() }
        mpEngine = null
        mpLoadedPath = null
        mpLoadedMaxTokens = -1
        mpLoadedMaxTopK = -1

        checkRamBudget(file)

        fun options(backend: LlmInference.Backend) = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(maxTokens)
            .setMaxTopK(maxTopK)
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
        mpLoadedMaxTokens = maxTokens
        mpLoadedMaxTopK = maxTopK
    }

    private fun newMpSession(params: InferenceParams, maxTopK: Int): LlmInferenceSession {
        val eng = mpEngine ?: throw Exception("Local model engine not initialized.")
        // topK must stay <= the engine's setMaxTopK; 0 (disabled) isn't valid here, so the
        // engine ceiling stands in for "no cut".
        return LlmInferenceSession.createFromOptions(
            eng,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(if (params.topK <= 0) maxTopK else params.topK)
                .setTopP(params.topP)
                .setTemperature(params.temperature)
                .build()
        )
    }

    private fun closeMpSessionInternal() {
        runCatching { mpSession?.close() }
        mpSession = null
        mpSessionChatId = null
        mpLastHistorySize = -1
        mpParams = null
    }

    private fun userTurnPrompt(content: String): String =
        "Human message:\n$content\n\nEchoFlow reply:"

    private fun generateMediaPipe(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            try {
                val maxTokens = effectiveMaxTokens(model, params)
                // The session top-k must be <= the engine ceiling, so the ceiling is the
                // larger of the default 64 and whatever the user asked for.
                val maxTopK = maxOf(64, params.topK)
                val coldStart = mpLoadedPath != modelFile(model).absolutePath ||
                    mpLoadedMaxTokens != maxTokens || mpLoadedMaxTopK != maxTopK
                if (coldStart) _modelLoading.value = true
                ensureMpEngine(model, maxTokens, maxTopK)

                val incremental = mpSession != null &&
                    mpSessionChatId == chatId &&
                    mpParams == params &&
                    history.size == mpLastHistorySize + 2

                // Rebuilding a session with prior turns means a slow prefill, surface it too.
                if (!incremental && history.size > 1) _modelLoading.value = true

                val activeSession: LlmInferenceSession
                if (incremental) {
                    activeSession = mpSession!!
                    activeSession.addQueryChunk(userTurnPrompt(history.last().content))
                } else {
                    closeMpSessionInternal()
                    activeSession = newMpSession(params, maxTopK)
                    mpSession = activeSession
                    mpSessionChatId = chatId
                    mpParams = params

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
        if (activeRuntime == Runtime.MEDIAPIPE && generating.getAndSet(false)) {
            runCatching { mpSession?.cancelGenerateResponseAsync() }
        }
    }

    // ── llama.cpp (.gguf) implementation ───────────────────────────────────────────────

    private fun releaseGgufInternal() {
        ggufJob?.cancel()
        ggufJob = null
        val id = ggufContextId
        val engine = ggufEngine
        if (id != null && engine != null) {
            runCatching { engine.releaseContext(id) }
        }
        ggufEngine = null
        ggufContextId = null
        ggufLoadedPath = null
        ggufLoadedMaxTokens = -1
        ggufChatId = null
        ggufBasePrompt = ""
        ggufPendingContext = null
        ggufFullText = StringBuilder()
    }

    private fun ensureGgufEngine(model: LocalModel, maxTokens: Int) {
        val file = modelFile(model)
        val path = file.absolutePath
        if (ggufEngine != null && ggufContextId != null && ggufLoadedPath == path && ggufLoadedMaxTokens == maxTokens) return

        releaseGgufInternal()
        checkRamBudget(file)

        val engine = LlamaAndroid(context.contentResolver)
        val uri = Uri.fromFile(file)
        val fd = context.contentResolver.openFileDescriptor(uri, "r")?.detachFd()
            ?: throw Exception("Could not open the on-device model file.")
        val config = mapOf<String, Any>(
            "model" to uri.toString(),
            "model_fd" to fd,
            "use_mmap" to false,
            "use_mlock" to false,
            "n_ctx" to maxTokens,
            "embedding" to false,
            "n_batch" to 512,
            "n_threads" to 0, // 0 = let llama.cpp pick a good thread count
            "n_gpu_layers" to 0, // the AAR runs CPU-only
            "vocab_only" to false,
            "lora" to "",
            "lora_scaled" to 1.0,
            "rope_freq_base" to 0.0,
            "rope_freq_scale" to 0.0,
        )

        // The token callback streams to whichever generation is in flight; it reads the
        // current producer at call time, so reusing one engine across turns is fine.
        val result = engine.startEngine(config) { token ->
            ggufFullText.append(token)
            ggufProducer?.trySend(StreamChunk.Content(token))
        } ?: throw Exception(
            "Could not load this GGUF model. It may be corrupt, an unsupported quantization, " +
                "or too large for this device."
        )

        ggufContextId = (result["contextId"] as Number).toInt()
        ggufEngine = engine
        ggufLoadedPath = path
        ggufLoadedMaxTokens = maxTokens
    }

    /** Producer of the in-flight GGUF generation; set under the setup lock before launch. */
    @Volatile
    private var ggufProducer: ProducerScope<StreamChunk>? = null

    /**
     * Builds the prompt for a turn by applying the model's own chat template (via llama.cpp)
     * to the system prompt + conversation. Falls back to a plain transcript if the embedded
     * template can't be applied.
     */
    private suspend fun buildGgufPrompt(systemPrompt: String, history: List<ChatMessage>): String {
        val engine = ggufEngine ?: return transcriptOf(history)
        val id = ggufContextId ?: return transcriptOf(history)
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add(mapOf("role" to "system", "content" to systemPrompt as Any))
            history.filter { it.role == "user" || it.role == "assistant" }
                .forEach { add(mapOf("role" to it.role, "content" to it.content as Any)) }
        }
        return runCatching { engine.getFormattedChat(id, messages, "").first() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: buildString {
                if (systemPrompt.isNotBlank()) append("System: ").append(systemPrompt).append("\n\n")
                append(transcriptOf(history.filter { it.role == "user" || it.role == "assistant" }))
                append("\nEchoFlow:")
            }
    }

    private fun generateGguf(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams
    ): Flow<StreamChunk> = callbackFlow {
        setupMutex.withLock {
            if (generating.get()) throw Exception("The on-device model is still responding — wait for it to finish.")
            val prompt: String
            try {
                val maxTokens = effectiveMaxTokens(model, params)
                val coldStart = ggufLoadedPath != modelFile(model).absolutePath || ggufLoadedMaxTokens != maxTokens
                if (coldStart) _modelLoading.value = true
                ensureGgufEngine(model, maxTokens)
                if (history.size > 1) _modelLoading.value = true
                ggufChatId = chatId
                prompt = buildGgufPrompt(systemPrompt, history)
                ggufBasePrompt = prompt
            } finally {
                _modelLoading.value = false
            }
            launchGgufCompletion(this@callbackFlow, prompt, params)
        }
        awaitClose { onGgufFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun continueGguf(): Flow<StreamChunk> = callbackFlow {
        val pending = ggufPendingContext
            ?: throw Exception("No pending context for the on-device conversation.")
        ggufPendingContext = null
        // Continue from the conversation plus what the model produced so far, then the
        // injected search results — a full reprompt, since llama.cpp completion is stateless
        // across calls here.
        val prompt = ggufBasePrompt + ggufFullText.toString() + "\n" + pending
        ggufBasePrompt = prompt

        var started = false
        var lastError: Throwable? = null
        for (attempt in 0 until 10) {
            try {
                launchGgufCompletion(this@callbackFlow, prompt, activeParams)
                started = true
                break
            } catch (e: Exception) {
                lastError = e
                delay(250)
            }
        }
        if (!started) throw Exception("On-device model is busy: ${lastError?.message}")
        awaitClose { onGgufFlowClosed() }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    private fun launchGgufCompletion(producer: ProducerScope<StreamChunk>, prompt: String, params: InferenceParams) {
        val engine = ggufEngine ?: throw Exception("No active on-device engine.")
        val id = ggufContextId ?: throw Exception("No active on-device context.")
        generating.set(true)
        ggufProducer = producer
        ggufFullText = StringBuilder()
        val completionParams = mapOf<String, Any>(
            "prompt" to prompt,
            "emit_partial_completion" to true,
            "temperature" to params.temperature.toDouble(),
            "top_k" to params.topK,
            "top_p" to params.topP.toDouble(),
            "n_predict" to -1, // stream until EOS or the context window fills
            "n_threads" to 0,
        )
        // launchCompletion blocks until done while streaming via the token callback, so run
        // it off the flow's coroutine and close the producer when it returns.
        ggufJob = ggufScope.launch {
            try {
                engine.launchCompletion(id, completionParams)
                generating.set(false)
                producer.close()
            } catch (e: Throwable) {
                generating.set(false)
                producer.close(Exception("On-device model error: ${e.message?.take(160)}"))
            }
        }
    }

    private fun onGgufFlowClosed() {
        if (activeRuntime == Runtime.GGUF && generating.getAndSet(false)) {
            val id = ggufContextId
            val engine = ggufEngine
            if (id != null && engine != null) {
                ggufScope.launch { runCatching { engine.stopCompletion(id) } }
            }
            ggufJob?.cancel()
        }
    }
}
