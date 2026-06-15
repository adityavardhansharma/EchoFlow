package com.echoflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "chat_threads")
data class ChatThread(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class ChatMessage(
    @PrimaryKey val id: String,
    val chatId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val createdAt: Long,
    val reasoning: String? = null, // reasoning/"thinking" trace for reasoning-capable models
    val localAttachmentUri: String? = null,
    val localAttachmentMimeType: String? = null,
    val localAttachmentName: String? = null,
    val toolEventsJson: String? = null, // JSON List<ToolEvent>: web searches the model ran for this answer
    val citationsJson: String? = null, // JSON List<Citation>: deduped sources backing this answer
    val segmentsJson: String? = null // JSON List<PersistedSegment>: ordered reply timeline (reasoning/search/text interleaved)
)

@Entity(tableName = "custom_models")
data class CustomModel(
    @PrimaryKey val id: String, // e.g., "google/gemini-2.0-flash"
    val name: String // e.g., "Gemini 2.0 Flash"
)

@Entity(tableName = "local_models")
data class LocalModel(
    @PrimaryKey val id: String, // "local/<slug>" — also used as the selected_model id
    val name: String, // e.g., "Gemma 3 1B"
    val fileName: String, // file name inside filesDir/models/
    val sizeBytes: Long,
    val source: String, // "curated" | "imported"
    val addedAt: Long,
    val maxTokens: Int? = null // known context window, when catalog/search/import can infer it
)

/**
 * A cloud chat model the user has whitelisted to orchestrate agentic Deep Research.
 * Kept separate from [CustomModel] (normal chat models) because Deep Research has its own
 * "add model" flow and a much smaller, deliberately-curated capable-model list.
 */
@Entity(tableName = "deep_research_models")
data class DeepResearchModel(
    @PrimaryKey val id: String, // OpenRouter id, e.g. "google/gemini-2.5-pro"
    val name: String,
    val addedAt: Long
)

/**
 * One Echo Adviser profile: a named, domain-specific advisor (e.g. "Coding", "Maths") backed
 * by a single strong OpenRouter model. In chat, any cloud model answers and consults the
 * selected profile's [modelId] mid-generation via the `openrouter:advisor` server tool.
 */
@Entity(tableName = "advisor_profiles")
data class AdvisorProfile(
    @PrimaryKey val id: String, // UUID
    val name: String, // "Coding", "Maths", "Research", …
    val modelId: String, // the advisor OpenRouter model id
    val modelName: String, // human label for the advisor model
    val createdAt: Long,
)

/**
 * One Echo Fusion panel: a named set of OpenRouter models that answer in parallel, plus an
 * optional judge that compares them, via the `openrouter:fusion` server tool. Model ids and
 * their display names are stored newline-joined (ids never contain newlines).
 */
@Entity(tableName = "fusion_panels")
data class FusionPanel(
    @PrimaryKey val id: String, // UUID
    val name: String, // "Heavy hitters", "Fast trio", …
    val modelIds: String, // newline-joined OpenRouter ids (2–8)
    val modelNames: String, // newline-joined display names, parallel to modelIds
    val judgeModelId: String? = null, // judge model; null = use the first panel model
    val createdAt: Long,
) {
    val models: List<String> get() = modelIds.split("\n").filter { it.isNotBlank() }
    val names: List<String> get() = modelNames.split("\n").filter { it.isNotBlank() }
}

/**
 * The durable record of one Deep Research run. This is the single source of truth: the
 * foreground service writes progress here and the UI observes it, so a run survives the
 * Activity/ViewModel being destroyed and can be resumed after the app is force-killed
 * (provider-native runs by re-polling [providerJobId]; agentic runs from accumulated
 * sources).
 */
@Entity(
    tableName = "research_runs",
    indices = [Index("chatId"), Index("status")]
)
data class ResearchRun(
    @PrimaryKey val id: String,
    val chatId: String,
    val topic: String, // the user's research question
    val engineId: String, // "exa-deep-reasoning" | "exa-agent" | "parallel-ultra" | "firecrawl-agent-pro" | a chat model id
    val engineKind: String, // "provider" | "agent" | "data-agent"
    val engineLabel: String, // human label shown in the progress card
    val searchProvider: String? = null, // agent mode: which search provider backs the tool
    val level: String? = null, // Exa Agent effort, or the Firecrawl Data Agent model token
    val costInfo: String? = null, // live cost/credits meter text (e.g. "$0.12" or "84 credits")
    val maxSearches: Int = 5,
    val maxSources: Int = 20,
    val maxCredits: Int = 2500,
    val providerJobId: String? = null, // exa researchId / parallel run_id / firecrawl job id
    val status: String = STATUS_QUEUED, // see STATUS_* constants
    val phase: String? = null, // human progress line, e.g. "Searching 3 of 8"
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val planJson: String? = null, // JSON List<String>: planned sub-questions
    val sourcesJson: String? = null, // JSON List<SearchSource>: accumulated sources
    val report: String? = null, // final report markdown (or partial on cancel)
    val error: String? = null,
    val assistantMessageId: String? = null, // the chat_messages row holding the final report
    val createdAt: Long,
    val updatedAt: Long
) {
    val isTerminal: Boolean get() = status in TERMINAL_STATUSES

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_PLANNING = "planning"
        const val STATUS_RESEARCHING = "researching"
        const val STATUS_SYNTHESIZING = "synthesizing"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"

        val TERMINAL_STATUSES = setOf(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED)
    }
}
