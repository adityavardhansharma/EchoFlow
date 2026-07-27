package com.echoflow.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns video renders that outlive the turn that started them.
 *
 * A clip takes minutes, so a job routinely survives its own chat turn and sometimes the whole
 * process. OpenRouter keeps rendering regardless and the user has already paid, so abandoning
 * an interrupted job would quietly cost them money for nothing. This class picks those jobs
 * back up on launch, and — just as importantly — knows how to *stop* them when the
 * conversation they belong to is deleted.
 *
 * It lives outside the ViewModel because none of it is about the streaming chat turn: it is
 * durable-job bookkeeping that happens to end in a chat message.
 */
class VideoJobRecovery(
    private val context: Context,
    private val store: GeneratedVideoStore,
    private val engine: OpenRouterVideoGenerationEngine,
    private val settings: SettingsRepository,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val scope: CoroutineScope,
) {
    /** Guards against a second resume pass doubling up on a job already being polled. */
    private val resuming = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Running resume jobs, by chat. Concurrent rather than plain: entries are removed from
     * job-completion handlers, which are not guaranteed to run on the registering dispatcher.
     */
    private val jobsByChat = ConcurrentHashMap<String, MutableSet<Job>>()

    /**
     * Resumes every job left mid-render by a process death. A run killed before its assistant
     * message was written has no card in the conversation, so one is inserted here — pointing
     * at the job row, which the card then follows live.
     */
    suspend fun resumeInterrupted() {
        val unfinished = runCatching { store.unfinished() }.getOrNull().orEmpty()
        if (unfinished.isEmpty()) return
        val apiKey = settings.getApiKeyDirect()

        unfinished.forEach { video ->
            if (!resuming.add(video.id)) return@forEach

            if (apiKey.isBlank() || video.jobId == null) {
                runCatching {
                    store.markFailed(
                        video, GeneratedVideo.STATUS_FAILED,
                        "The video was interrupted and could not be resumed.",
                    )
                }
                resuming.remove(video.id)
                return@forEach
            }
            ensureCardExists(video)
            launchResume(video, apiKey)
        }
    }

    /**
     * Cancels and *joins* this chat's resume jobs so the caller can safely delete its rows and
     * files afterwards. Cancellation alone is not enough — a coroutine is only stopped once it
     * has actually unwound.
     */
    suspend fun cancelForChat(chatId: String) {
        jobsByChat.remove(chatId)?.forEach { it.cancelAndJoin() }
    }

    /**
     * LAZY, registered, then started. Launching eagerly leaves a window — however brief, and
     * however much the current dispatcher happens to close it — where the writer is running
     * but [cancelForChat] cannot see it, so deleting the conversation would race a job it has
     * no way to cancel or join.
     */
    private fun launchResume(video: GeneratedVideo, apiKey: String) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            KeepAliveService.acquire(context, "Finishing a video…")
            try {
                engine.resume(video, apiKey).collect { /* the row is the UI's source of truth */ }
                notifyIfReady(video.id)
            } catch (_: Exception) {
                // The row already carries the failure; the card renders it on next open.
            } finally {
                KeepAliveService.release(context)
                resuming.remove(video.id)
            }
        }
        jobsByChat.getOrPut(video.chatId) { newJobSet() }.add(job)
        job.invokeOnCompletion {
            jobsByChat[video.chatId]?.let { jobs ->
                jobs.remove(job)
                if (jobs.isEmpty()) jobsByChat.remove(video.chatId)
            }
        }
        job.start()
    }

    /**
     * Announces a finished clip only when one actually exists. The resume flow also ends
     * *normally* when its row was deleted mid-render, so keying the notification off "the flow
     * completed" would announce a video for a conversation the user just deleted — and tapping
     * it would open nothing.
     */
    private suspend fun notifyIfReady(videoId: String) {
        val finished = runCatching { store.byId(videoId) }.getOrNull() ?: return
        if (!finished.isPlayable) return
        ReplyNotifications.notifyReplyReady(
            context, finished.chatId,
            title = "Your video is ready",
            text = "Tap to watch it in EchoFlow.",
        )
    }

    /** Adds the card for a recovered job when the killed turn never got to persist one. */
    private suspend fun ensureCardExists(video: GeneratedVideo) {
        // The conversation can be deleted between reading the unfinished rows and getting
        // here; inserting into it would fail the foreign key and take the resume pass with it.
        if (chatDao.getThreadById(video.chatId) == null) return
        val alreadyShown = messageDao.getMessagesForChatSync(video.chatId).any { message ->
            ToolEventJson.segmentsFromJson(message.segmentsJson).any { it.video?.videoId == video.id }
        }
        if (alreadyShown) return
        messageDao.insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = video.chatId,
                role = "assistant",
                content = "",
                createdAt = System.currentTimeMillis(),
                segmentsJson = ToolEventJson.segmentsToJson(
                    listOf(PersistedSegment("video", video = VideoRef(video.id, video.filePath)))
                ),
            )
        )
    }

    private fun newJobSet(): MutableSet<Job> =
        Collections.newSetFromMap(ConcurrentHashMap<Job, Boolean>())
}
