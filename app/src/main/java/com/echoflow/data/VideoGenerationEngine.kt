package com.echoflow.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * One video-generation turn. Duration is absent by design — the model decides how long the
 * clip runs; EchoFlow only expresses framing and (where supported) audio.
 */
data class VideoGenerationRequest(
    val chatId: String,
    val prompt: String,
    val modelId: String,
    val apiKey: String,
    val aspectRatio: String = VideoRequestPolicy.DEFAULT_ASPECT_RATIO,
    val resolution: String = VideoRequestPolicy.DEFAULT_RESOLUTION,
    val generateAudio: Boolean = false,
    /** A data URL to animate as the first frame; null makes this a text-to-video turn. */
    val startImageDataUrl: String? = null,
)

/** What the engine emits while a generation runs. Failures surface as flow exceptions. */
sealed interface VideoGenerationEvent {
    /** The durable job row exists (id is stable from here on, before the provider is called). */
    data class Queued(val video: GeneratedVideo) : VideoGenerationEvent

    /** The provider accepted the job, or reported a new non-terminal status while waiting. */
    data class Progress(val video: GeneratedVideo, val elapsedMs: Long) : VideoGenerationEvent

    /** The clip finished and the MP4 is on disk. */
    data class VideoFile(val video: GeneratedVideo) : VideoGenerationEvent
}

/**
 * Runs OpenRouter's asynchronous video pipeline — validate → submit → poll → download —
 * writing every state change through [GeneratedVideoStore] as it goes. Because the row is
 * updated before each await, a run killed at any point is resumable by [resume] with nothing
 * but its database record.
 */
class OpenRouterVideoGenerationEngine(
    private val service: OpenRouterVideoService,
    private val directory: OpenRouterVideoModelDirectory,
    private val store: GeneratedVideoStore,
) {

    fun generate(request: VideoGenerationRequest): Flow<VideoGenerationEvent> = flow {
        // Capabilities are per-model and an unsupported value is a hard 400, so reconcile the
        // user's framing preferences first. A directory outage is not fatal: the preferences
        // are simply dropped and the provider's own defaults apply.
        val capabilities = runCatching { directory.capabilities(request.modelId) }.getOrNull()
        val aspectRatio = VideoRequestPolicy.resolveAspectRatio(
            request.aspectRatio, capabilities?.aspectRatios.orEmpty()
        )
        val resolution = VideoRequestPolicy.resolveResolution(
            request.resolution, capabilities?.resolutions.orEmpty()
        )
        val startImage = request.startImageDataUrl?.takeIf { capabilities?.supportsFirstFrame != false }

        var video = store.createJob(
            chatId = request.chatId,
            prompt = request.prompt,
            modelId = request.modelId,
            aspectRatio = aspectRatio ?: request.aspectRatio,
            resolution = resolution,
        )
        emit(VideoGenerationEvent.Queued(video))

        val handle = try {
            service.submit(
                apiKey = request.apiKey,
                model = request.modelId,
                prompt = request.prompt,
                aspectRatio = aspectRatio,
                resolution = resolution,
                generateAudio = request.generateAudio.takeIf { capabilities?.supportsAudio == true },
                frameImageDataUrl = startImage,
            )
        } catch (e: Exception) {
            // Emit the failed row before rethrowing: the card is already on screen showing a
            // dot field, and an error banner alone would leave it dancing forever.
            emit(VideoGenerationEvent.Progress(store.markFailed(video, GeneratedVideo.STATUS_FAILED, e.message), 0L))
            throw e
        }
        video = store.markSubmitted(video, handle.id, handle.pollingUrl)
        emit(VideoGenerationEvent.Progress(video, 0L))

        awaitAndDownload(video, request.apiKey, startedAt = System.currentTimeMillis())
    }

    /**
     * Picks a job back up from its stored handle — used on a cold start, after the process was
     * killed mid-render. A job with no provider handle never reached OpenRouter and is failed
     * outright rather than left waiting forever.
     */
    fun resume(video: GeneratedVideo, apiKey: String): Flow<VideoGenerationEvent> = flow {
        if (video.jobId == null || video.pollingUrl == null) {
            store.markFailed(video, GeneratedVideo.STATUS_FAILED, "The generation was interrupted before it started.")
            return@flow
        }
        emit(VideoGenerationEvent.Progress(video, 0L))
        awaitAndDownload(video, apiKey, startedAt = video.createdAt)
    }

    /**
     * Polls until the job reaches a terminal state, then streams the MP4 to disk. The interval
     * ramps from [FIRST_POLL_MS] to [MAX_POLL_MS]: clips rarely land in under half a minute,
     * so a tight loop would only burn battery and rate limit for no earlier result.
     */
    private suspend fun FlowCollector<VideoGenerationEvent>.awaitAndDownload(
        initial: GeneratedVideo,
        apiKey: String,
        startedAt: Long,
    ) {
        var video = initial
        var interval = FIRST_POLL_MS
        while (true) {
            if (System.currentTimeMillis() - startedAt > TIMEOUT_MS) {
                val failed = store.markFailed(
                    video, GeneratedVideo.STATUS_FAILED,
                    "The video took longer than ${TIMEOUT_MS / 60_000} minutes and was given up on.",
                )
                emit(VideoGenerationEvent.Progress(failed, System.currentTimeMillis() - startedAt))
                throw VideoGenerationException.GenerationFailed(failed.error.orEmpty())
            }
            delay(interval)
            interval = (interval * 2).coerceAtMost(MAX_POLL_MS)

            val state = service.poll(apiKey, video.pollingUrl!!)
            if (state.status != video.status) {
                video = store.markStatus(video, state.status)
                emit(VideoGenerationEvent.Progress(video, System.currentTimeMillis() - startedAt))
            }
            if (!state.isTerminal) continue

            if (!state.succeeded) {
                // Re-emit after markFailed, not before: the status change above carries no
                // reason yet, and the card renders the provider's message verbatim.
                val failed = store.markFailed(
                    video, state.status, state.error ?: "The video generation ${state.status}.",
                )
                emit(VideoGenerationEvent.Progress(failed, System.currentTimeMillis() - startedAt))
                throw VideoGenerationException.GenerationFailed(failed.error.orEmpty())
            }

            video = store.markStatus(video, GeneratedVideo.STATUS_DOWNLOADING)
            emit(VideoGenerationEvent.Progress(video, System.currentTimeMillis() - startedAt))
            val downloaded = try {
                service.withVideoContent(apiKey, video.jobId!!) { stream ->
                    store.completeWithDownload(video, stream)
                }
            } catch (e: Exception) {
                val failed = store.markFailed(
                    video, GeneratedVideo.STATUS_FAILED, e.message ?: "Could not download the video.",
                )
                emit(VideoGenerationEvent.Progress(failed, System.currentTimeMillis() - startedAt))
                throw e
            }
            emit(VideoGenerationEvent.VideoFile(downloaded))
            return
        }
    }

    private companion object {
        const val FIRST_POLL_MS = 5_000L
        const val MAX_POLL_MS = 20_000L

        /** Generous, but bounded: a job still running after this is almost certainly stuck. */
        const val TIMEOUT_MS = 20 * 60_000L
    }
}
