package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageGenerationEngineRouterTest {
    @Test
    fun `routes each persisted runtime without exposing pipeline choice`() = runTest {
        val mediaPipe = RecordingEngine()
        val cpp = RecordingEngine()
        val router = LocalImageGenerationEngineRouter(mediaPipe, cpp)

        router.generate(request(runtime = LocalImageRuntime.MEDIAPIPE.id)).toList()
        router.generate(request(runtime = LocalImageRuntime.STABLE_DIFFUSION_CPP.id)).toList()

        assertEquals(1, mediaPipe.generations)
        assertEquals(1, cpp.generations)
    }

    @Test
    fun `rejects an unknown persisted runtime instead of silently rerouting`() = runTest {
        val mediaPipe = RecordingEngine()
        val cpp = RecordingEngine()
        val router = LocalImageGenerationEngineRouter(mediaPipe, cpp)

        val error = try {
            router.generate(request(runtime = "future-runtime")).toList()
            null
        } catch (failure: Throwable) {
            failure
        }
        assertTrue(error is ImageGenerationException.InitializationFailed)
        assertEquals(0, mediaPipe.generations)
        assertEquals(0, cpp.generations)
    }

    @Test
    fun `close releases both runtimes`() {
        val mediaPipe = RecordingEngine()
        val cpp = RecordingEngine()
        val router = LocalImageGenerationEngineRouter(mediaPipe, cpp)

        router.close()

        assertTrue(mediaPipe.closed)
        assertTrue(cpp.closed)
        assertFalse(mediaPipe.cancelled)
        assertFalse(cpp.cancelled)
    }

    private fun request(runtime: String) = ImageGenerationRequest(
        chatId = "chat",
        prompt = "a lighthouse",
        modelId = "local-image/test",
        localModel = LocalImageModel(
            id = "local-image/test",
            name = "Test model",
            directoryName = "test",
            installedBytes = 1,
            sourceRevision = "revision",
            sourceCheckpointSha256 = "a".repeat(64),
            bundleSha256 = "b".repeat(64),
            licenseId = "license",
            activationPhrase = null,
            defaultNegativePrompt = null,
            bundleFormatVersion = 1,
            runtime = runtime,
            modelFileName = null,
            addedAt = 1,
        ),
    )

    private class RecordingEngine : ImageGenerationEngine {
        var generations = 0
        var cancelled = false
        var closed = false

        override fun generate(request: ImageGenerationRequest): Flow<ImageGenerationEvent> {
            generations++
            return emptyFlow()
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            closed = true
        }
    }
}
