
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import com.echoflow.data.DefaultChatModels
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
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.EffortPill
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.components.SubagentCard
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch


/**
 * Doc types the local-model "Files" picker offers — everything the bundled anydoc parser reads.
 * Extra/unknown picks that anydoc can't handle simply fail their chip (retry/remove), so this list
 * only needs to be broad enough to surface the common formats in the system picker.
 */
private val DOC_ATTACH_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.oasis.opendocument.presentation",
    "application/rtf",
    "application/epub+zip",
    "text/plain",
    "text/csv",
    "text/markdown",
)

/**
 * The Chat surface: turn-taking conversation and every capability that makes an answer
 * better — search, research, artifacts, the browser, the Echo modes.
 *
 * Renders its own body and composer inside the router's Box. The top bar is not here: it is
 * chrome shared with Imagine and must not fade when the surfaces cross-fade.
 */
@Composable
internal fun ChatSurface(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    topBarInset: Dp,
) {
    val clipboard = LocalClipboardManager.current

    val messages by chatViewModel.currentMessages.collectAsState()
    val currentThreadProject by chatViewModel.currentThreadProject.collectAsState()
    val isStreaming by chatViewModel.isStreaming.collectAsState()
    val activeSegments by chatViewModel.activeSegments.collectAsState()
    val statusNote by chatViewModel.statusNote.collectAsState()
    val progressLoading by chatViewModel.apiProgressLoading.collectAsState()
    val localModelLoading by chatViewModel.localModelLoading.collectAsState()
    val anyLocalStreamActive by chatViewModel.anyLocalStreamActive.collectAsState()

    val pendingAttachments by chatViewModel.pendingAttachments.collectAsState()
    val selectedModelID by settingsViewModel.selectedModel.collectAsState()
    val customProviderConfig by settingsViewModel.customProviderConfig.collectAsState()
    val customModelsList by settingsViewModel.customModels.collectAsState()
    val customProviderModels by settingsViewModel.customProviderModels.collectAsState()
    val localModelsList by settingsViewModel.localModels.collectAsState()
    val localModelsEnabled by settingsViewModel.localModelsEnabled.collectAsState()
    val currentThreadId by chatViewModel.currentChatThreadId.collectAsState()
    val editingUserMessageId by chatViewModel.editingUserMessageId.collectAsState()
    val replyVersionPick by chatViewModel.replyVersionPick.collectAsState()
    val lastUserMessageId = remember(messages) { messages.lastOrNull { it.role == "user" }?.id }
    val reducedMotion = rememberReducedMotion()

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

    // Speech to text: always OpenRouter with the Cloud-models key, independent of the chat model.
    // Mic only appears when Cloud mode is selected and that key is present (On-device is coming soon).
    val openRouterKey by settingsViewModel.apiKey.collectAsState()
    val sttCloudModelId by settingsViewModel.sttCloudModel.collectAsState()
    val sttMode by settingsViewModel.sttMode.collectAsState()
    val voice = rememberVoiceInputController()
    val voiceAmplitude by voice.amplitude.collectAsState()
    val sttAvailable = openRouterKey.isNotBlank() && sttMode == com.echoflow.data.SttMode.Cloud
    val sttContext = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voice.startRecording()
        } else {
            Toast.makeText(
                sttContext,
                "Microphone access is needed for dictation.",
                Toast.LENGTH_SHORT,
            ).show()
            // When the OS will no longer re-prompt, send the user to app settings.
            val activity = sttContext as? Activity
            if (activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            ) {
                runCatching {
                    sttContext.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", sttContext.packageName, null)
                        },
                    )
                }
            }
        }
    }
    LaunchedEffect(voice.error) {
        voice.error?.let {
            Toast.makeText(sttContext, it, Toast.LENGTH_SHORT).show()
            voice.clearError()
        }
    }

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
            selectedModelID.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_XAI) ->
                CustomProviderCapabilities.xAiSupportsImages(selectedModelID.removePrefix(com.echoflow.data.CustomProviderConfig.PREFIX_XAI))
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
    // On-device models parse doc files locally (PDF/Word/Excel/…). Fusion/Adviser/Agent/Browser
    // do not consume that Markdown, so they stay on the single raw-PDF path (or none).
    val isLocalModel = remember(selectedModelID) { selectedModelID.startsWith("local/") }
    val filesAttachAllowed = isLocalModel &&
        !deepResearchActive &&
        !dataAgentActive &&
        !echoFusionActive &&
        !echoAdviserActive &&
        !echoAgentActive &&
        !browserFlowActive
    val fileMenuEnabled = pdfAttachAllowed || filesAttachAllowed

    LaunchedEffect(pendingAttachments, imageAttachAvailable, pdfAttachAllowed, filesAttachAllowed) {
        chatViewModel.reconcilePendingAttachments(
            imageAllowed = imageAttachAvailable,
            pdfAllowed = pdfAttachAllowed,
            localFilesAllowed = filesAttachAllowed,
        )
    }

    val activeModelList = remember(customModelsList, customProviderModels) {
        val list = DefaultChatModels.BUILT_IN.toMutableList()
        customModelsList.forEach { custom -> if (list.none { it.first == custom.id }) list.add(custom.id to custom.name) }
        customProviderModels.filter { !it.isLocalLike }.forEach { model ->
            if (list.none { it.first == model.id }) list.add(model.id to "${model.group}: ${model.name}")
        }
        list
    }
    val openRouterOnlyModelList = remember(customModelsList) {
        val list = DefaultChatModels.BUILT_IN.toMutableList()
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

    // The pill shows — and the picker changes — whichever selector the active capability
    // actually uses. One derivation feeds both, so they can never disagree about what a tap
    // is going to open.
    val contextModelId = when {
        echoFusionActive -> activePanel?.models?.firstOrNull().orEmpty()
        dataAgentActive -> dataAgentEngineId
        deepResearchActive -> drModelId
        else -> selectedModelID
    }
    val contextModelLabel = when {
        echoFusionActive -> activePanel?.name ?: "Choose panel"
        echoAdviserActive -> activeAdvisor?.let { "$modelShortName · ${it.name}" } ?: "Pick an advisor"
        echoAgentActive -> activeAgent?.let { "$modelShortName · ${it.name}" } ?: "Pick an Echo Agent"
        dataAgentActive -> dataAgentLabel
        deepResearchActive -> drEngineLabel
        else -> modelShortName
    }
    val localSendBlocked = selectedModelID.startsWith("local/") && anyLocalStreamActive && !isStreaming

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) chatViewModel.setPendingAttachment(uri) },
    )
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) chatViewModel.setPendingAttachment(uri, "application/pdf") },
    )
    // Local-model doc path: pick several files at once; each is parsed on-device (anydoc).
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> if (uris.isNotEmpty()) chatViewModel.addPendingDocs(uris) },
    )

    // Measure the floating input's height so the message list always pads exactly enough to clear
    // it — including when the keyboard pushes the input up (its measured height grows with the inset).
    val density = LocalDensity.current
    var inputHeightPx by remember { mutableStateOf(0) }
    val messageBottomInset = if (inputHeightPx > 0) with(density) { inputHeightPx.toDp() } else 96.dp
    Box(Modifier.fillMaxSize()) {
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
                    onArtifactOpen = { artifactId, version ->
                        chatViewModel.openArtifactWorkspace(artifactId, version)
                    },
                    onResearchOpen = chatViewModel::openResearchWorkspace,
                    onResearchRetry = chatViewModel::retryResearch,
                    observeResearchRun = chatViewModel::observeResearchRun,
                    observeVideo = chatViewModel::observeVideo,
                    observeArtifactVersions = chatViewModel::observeArtifactVersions,
                    lastUserMessageId = lastUserMessageId,
                    onEditUserMessage = { id ->
                        chatViewModel.beginEditUserMessage(id)?.let { textInput = it }
                    },
                    replyVersionIndexFor = { messageId, total ->
                        replyVersionPick[messageId]?.coerceIn(0, (total - 1).coerceAtLeast(0))
                            ?: (total - 1).coerceAtLeast(0)
                    },
                    onReplyVersionChange = chatViewModel::selectReplyVersion,
                    canEditMessages = !isStreaming &&
                        !progressLoading &&
                        researchRun == null &&
                        editingUserMessageId == null,
                )
            }
        }


        // Floating composer stack (optional edit banner + input). Measure the whole stack so
        // the message list clears both the banner and the toolbar.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { inputHeightPx = it.height },
        ) {
            AnimatedVisibility(
                visible = editingUserMessageId != null,
                enter = if (reducedMotion) {
                    fadeIn(tween(120))
                } else {
                    fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Bottom)
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(90))
                } else {
                    fadeOut(tween(120)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
                },
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.base, vertical = Spacing.xs),
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.base, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Editing prompt",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            chatViewModel.cancelEditMessage()
                            textInput = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        InputToolbar(
            modifier = Modifier,
            text = textInput,
            onText = { textInput = it },
            attachments = pendingAttachments,
            attachmentLimit = com.echoflow.ui.ChatViewModel.MAX_MESSAGE_ATTACHMENTS,
            onRemoveAttachment = { id -> chatViewModel.removePendingAttachment(id) },
            onRetryAttachment = { id -> chatViewModel.retryPendingAttachment(id) },
            onAttach = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAttachPdf = {
                if (filesAttachAllowed) docPicker.launch(DOC_ATTACH_MIME_TYPES)
                else pdfPicker.launch(arrayOf("application/pdf"))
            },
            onReceiveImage = { uri -> chatViewModel.setPendingPastedImage(uri) },
            imageAttachEnabled = imageAttachAvailable,
            pdfAttachEnabled = fileMenuEnabled &&
                (!filesAttachAllowed || pendingAttachments.size < com.echoflow.ui.ChatViewModel.MAX_MESSAGE_ATTACHMENTS),
            requireExtractedDocs = filesAttachAllowed,
            isStreaming = isStreaming,
            deepResearchActive = deepResearchActive,
            webSearchChipOn = webSearchChipOn,
            dataAgentActive = dataAgentActive,
            dataAgentAvailable = dataAgentAvailable,
            echoAdviserActive = echoAdviserActive,
            echoFusionActive = echoFusionActive,
            echoAgentActive = echoAgentActive,
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
            modelId = contextModelId,
            modelLabel = contextModelLabel,
            onOpenModelPicker = { showModelMenu = true },
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
                localSendBlocked -> "On-device model is busy in another chat"
                else -> null
            },
            onSend = { val t = textInput; textInput = ""; chatViewModel.sendMessage(t) },
            onStop = {
                // One Stop for both chat streams and Deep Research / Data Agent runs.
                chatViewModel.stopStreaming()
                if (researchRun != null) chatViewModel.cancelResearch()
            },
            sttAvailable = sttAvailable,
            voicePhase = voice.phase,
            voiceAmplitude = voiceAmplitude,
            onMicTap = {
                when (voice.phase) {
                    VoicePhase.Idle -> {
                        val granted = ContextCompat.checkSelfPermission(
                            sttContext, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) voice.startRecording()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    VoicePhase.Recording -> voice.stopAndTranscribe(openRouterKey, sttCloudModelId) { transcript ->
                        // Append at the end of whatever is already in the box.
                        textInput = if (textInput.isBlank()) transcript
                        else textInput.trimEnd() + " " + transcript
                    }
                    VoicePhase.Transcribing -> {}
                }
            },
            onCancelTranscribe = { voice.cancel() },
            projectName = currentThreadProject?.name,
            projectColorIndex = currentThreadProject?.colorIndex ?: 0,
            onOpenProject = { currentThreadProject?.let { chatViewModel.openProjectFromChat(it.id) } },
        )
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
