
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
import com.echoflow.data.ResearchRun
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ArtifactCard
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
    val customProviderConfig by settingsViewModel.customProviderConfig.collectAsState()
    val customModelsList by settingsViewModel.customModels.collectAsState()
    val customProviderModels by settingsViewModel.customProviderModels.collectAsState()
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

    // Echo Labs master switches gate which experimental modes appear in the "+" menu.
    val echoAdviserEnabled by settingsViewModel.echoAdviserEnabled.collectAsState()
    val echoFusionEnabled by settingsViewModel.echoFusionEnabled.collectAsState()
    val echoAgentEnabled by settingsViewModel.echoAgentEnabled.collectAsState()

    val artifactActive by chatViewModel.artifactActive.collectAsState()
    val imageGenActive by chatViewModel.imageGenActive.collectAsState()
    val imageGenModelId by settingsViewModel.imageGenModelId.collectAsState()
    val imageModels by settingsViewModel.imageModels.collectAsState()
    val imageGenEngine by settingsViewModel.imageGenEngine.collectAsState()
    val localImageModels by settingsViewModel.localImageModels.collectAsState()
    val localImageModelId by settingsViewModel.localImageModelId.collectAsState()
    // "Create image" swaps the model picker to image models only: OpenRouter entries plus
    // the installed on-device ones. Picking a row also selects the matching engine.
    val imageModelEntries = remember(imageModels) {
        val default = com.echoflow.data.SettingsRepository.DEFAULT_IMAGE_MODEL_ID to "Gemini 2.5 Flash Image"
        listOf(default) + imageModels.filter { it.id != default.first }.map { it.id to it.name }
    }
    val localImageEntries = remember(localImageModels) { localImageModels.map { it.id to it.name } }
    val imageEngineIsLocal = imageGenEngine == com.echoflow.data.SettingsRepository.IMAGE_ENGINE_LOCAL
    val selectedImageModelId = if (imageEngineIsLocal) localImageModelId else imageGenModelId
    val imageModelLabel = if (imageEngineIsLocal) {
        localImageModels.firstOrNull { it.id == localImageModelId }?.name ?: "Pick an image model"
    } else {
        imageModelEntries.firstOrNull { it.first == imageGenModelId }?.second ?: imageGenModelId
    }
    val browserFlowActive by chatViewModel.browserFlowActive.collectAsState()
    val browserFlowAvailable by chatViewModel.browserFlowAvailable.collectAsState()
    val browserSession by chatViewModel.currentBrowserSession.collectAsState()
    val browserSteps by chatViewModel.currentBrowserSteps.collectAsState()
    val browserStartConflict by chatViewModel.browserStartConflict.collectAsState()
    // The owning chat is "captured" while a session is live — every message drives the browser.
    val browserCaptured = browserSession != null
    val browserBusy = browserSession?.let {
        it.status == com.echoflow.data.BrowserSession.STATUS_RUNNING ||
            it.status == com.echoflow.data.BrowserSession.STATUS_STARTING ||
            it.status == com.echoflow.data.BrowserSession.STATUS_RESOLVING
    } == true

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
    val imageAttachAllowed = remember(selectedModelID, localModelsList, customProviderConfig) {
        when {
            selectedModelID.startsWith("local/") ->
                localModelsList.firstOrNull { it.id == selectedModelID }
                    ?.fileName?.endsWith(".litertlm", ignoreCase = true) == true
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OLLAMA) -> customProviderConfig.ollamaImagesEnabled
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE) -> customProviderConfig.openAiCompatibleImagesEnabled
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_CEREBRAS) ->
                CustomProviderCapabilities.cerebrasSupportsImages(selectedModelID.removePrefix(com.echoflow.data.CustomProviderConfig.PREFIX_CEREBRAS))
            else -> true
        }
    }
    val imageAttachAvailable = imageAttachAllowed && !deepResearchActive && !dataAgentActive
    val selectedModelIsOpenRouter = remember(selectedModelID) {
        !selectedModelID.startsWith("local/") && !selectedModelID.startsWith("custom/")
    }
    val selectedModelIsDirectCloudPdfCapable = remember(selectedModelID) {
        selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OPENAI) ||
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_CLAUDE) ||
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_GEMINI) ||
            (
                selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_CEREBRAS) &&
                    CustomProviderCapabilities.cerebrasSupportsPdfs(selectedModelID.removePrefix(com.echoflow.data.CustomProviderConfig.PREFIX_CEREBRAS))
                )
    }
    val selectedModelIsCustomPdfCapable = remember(selectedModelID, customProviderConfig) {
        selectedModelIsDirectCloudPdfCapable ||
            (selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OLLAMA) && customProviderConfig.ollamaPdfsEnabled) ||
            (selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE) && customProviderConfig.openAiCompatiblePdfsEnabled)
    }
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
        selectedModelIsCustomPdfCapable,
    ) {
        when {
            dataAgentActive -> false
            deepResearchActive -> deepResearchUsesOpenRouter
            echoFusionActive -> true
            echoAdviserActive -> selectedModelIsOpenRouter
            echoAgentActive -> selectedModelIsOpenRouter
            else -> selectedModelIsOpenRouter || selectedModelIsCustomPdfCapable
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

    val activeModelList = remember(customModelsList, customProviderModels) {
        val list = mutableListOf(DEFAULT_MODEL)
        customModelsList.forEach { custom -> if (list.none { it.first == custom.id }) list.add(custom.id to custom.name) }
        customProviderModels.filter { !it.isLocalLike }.forEach { model ->
            if (list.none { it.first == model.id }) list.add(model.id to "${model.group}: ${model.name}")
        }
        list
    }
    val openRouterOnlyModelList = remember(customModelsList) {
        val list = mutableListOf(DEFAULT_MODEL)
        customModelsList.forEach { custom -> if (list.none { it.first == custom.id }) list.add(custom.id to custom.name) }
        list
    }
    val localModelEntries = remember(localModelsList, localModelsEnabled, customProviderModels) {
        val entries = mutableListOf<Pair<String, String>>()
        if (localModelsEnabled) entries.addAll(localModelsList.map { it.id to it.name })
        customProviderModels.filter { it.isLocalLike }.forEach { model ->
            entries.add(model.id to "${model.group}: ${model.name}")
        }
        entries
    }
    val modelShortName = activeModelList.firstOrNull { it.first == selectedModelID }?.second
        ?: customModelsList.firstOrNull { it.id == selectedModelID }?.name
        ?: customProviderModels.firstOrNull { it.id == selectedModelID }?.let { "${it.group}: ${it.name}" }
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
                        onArtifactOpen = { chatViewModel.openArtifactWorkspace() },
                    )
                }
            }

            // Floating, transparent top bar (chat scrolls behind it).
            ChatTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                modelName = when {
                    imageGenActive -> imageModelLabel
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
                onReceiveImage = { uri -> chatViewModel.setPendingPastedImage(uri) },
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
                echoAdviserAvailable = echoAdviserEnabled,
                echoFusionAvailable = echoFusionEnabled,
                echoAgentAvailable = echoAgentEnabled,
                browserFlowActive = browserFlowActive,
                browserFlowAvailable = browserFlowAvailable,
                onToggleBrowserFlow = { chatViewModel.toggleBrowserFlow() },
                artifactActive = artifactActive,
                onToggleArtifact = { chatViewModel.toggleArtifact() },
                imageGenActive = imageGenActive,
                onToggleImageGen = { chatViewModel.toggleImageGen() },
                browserSession = browserSession,
                browserSteps = browserSteps,
                onBrowserOpen = { browserSession?.let { chatViewModel.openBrowserWorkspace(it.chatId) } },
                onBrowserFinish = { browserSession?.let { chatViewModel.browserFinish(it.id) } },
                onBrowserStop = { browserSession?.let { chatViewModel.browserStop(it.id) } },
                onBrowserPick = { url -> browserSession?.let { chatViewModel.browserResolveCandidate(it.id, url) } },
                onBrowserConfirmDomain = { browserSession?.let { chatViewModel.browserConfirmDomain(it.id) } },
                onBrowserConfirmSend = { browserSession?.let { chatViewModel.browserConfirmSend(it.id) } },
                onBrowserCancel = { browserSession?.let { chatViewModel.browserCancelPending(it.id) } },
                researchInProgress = researchRun != null,
                researchEngineLabel = drEngineLabel,
                dataAgentLabel = dataAgentLabel,
                showEffortPill = deepResearchActive && drModelId == "exa-agent",
                exaEffort = exaEffort,
                onSelectEffort = { settingsViewModel.saveDeepResearchExaEffort(it) },
                blockedReason = when {
                    browserBusy -> "Browser is working — please wait…"
                    researchRun != null -> "A run is in progress — see the card above"
                    localSendBlocked -> "On-device model is busy in another chat"
                    else -> null
                },
                onSend = { val t = textInput; textInput = ""; chatViewModel.sendMessage(t) },
                onStop = { chatViewModel.stopStreaming() },
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

            // One live browser session app-wide: starting a second is blocked with a choice.
            browserStartConflict?.let { conflict ->
                AlertDialog(
                    onDismissRequest = { chatViewModel.dismissBrowserConflict() },
                    title = { Text("A browser session is already active") },
                    text = {
                        Text(
                            "Browser Flow is running in \"${conflict.activeSession.goal.take(40)}\". " +
                                "Finish it before starting another, or return to it.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { chatViewModel.browserReturnToActive() }) { Text("Return to browser") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    textInput = ""
                                    chatViewModel.browserFinishActiveThenStart()
                                },
                                enabled = conflict.pendingPrompt.isNotEmpty(),
                            ) { Text("Finish & start new") }
                            TextButton(onClick = { chatViewModel.dismissBrowserConflict() }) { Text("Cancel") }
                        }
                    },
                )
            }
        }
    }

    if (showModelMenu) {
        if (imageGenActive) {
            ModelPickerSheet(
                models = imageModelEntries,
                localModels = localImageEntries,
                selectedId = selectedImageModelId,
                onSelect = { id ->
                    // Selecting a model also selects its engine; the normal chat LLM
                    // selection is untouched either way.
                    if (id.startsWith("local-image/")) settingsViewModel.selectLocalImageModel(id)
                    else settingsViewModel.selectCloudImageModel(id)
                    showModelMenu = false
                },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (echoFusionActive) {
            FusionPickerSheet(
                panels = fusionPanels,
                selectedId = echoFusionPanelId,
                onSelect = { settingsViewModel.saveEchoFusionPanel(it); showModelMenu = false },
                onManage = { showModelMenu = false; onSettingsClicked() },
                onDismiss = { showModelMenu = false },
            )
        } else if (echoAdviserActive) {
            AdvisorPickerSheet(
                models = openRouterOnlyModelList,
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
                models = openRouterOnlyModelList,
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
