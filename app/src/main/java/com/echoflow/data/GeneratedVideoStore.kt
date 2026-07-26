package com.echoflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Owns generated-video persistence. Unlike images (one synchronous stream, one file) a video
 * is an asynchronous job, so the [GeneratedVideo] row is created BEFORE anything is generated
 * and is updated through its whole life — queued → pending/in_progress → downloading →
 * completed. That makes the row the resume point after a process death.
 *
 * The MP4 itself lands under filesDir/generated_videos/ and only the path is ever stored.
 */
class GeneratedVideoStore(
    context: Context,
    private val dao: GeneratedVideoDao,
) {
    private val videosDir = File(context.filesDir, "generated_videos")

    /** Creates the durable job row for a turn that is about to be submitted. */
    suspend fun createJob(
        chatId: String,
        prompt: String,
        modelId: String,
        aspectRatio: String?,
        resolution: String?,
    ): GeneratedVideo {
        val now = System.currentTimeMillis()
        val video = GeneratedVideo(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            prompt = prompt,
            modelId = modelId,
            status = GeneratedVideo.STATUS_QUEUED,
            aspectRatio = aspectRatio,
            resolution = resolution,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(video)
        return video
    }

    /** Records the provider's job handle so a cold start can resume polling this run. */
    suspend fun markSubmitted(video: GeneratedVideo, jobId: String, pollingUrl: String): GeneratedVideo =
        update(video.copy(jobId = jobId, pollingUrl = pollingUrl, status = GeneratedVideo.STATUS_PENDING))

    suspend fun markStatus(video: GeneratedVideo, status: String): GeneratedVideo =
        if (video.status == status) video else update(video.copy(status = status))

    suspend fun markFailed(video: GeneratedVideo, status: String, error: String?): GeneratedVideo =
        update(video.copy(status = status, error = error))

    /**
     * Streams the finished MP4 to disk and completes the row. The download writes to a
     * temporary file and is renamed into place only once it is whole, so a connection dropped
     * mid-transfer can never leave a truncated clip that the row claims is playable.
     */
    suspend fun completeWithDownload(
        video: GeneratedVideo,
        body: InputStream,
    ): GeneratedVideo = withContext(Dispatchers.IO) {
        videosDir.mkdirs()
        val finalFile = File(videosDir, "${video.id}.mp4")
        val tempFile = File(videosDir, "${video.id}.mp4.tmp")
        try {
            tempFile.outputStream().use { out -> body.use { it.copyTo(out) } }
            if (tempFile.length() == 0L) throw Exception("The downloaded video was empty.")
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            finalFile.delete()
            throw e
        }
        update(
            video.copy(
                filePath = finalFile.absolutePath,
                status = GeneratedVideo.STATUS_COMPLETED,
                error = null,
            )
        )
    }

    /** The newest playable clip in this chat — what a follow-up turn continues from. */
    suspend fun latestForChat(chatId: String): GeneratedVideo? {
        val latest = dao.getLatestForChat(chatId) ?: return null
        return latest.takeIf { it.filePath != null && File(it.filePath).exists() }
    }

    suspend fun byId(id: String): GeneratedVideo? = dao.getById(id)

    /** Runs interrupted by a process kill, newest first. */
    suspend fun unfinished(): List<GeneratedVideo> = dao.getUnfinished().sortedByDescending { it.createdAt }

    /**
     * Removes this chat's video files from disk. Must run BEFORE the chat thread row is
     * deleted — the generated_videos rows cascade away with it, and MP4s are large enough
     * that orphaning them would visibly grow app storage.
     */
    suspend fun deleteFilesForChat(chatId: String) = withContext(Dispatchers.IO) {
        dao.getForChat(chatId).forEach { video ->
            video.filePath?.let { path -> runCatching { File(path).delete() } }
        }
    }

    private suspend fun update(video: GeneratedVideo): GeneratedVideo {
        val updated = video.copy(updatedAt = System.currentTimeMillis())
        dao.upsert(updated)
        return updated
    }
}
