
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.echoflow.data.ArtifactVersion
import com.echoflow.data.ChatMessage
import com.echoflow.data.GeneratedVideo
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ReplyVersions
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRef
import com.echoflow.data.ResearchRun
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatResponsePolicy
import com.echoflow.ui.StreamRevealState
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.ResearchTimeline
import com.echoflow.ui.legacy.LegacyResearchProgressCard
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * The scrolling message list for one conversation. Owns its own [LazyListState] so each chat keeps
 * its scroll position and a switch (via the parent key()) doesn't inherit the previous chat's
 * offset. Streaming never programmatically changes this list's position; the user's viewport
 * remains stable until they choose to scroll.
 */
@Composable
internal fun MessagesPane(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    segments: List<StreamSegment>,
    activeMessageId: String? = null,
    revealState: StreamRevealState? = null,
    statusNote: String?,
    progressLoading: Boolean,
    modelLoading: Boolean,
    researchRun: ResearchRun?,
    onCancelResearch: () -> Unit,
    topInset: Dp = Spacing.l,
    bottomInset: Dp = Spacing.l,
    onCopy: (String) -> Unit,
    onArtifactOpen: (artifactId: String, version: Int) -> Unit = { _, _ -> },
    onResearchOpen: (ResearchRef) -> Unit = {},
    onResearchRetry: (ResearchRef) -> Unit = {},
    observeResearchRun: (String) -> Flow<ResearchRun?> = { flowOf(null) },
    observeVideo: (String) -> Flow<GeneratedVideo?> = { flowOf(null) },
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
    lastUserMessageId: String? = null,
    onEditUserMessage: (String) -> Unit = {},
    replyVersionIndexFor: (messageId: String, total: Int) -> Int = { _, total -> (total - 1).coerceAtLeast(0) },
    onReplyVersionChange: (messageId: String, index: Int) -> Unit = { _, _ -> },
    canEditMessages: Boolean = true,
) {
    val listState = rememberLazyListState()
    val viewport = remember { Any() }
    var initialPositioned by remember { mutableStateOf(false) }
    var initialRevealEligible by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(revealState, activeMessageId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            revealState?.beginViewport(viewport)
            try {
                // Register while waiting for the first chunk too, so a one-burst response gets
                // a reveal. A reply scrolled out of view must never hold up persistence.
                snapshotFlow {
                    activeMessageId != null &&
                        listState.layoutInfo.visibleItemsInfo.any { it.key == activeMessageId }
                }.collect { visible ->
                    if (visible) revealState?.attachViewport(viewport)
                    else revealState?.detachViewport(viewport)
                }
            } finally {
                revealState?.abandonViewport(viewport)
            }
        }
    }
    val atBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= layout.totalItemsCount - 1 &&
                last.offset + last.size <= layout.viewportEndOffset + 8
        }
    }
    // A fresh pane should open at the latest persisted turn, but this runs only once. It never
    // participates in streaming updates or the streaming-to-persisted handoff.
    LaunchedEffect(messages.isNotEmpty()) {
        if (!initialPositioned && messages.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            if (!initialPositioned) {
                val index = listState.layoutInfo.totalItemsCount - 1
                if (index >= 0) runCatching { listState.scrollToItem(index) }
                initialPositioned = true
            }
        }
    }
    // Preserve the user's choice while the request is waiting for its first visible segment. A
    // reveal is allowed only if they were still at the bottom when the stream began and remained
    // there; after the first segment, no automatic scrolling occurs at all.
    LaunchedEffect(isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        initialRevealEligible = atBottom
        snapshotFlow { listState.isScrollInProgress to atBottom }.collect { (scrolling, bottom) ->
            if (scrolling && !bottom) initialRevealEligible = false
        }
    }
    // Reveal the beginning of a new response once, but never follow its growing content. Keeping
    // this keyed only to stream start/content appearance prevents completion and persistence from
    // moving the user's viewport.
    LaunchedEffect(isStreaming, segments.isNotEmpty()) {
        if (isStreaming && segments.isNotEmpty() && initialRevealEligible) {
            withFrameNanos { }
            // The streaming row is appended after all persisted/research rows.
            val index = listState.layoutInfo.totalItemsCount - 1
            if (initialRevealEligible && index >= 0) {
                runCatching { listState.scrollToItem(index) }
                initialRevealEligible = false
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = topInset, bottom = bottomInset),
    ) {
        val timelineMessages = buildList {
            messages.forEach { add(ChatTimelineMessage(it.id, it)) }
            if (
                activeMessageId != null &&
                segments.isNotEmpty() &&
                messages.none { it.id == activeMessageId }
            ) {
                add(ChatTimelineMessage(activeMessageId, null))
            }
        }
        items(
            items = timelineMessages,
            key = { it.id },
            contentType = { it.message?.role ?: "assistant" },
        ) { row ->
            val message = row.message
            if (message?.role == "user") {
                MessageBubble(
                    message,
                    onCopy = onCopy,
                    canEditUserMessage = canEditMessages && message.id == lastUserMessageId,
                    onEditUserMessage = onEditUserMessage,
                )
            } else {
                val versionTotal = message?.let(ReplyVersions::count) ?: 1
                StableAssistantMessage(
                    messageId = row.id,
                    persistedMessage = message,
                    liveSegments = if (row.id == activeMessageId) segments else emptyList(),
                    statusNote = statusNote,
                    streamActive = isStreaming && row.id == activeMessageId,
                    revealState = revealState.takeIf { row.id == activeMessageId },
                    onCopy = onCopy,
                    onArtifactOpen = onArtifactOpen,
                    onResearchOpen = onResearchOpen,
                    onResearchRetry = onResearchRetry,
                    observeResearchRun = observeResearchRun,
                    observeVideo = observeVideo,
                    observeArtifactVersions = observeArtifactVersions,
                    replyVersionIndex = message?.let { replyVersionIndexFor(it.id, versionTotal) } ?: 0,
                    onReplyVersionChange = onReplyVersionChange,
                )
            }
        }
        researchRun?.let { run ->
            item(key = "research") {
                // A run that was already in flight when the app updated is stamped legacy and
                // finishes in the card it started in; everything new gets the step timeline.
                if (run.usesLegacyUi) {
                    LegacyResearchProgressCard(run = run, onCancel = onCancelResearch)
                } else {
                    val steps = remember(run.stepsJson) { ResearchJson.timelineFromJson(run.stepsJson) }
                    val runSources = remember(run.sourcesJson) { ResearchJson.sourcesFromJson(run.sourcesJson) }
                    ResearchTimeline(
                        run = run,
                        steps = steps,
                        sources = runSources,
                        onCancel = onCancelResearch,
                    )
                }
            }
        }
        // While research owns the timeline, don't also draw a chat Thinking / model-loading row —
        // that reads as a second reply starting under the research work.
        if (researchRun == null) {
            if (modelLoading && segments.isEmpty()) {
                item { ModelLoadingRow() }
            } else if (progressLoading && segments.isEmpty()) {
                item { ThinkingRow() }
            }
        }
    }
}

private data class ChatTimelineMessage(
    val id: String,
    val message: ChatMessage?,
)

/**
 * One assistant composition for the complete live-to-persisted lifecycle. The final live segment
 * tree is retained while this item remains mounted, so Markdown/LaTeX state, measured height and
 * the LazyColumn anchor survive the Room handoff unchanged.
 */
@Composable
private fun StableAssistantMessage(
    messageId: String,
    persistedMessage: ChatMessage?,
    liveSegments: List<StreamSegment>,
    statusNote: String?,
    streamActive: Boolean,
    revealState: StreamRevealState?,
    onCopy: (String) -> Unit,
    onArtifactOpen: (artifactId: String, version: Int) -> Unit,
    onResearchOpen: (ResearchRef) -> Unit,
    onResearchRetry: (ResearchRef) -> Unit,
    observeResearchRun: (String) -> Flow<ResearchRun?>,
    observeVideo: (String) -> Flow<GeneratedVideo?>,
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>>,
    replyVersionIndex: Int,
    onReplyVersionChange: (messageId: String, index: Int) -> Unit,
) {
    var retainedSegments by remember(messageId) { mutableStateOf<List<StreamSegment>>(emptyList()) }
    if (liveSegments.isNotEmpty()) {
        SideEffect {
            retainedSegments = liveSegments
        }
    }

    val displayedSegments = liveSegments.ifEmpty { retainedSegments }
    val versionCount = persistedMessage?.let(ReplyVersions::count) ?: 1
    val latestVersion = (versionCount - 1).coerceAtLeast(0)
    val keepMountedLiveTree = displayedSegments.isNotEmpty() &&
        (persistedMessage == null || replyVersionIndex == latestVersion)
    val selectedMessage = persistedMessage?.let { ReplyVersions.display(it, replyVersionIndex) }
    val persistedSegments = remember(selectedMessage?.segmentsJson) {
        ToolEventJson.segmentsFromJson(selectedMessage?.segmentsJson)
    }
    val settled = remember(displayedSegments, persistedSegments, streamActive) {
        if (!streamActive && selectedMessage != null) {
            settleStreamPresentation(displayedSegments, persistedSegments)
        } else {
            SettledStreamPresentation(displayedSegments)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (keepMountedLiveTree) {
            StreamingAssistantBubble(
                segments = settled.segments,
                statusNote = if (streamActive) statusNote else null,
                isStreaming = streamActive,
                revealState = revealState,
                onArtifactOpen = onArtifactOpen,
                observeVideo = observeVideo,
                observeArtifactVersions = observeArtifactVersions,
                terminalText = settled.terminalText,
                stopped = settled.stopped,
                onCopy = persistedMessage?.let { message ->
                    { onCopy(ReplyVersions.copyText(message, replyVersionIndex)) }
                }.takeIf { !streamActive },
            )
            if (!streamActive && persistedMessage != null) {
                AssistantAnswerActions(
                    message = persistedMessage,
                    replyVersionIndex = replyVersionIndex,
                    onReplyVersionChange = onReplyVersionChange,
                    onCopy = onCopy,
                )
            }
        } else if (persistedMessage != null) {
            MessageBubble(
                persistedMessage,
                onCopy = onCopy,
                onArtifactOpen = onArtifactOpen,
                onResearchOpen = onResearchOpen,
                onResearchRetry = onResearchRetry,
                observeResearchRun = observeResearchRun,
                observeVideo = observeVideo,
                observeArtifactVersions = observeArtifactVersions,
                replyVersionIndex = replyVersionIndex,
                onReplyVersionChange = onReplyVersionChange,
            )
        }
    }
}

private data class SettledStreamPresentation(
    val segments: List<StreamSegment>,
    val terminalText: List<String> = emptyList(),
    val stopped: Boolean = false,
)

/**
 * Reconciles the mounted streaming tree with the durable terminal snapshot. Text blocks keep
 * their existing nodes, while process cards receive their settled payloads and transient cards
 * that persistence deliberately rejected disappear. Persist-only notices are returned separately
 * so they can be appended without replacing the already-mounted Markdown/LaTeX composition.
 */
private fun settleStreamPresentation(
    liveSegments: List<StreamSegment>,
    persistedSegments: List<PersistedSegment>,
): SettledStreamPresentation {
    val remaining = persistedSegments.groupBy(PersistedSegment::type)
        .mapValues { (_, segments) -> ArrayDeque(segments) }
        .toMutableMap()
    fun take(type: String): PersistedSegment? = remaining[type]?.removeFirstOrNull()

    val normalized = ChatResponsePolicy.normalizeForPersistence(liveSegments)
    val settled = normalized.mapNotNull { segment ->
        when (segment) {
            is StreamSegment.Memory -> take("memory")?.let { durable ->
                segment.copy(label = durable.text.orEmpty().ifBlank { segment.label }, active = false)
            }
            is StreamSegment.Text -> take("text")?.let { segment }
            is StreamSegment.Reasoning -> take("reasoning")?.let { segment }
            is StreamSegment.Search -> take("search")?.let { durable ->
                segment.copy(
                    query = durable.query.orEmpty().ifBlank { segment.query },
                    sources = durable.sources.orEmpty(),
                    active = false,
                )
            }
            is StreamSegment.Advisor -> take("advisor")?.advisor?.let { durable ->
                segment.copy(
                    advisorName = durable.advisorName,
                    advisorModel = durable.advisorModel,
                    prompt = durable.prompt,
                    advice = durable.advice,
                    active = false,
                )
            }
            is StreamSegment.Fusion -> take("fusion")?.fusion?.let { durable ->
                segment.copy(
                    panelName = durable.panelName,
                    models = durable.models,
                    analysis = durable,
                    active = false,
                )
            }
            is StreamSegment.AgentRun -> null
            is StreamSegment.Subagent -> take("subagent")?.subagent?.let { durable ->
                segment.copy(
                    taskName = durable.taskName,
                    taskDescription = durable.taskDescription,
                    workerModel = durable.workerModel,
                    outcome = durable.outcome,
                    error = durable.error,
                    active = false,
                )
            }
            is StreamSegment.Artifact -> take("artifact")?.artifact?.let { durable ->
                segment.copy(
                    artifactId = durable.artifactId,
                    title = durable.title,
                    artifactType = durable.type,
                    version = durable.version,
                    building = false,
                )
            }
            is StreamSegment.Image -> take("image")?.image?.let { durable ->
                segment.copy(
                    imageId = durable.imageId,
                    filePath = durable.filePath,
                    generating = false,
                )
            }
            is StreamSegment.Video -> take("video")?.video?.let { durable ->
                segment.copy(filePath = durable.filePath ?: segment.filePath)
            }
        }
    }
    return SettledStreamPresentation(
        segments = settled,
        terminalText = remaining["text"].orEmpty().mapNotNull { it.text?.takeIf(String::isNotBlank) },
        stopped = persistedSegments.any { it.type == "stopped" },
    )
}
