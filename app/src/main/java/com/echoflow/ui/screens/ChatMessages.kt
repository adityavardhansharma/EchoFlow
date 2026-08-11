
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AgentProfile
import com.echoflow.data.ChatMessage
import com.echoflow.data.CustomProviderCapabilities
import com.echoflow.data.DataAgentCatalog
import com.echoflow.data.DeepResearchCatalog
import com.echoflow.data.DeepResearchModel
import com.echoflow.data.DrEngine
import com.echoflow.data.FusionPanel
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRef
import com.echoflow.data.ResearchRun
import com.echoflow.data.SearchSource
import com.echoflow.data.AppMode
import com.echoflow.data.ArtifactVersion
import com.echoflow.data.GeneratedVideo
import com.echoflow.data.ReplyVersions
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ArtifactCard
import com.echoflow.ui.components.CapabilityChip
import com.echoflow.ui.components.ModeSwitch
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.EffortPill
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.ResearchResultCard
import com.echoflow.ui.components.ResearchTimeline
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.legacy.LegacyArtifactCard
import com.echoflow.ui.legacy.LegacyDataResultCard
import com.echoflow.ui.legacy.LegacyReportCard
import com.echoflow.ui.legacy.LegacyResearchProgressCard
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.components.SubagentCard
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch



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
    statusNote: String?,
    progressLoading: Boolean,
    modelLoading: Boolean,
    researchRun: ResearchRun?,
    onCancelResearch: () -> Unit,
    topInset: Dp = Spacing.l,
    bottomInset: Dp = Spacing.l,
    onCopy: (String) -> Unit,
    onArtifactOpen: (Int) -> Unit = {},
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
        if (segments.isNotEmpty()) item(key = "streaming") {
            StreamingAssistantBubble(segments = segments, statusNote = statusNote, isStreaming = isStreaming, onArtifactOpen = onArtifactOpen, observeArtifactVersions = observeArtifactVersions)
        }
    }
}

/**
 * Shown while an on-device model is loaded into RAM (or a long chat is prefilled) —
 * this can take several seconds to a minute on first use, so it gets a richer
 * animation than the plain "Thinking…" row: breathing brand mark plus an
 * indeterminate expressive wavy progress line.
 */
@Composable
internal fun ModelLoadingRow(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(modifier = Modifier.padding(end = Spacing.m), size = 32.dp, animated = true)
            Column(Modifier.weight(1f)) {
                Text(
                    "Loading model…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Getting it into memory — first use takes the longest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.xl),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The live assistant reply, rendered as an ordered timeline: reasoning traces, web search
 * steps and text blocks appear in the order the model produced them — so when the model
 * searches, writes, then searches again, the user sees exactly that.
 */
@Composable
internal fun StreamingAssistantBubble(
    segments: List<StreamSegment>,
    statusNote: String?,
    isStreaming: Boolean,
    onArtifactOpen: (Int) -> Unit = {},
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp, animated = true)
            Spacer(Modifier.width(Spacing.s))
            Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Spacing.s))

        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            key(index) {
                when (segment) {
                    is StreamSegment.Reasoning -> {
                        ReasoningSection(reasoning = segment.text, active = isStreaming && isLast)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Search -> {
                        SearchActivityCard(query = segment.query, sources = segment.sources, active = segment.active)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Advisor -> {
                        AdvisorCard(
                            advisorName = segment.advisorName,
                            advisorModel = segment.advisorModel,
                            prompt = segment.prompt,
                            advice = segment.advice,
                            active = segment.active,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Fusion -> {
                        FusionCard(
                            panelName = segment.panelName,
                            models = segment.models,
                            analysis = segment.analysis,
                            active = segment.active,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.AgentRun -> {
                        AgentDeployingCard()
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Subagent -> {
                        SubagentCard(
                            taskName = segment.taskName,
                            taskDescription = segment.taskDescription,
                            workerModel = segment.workerModel,
                            outcome = segment.outcome,
                            error = segment.error,
                            active = segment.active,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Artifact -> {
                        ArtifactCard(
                            artifactId = segment.artifactId,
                            title = segment.title,
                            artifactType = segment.artifactType,
                            version = segment.version,
                            building = segment.building,
                            charCount = segment.charCount,
                            truncated = segment.truncated,
                            observeVersions = observeArtifactVersions,
                            onOpen = onArtifactOpen,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Image -> {
                        // One composable through generating → stretch → reveal → settled, so the
                        // dot field animates INTO the image instead of hard-swapping components.
                        com.echoflow.ui.components.GeneratedImageSegment(
                            filePath = segment.filePath,
                            pattern = segment.pattern,
                            previousImagePath = segment.previousImagePath,
                            animate = true,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Video -> {
                        com.echoflow.ui.components.GeneratedVideoSegment(
                            videoId = segment.videoId,
                            filePath = segment.filePath,
                            pattern = segment.pattern,
                            aspectRatio = segment.aspectRatio,
                            status = segment.status,
                            animate = true,
                            errorMessage = segment.error,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Text -> {
                        SmoothStreamingText(segment.text, Modifier.fillMaxWidth())
                        if (!isLast) Spacer(Modifier.height(Spacing.s))
                    }
                }
            }
        }

        statusNote?.let { note ->
            Spacer(Modifier.height(Spacing.s))
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The floating top bar: place on the left, **identity** in the middle, action on the right.
 *
 * The centre slot belongs to the most permanent thing on screen, which is which surface you
 * are on — so the mode switch lives here and the model selector moved down to the composer,
 * where it sits with the other controls you change mid-thought.
 */
@Composable
internal fun ChatTopBar(
    mode: AppMode,
    onSelectMode: (AppMode) -> Unit,
    renderingModes: Set<AppMode>,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newLabel = if (mode == AppMode.Imagine) "New creation" else "New conversation"
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            // Nudge inward from the screen edge — flush against the bezel reads cramped.
            Box(Modifier.padding(start = Spacing.s)) {
                ShapedIconButton(
                    onClick = onMenu, enabled = true, size = 44.dp,
                    restShape = MaterialShapes.Cookie4Sided, pressedShape = MaterialShapes.Cookie7Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                ) { Icon(Icons.Default.Menu, "Open conversations", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        },
        title = {
            ModeSwitch(
                selected = mode,
                onSelect = onSelectMode,
                renderingModes = renderingModes,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        },
        actions = {
            ShapedIconButton(
                onClick = onNewChat, enabled = true, size = 44.dp,
                restShape = MaterialShapes.Cookie7Sided, pressedShape = MaterialShapes.Sunny,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                pulseOnClick = true,
            ) { Icon(Icons.Default.Create, newLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.s),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(Spacing.m))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    onArtifactOpen: (Int) -> Unit = {},
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
            attachment = message.localAttachmentUri?.let { uri ->
                {
                    MessageAttachmentPreview(
                        uri = uri,
                        mimeType = message.localAttachmentMimeType,
                        name = message.localAttachmentName,
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
                }
            },
        )
    } else {
        val versionCount = ReplyVersions.count(message)
        val displayMessage = ReplyVersions.display(message, replyVersionIndex)
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
        }
    }
}

@Composable
private fun AssistantAnswerBody(
    message: ChatMessage,
    messageKey: String,
    streaming: Boolean,
    onArtifactOpen: (Int) -> Unit,
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
        val timelineText = ReplyVersions.textFromSegments(message.segmentsJson)
        onCopy(timelineText.ifBlank { message.content })
    }

    message.localAttachmentUri?.let { uri ->
        MessageAttachmentPreview(
            uri = uri,
            mimeType = message.localAttachmentMimeType,
            name = message.localAttachmentName,
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
                            FusionCard(
                                panelName = f.panelName,
                                models = f.models,
                                analysis = f,
                                active = false,
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
                                    onOpen = { onArtifactOpen(a.version) },
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

/**
 * Smooth "typewriter" reveal, the way production AI apps (T3 Chat, Vercel v0, ChatGPT) do it:
 * the network delivers text in bursts, but we reveal it at a steady, frame-synced cadence so it
 * reads pleasantly instead of flickering in chunks. The pace is a gentle base speed plus a
 * proportional catch-up, so it never lags far behind a fast model yet never feels rushed.
 */
@Composable
internal fun SmoothStreamingText(
    text: String,
    modifier: Modifier = Modifier,
    markdown: Boolean = true,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val target by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 0f else (frame - lastFrame) / 1_000_000_000f
            lastFrame = frame
            val t = target
            if (shown > t.length) shown = 0 // a new message started — restart the reveal
            if (shown < t.length) {
                val remaining = t.length - shown
                // Steady, pleasant cadence that scales with backlog so the whole answer finishes a
                // beat after the model (≈2s drain), never instant-dumping even at 1000+ tps.
                val charsPerSec = (remaining / 2f).coerceIn(40f, 900f)
                val add = (charsPerSec * dt).toInt().coerceAtLeast(1)
                shown = (shown + add).coerceAtMost(t.length)
            }
        }
    }
    val revealed = target.take(shown)
    if (markdown) {
        // Live markdown while streaming. Because the text is revealed gradually (not in bursts),
        // markdown spans complete one char at a time, so re-layout stays smooth like ChatGPT/Claude.
        MarkdownText(text = revealed, modifier = modifier, textColor = color, style = style)
    } else {
        SelectionContainer { Text(text = revealed, style = style, color = color, modifier = modifier) }
    }
}

/**
 * Collapsible reasoning ("thinking") trace, styled like T3 Chat / Claude. Subtly tinted so it reads
 * as meta-content. Auto-expands and streams while the model is reasoning, then auto-collapses once
 * the answer begins; the user can expand/collapse at any time (collapsed by default once complete).
 */
@Composable
internal fun ReasoningSection(reasoning: String, active: Boolean) {
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: active
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "reasoning-chevron")
    val toggleInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = toggleInteraction,
                indication = null,
                onClick = { userToggled = !expanded },
            ),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    if (active) "Reasoning…" else "Reasoning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Spacer(Modifier.width(Spacing.s))
                    LoadingIndicator(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                    Modifier.size(20.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                if (active) {
                    // Bounded, internally auto-scrolling panel so a fast reasoning stream can never
                    // overflow / "break out" of the container — it scrolls within a fixed height.
                    val sc = rememberScrollState()
                    LaunchedEffect(sc.maxValue) { sc.scrollTo(sc.maxValue) }
                    Box(
                        Modifier
                            .padding(top = Spacing.s)
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .verticalScroll(sc),
                    ) {
                        SmoothStreamingText(
                            reasoning, Modifier.fillMaxWidth(),
                            markdown = true,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(Modifier.padding(top = Spacing.s)) {
                        MarkdownText(
                            reasoning, Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThinkingRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(modifier = Modifier.padding(end = Spacing.m), size = 32.dp, animated = true)
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.s))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Red "Message stopped" line shown under a reply the user cancelled with the Stop button. */
@Composable
internal fun StoppedNotice(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Stop,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "Message stopped",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Haloed, morphing hero — a strong colored focal point.
        Box(contentAlignment = Alignment.Center) {
            val morph = rememberMorph(BrandShapes.heroStart, BrandShapes.heroEnd)
            val progress by rememberMorphProgress(3400)
            Box(
                Modifier.size(150.dp).clip(MorphPolygonShape(morph, progress)).background(MaterialTheme.colorScheme.primaryContainer),
            )
            BrandMark(size = 84.dp, animated = true, iconScale = 0.42f)
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            "How can I help\nyou today?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))

        data class Sug(val icon: ImageVector, val label: String, val prompt: String, val container: Color, val onContainer: Color)
        val cs = MaterialTheme.colorScheme
        val suggestions = listOf(
            Sug(Icons.Default.Lightbulb, "Explain", "Explain quantum computing in simple terms", cs.primaryContainer, cs.onPrimaryContainer),
            Sug(Icons.Default.Edit, "Write", "Write an email asking for a deadline extension", cs.secondaryContainer, cs.onSecondaryContainer),
            Sug(Icons.Default.Map, "Plan", "Plan a 3-day itinerary for Tokyo", cs.tertiaryContainer, cs.onTertiaryContainer),
            Sug(Icons.Default.Code, "Code", "Write a Python script to rename files in a folder", cs.surfaceContainerHigh, cs.onSurface),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            suggestions.forEach { s ->
                AssistPill(s.icon, s.label, s.container, s.onContainer) { onSuggestion(s.prompt) }
            }
        }
    }
}

@Composable
internal fun AssistPill(icon: ImageVector, label: String, container: Color, onContainer: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = container) {
        Row(
            Modifier.padding(start = 14.dp, end = 18.dp, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = onContainer)
            Spacer(Modifier.width(Spacing.s))
            Text(label, style = MaterialTheme.typography.labelLarge, color = onContainer)
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
    if (mimeType.equals("application/pdf", ignoreCase = true)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.widthIn(max = 280.dp),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PictureAsPdf,
                    null,
                    Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    name ?: "PDF file",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        AsyncImage(
            uri,
            null,
            modifier.size(200.dp).clip(MaterialTheme.shapes.large),
            contentScale = ContentScale.Crop,
        )
    }
}
