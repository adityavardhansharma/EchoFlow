package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Routes an installed model to its persisted runtime. The settings screen exposes models,
 * not implementation details, so this is the only place the app chooses an on-device engine.
 */
class LocalImageGenerationEngineRouter(
    private val mediaPipe: ImageGenerationEngine,
    private val stableDiffusionCpp: ImageGenerationEngine,
) : ImageGenerationEngine {
    @Volatile
    private var active: ImageGenerationEngine? = null

    override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> {
        val model = request.localModel
            ?: return failedFlow(
                ImageGenerationException.ModelNotInstalled(
                    "Pick an on-device image model in Settings → Image generation first."
                )
            )
        val engine = when (model.runtime) {
            LocalImageRuntime.MEDIAPIPE.id -> mediaPipe
            LocalImageRuntime.STABLE_DIFFUSION_CPP.id -> stableDiffusionCpp
            else -> return failedFlow(
                ImageGenerationException.InitializationFailed(
                    "${model.name} uses an image runtime this version of EchoFlow doesn't support."
                )
            )
        }
        return flow {
            active = engine
            try {
                emitAll(engine.generate(request))
            } finally {
                if (active === engine) active = null
            }
        }
    }

    override fun cancel() {
        active?.cancel()
    }

    override fun close() {
        active = null
        mediaPipe.close()
        stableDiffusionCpp.close()
    }

    private fun failedFlow(error: ImageGenerationException): Flow<ImageGenerationEvent> = flow {
        throw error
    }
}
