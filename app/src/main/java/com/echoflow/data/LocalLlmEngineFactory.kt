package com.echoflow.data

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession

/** Runtime-native engine/session construction, separate from ownership and lifecycle. */
internal object LocalLlmEngineFactory {
    fun createLiteRt(
        path: String,
        maxTokens: Int,
        backend: Backend,
        visionBackend: Backend?,
    ): Engine {
        val engine = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                visionBackend = visionBackend,
                audioBackend = null,
                maxNumTokens = maxTokens,
                maxNumImages = if (visionBackend != null) 1 else null,
                cacheDir = null,
            ),
        )
        try {
            engine.initialize()
        } catch (error: Throwable) {
            runCatching { engine.close() }
            throw error
        }
        return engine
    }

    fun createMediaPipeSession(
        engine: LlmInference,
        params: InferenceParams,
        maxTopK: Int,
    ): LlmInferenceSession = LlmInferenceSession.createFromOptions(
        engine,
        LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(if (params.topK <= 0) maxTopK else params.topK)
            .setTopP(params.topP)
            .setTemperature(params.temperature)
            .build(),
    )
}
