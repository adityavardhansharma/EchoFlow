
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.ArtifactVersion
import com.echoflow.data.ChatMessage
import com.echoflow.data.GeneratedVideo
import com.echoflow.data.ReplyVersions
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRef
import com.echoflow.data.ResearchRun
import com.echoflow.data.SearchSource
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.ArtifactCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.ResearchResultCard
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SubagentCard
import com.echoflow.ui.legacy.LegacyArtifactCard
import com.echoflow.ui.legacy.LegacyDataResultCard
import com.echoflow.ui.legacy.LegacyReportCard
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    onArtifactOpen: (artifactId: String, version: Int) -> Unit = { _, _ -> },
    onResearchOpen: (ResearchRef) -> Unit = {},
    onResearchRetry: (ResearchRef) -> Unit = {},
    observeResearchRun: (String) -> Flow<ResearchRun?> = { flowOf(null) },
    observeVideo: (String) -> Flow<GeneratedVideo?> = { flowOf(null) },
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
    onCopy: (String) -> Unit,
    canEditUserMessage: Boolean = false,
    onEditUserMessage: (String) -> Unit = {},
    replyVersionIndex: Int = 0,
    onReplyVersionChange: (String, Int) -> Unit = { _, _ -> },
) {
    val isUser = message.role == "user"
    if (isUser) {
        UserPromptBubble(
            content = message.content,
            canEdit = canEditUserMessage,
            onCopy = { onCopy(message.content) },
            onEdit = { onEditUserMessage(message.id) },
            modifier = modifier,
            attachment = message.attachments.takeIf { it.isNotEmpty() }?.let { list ->
                {
                    Column {
                        list.forEach { att ->
                            MessageAttachmentPreview(
                                uri = att.uri,
                                mimeType = att.mimeType,
                                name = att.name,
                                modifier = Modifier.padding(bottom = Spacing.s),
                            )
                        }
                    }
                }
            },
        )
    } else {
        // ChatGPT / Claude style: no bubble, full content width.
        Column(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 26.dp, animated = streaming)
                Spacer(Modifier.width(Spacing.s))
                Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.s))

            AnimatedAnswerVersion(versionIndex = replyVersionIndex) { versionIndex ->
                // AnimatedContent overlays multiple root children. Give every answer version one
                // root layout so its timeline is measured vertically and reports its true height.
                Column(Modifier.fillMaxWidth()) {
                    AssistantAnswerBody(
                        message = ReplyVersions.display(message, versionIndex),
                        messageKey = "${message.id}-$versionIndex",
                        streaming = streaming,
                        onArtifactOpen = onArtifactOpen,
                        onResearchOpen = onResearchOpen,
                        onResearchRetry = onResearchRetry,
                        observeResearchRun = observeResearchRun,
                        observeVideo = observeVideo,
                        observeArtifactVersions = observeArtifactVersions,
                        onCopy = onCopy,
                    )
                }
            }

            if (!streaming) {
                AssistantAnswerActions(
                    message = message,
                    replyVersionIndex = replyVersionIndex,
                    onReplyVersionChange = onReplyVersionChange,
                    onCopy = onCopy,
                )
            }
        }
    }
}

/** Completion controls shared by an ordinary persisted row and a just-finished live row. */
@Composable
internal fun AssistantAnswerActions(
    message: ChatMessage,
    replyVersionIndex: Int,
    onReplyVersionChange: (messageId: String, index: Int) -> Unit,
    onCopy: (String) -> Unit,
) {
    val versionCount = ReplyVersions.count(message)
    val displayMessage = ReplyVersions.display(message, replyVersionIndex)
    val segments = ToolEventJson.segmentsFromJson(displayMessage.segmentsJson)
    val lastGeneratedMediaIndex = segments.indexOfLast { segment ->
        (segment.type == "image" && segment.image != null) ||
            (segment.type == "video" && segment.video != null)
    }
    AnswerActionBar(
        versionIndex = replyVersionIndex,
        versionCount = versionCount,
        onPreviousVersion = {
            onReplyVersionChange(message.id, (replyVersionIndex - 1).coerceAtLeast(0))
        },
        onNextVersion = {
            onReplyVersionChange(
                message.id,
                (replyVersionIndex + 1).coerceAtMost(versionCount - 1),
            )
        },
        onCopy = { onCopy(ReplyVersions.copyText(message, replyVersionIndex)) },
        showCopy = lastGeneratedMediaIndex == -1,
    )
}

@Composable
private fun AssistantAnswerBody(
    message: ChatMessage,
    messageKey: String,
    streaming: Boolean,
    onArtifactOpen: (artifactId: String, version: Int) -> Unit,
    onResearchOpen: (ResearchRef) -> Unit = {},
    onResearchRetry: (ResearchRef) -> Unit = {},
    observeResearchRun: (String) -> Flow<ResearchRun?> = { flowOf(null) },
    observeVideo: (String) -> Flow<GeneratedVideo?> = { flowOf(null) },
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
    onCopy: (String) -> Unit,
) {
    // Finished replies render their persisted timeline in arrival order, so
    // reason → search → reason → search → answer keeps exactly the layout it
    // streamed with instead of merging all reasoning into one block.
    val persistedSegments = remember(messageKey, message.segmentsJson) {
        ToolEventJson.segmentsFromJson(message.segmentsJson)
    }
    // Generated media carries its own copy/save/share row, so the bubble's own copy
    // button is suppressed when a clip or image is the reply's last word.
    val lastGeneratedMediaIndex = persistedSegments.indexOfLast { segment ->
        (segment.type == "image" && segment.image != null) ||
            (segment.type == "video" && segment.video != null)
    }
    // [message] is already the selected snapshot. Reading its body directly keeps embedded
    // report/data copy actions aligned with the version currently on screen.
    val copyAction: () -> Unit = {
        onCopy(ReplyVersions.copyText(message))
    }

    message.attachments.forEach { att ->
        MessageAttachmentPreview(
            uri = att.uri,
            mimeType = att.mimeType,
            name = att.name,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
    }

    when {
        streaming -> {
            // Live markdown, revealed at a smooth steady cadence (decoupled from bursty chunks).
            if (message.content.isNotBlank()) SmoothStreamingText(message.content, Modifier.fillMaxWidth())
        }
        persistedSegments.isNotEmpty() -> {
            val planSteps = remember(messageKey) {
                persistedSegments.firstOrNull { it.type == "plan" }?.text
                    ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            }
            val reportCitations = remember(messageKey, message.citationsJson) {
                ToolEventJson.citationsFromJson(message.citationsJson)
            }
            persistedSegments.forEachIndexed { index, segment ->
                when (segment.type) {
                    "memory" -> MemoryActivityLine(segment.text.orEmpty(), false)
                    "reasoning" -> {
                        ReasoningSection(reasoning = segment.text.orEmpty(), active = false)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    "search" -> {
                        SearchActivityCard(query = segment.query.orEmpty(), sources = segment.sources.orEmpty(), active = false)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    "advisor" -> {
                        segment.advisor?.let { a ->
                            AdvisorCard(
                                advisorName = a.advisorName,
                                advisorModel = a.advisorModel,
                                prompt = a.prompt,
                                advice = a.advice,
                                active = false,
                            )
                            Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    "fusion" -> {
                        segment.fusion?.let { f ->
                            // History: process settled; default collapsed so only the final
                            // answer body reads as the reply. Expand for steps / panel answers.
                            FusionCard(
                                panelName = f.panelName,
                                models = f.models,
                                analysis = f,
                                active = false,
                                isStreaming = false,
                                answerStarted = true,
                            )
                            Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    "subagent" -> {
                        segment.subagent?.let { s ->
                            SubagentCard(
                                taskName = s.taskName,
                                taskDescription = s.taskDescription,
                                workerModel = s.workerModel,
                                outcome = s.outcome,
                                error = s.error,
                                active = false,
                            )
                            Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    // ── Artifacts ───────────────────────────────────────────────────
                    // Old and new artifact cards are told apart here, and only here.
                    // Pre-redesign rows omit ArtifactRef.uiVersion and deserialize to
                    // UI_VERSION_LEGACY, so they draw through the frozen ui/legacy card and
                    // look exactly as they always have. Artifacts produced by the current app
                    // stamp UI_VERSION_CURRENT and get the redesigned chips + preview card.
                    // Because the default is the legacy value, no existing conversation can
                    // ever be reclassified.
                    "artifact" -> {
                        segment.artifact?.let { a ->
                            if (a.usesLegacyUi) {
                                LegacyArtifactCard(
                                    title = a.title,
                                    artifactType = a.type,
                                    version = a.version,
                                    building = false,
                                    charCount = 0,
                                    truncated = false,
                                    onOpen = { onArtifactOpen(a.artifactId, a.version) },
                                )
                            } else {
                                ArtifactCard(
                                    artifactId = a.artifactId,
                                    title = a.title,
                                    artifactType = a.type,
                                    version = a.version,
                                    building = false,
                                    charCount = 0,
                                    truncated = false,
                                    observeVersions = observeArtifactVersions,
                                    onOpen = onArtifactOpen,
                                )
                            }
                            if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    "image" -> {
                        segment.image?.let { ref ->
                            com.echoflow.ui.components.GeneratedImageSegment(
                                filePath = ref.filePath,
                                pattern = "ripple",
                                previousImagePath = null,
                                animate = false,
                                onCopy = copyAction.takeIf { index == lastGeneratedMediaIndex },
                            )
                            if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    "video" -> {
                        segment.video?.let { ref ->
                            // The row, not the segment, is the truth: a clip can still be
                            // rendering when its message is written (or finish while the
                            // app is dead), so the card follows the job live.
                            val live by remember(ref.videoId) { observeVideo(ref.videoId) }
                                .collectAsState(initial = null)
                            com.echoflow.ui.components.GeneratedVideoSegment(
                                videoId = ref.videoId,
                                filePath = live?.filePath ?: ref.filePath,
                                pattern = "ripple",
                                aspectRatio = live?.aspectRatio
                                    ?: com.echoflow.data.VideoRequestPolicy.DEFAULT_ASPECT_RATIO,
                                status = live?.status ?: if (ref.filePath != null) {
                                    GeneratedVideo.STATUS_COMPLETED
                                } else {
                                    GeneratedVideo.STATUS_IN_PROGRESS
                                },
                                // A message written before the file existed means the clip
                                // lands in front of the user — that one gets the reveal.
                                animate = ref.filePath == null,
                                errorMessage = live?.error,
                                onCopy = copyAction.takeIf { index == lastGeneratedMediaIndex },
                            )
                            if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    "stopped" -> {
                        StoppedNotice()
                        if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                    }
                    // ── Research ────────────────────────────────────────────────────
                    // Old and new research are told apart here, and only here. "plan", "report"
                    // and "data" segments were written before the timeline redesign, so they draw
                    // through the frozen ui/legacy components and look exactly as they always
                    // have. Research produced by the current app writes a single "research"
                    // segment instead. Because the split is on a type string that old rows simply
                    // do not contain, no existing conversation can ever be reclassified.
                    // The plan is rendered as a disclosure inside the legacy report card.
                    "plan" -> Unit
                    "report" -> {
                        LegacyReportCard(
                            report = segment.text.orEmpty(),
                            citations = reportCitations,
                            planSteps = planSteps,
                            onCopy = copyAction,
                        )
                        if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                    }
                    "data" -> {
                        LegacyDataResultCard(
                            json = segment.text.orEmpty(),
                            citations = reportCitations,
                            onCopy = copyAction,
                        )
                        if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                    }
                    "research" -> {
                        segment.research?.let { ref ->
                            // The steps and sources come from the run row when it is still
                            // around; the card degrades to its header alone when it is not.
                            val liveRun by remember(ref.runId) { observeResearchRun(ref.runId) }
                                .collectAsState(initial = null)
                            val steps = remember(liveRun?.stepsJson) {
                                ResearchJson.timelineFromJson(liveRun?.stepsJson)
                            }
                            val runSources = remember(liveRun?.sourcesJson, reportCitations) {
                                ResearchJson.sourcesFromJson(liveRun?.sourcesJson).ifEmpty {
                                    reportCitations.map { SearchSource(title = it.title, url = it.url) }
                                }
                            }
                            ResearchResultCard(
                                research = ref,
                                steps = steps,
                                sources = runSources,
                                onOpen = { onResearchOpen(ref) },
                                onRetry = { onResearchRetry(ref) },
                            )
                            if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                        }
                    }
                    else -> {
                        RichMarkdown(segment.text.orEmpty(), Modifier.fillMaxWidth())
                        if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                    }
                }
            }
        }
        else -> {
            // Legacy messages saved before the timeline column existed.
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
            RichMarkdown(message.content, Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun MessageAttachmentPreview(
    uri: String,
    mimeType: String?,
    name: String?,
    modifier: Modifier = Modifier,
) {
    val isImage = mimeType?.startsWith("image/", ignoreCase = true) == true
    if (isImage) {
        AsyncImage(
            uri,
            null,
            modifier.size(200.dp).clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop,
        )
        return
    }
    val isPdf = mimeType.equals("application/pdf", ignoreCase = true)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.widthIn(max = 280.dp),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                null,
                Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(Spacing.s))
            Text(
                name ?: if (isPdf) "PDF file" else "Document",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
