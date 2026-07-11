package com.echoflow.ui

import com.echoflow.data.FusionAnalysis
import com.echoflow.data.BrowserSession
import com.echoflow.data.SearchSource

internal data class ActiveStreamState(
    val segments: List<StreamSegment> = emptyList(),
    val statusNote: String? = null,
    val progressLoading: Boolean = false,
    val isLocal: Boolean = false,
)

data class BrowserStartConflict(
    val activeSession: BrowserSession,
    val targetChatId: String?,
    val pendingPrompt: String,
)

/** One visual block of the in-progress assistant reply, rendered in arrival order. */
sealed class StreamSegment {
    data class Text(val text: String) : StreamSegment()
    data class Reasoning(val text: String) : StreamSegment()
    data class Search(
        val query: String,
        val sources: List<SearchSource>,
        val active: Boolean,
    ) : StreamSegment()

    /** Echo Adviser consultation: the question asked and the advice (null while pending). */
    data class Advisor(
        val advisorName: String,
        val advisorModel: String,
        val prompt: String,
        val advice: String?,
        val active: Boolean,
    ) : StreamSegment()

    /** Echo Fusion deliberation: the panel roster and the judge's analysis (null while pending). */
    data class Fusion(
        val panelName: String,
        val models: List<String>,
        val analysis: FusionAnalysis?,
        val active: Boolean,
    ) : StreamSegment()

    /** Transient banner shown while the non-streaming agent request runs. */
    object AgentRun : StreamSegment()

    /** Echo Agent delegation: a task handed to the worker and its outcome (null while running). */
    data class Subagent(
        val taskName: String,
        val taskDescription: String,
        val workerModel: String,
        val outcome: String?,
        val error: String?,
        val active: Boolean,
    ) : StreamSegment()

    /** A live or persisted artifact card in the assistant stream. */
    data class Artifact(
        val artifactId: String?,
        val title: String,
        val artifactType: String,
        val version: Int,
        val charCount: Int,
        val building: Boolean,
        val truncated: Boolean,
    ) : StreamSegment()
}
