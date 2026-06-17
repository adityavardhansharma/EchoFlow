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
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AgentProfile
import com.echoflow.data.ChatMessage
import com.echoflow.data.DataAgentCatalog
import com.echoflow.data.DeepResearchCatalog
import com.echoflow.data.DeepResearchModel
import com.echoflow.data.DrEngine
import com.echoflow.data.FusionPanel
import com.echoflow.data.ResearchRun
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.CapabilityChip
import com.echoflow.ui.components.DataResultCard
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.EffortPill
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.ReportCard
import com.echoflow.ui.components.ResearchProgressCard
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.components.SubagentCard
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
    val pendingMime by chatViewModel.pendingAttachmentMimeType.collectAsState()
    val pendingName by chatViewModel.pendingAttachmentName.collectAsState()
    val selectedModelID by settingsViewModel.selectedModel.collectAsState()
    val customModelsList by settingsViewModel.customModels.collectAsState()
    val localModelsList by settingsViewModel.localModels.collectAsState()
    val localModelsEnabled by settingsViewModel.localModelsEnabled.collectAsState()
    val currentThreadId by chatViewModel.currentChatThreadId.collectAsState()

    val deepResearchActive by chatViewModel.deepResearchActive.collectAsState()
    val webSearchChipOn by chatViewModel.webSearchChipOn.collectAsState()
    val dataAgentActive by chatViewModel.dataAgentActive.collectAsState()
    val researchRun by chatViewModel.currentResearchRun.collectAsState()
    val drModelId by settingsViewModel.deepResearchModelId.collectAsState()
    val drModels by settingsViewModel.deepResearchModels.collectAsState()
    val exaKey by settingsViewModel.exaApiKey.collectAsState()
    val parallelKey by settingsViewModel.parallelApiKey.collectAsState()
    val firecrawlKey by settingsViewModel.firecrawlApiKey.collectAsState()
    val exaEffort by settingsViewModel.deepResearchExaEffort.collectAsState()
    val dataAgentEnabled by settingsViewModel.dataAgentEnabled.collectAsState()
    val dataAgentEngineId by settingsViewModel.dataAgentEngine.collectAsState()

    val echoAdviserActive by chatViewModel.echoAdviserActive.collectAsState()
    val echoFusionActive by chatViewModel.echoFusionActive.collectAsState()
    val advisorProfiles by settingsViewModel.advisorProfiles.collectAsState()
    val fusionPanels by settingsViewModel.fusionPanels.collectAsState()
    val echoAdviserProfileId by settingsViewModel.echoAdviserProfileId.collectAsState()
    val echoFusionPanelId by settingsViewModel.echoFusionPanelId.collectAsState()
    val activeAdvisor = advisorProfiles.firstOrNull { it.id == echoAdviserProfileId }
    val activePanel = fusionPanels.firstOrNull { it.id == echoFusionPanelId }

    val echoAgentActive by chatViewModel.echoAgentActive.collectAsState()
    val agentProfiles by settingsViewModel.agentProfiles.collectAsState()
    val echoAgentProfileId by settingsViewModel.echoAgentProfileId.collectAsState()
    val activeAgent = agentProfiles.firstOrNull { it.id == echoAgentProfileId }

    var textInput by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }

    val drEngineLabel = remember(drModelId, drModels) {
        DeepResearchCatalog.providerEngineById(drModelId)?.name
            ?: drModels.firstOrNull { it.id == drModelId }?.name
            ?: if (drModelId.isBlank()) "Choose engine" else drModelId
    }
    val dataAgentLabel = remember(dataAgentEngineId) {
        DataAgentCatalog.byId(dataAgentEngineId)?.name ?: "Choose agent"
    }
    val dataAgentAvailable = dataAgentEnabled && firecrawlKey.isNotBlank()

    // Image attachments work for cloud models and for on-device .litertlm bundles (which
    // support vision); .task models are text-only, so the Image option is hidden for them.
    val imageAttachAllowed = remember(selectedModelID, localModelsList) {
        if (selectedModelID.startsWith("local/")) {
            localModelsList.firstOrNull { it.id == selectedModelID }
                ?.fileName?.endsWith(".litertlm", ignoreCase = true) == true
        } else true
    }
    val imageAttachAvailable = imageAttachAllowed && !deepResearchActive && !dataAgentActive
    val selectedModelIsOpenRouter = remember(selectedModelID) { !selectedModelID.startsWith("local/") }
    val deepResearchUsesOpenRouter = remember(deepResearchActive, drModelId) {
        deepResearchActive && drModelId.isNotBlank() && !DeepResearchCatalog.isProviderEngine(drModelId)
    }
    val pdfAttachAllowed = remember(
        selectedModelIsOpenRouter,
        deepResearchActive,
        deepResearchUsesOpenRouter,
        dataAgentActive,
        echoAdviserActive,
        echoFusionActive,
        echoAgentActive,
    ) {
        when {
            dataAgentActive -> false
            deepResearchActive -> deepResearchUsesOpenRouter
            echoFusionActive -> true
            echoAdviserActive -> selectedModelIsOpenRouter
            echoAgentActive -> selectedModelIsOpenRouter
            else -> selectedModelIsOpenRouter
        }
    }
    LaunchedEffect(pendingUri, pendingMime, imageAttachAvailable, pdfAttachAllowed) {
        if (pendingUri != null) {
            val pendingIsPdf = pendingMime.equals("application/pdf", ignoreCase = true)
            if ((pendingIsPdf && !pdfAttachAllowed) || (!pendingIsPdf && !imageAttachAvailable)) {
                chatViewModel.clearPendingAttachment()
            }
        }
    }

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
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) chatViewModel.setPendingAttachment(uri, "application/pdf") },
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
                        researchRun = researchRun,
                        onCancelResearch = { chatViewModel.cancelResearch() },
                        topInset = topBarInset,
                        bottomInset = messageBottomInset,
                        onCopy = { clipboard.setText(AnnotatedString(it)) },
                    )
                }
            }

            // Floating, transparent top bar (chat scrolls behind it).
            ChatTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                modelName = when {
                    echoFusionActive -> activePanel?.name ?: "Choose panel"
                    echoAdviserActive -> activeAdvisor?.let { "$modelShortName · ${it.name}" } ?: "Pick an advisor"
                    echoAgentActive -> activeAgent?.let { "$modelShortName · ${it.name}" } ?: "Pick an Echo Agent"
                    dataAgentActive -> dataAgentLabel
                    deepResearchActive -> drEngineLabel
                    else -> modelShortName
                },
                researchMode = deepResearchActive || dataAgentActive,
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
                pendingMime = pendingMime,
                pendingName = pendingName,
                onClearAttachment = { chatViewModel.clearPendingAttachment() },
                onAttach = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onAttachPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                imageAttachEnabled = imageAttachAvailable,
                pdfAttachEnabled = pdfAttachAllowed,
                isStreaming = isStreaming,
                deepResearchActive = deepResearchActive,
                webSearchChipOn = webSearchChipOn,
                dataAgentActive = dataAgentActive,
                dataAgentAvailable = dataAgentAvailable,
                echoAdviserActive = echoAdviserActive,
                echoFusionActive = echoFusionActive,
                echoAgentActive = echoAgentActive,
                advisorChipLabel = activeAdvisor?.name,
                fusionChipLabel = activePanel?.name,
                agentChipLabel = activeAgent?.name,
                onToggleDeepResearch = { chatViewModel.toggleDeepResearch() },
                onToggleWebSearch = { chatViewModel.toggleWebSearchChip() },
                onToggleDataAgent = { chatViewModel.toggleDataAgent() },
                onToggleEchoAdviser = { chatViewModel.toggleEchoAdviser() },
                onToggleEchoFusion = { chatViewModel.toggleEchoFusion() },
                onToggleEchoAgent = { chatViewModel.toggleEchoAgent() },
                researchInProgress = researchRun != null,
                researchEngineLabel = drEngineLabel,
                dataAgentLabel = dataAgentLabel,
                showEffortPill = deepResearchActive && drModelId == "exa-agent",
                exaEffort = exaEffort,
                onSelectEffort = { settingsViewModel.saveDeepResearchExaEffort(it) },
                blockedReason = when {
                    researchRun != null -> "A run is in progress — see the card above"
                    localSendBlocked -> "On-device model is busy in another chat"
                    else -> null
                },
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
        if (echoFusionActive) {
            FusionPickerSheet(
                panels = fusionPanels,
                selectedId = echoFusionPanelId,
                onSelect = { settingsViewModel.saveEchoFusionPanel(it); showModelMenu = false },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (echoAdviserActive) {
            AdvisorPickerSheet(
                models = activeModelList,
                selectedModelId = selectedModelID,
                profiles = advisorProfiles,
                selectedProfileId = echoAdviserProfileId,
                onSelectModel = { settingsViewModel.saveSelectedModel(it) },
                onSelectProfile = { settingsViewModel.saveEchoAdviserProfile(it) },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (echoAgentActive) {
            AgentPickerSheet(
                models = activeModelList,
                selectedModelId = selectedModelID,
                profiles = agentProfiles,
                selectedProfileId = echoAgentProfileId,
                onSelectModel = { settingsViewModel.saveSelectedModel(it) },
                onSelectProfile = { settingsViewModel.saveEchoAgentProfile(it) },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (dataAgentActive) {
            val dataEngines = remember(firecrawlKey) {
                if (firecrawlKey.isNotBlank()) DataAgentCatalog.engines else emptyList()
            }
            DeepResearchModelSheet(
                title = "Data Agent engine",
                subtitle = "Firecrawl agents that extract data from the web",
                providerEngines = dataEngines,
                agentModels = emptyList(),
                showAgentSection = false,
                selectedId = dataAgentEngineId,
                onSelect = { settingsViewModel.saveDataAgentEngine(it); showModelMenu = false },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (deepResearchActive) {
            val availableProviderEngines = remember(exaKey, parallelKey, firecrawlKey) {
                DeepResearchCatalog.providerEngines.filter { eng ->
                    when (eng.provider) {
                        "exa" -> exaKey.isNotBlank()
                        "parallel" -> parallelKey.isNotBlank()
                        "firecrawl" -> firecrawlKey.isNotBlank()
                        else -> false
                    }
                }
            }
            DeepResearchModelSheet(
                providerEngines = availableProviderEngines,
                agentModels = drModels,
                selectedId = drModelId,
                onSelect = { settingsViewModel.saveDeepResearchModel(it); showModelMenu = false },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else {
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
    researchRun: ResearchRun?,
    onCancelResearch: () -> Unit,
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
        researchRun?.let { run ->
            item(key = "research") { ResearchProgressCard(run = run, onCancel = onCancelResearch) }
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
private fun ChatTopBar(modelName: String, researchMode: Boolean, onMenu: () -> Unit, onModel: () -> Unit, onNewChat: () -> Unit, modifier: Modifier = Modifier) {
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
            // In Deep Research mode it selects the research engine instead of the chat model.
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = onModel) {
                        if (researchMode) {
                            Icon(Icons.Default.Science, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.xs))
                        }
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
                message.localAttachmentUri?.let { uri ->
                    MessageAttachmentPreview(
                        uri = uri,
                        mimeType = message.localAttachmentMimeType,
                        name = message.localAttachmentName,
                        modifier = Modifier.padding(bottom = Spacing.s),
                    )
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

            // Finished replies render their persisted timeline in arrival order, so
            // reason → search → reason → search → answer keeps exactly the layout it
            // streamed with instead of merging all reasoning into one block.
            val persistedSegments = remember(message.id) { ToolEventJson.segmentsFromJson(message.segmentsJson) }

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
                    val planSteps = remember(message.id) {
                        persistedSegments.firstOrNull { it.type == "plan" }?.text
                            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    }
                    val reportCitations = remember(message.id) { ToolEventJson.citationsFromJson(message.citationsJson) }
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
                            // The plan is rendered as a disclosure inside the report card.
                            "plan" -> Unit
                            "report" -> {
                                ReportCard(
                                    report = segment.text.orEmpty(),
                                    citations = reportCitations,
                                    planSteps = planSteps,
                                    onCopy = onCopy,
                                )
                                if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
                            }
                            "data" -> {
                                DataResultCard(
                                    json = segment.text.orEmpty(),
                                    citations = reportCitations,
                                    onCopy = onCopy,
                                )
                                if (index != persistedSegments.lastIndex) Spacer(Modifier.height(Spacing.s))
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
                    val toolEvents = remember(message.id) { ToolEventJson.toolEventsFromJson(message.toolEventsJson) }
                    toolEvents.forEach { event ->
                        SearchActivityCard(query = event.query, sources = event.sources, active = false)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    RichMarkdown(message.content, Modifier.fillMaxWidth())
                }
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
private fun MessageAttachmentPreview(
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

@Composable
private fun InputToolbar(
    text: String,
    onText: (String) -> Unit,
    pendingUri: String?,
    pendingMime: String?,
    pendingName: String?,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    onAttachPdf: () -> Unit,
    imageAttachEnabled: Boolean,
    pdfAttachEnabled: Boolean,
    isStreaming: Boolean,
    deepResearchActive: Boolean,
    webSearchChipOn: Boolean,
    dataAgentActive: Boolean,
    dataAgentAvailable: Boolean,
    echoAdviserActive: Boolean,
    echoFusionActive: Boolean,
    echoAgentActive: Boolean,
    advisorChipLabel: String?,
    fusionChipLabel: String?,
    agentChipLabel: String?,
    onToggleDeepResearch: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleDataAgent: () -> Unit,
    onToggleEchoAdviser: () -> Unit,
    onToggleEchoFusion: () -> Unit,
    onToggleEchoAgent: () -> Unit,
    researchInProgress: Boolean,
    researchEngineLabel: String?,
    dataAgentLabel: String,
    showEffortPill: Boolean,
    exaEffort: String,
    onSelectEffort: (String) -> Unit,
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
                        if (pendingMime.equals("application/pdf", ignoreCase = true)) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                null,
                                Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            AsyncImage(uri, null, Modifier.size(40.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(Spacing.m))
                        Text(pendingName ?: "Attachment", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }
        }

        // Active capability chips — always show what's on so behaviour is never hidden.
        AnimatedVisibility(visible = webSearchChipOn || deepResearchActive || dataAgentActive || echoAdviserActive || echoFusionActive || echoAgentActive) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(start = Spacing.s, bottom = Spacing.s),
            ) {
                if (webSearchChipOn) {
                    CapabilityChip(Icons.Default.TravelExplore, "Web search", onRemove = onToggleWebSearch)
                }
                if (echoAdviserActive) {
                    CapabilityChip(
                        Icons.Default.Psychology,
                        advisorChipLabel?.let { "Adviser · $it" } ?: "Echo Adviser",
                        onRemove = onToggleEchoAdviser,
                    )
                }
                if (echoFusionActive) {
                    CapabilityChip(
                        Icons.Default.AccountTree,
                        fusionChipLabel?.let { "Fusion · $it" } ?: "Echo Fusion",
                        onRemove = onToggleEchoFusion,
                    )
                }
                if (echoAgentActive) {
                    CapabilityChip(
                        Icons.Default.Hub,
                        agentChipLabel?.let { "Echo Agent · $it" } ?: "Echo Agents",
                        onRemove = onToggleEchoAgent,
                    )
                }
                if (deepResearchActive) {
                    CapabilityChip(
                        Icons.Default.Science,
                        researchEngineLabel?.takeIf { it.isNotBlank() && it != "Choose engine" }?.let { "Research · $it" } ?: "Deep Research",
                        onRemove = onToggleDeepResearch,
                    )
                    // Exa Agent's depth/cost dial lives here, not in the engine list.
                    if (showEffortPill) EffortPill(effort = exaEffort, onSelect = onSelectEffort)
                }
                if (dataAgentActive) {
                    CapabilityChip(
                        Icons.Default.Science,
                        dataAgentLabel.takeIf { it.isNotBlank() && it != "Choose agent" }?.let { "Data Agent · $it" } ?: "Data Agent",
                        onRemove = onToggleDataAgent,
                    )
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
        AnimatedVisibility(visible = (deepResearchActive || dataAgentActive) && blockedReason == null) {
            Text(
                if (dataAgentActive) "Collects data into a table · runs in the background"
                else "Runs in the background · multiple searches · a few minutes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.base, bottom = Spacing.s),
            )
        }
        AnimatedVisibility(visible = (echoAdviserActive || echoFusionActive || echoAgentActive) && blockedReason == null) {
            Text(
                when {
                    echoFusionActive -> "Runs a panel of models + a judge every message · cost-heavy"
                    echoAgentActive -> "The main model hands tasks to your Echo Agent each message · adds cost"
                    else -> "Consults a stronger advisor model each message · adds cost"
                },
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
                var plusMenuOpen by remember { mutableStateOf(false) }
                Box {
                    ShapedIconButton(
                        onClick = { plusMenuOpen = true },
                        enabled = true,
                        size = 44.dp,
                        restShape = MaterialShapes.Cookie6Sided,
                        pressedShape = MaterialShapes.Flower,
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        pulseOnClick = true,
                    ) {
                        Icon(Icons.Default.Add, "Add context or capability", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    PlusMenu(
                        expanded = plusMenuOpen,
                        onDismiss = { plusMenuOpen = false },
                        showImage = imageAttachEnabled,
                        showFiles = pdfAttachEnabled,
                        webSearchOn = webSearchChipOn,
                        deepResearchOn = deepResearchActive,
                        dataAgentOn = dataAgentActive,
                        dataAgentAvailable = dataAgentAvailable,
                        echoAdviserOn = echoAdviserActive,
                        echoFusionOn = echoFusionActive,
                        echoAgentOn = echoAgentActive,
                        onImage = { plusMenuOpen = false; onAttach() },
                        onFiles = { plusMenuOpen = false; onAttachPdf() },
                        onToggleWebSearch = { plusMenuOpen = false; onToggleWebSearch() },
                        onToggleDeepResearch = { plusMenuOpen = false; onToggleDeepResearch() },
                        onToggleDataAgent = { plusMenuOpen = false; onToggleDataAgent() },
                        onToggleEchoAdviser = { plusMenuOpen = false; onToggleEchoAdviser() },
                        onToggleEchoFusion = { plusMenuOpen = false; onToggleEchoFusion() },
                        onToggleEchoAgent = { plusMenuOpen = false; onToggleEchoAgent() },
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = {
                        Text(
                            when {
                                dataAgentActive -> "Describe the data to extract…"
                                deepResearchActive -> "Research a topic…"
                                else -> "Ask anything…"
                            }
                        )
                    },
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

                val researchMode = deepResearchActive || dataAgentActive
                val hasText = text.trim().isNotEmpty()
                val hasContent = hasText || (pendingUri != null && !researchMode)
                val canSend = hasContent && !isStreaming && !researchInProgress && blockedReason == null
                SendButton(enabled = canSend, isStreaming = isStreaming, research = researchMode) {
                    if (canSend) onSend()
                }
            }
        }
    }
}

/** The unified "+" menu: add context (image) or turn on a capability (search / research). */
@Composable
private fun PlusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showImage: Boolean,
    showFiles: Boolean,
    webSearchOn: Boolean,
    deepResearchOn: Boolean,
    dataAgentOn: Boolean,
    dataAgentAvailable: Boolean,
    echoAdviserOn: Boolean,
    echoFusionOn: Boolean,
    echoAgentOn: Boolean,
    onImage: () -> Unit,
    onFiles: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleDeepResearch: () -> Unit,
    onToggleDataAgent: () -> Unit,
    onToggleEchoAdviser: () -> Unit,
    onToggleEchoFusion: () -> Unit,
    onToggleEchoAgent: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Image is offered for cloud models and vision-capable on-device (.litertlm) bundles.
        if (showImage) {
            DropdownMenuItem(
                text = { Text("Image") },
                leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null) },
                onClick = onImage,
            )
        }
        if (showFiles) {
            DropdownMenuItem(
                text = { Text("Files") },
                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                onClick = onFiles,
            )
        }
        DropdownMenuItem(
            text = { Text("Web search") },
            leadingIcon = { Icon(Icons.Default.TravelExplore, null) },
            trailingIcon = { if (webSearchOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleWebSearch,
        )
        DropdownMenuItem(
            text = { Text("Deep Research") },
            leadingIcon = { Icon(Icons.Default.Science, null) },
            trailingIcon = { if (deepResearchOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleDeepResearch,
        )
        // Data Agent only appears once it's enabled in Settings and a Firecrawl key exists.
        if (dataAgentAvailable) {
            DropdownMenuItem(
                text = { Text("Data Agent") },
                leadingIcon = { Icon(Icons.Default.Dataset, null) },
                trailingIcon = { if (dataAgentOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
                onClick = onToggleDataAgent,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
        // Echo Adviser / Echo Fusion — OpenRouter-only, cost-heavy modes. Always shown; if the
        // key or a profile/panel is missing, sending surfaces a "set it up" message.
        DropdownMenuItem(
            text = { Text("Echo Adviser") },
            leadingIcon = { Icon(Icons.Default.Psychology, null) },
            trailingIcon = { if (echoAdviserOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleEchoAdviser,
        )
        DropdownMenuItem(
            text = { Text("Echo Fusion") },
            leadingIcon = { Icon(Icons.Default.AccountTree, null) },
            trailingIcon = { if (echoFusionOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleEchoFusion,
        )
        DropdownMenuItem(
            text = { Text("Echo Agents") },
            leadingIcon = { Icon(Icons.Default.Hub, null) },
            trailingIcon = { if (echoAgentOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleEchoAgent,
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, isStreaming: Boolean, research: Boolean = false, onClick: () -> Unit) {
    if (isStreaming) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
    } else {
        // The hero action gets the boldest shape — a "Sunny" that morphs to a rounder cookie on
        // press with an expressive (bouncy) spring. In research mode it starts the investigation.
        ShapedIconButton(
            onClick = onClick,
            enabled = enabled,
            size = 48.dp,
            restShape = MaterialShapes.Sunny,
            pressedShape = MaterialShapes.Cookie12Sided,
            container = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Icon(
                if (research) Icons.Default.Science else Icons.AutoMirrored.Filled.Send,
                if (research) "Start research" else "Send",
                Modifier.size(20.dp),
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

/**
 * Echo Fusion panel picker: each saved panel (a named roster of 2–8 models) is a selectable
 * card showing its members. The chosen panel deliberates on every message until turned off.
 */
@Composable
private fun FusionPickerSheet(
    panels: List<FusionPanel>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Choose a fusion panel", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Models answer in parallel, a judge compares them", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }
            if (panels.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text("No panels yet.\nTap Manage to build one in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                items(panels, key = { it.id }) { panel ->
                    DrEngineRow(
                        name = panel.name,
                        description = panel.names.joinToString(" · ").ifBlank { "${panel.models.size} models" },
                        selected = panel.id == selectedId,
                    ) { onSelect(panel.id) }
                }
            }
        }
    }
}

/**
 * Echo Adviser picker: two zones — the answering model (any cloud model) and which advisor
 * profile it escalates to. Both persist immediately; the sheet stays open so the user can set
 * both before dismissing.
 */
@Composable
private fun AdvisorPickerSheet(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    profiles: List<AdvisorProfile>,
    selectedProfileId: String,
    onSelectModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Adviser setup", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("A cloud model answers and consults your advisor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Advisor") }
            if (profiles.isEmpty()) {
                Text(
                    "No advisors yet — tap Manage to set one up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.m)) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == selectedProfileId,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                            leadingIcon = { Icon(Icons.Default.Psychology, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Answering model") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                items(models, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedModelId, isLocal = false) { onSelectModel(id) }
                }
            }

            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                Text("Done")
            }
        }
    }
}

/**
 * Echo Agent picker: two zones — the orchestrator (any cloud model, which drives the toolbox)
 * and which agent profile (the worker model it delegates to). Both persist immediately; the
 * sheet stays open so the user can set both before dismissing.
 */
@Composable
private fun AgentPickerSheet(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    profiles: List<AgentProfile>,
    selectedProfileId: String,
    onSelectModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Echo Agents", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Your main model uses tools and hands tasks to your Echo Agent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Echo Agent") }
            if (profiles.isEmpty()) {
                Text(
                    "No Echo Agents yet — tap Manage to set one up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.m)) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == selectedProfileId,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                            leadingIcon = { Icon(Icons.Default.Hub, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Main model") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                items(models, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedModelId, isLocal = false) { onSelectModel(id) }
                }
            }

            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                Text("Done")
            }
        }
    }
}

/**
 * Engine picker shown in Deep Research mode. Lists built-in provider-native engines (only
 * those whose API key is configured) plus the user's added agentic chat models. Selecting
 * one stores it as the active Deep Research engine.
 */
@Composable
private fun DeepResearchModelSheet(
    providerEngines: List<DrEngine>,
    agentModels: List<DeepResearchModel>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Deep Research engine",
    subtitle: String = "Providers run research themselves; chat models orchestrate it",
    showAgentSection: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Manage")
                }
            }

            if (providerEngines.isEmpty() && (!showAgentSection || agentModels.isEmpty())) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Nothing set up yet.\nAdd a provider key (or model) in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                if (providerEngines.isNotEmpty()) {
                    item(key = "provider-section") { Box(Modifier.padding(top = Spacing.s)) { SectionLabel(if (showAgentSection) "Provider research" else "Agents") } }
                    items(providerEngines, key = { it.id }) { engine ->
                        DrEngineRow(engine.name, engine.description, engine.id == selectedId) { onSelect(engine.id) }
                    }
                }
                if (showAgentSection && agentModels.isNotEmpty()) {
                    item(key = "agent-section") { Box(Modifier.padding(top = Spacing.m)) { SectionLabel("Your research models") } }
                    items(agentModels, key = { it.id }) { model ->
                        DrEngineRow(model.name, "Orchestrates searches into a report", model.id == selectedId) { onSelect(model.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrEngineRow(name: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Science, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ModelRow(name: String, modelId: String, selected: Boolean, isLocal: Boolean = false, onClick: () -> Unit) {
    val displayName = remember(name, isLocal) { modelPickerDisplayName(name, isLocal) }
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
                        displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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
                Text(
                    provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun modelPickerDisplayName(name: String, isLocal: Boolean): String {
    if (!isLocal || name.length <= 24) return name

    var clean = name
        .removeSuffix(".gguf")
        .removeSuffix(".GGUF")
        .removeSuffix(".task")
        .removeSuffix(".TASK")
        .removeSuffix(".litertlm")
        .removeSuffix(".LITERTLM")
        .trim()

    val quantOrPrecisionSuffix = Regex(
        pattern = """(?i)(?:[-_](?:q[2-8](?:[-_][a-z0-9]+){0,5}|iq[1-4](?:[-_][a-z0-9]+){0,5}|mixed[-_]?int[48]|int[48]|f16|fp16|bf16))+$"""
    )
    clean = clean.replace(quantOrPrecisionSuffix, "")

    if (clean.length > 24) {
        clean = clean.replace(Regex("""(?i)[-_](?:20\d{2}|2[0-9]{3}|[0-9]{4})(?=$|[-_])"""), "")
    }
    if (clean.length > 30) {
        clean = clean
            .replace(Regex("""(?i)[-_](?:gguf|k[-_]?m|instruct[-_]?gguf)$"""), "")
            .trim('-', '_', ' ')
    }

    return clean.ifBlank { name }.takeIf { it.length >= 8 } ?: name
}
