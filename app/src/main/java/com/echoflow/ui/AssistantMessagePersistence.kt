package com.echoflow.ui

import com.echoflow.data.*

internal data class AssistantMessageDraft(
    val content: String,
    val reasoning: String?,
    val toolEvents: List<ToolEvent>,
    val citations: List<Citation>,
    val segments: List<PersistedSegment>,
)

/** Pure conversion from transient streaming UI state to the persisted message representation. */
internal object AssistantMessagePersistence {
    fun draft(segments: List<StreamSegment>, interrupted: String?, stopped: Boolean): AssistantMessageDraft? {
        val normalized = ChatResponsePolicy.normalizeForPersistence(segments)
        val content = normalized.filterIsInstance<StreamSegment.Text>().joinToString("\n\n") { it.text }.trim()
        val hasDurableCard = normalized.any {
            it is StreamSegment.Advisor || it is StreamSegment.Fusion || it is StreamSegment.Subagent ||
                (it is StreamSegment.Artifact && it.artifactId != null)
        }
        if (content.isEmpty() && !hasDurableCard && !stopped) return null
        val reasoning = normalized.filterIsInstance<StreamSegment.Reasoning>()
            .joinToString("\n\n") { it.text }.trim().ifBlank { null }
        val events = normalized.mapIndexedNotNull { index, segment ->
            (segment as? StreamSegment.Search)?.let { ToolEvent(query = it.query, sources = it.sources, orderIndex = index) }
        }
        val citations = events.flatMap(ToolEvent::sources).distinctBy { it.url }
            .map { Citation(it.title, it.url) }
        val persisted = normalized.mapNotNull(::persistedSegment).toMutableList().apply {
            if (interrupted != null) add(PersistedSegment(type = "text", text = "*[Connection lost: $interrupted]*"))
            if (stopped) add(PersistedSegment(type = "stopped"))
        }
        val finalContent = interrupted?.let { "$content\n\n*[Connection lost: $it]*" } ?: content
        return AssistantMessageDraft(finalContent, reasoning, events, citations, persisted)
    }

    private fun persistedSegment(segment: StreamSegment): PersistedSegment? = when (segment) {
        is StreamSegment.Reasoning -> segment.text.trim().takeIf(String::isNotEmpty)?.let { PersistedSegment("reasoning", text = it) }
        is StreamSegment.Search -> PersistedSegment("search", query = segment.query, sources = segment.sources)
        is StreamSegment.Advisor -> PersistedSegment("advisor", advisor = AdvisorAdvice(segment.advisorName, segment.advisorModel, segment.prompt, segment.advice.orEmpty()))
        is StreamSegment.Fusion -> PersistedSegment(
            "fusion",
            fusion = (segment.analysis ?: FusionAnalysis(segment.panelName, null, segment.models))
                .copy(panelName = segment.panelName, models = segment.models.ifEmpty { segment.analysis?.models ?: emptyList() }),
        )
        is StreamSegment.Subagent -> PersistedSegment("subagent", subagent = SubagentResult(segment.taskName, segment.taskDescription, segment.workerModel, segment.outcome.orEmpty(), segment.error))
        is StreamSegment.Artifact -> segment.artifactId?.let { PersistedSegment("artifact", artifact = ArtifactRef(it, segment.title, segment.artifactType, segment.version)) }
        is StreamSegment.Text -> segment.text.trim().takeIf(String::isNotEmpty)?.let { PersistedSegment("text", text = it) }
        is StreamSegment.AgentRun -> null
    }
}
