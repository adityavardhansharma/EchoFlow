package com.echoflow.data.memory

import android.content.Context
import androidx.room.*
import androidx.work.*
import com.echoflow.data.AppDatabase
import com.echoflow.data.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log
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

data class MemoryLearningStatus(
    val queued: Int = 0,
    val processing: Int = 0,
    val ready: Int = 0,
    val unavailable: Int = 0,
) {
    val summary: String get() = buildList {
        if (queued > 0) add("$queued queued")
        if (processing > 0) add("$processing processing")
        if (ready > 0) add("$ready ready")
        if (unavailable > 0) add("$unavailable needs attention")
    }.joinToString(" · ").ifBlank { "No conversations queued yet" }
}

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
    @Query("UPDATE memory_sync SET status = 'ready' WHERE chatId = :chatId AND generation = :generation AND revision = :revision AND sentRevision = :revision AND documentId = :documentId")
    suspend fun readyRevision(chatId: String, generation: Long, revision: String, documentId: String)
    @Query("UPDATE memory_sync SET status = 'unavailable' WHERE chatId = :chatId AND generation = :generation AND revision = :revision AND documentId = :documentId")
    suspend fun unavailableRevision(chatId: String, generation: Long, revision: String, documentId: String)
    @Query("UPDATE memory_sync SET status = 'pending', sentRevision = '', documentId = '' WHERE generation = :generation AND status = 'unavailable'")
    suspend fun retryMissing(generation: Long)
    @Query("DELETE FROM memory_sync WHERE chatId = :chatId") suspend fun remove(chatId: String)
    @Query("DELETE FROM memory_sync WHERE generation < :generation")
    suspend fun removeObsolete(generation: Long)
    @Query("DELETE FROM memory_sync") suspend fun clear()
}

/** Stores only revision hashes, never a second copy of conversation content. */
object MemoryLearning {
    private val queueLock = Mutex()
    internal val workerLock = Mutex()
    /** Serialize with ledger inserts; a delayed cleanup must never delete a newer session. */
    suspend fun pruneObsolete(context: Context) {
        val generation = MemorySettings(context).generation
        queueLock.withLock {
            AppDatabase.getDatabase(context).memorySyncDao().removeObsolete(generation)
        }
    }
    /** Starts at an eligible user turn so a pre-consent prompt cannot leak through its later reply. */
    fun transcript(messages: List<ChatMessage>, since: Long): String = messages
        // Assistant prose is deliberately excluded: provider extraction previously turned claims
        // such as "I can retrieve your name" into user memories. User-authored evidence is safer.
        .filter { it.createdAt >= since && it.role == "user" }
        .takeLast(120).joinToString("\n\n") { "user: ${MemoryPrivacy.redact(it.content)}" }.takeLast(180_000)
    fun revision(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    suspend fun queue(context: Context, chatId: String, local: Boolean, session: MemorySession? = MemorySettings(context).session()) {
        val settings = MemorySettings(context)
        if (local) settings.recordLocalTurn(chatId)
        if (session == null || !settings.permits(session) || settings.excluded(chatId)) return
        settings.journal(chatId, session)
        schedule(context)
        recoverQueue(context, settings, session)
    }

    internal suspend fun recoverQueue(context: Context, settings: MemorySettings, session: MemorySession) = queueLock.withLock {
        val db = AppDatabase.getDatabase(context)
        for ((chatId, cutoff) in settings.pendingQueue()) {
            if (!settings.permits(session)) return@withLock
            db.withTransaction {
                if (db.chatDao().getThreadById(chatId) == null) return@withTransaction
                val messages = db.messageDao().getMessagesForChatSync(chatId).filter { it.createdAt <= cutoff }
                if (!settings.excluded(chatId) && messages.lastOrNull()?.role == "assistant" && messages.any { it.role == "user" && it.createdAt >= session.since }) {
                    val revision = revision(transcript(messages, session.since))
                    val old = db.memorySyncDao().entries(session.generation).find { it.chatId == chatId }
                    if (old?.revision != revision && settings.permits(session)) db.memorySyncDao().put(MemorySync(
                        chatId, session.generation, revision, old?.sentRevision.orEmpty(), old?.documentId.orEmpty(),
                        updatedAt = if (old != null && old.revision != old.sentRevision) old.updatedAt else cutoff,
                        includesLocal = settings.includesLocal(chatId) || old?.includesLocal == true))
                }
            }
            settings.acknowledgeQueue(chatId, cutoff, session)
        }
    }
    /** Persists both jobs before reporting success so scheduler failures remain observable. */
    suspend fun schedule(context: Context, force: Boolean = false) {
        // Also runs at startup while disconnected, recovering interrupted cleanup.
        pruneObsolete(context)
        if (MemorySettings(context).session() == null) return
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniquePeriodicWork("memory-maintenance", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MemorySyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()).await()
        manager.enqueueUniqueWork(if (force) "memory-retry" else "memory-learning", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MemorySyncWorker>()
                .setInputData(workDataOf("force" to force))
                .setInitialDelay(if (force) 0 else 2, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()).await()
    }
    suspend fun cancel(context: Context) {
        pruneObsolete(context)
        val manager = WorkManager.getInstance(context)
        listOf("memory-learning", "memory-maintenance", "memory-retry").forEach { manager.cancelUniqueWork(it).await() }
    }
    suspend fun retry(context: Context) {
        val settings = MemorySettings(context)
        val session = settings.session() ?: return
        AppDatabase.getDatabase(context).memorySyncDao().retryMissing(session.generation)
        if (!settings.permits(session)) return
        settings.learningBlocked = false
        settings.learningNote = ""
        schedule(context, force = true)
    }

    suspend fun status(context: Context): MemoryLearningStatus {
        val settings = MemorySettings(context)
        val entries = AppDatabase.getDatabase(context).memorySyncDao().entries(settings.generation)
        val queuedChats = entries.filter { it.status == "pending" && it.revision != it.sentRevision }.map { it.chatId }.toSet() +
            settings.pendingQueue().keys
        return MemoryLearningStatus(
            queued = queuedChats.size,
            processing = entries.count { it.status == "processing" },
            ready = entries.count { it.status == "ready" },
            unavailable = entries.count { it.status == "unavailable" },
        )
    }
    fun reportFailure(context: Context, failure: Exception, note: String = "Couldn't queue learning. Your chat is saved; retry learning in Memory settings.") {
        // Exception messages/causes may contain credentials, URLs or conversation text.
        val safe = Exception("Memory operation failed (${failure.javaClass.simpleName})")
        safe.stackTrace = failure.stackTrace
        Log.w("MemoryLearning", "Memory operation failed", safe)
        MemorySettings(context).learningNote = note
    }

    suspend fun pendingContext(context: Context, query: String, currentChat: String): String {
        val settings = MemorySettings(context)
        val session = settings.session() ?: return ""
        val accessRevision = settings.accessRevision
        val db = AppDatabase.getDatabase(context)
        val words = queryWords(query)
        if (words.isEmpty()) return ""
        val localMessages = db.messageDao().recentUserMessages(currentChat, session.since, 240)
            .filterNot { settings.excluded(it.chatId) }
            .filter { !settings.includesLocal(it.chatId) || settings.allowLocal }
        val anchors = localMessages.asSequence()
            .map { message ->
                val messageWords = queryWords(message.content)
                val semanticTypeBonus = MemoryPolicy.durableFacts(message.content).maxOfOrNull { fact ->
                    when {
                        "name" in words && fact.kind == "identity" -> 4
                        ("age" in words || "old" in words) && fact.kind == "demographic" -> 4
                        ("preference" in words || "favorite" in words) && fact.kind == "preference" -> 3
                        ("project" in words || "work" in words) && fact.kind == "project" -> 3
                        else -> 0
                    }
                } ?: 0
                message to (messageWords.intersect(words).size + semanticTypeBonus)
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<ChatMessage, Int>> { it.second }.thenByDescending { it.first.createdAt })
            .distinctBy { MemoryPolicy.normalize(it.first.content) }
            .take(3).map { it.first }.toList()
        val result = anchors.joinToString("\n\n") { anchor ->
            val sameChat = localMessages.filter { it.chatId == anchor.chatId }.sortedBy { it.createdAt }
            val index = sameChat.indexOfFirst { it.id == anchor.id }
            val window = sameChat.subList((index - 1).coerceAtLeast(0), (index + 2).coerceAtMost(sameChat.size))
                .distinctBy { MemoryPolicy.normalize(it.content) }
                .joinToString("\n") { "User: ${MemoryPrivacy.redact(it.content).take(700)}" }
            "Earlier conversation excerpt:\n$window"
        }
        return if (settings.permits(session) && settings.accessRevision == accessRevision) result else ""
    }

    private fun queryWords(text: String): Set<String> {
        val base = Regex("[\\p{L}\\p{N}]{3,}").findAll(text.lowercase()).map { it.value }.toMutableSet()
        base.removeAll(setOf("the", "and", "what", "about", "with", "memory", "remember", "user", "relevant", "facts", "previous", "conversations"))
        if (base.any { it in setOf("name", "called", "identity") }) base += setOf("name", "called")
        if (base.any { it in setOf("age", "old", "birth") }) base += setOf("age", "old", "birth")
        if (base.any { it in setOf("favorite", "favourite", "prefer", "preferences") }) base += setOf("favorite", "favourite", "prefer")
        return base
    }
}

class MemorySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = MemoryLearning.workerLock.withLock { deliver() }
    private suspend fun deliver(): Result {
        val settings = MemorySettings(applicationContext)
        val session = settings.session() ?: return Result.success()
        if (settings.learningBlocked) return Result.success()
        val generation = session.generation
        val client = SupermemoryClient(settings.key, settings.space)
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.memorySyncDao()
        try {
            MemoryLearning.recoverQueue(applicationContext, settings, session)
            val entries = dao.entries(generation)
            var missing = entries.any { it.status == "unavailable" }
            for (entry in entries.filter { it.status == "processing" && it.documentId.isNotBlank() }) {
                if (!settings.permits(session)) return Result.success()
                try {
                    val document = client.document(entry.documentId)
                    if (!settings.permits(session)) return Result.success()
                    if (document.optString("dreamingStatus") == "done") dao.readyRevision(entry.chatId, generation, entry.revision, entry.documentId)
                } catch (e: MemoryApiException) {
                    if (!settings.permits(session)) return Result.success()
                    if (e.code == 404) { dao.unavailableRevision(entry.chatId, generation, entry.revision, entry.documentId); missing = true } else throw e
                }
            }
            val pending = dao.entries(generation).filter { it.status != "unavailable" && it.revision != it.sentRevision && !settings.excluded(it.chatId) && (!(it.includesLocal || settings.includesLocal(it.chatId)) || settings.allowLocal) }
            val aged = pending.any { System.currentTimeMillis() - it.updatedAt >= TimeUnit.HOURS.toMillis(6) }
            val flush = pending.size >= MemorySettings.batchSize(settings.plan) || aged || inputData.getBoolean("force", false)
            for (entry in if (flush) pending else emptyList()) {
                if (!settings.permits(session)) return Result.success()
                if (settings.excluded(entry.chatId) || ((entry.includesLocal || settings.includesLocal(entry.chatId)) && !settings.allowLocal)) continue
                val messages = db.messageDao().getMessagesForChatSync(entry.chatId)
                if (messages.isEmpty()) { dao.remove(entry.chatId); continue }
                val text = MemoryLearning.transcript(messages, session.since)
                if (text.isBlank()) { dao.remove(entry.chatId); continue }
                if (messages.lastOrNull()?.role != "assistant" || MemoryLearning.revision(text) != entry.revision) continue
                if (!settings.permits(session)) return Result.success()
                if (settings.excluded(entry.chatId) || ((entry.includesLocal || settings.includesLocal(entry.chatId)) && !settings.allowLocal)) continue
                // Stable IDs make retrying an interrupted upload safe.
                val id = client.ingest(entry.chatId, text, Instant.ofEpochMilli(messages.last().createdAt).toString())
                if (!settings.permits(session)) return Result.success()
                dao.accepted(entry.chatId, generation, MemoryLearning.revision(text), id)
            }
            if (settings.permits(session)) settings.learningNote = if (missing) "A source is missing in Supermemory. Retry learning to upload it again, or leave it removed." else ""
            return Result.success() // Periodic maintenance polls extraction and flushes aged partial batches.
        } catch (e: CancellationException) { throw e }
        catch (e: MemoryApiException) {
            if (!settings.permits(session)) return Result.success()
            val retryable = e.code == 408 || e.code == 429 || e.code >= 500
            settings.learningBlocked = !retryable
            MemoryLearning.reportFailure(applicationContext, e, e.message.orEmpty())
            return if (retryable) Result.retry() else Result.failure()
        } catch (e: Exception) {
            if (!settings.permits(session)) return Result.success()
            MemoryLearning.reportFailure(applicationContext, e, "Learning is waiting for a connection. You can retry in Memory settings.")
            return Result.retry()
        }
    }
}
