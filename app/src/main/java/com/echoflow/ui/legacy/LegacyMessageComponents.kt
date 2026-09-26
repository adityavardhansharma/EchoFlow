package com.echoflow.ui.legacy

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.echoflow.data.ChatMessage
import com.echoflow.data.Citation
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.screens.chat.ReasoningSection
import com.echoflow.ui.theme.Spacing

/**
 * The parts of an assistant message that only old rows ever reach. `ChatMessageBubble` dispatches
 * here and nowhere else decides it; like the rest of `ui/legacy`, composition is frozen so old chats
 * keep looking the way they were written.
 */

/** Segment types that research wrote before the timeline redesign. New research writes `"research"`. */
internal val LEGACY_RESEARCH_SEGMENT_TYPES = setOf("plan", "report", "data")

/**
 * Pre-redesign research: a `"report"` or `"data"` segment, with the run's plan (its own `"plan"`
 * segment) folded into the report card as a disclosure. The `"plan"` segment draws nothing itself.
 */
@Composable
internal fun LegacyResearchSegment(
    segment: PersistedSegment,
    segments: List<PersistedSegment>,
    citations: List<Citation>,
    isLast: Boolean,
    onCopy: () -> Unit,
) {
    when (segment.type) {
        "report" -> {
            val planSteps = remember(segments) {
                segments.firstOrNull { it.type == "plan" }?.text
                    ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            }
            LegacyReportCard(
                report = segment.text.orEmpty(),
                citations = citations,
                planSteps = planSteps,
                onCopy = onCopy,
            )
        }
        "data" -> LegacyDataResultCard(
            json = segment.text.orEmpty(),
            citations = citations,
            onCopy = onCopy,
        )
        else -> return
    }
    if (!isLast) Spacer(Modifier.height(Spacing.s))
}

/**
 * Messages saved before the `segmentsJson` timeline column existed keep their reasoning and
 * searches in the flat `reasoning` / `toolEventsJson` columns, so their order is lost: all the
 * reasoning draws as one block, then every search, then (drawn by the caller) the answer.
 */
@Composable
internal fun LegacyFlatReplyPrelude(message: ChatMessage, messageKey: String) {
    val reasoningText = message.reasoning
    if (!reasoningText.isNullOrBlank()) {
        ReasoningSection(reasoning = reasoningText, active = false)
        Spacer(Modifier.height(Spacing.s))
    }
    val toolEvents = remember(messageKey, message.toolEventsJson) {
        ToolEventJson.toolEventsFromJson(message.toolEventsJson)
    }
    toolEvents.forEach { event ->
        SearchActivityCard(query = event.query, sources = event.sources, active = false)
        Spacer(Modifier.height(Spacing.s))
    }
}
