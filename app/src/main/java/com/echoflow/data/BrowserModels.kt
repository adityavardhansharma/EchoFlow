package com.echoflow.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

/**
 * A durable, stateful Browser Flow session: one live Firecrawl browser (`scrapeId`) controlled
 * through chat over many turns. This is the single source of truth — the chat card, the global
 * pill and the fullscreen workspace all observe this row, so the session survives the workspace
 * being closed, the app being minimized, or a process kill (resolved conservatively on relaunch).
 *
 * Routing rule: while a session is non-[isTerminal] it **owns** its [chatId] — every message in
 * that chat drives `/interact` on this [scrapeId]. Only one non-terminal session may exist
 * app-wide at a time (the start-a-session lock); other chats stay normal.
 */
@Entity(
    tableName = "browser_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("chatId"), Index("status")],
)
data class BrowserSession(
    @PrimaryKey val id: String,
    val chatId: String,
    val goal: String, // the first instruction that started the session
    val resolvedUrl: String? = null,
    val scrapeId: String? = null,
    val liveViewUrl: String? = null, // read-only browser stream
    val interactiveLiveViewUrl: String? = null, // user-controllable stream (the WebView source)
    val status: String = STATUS_RESOLVING, // see STATUS_* constants
    val phase: String? = null, // human progress line, e.g. "Running instruction…"
    val pendingKind: String? = null, // when awaiting_user: what we're waiting for (PENDING_*)
    val pendingInstruction: String? = null, // instruction held across a disambiguation/confirm
    val pendingDraft: String? = null, // composed message awaiting the user's send-confirmation
    val candidatesJson: String? = null, // JSON List<BrowserCandidate> for disambiguation
    val lastOutput: String? = null, // latest agent output (for the card summary)
    val error: String? = null,
    val openedAt: Long? = null, // when the Firecrawl browser actually opened (for cost/elapsed)
    val createdAt: Long,
    val updatedAt: Long,
    val lastActivityAt: Long, // drives the idle auto-finish timer
) {
    val isTerminal: Boolean get() = status in TERMINAL_STATUSES
    val isActive: Boolean get() = !isTerminal
    val hasLiveBrowser: Boolean get() = !scrapeId.isNullOrBlank()
    val candidates: List<BrowserCandidate> get() = BrowserJson.candidatesFromJson(candidatesJson)

    companion object {
        const val STATUS_RESOLVING = "resolving"
        const val STATUS_STARTING = "starting"
        const val STATUS_RUNNING = "running"
        const val STATUS_AWAITING_USER = "awaiting_user"
        const val STATUS_AWAITING_INSTRUCTION = "awaiting_instruction"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_STOPPED = "stopped"
        const val STATUS_EXPIRED = "expired"

        // What an awaiting_user pause is about.
        const val PENDING_DISAMBIGUATION = "disambiguation" // pick a site (chips + Other)
        const val PENDING_CONFIRM_DOMAIN = "confirm_domain" // sensitive site — confirm before opening
        const val PENDING_HANDOFF = "handoff"               // login/CAPTCHA/payment — drive it yourself
        const val PENDING_DRAFT_CONFIRM = "draft_confirm"   // confirm an outgoing message before send

        val TERMINAL_STATUSES = setOf(STATUS_COMPLETED, STATUS_FAILED, STATUS_STOPPED, STATUS_EXPIRED)

        // Firecrawl billing for prompt-driven interact (shown to the user for trust).
        const val CREDITS_PER_MINUTE = 7
    }
}

/**
 * One entry in a session's timeline (the activity log shown in the card / workspace drawer).
 * Roles: "user" (a command/answer), "agent" (an output) or "system" (a status/handoff note).
 * Meaningful agent results are *also* mirrored into the chat thread as assistant messages; the
 * timeline keeps the full mechanical record.
 */
@Entity(
    tableName = "browser_steps",
    foreignKeys = [
        ForeignKey(
            entity = BrowserSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class BrowserStep(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" | "agent" | "system"
    val text: String,
    val createdAt: Long,
)

/** A candidate website surfaced during resolution (for the disambiguation chips). */
data class BrowserCandidate(
    val title: String,
    val url: String,
    val domain: String,
)

/** Moshi (de)serialization for the small embedded lists, mirroring [ResearchJson]. */
object BrowserJson {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val candidateListType = Types.newParameterizedType(List::class.java, BrowserCandidate::class.java)
    private val candidateAdapter = moshi.adapter<List<BrowserCandidate>>(candidateListType)

    fun candidatesToJson(list: List<BrowserCandidate>): String = candidateAdapter.toJson(list)

    fun candidatesFromJson(json: String?): List<BrowserCandidate> =
        if (json.isNullOrBlank()) emptyList()
        else runCatching { candidateAdapter.fromJson(json) }.getOrNull().orEmpty()
}

@Dao
interface BrowserSessionDao {
    /** The live (non-terminal) session for one chat, if any — drives the in-chat card. */
    @Query(
        "SELECT * FROM browser_sessions WHERE chatId = :chatId " +
            "AND status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeActiveForChat(chatId: String): Flow<BrowserSession?>

    /** The single app-wide live session (start-a-session lock + global pill). */
    @Query(
        "SELECT * FROM browser_sessions WHERE status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeAnyActive(): Flow<BrowserSession?>

    @Query("SELECT * FROM browser_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BrowserSession?

    @Query(
        "SELECT * FROM browser_sessions WHERE chatId = :chatId " +
            "AND status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getActiveForChat(chatId: String): BrowserSession?

    @Query("SELECT * FROM browser_sessions WHERE status NOT IN ('completed','failed','stopped','expired')")
    suspend fun getAllActive(): List<BrowserSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: BrowserSession)

    @Query("DELETE FROM browser_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BrowserStepDao {
    @Query("SELECT * FROM browser_steps WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<BrowserStep>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: BrowserStep)
}
