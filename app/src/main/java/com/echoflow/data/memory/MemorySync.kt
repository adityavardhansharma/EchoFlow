package com.echoflow.data.memory

import android.content.Context
import androidx.room.*
import androidx.work.*
import com.echoflow.data.AppDatabase
import com.echoflow.data.ChatMessage
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

@Entity(tableName = "memory_sync", primaryKeys = ["chatId", "generation"])
data class MemorySync(
    val chatId: String,
    val generation: Long,
    val revision: String,
    val sentRevision: String = "",
    val documentId: String = "",
    val status: String = "pending",
    val updatedAt: Long = System.currentTimeMillis(),
    val includesLocal: Boolean = false,
)

@Dao
interface MemorySyncDao {
    @Query("SELECT * FROM memory_sync WHERE generation = :generation ORDER BY updatedAt ASC")
    suspend fun entries(generation: Long): List<MemorySync>
    @Upsert suspend fun put(entry: MemorySync)
    @Query("UPDATE memory_sync SET sentRevision = :revision, documentId = :documentId, status = 'processing' WHERE chatId = :chatId AND generation = :generation AND revision = :revision")
    suspend fun accepted(chatId: String, generation: Long, revision: String, documentId: String)
    @Query("UPDATE memory_sync SET status = 'ready' WHERE chatId = :chatId AND generation = :generation AND revision = sentRevision")
    suspend fun ready(chatId: String, generation: Long)
    @Query("UPDATE memory_sync SET status = 'unavailable' WHERE chatId = :chatId AND generation = :generation")
    suspend fun unavailable(chatId: String, generation: Long)
    @Query("DELETE FROM memory_sync WHERE chatId = :chatId") suspend fun remove(chatId: String)
    @Query("DELETE FROM memory_sync") suspend fun clear()
}

/** Stores only revision hashes, never a second copy of conversation content. */
object MemoryLearning {
    fun transcript(messages: List<ChatMessage>, since: Long): String = MemoryPrivacy.redact(messages
        .filter { it.createdAt >= since && it.role in listOf("user", "assistant") }
        .takeLast(120).joinToString("\n\n") { "${it.role}: ${it.content}" }.takeLast(180_000))
    fun revision(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun queue(context: Context, chatId: String, local: Boolean, session: MemorySession? = MemorySettings(context).session()) {
        val settings = MemorySettings(context)
        if (local) settings.recordLocalTurn(chatId)
        if (session == null || !settings.permits(session) || settings.excluded(chatId)) return
        val db = AppDatabase.getDatabase(context)
        val messages = db.messageDao().getMessagesForChatSync(chatId)
        if (messages.none { it.role == "assistant" && it.createdAt >= session.since }) return
        val text = transcript(messages, session.since)
        val old = db.memorySyncDao().entries(session.generation).find { it.chatId == chatId }
        val revision = revision(text)
        if (old?.revision == revision) return
        if (!settings.permits(session)) return
        db.memorySyncDao().put(MemorySync(chatId, session.generation, revision,
            old?.sentRevision.orEmpty(), old?.documentId.orEmpty(), includesLocal = settings.includesLocal(chatId) || old?.includesLocal == true))
        schedule(context)
    }
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("memory-learning", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MemorySyncWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
    }

    suspend fun pendingContext(context: Context, query: String, currentChat: String): String {
        val settings = MemorySettings(context)
        if (!settings.learn) return ""
        val db = AppDatabase.getDatabase(context)
        val words = Regex("[\\p{L}\\p{N}]{3,}").findAll(query.lowercase()).map { it.value }.toSet()
            .minus(setOf("the", "and", "what", "about", "with", "memory", "remember"))
        if (words.isEmpty()) return ""
        return db.memorySyncDao().entries(settings.generation).filter { it.chatId != currentChat && it.status != "ready" && !settings.excluded(it.chatId) && (!it.includesLocal || settings.allowLocal) }
            .takeLast(15).flatMap { entry ->
                db.messageDao().getMessagesForChatSync(entry.chatId)
                    .filter { it.createdAt >= settings.since && it.role in listOf("user", "assistant") }
                    .map { message -> message to words.count { message.content.contains(it, ignoreCase = true) } }
            }.filter { it.second > 0 }.sortedByDescending { it.second }.take(4)
            .joinToString("\n") { "Recent unsynced ${it.first.role}: ${MemoryPrivacy.redact(it.first.content).take(900)}" }
    }
}

class MemorySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = MemorySettings(applicationContext)
        if (!settings.connected || !settings.learn) return Result.success()
        val generation = settings.generation
        val client = SupermemoryClient(settings.key, settings.space)
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.memorySyncDao()
        try {
            val entries = dao.entries(generation)
            var stillProcessing = false
            for (entry in entries.filter { it.status == "processing" && it.documentId.isNotBlank() }) {
                if (settings.generation != generation || !settings.learn) return Result.success()
                try {
                    if (client.document(entry.documentId).optString("dreamingStatus") == "done") dao.ready(entry.chatId, generation)
                    else stillProcessing = true
                } catch (e: MemoryApiException) { if (e.code == 404) dao.unavailable(entry.chatId, generation) else throw e }
            }
            val pending = entries.filter { it.revision != it.sentRevision && !settings.excluded(it.chatId) && (!it.includesLocal || settings.allowLocal) }
            if (pending.size < MemorySettings.batchSize(settings.plan)) {
                return if (stillProcessing && runAttemptCount < 8) Result.retry() else Result.success()
            }
            for (entry in pending) {
                if (!settings.learn || settings.generation != generation || !settings.connected) return Result.success()
                if (settings.excluded(entry.chatId) || (entry.includesLocal && !settings.allowLocal)) continue
                val messages = db.messageDao().getMessagesForChatSync(entry.chatId)
                if (messages.isEmpty()) { dao.remove(entry.chatId); continue }
                val text = MemoryLearning.transcript(messages, settings.since)
                if (text.isBlank()) { dao.remove(entry.chatId); continue }
                // Stable IDs make retrying an interrupted upload safe.
                val id = client.ingest(entry.chatId, text, Instant.ofEpochMilli(messages.last().createdAt).toString())
                if (settings.generation != generation) return Result.success()
                dao.accepted(entry.chatId, generation, MemoryLearning.revision(text), id)
            }
            settings.learningNote = ""
            return Result.retry() // Poll extraction separately; an accepted document isn't a ready memory.
        } catch (e: CancellationException) { throw e }
        catch (e: MemoryApiException) {
            settings.learningNote = e.message.orEmpty()
            return if (e.code == 429 || e.code >= 500) Result.retry() else Result.failure()
        } catch (_: Exception) { settings.learningNote = "Learning is waiting for a connection."; return Result.retry() }
    }
}
