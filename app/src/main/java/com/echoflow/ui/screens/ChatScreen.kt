@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.echoflow.data.ChatMessage
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import kotlinx.coroutines.launch

/** Single built-in model. Everything else the user adds in Settings. */
private val DEFAULT_MODEL = "google/gemini-2.0-flash" to "Gemini 2.0 Flash"

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onMenuClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    val messages by chatViewModel.currentMessages.collectAsState()
    val isStreaming by chatViewModel.isStreaming.collectAsState()
    val activeSegments by chatViewModel.activeSegments.collectAsState()
    val statusNote by chatViewModel.statusNote.collectAsState()
    val progressLoading by chatViewModel.apiProgressLoading.collectAsState()
    val localModelLoading by chatViewModel.localModelLoading.collectAsState()
    val anyLocalStreamActive by chatViewModel.anyLocalStreamActive.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()

    val pendingUri by chatViewModel.pendingAttachmentUri.collectAsState()
    val pendingName by chatViewModel.pendingAttachmentName.collectAsState()
    val selectedModelID by settingsViewModel.selectedModel.collectAsState()
    val customModelsList by settingsViewModel.customModels.collectAsState()
    val localModelsList by settingsViewModel.localModels.collectAsState()
    val localModelsEnabled by settingsViewModel.localModelsEnabled.collectAsState()
    val currentThreadId by chatViewModel.currentChatThreadId.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val activeModelList = remember(customModelsList) {
        val list = mutableListOf(DEFAULT_MODEL)
        customModelsList.forEach { custom -> if (list.none { it.first == custom.id }) list.add(custom.id to custom.name) }
        list
    }
    val localModelEntries = remember(localModelsList, localModelsEnabled) {
        if (localModelsEnabled) localModelsList.map { it.id to it.name } else emptyList()
    }
    val modelShortName = activeModelList.firstOrNull { it.first == selectedModelID }?.second
        ?: customModelsList.firstOrNull { it.id == selectedModelID }?.name
        ?: localModelsList.firstOrNull { it.id == selectedModelID }?.name
        ?: selectedModelID
    val localSendBlocked = selectedModelID.startsWith("local/") && anyLocalStreamActive && !isStreaming

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) chatViewModel.setPendingAttachment(uri) },
    )

    // Measure the floating input's height so the message list always pads exactly enough to clear
    // it — including when the keyboard pushes the input up (its measured height grows with the inset).
    val density = LocalDensity.current
    var inputHeightPx by remember { mutableStateOf(0) }
    val messageBottomInset = if (inputHeightPx > 0) with(density) { inputHeightPx.toDp() } else 96.dp
    // Top inset so the chat scrolls behind the floating top bar without hiding the first message.
    val topBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Everything (top bar + input) floats; the chat fills behind it. Insets handled per-element.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty() && !isStreaming && !progressLoading) {
                EmptyState { textInput = it }
            } else {
                // key() gives each conversation a fresh MessagesPane (own scroll state), so switching
                // opens at the bottom with no inherited-offset jump. bottomInset keeps the last
                // message clear of the floating input.
                key(currentThreadId) {
                    MessagesPane(
                        messages = messages,
                        isStreaming = isStreaming,
                        segments = activeSegments,
                        statusNote = statusNote,
                        progressLoading = progressLoading,
                        modelLoading = localModelLoading,
                        topInset = topBarInset,
                        bottomInset = messageBottomInset,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                    )
                }
            }

            // Floating, transparent top bar (chat scrolls behind it).
            ChatTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                modelName = modelShortName,
                onMenu = onMenuClicked,
                onModel = { showModelMenu = true },
                onNewChat = { chatViewModel.startNewChat() },
            )

            // Floating input toolbar over the bottom of the chat.
            InputToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { inputHeightPx = it.height },
                text = textInput,
                onText = { textInput = it },
                pendingUri = pendingUri?.toString(),
                pendingName = pendingName,
                onClearAttachment = { chatViewModel.clearPendingAttachment() },
                onAttach = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                isStreaming = isStreaming,
                blockedReason = if (localSendBlocked) "On-device model is busy in another chat" else null,
                onSend = { val t = textInput; textInput = ""; chatViewModel.sendMessage(t) },
            )

            // Error banner floats just below the top bar.
            AnimatedVisibility(
                visible = errorMessage != null,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topBarInset),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                errorMessage?.let { ErrorBanner(it) { chatViewModel.clearError() } }
            }
        }
    }

    if (showModelMenu) {
        ModelPickerSheet(
            models = activeModelList,
            localModels = localModelEntries,
            selectedId = selectedModelID,
            onSelect = { settingsViewModel.saveSelectedModel(it); showModelMenu = false },
            onManage = { showModelMenu = false; onSettingsClicked() },
            onDismiss = { showModelMenu = false },
        )
    }
}

/**
 * The scrolling message list for one conversation. Owns its own [LazyListState] so each chat keeps
 * its scroll position and a switch (via the parent key()) doesn't inherit the previous chat's
 * offset. Keeps the stick-to-bottom behaviour (respects manual scroll, follows streaming).
 */
@Composable
private fun MessagesPane(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    segments: List<StreamSegment>,
    statusNote: String?,
    progressLoading: Boolean,
    modelLoading: Boolean,
    topInset: Dp = Spacing.l,
    bottomInset: Dp = Spacing.l,
    onCopy: (String) -> Unit,
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
            MessageBubble(msg) { onCopy(msg.content) }
        }
        if (modelLoading && segments.isEmpty()) {
            item { ModelLoadingRow() }
        } else if (progressLoading && segments.isEmpty()) {
            item { ThinkingRow() }
        }
        if (segments.isNotEmpty()) item(key = "streaming") {
            StreamingAssistantBubble(segments = segments, statusNote = statusNote, isStreaming = isStreaming)
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
private fun ModelLoadingRow(modifier: Modifier = Modifier) {
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
private fun StreamingAssistantBubble(
    segments: List<StreamSegment>,
    statusNote: String?,
    isStreaming: Boolean,
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

@Composable
private fun ChatTopBar(modelName: String, onMenu: () -> Unit, onModel: () -> Unit, onNewChat: () -> Unit, modifier: Modifier = Modifier) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            // Fun shaped icon button (morphs on press), vividly themed.
            ShapedIconButton(
                onClick = onMenu, enabled = true, size = 44.dp,
                restShape = MaterialShapes.Cookie4Sided, pressedShape = MaterialShapes.Cookie7Sided,
                container = MaterialTheme.colorScheme.primaryContainer,
            ) { Icon(Icons.Default.Menu, "Open conversations", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        },
        title = {
            // Model selector as a Material 3 Expressive split button — both halves open the picker.
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = onModel) {
                        Text(
                            modelName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                    }
                },
                trailingButton = {
                    // TrailingButton is a toggle in M3; we just use its tap to open the picker.
                    SplitButtonDefaults.TrailingButton(checked = false, onCheckedChange = { onModel() }) {
                        Icon(Icons.Default.KeyboardArrowDown, "Choose model", Modifier.size(20.dp))
                    }
                },
            )
        },
        actions = {
            // Fun shaped icon button that pops + morphs on click, vividly themed.
            ShapedIconButton(
                onClick = onNewChat, enabled = true, size = 44.dp,
                restShape = MaterialShapes.Cookie7Sided, pressedShape = MaterialShapes.Sunny,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                pulseOnClick = true,
            ) { Icon(Icons.Default.Add, "New conversation", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
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
private fun MessageBubble(message: ChatMessage, modifier: Modifier = Modifier, streaming: Boolean = false, onCopy: () -> Unit) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 320.dp)) {
                message.localAttachmentUri?.let {
                    AsyncImage(it, null, Modifier.padding(bottom = Spacing.s).size(200.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                }
                Surface(
                    shape = RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(message.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = Spacing.m))
                }
            }
        }
    } else {
        // ChatGPT / Claude style: no bubble, full content width.
        Column(modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(size = 26.dp, animated = streaming)
                Spacer(Modifier.width(Spacing.s))
                Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.s))

            // Reasoning ("thinking") trace for reasoning-capable models.
            val reasoningText = message.reasoning
            if (!reasoningText.isNullOrBlank()) {
                ReasoningSection(
                    reasoning = reasoningText,
                    active = streaming && message.content.isBlank(),
                )
                Spacer(Modifier.height(Spacing.s))
            }

            // Web searches the model ran for this answer (persisted, collapsed).
            val toolEvents = remember(message.id) { ToolEventJson.toolEventsFromJson(message.toolEventsJson) }
            toolEvents.forEach { event ->
                SearchActivityCard(query = event.query, sources = event.sources, active = false)
                Spacer(Modifier.height(Spacing.s))
            }

            message.localAttachmentUri?.let {
                AsyncImage(it, null, Modifier.padding(bottom = Spacing.s).size(200.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
            }
            if (streaming) {
                // Live markdown, revealed at a smooth steady cadence (decoupled from bursty chunks).
                if (message.content.isNotBlank()) SmoothStreamingText(message.content, Modifier.fillMaxWidth())
            } else {
                // World-class render for the finished message: tables, lists, highlighted code, etc.
                RichMarkdown(message.content, Modifier.fillMaxWidth())
            }
            if (!streaming) {
                Spacer(Modifier.height(Spacing.xs))
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp)) }
            }
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
private fun SmoothStreamingText(
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
private fun ReasoningSection(reasoning: String, active: Boolean) {
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: active
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "reasoning-chevron")
    Surface(
        onClick = { userToggled = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
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
private fun ThinkingRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(modifier = Modifier.padding(end = Spacing.m), size = 32.dp, animated = true)
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.s))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit) {
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
private fun AssistPill(icon: ImageVector, label: String, container: Color, onContainer: Color, onClick: () -> Unit) {
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
private fun InputToolbar(
    text: String,
    onText: (String) -> Unit,
    pendingUri: String?,
    pendingName: String?,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    isStreaming: Boolean,
    blockedReason: String? = null,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = Spacing.base, vertical = Spacing.m),
    ) {
        AnimatedVisibility(visible = pendingUri != null) {
            pendingUri?.let { uri ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = Spacing.s),
                ) {
                    Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(uri, null, Modifier.size(40.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(Spacing.m))
                        Text(pendingName ?: "Attachment", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }
        }
        AnimatedVisibility(visible = blockedReason != null) {
            Text(
                blockedReason.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.base, bottom = Spacing.s),
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                ShapedIconButton(
                    onClick = onAttach,
                    enabled = true,
                    size = 44.dp,
                    restShape = MaterialShapes.Cookie6Sided,
                    pressedShape = MaterialShapes.Flower,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    pulseOnClick = true,
                ) {
                    Icon(Icons.Outlined.AddPhotoAlternate, "Attach image", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }

                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = { Text("Ask anything…") },
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).testTag("chat_input_field"),
                )

                val hasContent = text.trim().isNotEmpty() || pendingUri != null
                val canSend = hasContent && !isStreaming && blockedReason == null
                SendButton(enabled = canSend, isStreaming = isStreaming) {
                    if (canSend) onSend()
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, isStreaming: Boolean, onClick: () -> Unit) {
    if (isStreaming) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
    } else {
        // The hero action gets the boldest shape — a "Sunny" that morphs to a rounder cookie on
        // press with an expressive (bouncy) spring.
        ShapedIconButton(
            onClick = onClick,
            enabled = enabled,
            size = 48.dp,
            restShape = MaterialShapes.Sunny,
            pressedShape = MaterialShapes.Cookie12Sided,
            container = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send, "Send", Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A "fun shape" icon button: filled with a [MaterialShapes] polygon that **morphs to a second
 * shape on press** via the expressive bouncy spring (motion physics). A soft top-lit vertical
 * gradient gives the flat shape a 2.5D sense of volume.
 */
@Composable
private fun ShapedIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    size: Dp,
    restShape: RoundedPolygon,
    pressedShape: RoundedPolygon,
    container: Color,
    pulseOnClick: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "icon-shape-morph",
    )
    // One-shot "pop + morph" pulse fired on click (for buttons whose action doesn't change the
    // screen, like attaching a photo, so the feedback is actually seen).
    val scope = rememberCoroutineScope()
    val clickPulse = remember { Animatable(0f) }
    val progress = maxOf(pressProgress, clickPulse.value)
    val morph = rememberMorph(restShape, pressedShape)
    val shape = MorphPolygonShape(morph, progress)
    val popScale = 1f + 0.18f * clickPulse.value
    // 2.5D volume: lighter at the top, base colour at the bottom.
    val brush = Brush.verticalGradient(listOf(lerp(container, Color.White, 0.16f), container))
    Box(
        Modifier
            .size(size)
            .scale(popScale)
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                enabled = enabled,
                onClick = {
                    if (pulseOnClick) scope.launch {
                        clickPulse.snapTo(0f)
                        clickPulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        clickPulse.animateTo(0f, tween(durationMillis = 180))
                    }
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ModelPickerSheet(
    models: List<Pair<String, String>>,
    localModels: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Text("Choose a model", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Manage")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search models") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            val filtered = models.filter { it.second.contains(query, true) || it.first.contains(query, true) }
            val filteredLocal = localModels.filter { it.second.contains(query, true) || it.first.contains(query, true) }
            if (filtered.isEmpty() && filteredLocal.isEmpty()) {
                Text(
                    "No models. Tap Manage to add one in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(filtered, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedId, isLocal = false) { onSelect(id) }
                }
                if (filteredLocal.isNotEmpty()) {
                    item(key = "local-section") {
                        Box(Modifier.padding(top = Spacing.m)) { SectionLabel("On-device") }
                    }
                    items(filteredLocal, key = { it.first }) { (id, name) ->
                        ModelRow(name, id, id == selectedId, isLocal = true) { onSelect(id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(name: String, modelId: String, selected: Boolean, isLocal: Boolean = false, onClick: () -> Unit) {
    val provider = when {
        isLocal -> "Runs on this device — private & offline"
        modelId.contains("/") -> modelId.substringBefore("/").replaceFirstChar { it.uppercase() }
        else -> "Custom"
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (isLocal) {
                        Spacer(Modifier.width(Spacing.s))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Row(
                                Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.OfflineBolt, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.width(3.dp))
                                Text("Local", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                Text(provider, style = MaterialTheme.typography.bodySmall, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
