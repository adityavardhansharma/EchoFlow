
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
import com.echoflow.data.ReplyVersions
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRef
import com.echoflow.data.ResearchRun
import com.echoflow.ui.StreamRevealState
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.ResearchTimeline
import com.echoflow.ui.legacy.LegacyResearchProgressCard
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf

/**
 * The scrolling message list for one conversation. Owns its own [LazyListState] so each chat keeps
 * its scroll position and a switch (via the parent key()) doesn't inherit the previous chat's
 * offset. Keeps the stick-to-bottom behaviour (respects manual scroll, follows streaming).
 */
@Composable
internal fun MessagesPane(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    segments: List<StreamSegment>,
    handoffMessageId: String? = null,
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
    var autoFollow by remember { mutableStateOf(true) }
    val viewport = remember { Any() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(revealState, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                // Register while waiting for the first chunk too, so a one-burst response gets
                // a reveal. A reply scrolled out of view must never hold up persistence.
                snapshotFlow {
                    autoFollow || listState.layoutInfo.visibleItemsInfo.any { it.key == "streaming" }
                }.collect { visible ->
                    if (visible) revealState?.attachViewport(viewport)
                    else revealState?.detachViewport(viewport)
                }
            } finally {
                revealState?.detachViewport(viewport)
            }
        }
    }
    val atBottom by remember {
        derivedStateOf {
            val li = listState.layoutInfo
            val last = li.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= li.totalItemsCount - 1 && (last.offset + last.size) <= li.viewportEndOffset + 8
        }
    }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) autoFollow = atBottom
    }
    LaunchedEffect(messages.size, progressLoading) {
        if (autoFollow) {
            val idx = listState.layoutInfo.totalItemsCount - 1
            if (idx >= 0) runCatching { listState.scrollToItem(idx, Int.MAX_VALUE) }
        }
    }
    LaunchedEffect(autoFollow, isStreaming, progressLoading) {
        if (autoFollow && (isStreaming || progressLoading)) {
            while (true) {
                withFrameNanos { it }
                if (!listState.isScrollInProgress) {
                    val idx = listState.layoutInfo.totalItemsCount - 1
                    if (idx >= 0) runCatching { listState.scrollToItem(idx, Int.MAX_VALUE) }
                }
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = topInset, bottom = bottomInset),
    ) {
        items(messages, key = { it.id }) { msg ->
            val versionTotal = if (msg.role == "assistant") ReplyVersions.count(msg) else 1
            MessageBubble(
                msg,
                onCopy = { text -> onCopy(text) },
                onArtifactOpen = onArtifactOpen,
                onResearchOpen = onResearchOpen,
                onResearchRetry = onResearchRetry,
                observeResearchRun = observeResearchRun,
                observeVideo = observeVideo,
                observeArtifactVersions = observeArtifactVersions,
                canEditUserMessage = canEditMessages && msg.role == "user" && msg.id == lastUserMessageId,
                onEditUserMessage = onEditUserMessage,
                replyVersionIndex = if (msg.role == "assistant") {
                    replyVersionIndexFor(msg.id, versionTotal)
                } else {
                    0
                },
                onReplyVersionChange = onReplyVersionChange,
            )
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
        val persistedHandoffVisible = handoffMessageId != null && messages.any { it.id == handoffMessageId }
        if (segments.isNotEmpty() && !persistedHandoffVisible) item(key = "streaming") {
            key(revealState) {
                StreamingAssistantBubble(
                    segments = segments,
                    statusNote = statusNote,
                    isStreaming = isStreaming,
                    revealState = revealState,
                    onArtifactOpen = onArtifactOpen,
                    observeArtifactVersions = observeArtifactVersions,
                )
            }
        }
    }
}
