package com.echoflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.ColumnInfo

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
 * One Echo Agent profile: a named orchestration setup whose **worker model** is the cheap,
 * fast model that the answering (orchestrator) model delegates self-contained subtasks to via
 * the `openrouter:subagent` server tool. The orchestrator itself is whichever cloud model is
 * selected in chat; this profile only pins the worker and how many tool-calling steps it may
 * take per delegation.
 */
@Entity(tableName = "agent_profiles")
data class AgentProfile(
    @PrimaryKey val id: String, // UUID
    val name: String, // "Fast worker", "Cheap drone", …
    val workerModelId: String, // the worker OpenRouter model id
    val workerModelName: String, // human label for the worker model
    val maxToolCalls: Int = 8, // worker tool-calling budget per delegation (1–25)
    val createdAt: Long,
)

/**
 * A cloud model the user has whitelisted for image generation. Kept separate from
 * [CustomModel] (chat models) because image output only works on the few directory models
 * whose `output_modalities` include "image", so it has its own add-model flow and search.
 */
@Entity(tableName = "image_models")
data class ImageModel(
    @PrimaryKey val id: String, // OpenRouter id, e.g. "google/gemini-2.5-flash-image"
    val name: String,
    val addedAt: Long,
)

/**
 * One installed on-device image model (a preconverted MediaPipe Image Generator bundle
 * extracted under filesDir/image_models/[directoryName]). A row exists only after the
 * bundle downloaded, verified, validated, passed its smoke test and atomically installed.
 */
@Entity(tableName = "local_image_models")
data class LocalImageModel(
    @PrimaryKey val id: String, // catalog id, "local-image/<slug>"
    val name: String,
    val directoryName: String, // directory under filesDir/image_models/
    val installedBytes: Long,
    val sourceRevision: String,
    val sourceCheckpointSha256: String,
    val bundleSha256: String,
    val licenseId: String,
    val activationPhrase: String?,
    val defaultNegativePrompt: String?,
    val bundleFormatVersion: Int,
    /** Runtime id persisted with the install so catalog changes cannot reroute it. */
    @ColumnInfo(defaultValue = "'mediapipe'") val runtime: String,
    /** File inside [directoryName] for single-file stable-diffusion.cpp installs. */
    val modelFileName: String?,
    val addedAt: Long,
)

/**
 * One generated image: the decoded PNG lives as a file under filesDir/generated_images/
 * (never as a DB blob); this row is the durable record. [parentId] links an edit to the
 * version it was produced from, so walking the chain yields the image's version history.
 */
@Entity(
    tableName = "generated_images",
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
data class GeneratedImage(
    @PrimaryKey val id: String, // UUID
    val chatId: String,
    val filePath: String, // absolute path of the PNG in app storage
    val prompt: String, // the user turn that produced this version
    val parentId: String? = null, // previous version in the edit chain (null = first)
    val createdAt: Long,
)

/**
 * A cloud model the user has whitelisted for video generation. Separate from [ImageModel]
 * because video runs on a different OpenRouter surface entirely (`/api/v1/videos`, an async
 * job API) and its directory carries per-model capability sets rather than chat modalities.
 */
@Entity(tableName = "video_models")
data class VideoModel(
    @PrimaryKey val id: String, // OpenRouter id, e.g. "google/veo-3.1"
    val name: String,
    val addedAt: Long,
)

/**
 * One video generation, from submitted job to downloaded MP4. Unlike images (a single
 * synchronous stream) OpenRouter video is asynchronous — submit, poll, download — and a
 * clip takes minutes, so this row is the durable job record as well as the result: the app
 * can be killed mid-generation and resume polling [pollingUrl] from here on next launch.
 *
 * The MP4 lives as a file under filesDir/generated_videos/; [filePath] is only set once the
 * download finished. Video bytes never enter Room or segmentsJson.
 */
@Entity(
    tableName = "generated_videos",
    foreignKeys = [
        ForeignKey(
            entity = ChatThread::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId"), Index("status")]
)
data class GeneratedVideo(
    @PrimaryKey val id: String, // UUID — our row id, not the provider's job id
    val chatId: String,
    val prompt: String, // the user turn that produced this clip
    val modelId: String, // the OpenRouter video model that ran
    val jobId: String? = null, // provider job id, set once the submit call returns
    val pollingUrl: String? = null, // absolute URL to poll; resumes a run after a cold start
    val status: String = STATUS_QUEUED,
    val filePath: String? = null, // absolute path of the MP4 once downloaded
    val aspectRatio: String? = null, // the ratio we asked for (duration is always the model's call)
    val resolution: String? = null,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isTerminal: Boolean get() = status in TERMINAL_STATUSES

    companion object {
        /** Our own pre-submit state; every other status is OpenRouter's own vocabulary. */
        const val STATUS_QUEUED = "queued"
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"

        /** Ours: the job completed and we are pulling the MP4 down. */
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_EXPIRED = "expired"

        val TERMINAL_STATUSES = setOf(STATUS_COMPLETED, STATUS_FAILED, STATUS_CANCELLED, STATUS_EXPIRED)
    }
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
    val localAttachmentUri: String? = null,
    val localAttachmentMimeType: String? = null,
    val localAttachmentName: String? = null,
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
