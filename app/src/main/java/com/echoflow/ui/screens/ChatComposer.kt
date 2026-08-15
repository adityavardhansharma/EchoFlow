
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.echoflow.ui.rememberActionHaptics
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ArtifactCard
import com.echoflow.ui.components.CapabilityChip
import com.echoflow.ui.components.ContextChipRow
import com.echoflow.ui.components.ModelPill
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



@Composable
internal fun InputToolbar(
    text: String,
    onText: (String) -> Unit,
    pendingUri: String?,
    pendingMime: String?,
    pendingName: String?,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    onAttachPdf: () -> Unit,
    onReceiveImage: (Uri) -> Unit,
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
    onToggleDeepResearch: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleDataAgent: () -> Unit,
    onToggleEchoAdviser: () -> Unit,
    onToggleEchoFusion: () -> Unit,
    onToggleEchoAgent: () -> Unit,
    echoAdviserAvailable: Boolean,
    echoFusionAvailable: Boolean,
    echoAgentAvailable: Boolean,
    browserFlowActive: Boolean,
    browserFlowAvailable: Boolean,
    onToggleBrowserFlow: () -> Unit,
    artifactActive: Boolean,
    onToggleArtifact: () -> Unit,
    modelId: String,
    modelLabel: String,
    onOpenModelPicker: () -> Unit,
    browserSession: com.echoflow.data.BrowserSession?,
    browserSteps: List<com.echoflow.data.BrowserStep>,
    onBrowserOpen: () -> Unit,
    onBrowserFinish: () -> Unit,
    onBrowserStop: () -> Unit,
    onBrowserPick: (String) -> Unit,
    onBrowserConfirmDomain: () -> Unit,
    onBrowserConfirmSend: () -> Unit,
    onBrowserCancel: () -> Unit,
    researchInProgress: Boolean,
    researchEngineLabel: String?,
    dataAgentLabel: String,
    showEffortPill: Boolean,
    exaEffort: String,
    onSelectEffort: (String) -> Unit,
    blockedReason: String? = null,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sttAvailable: Boolean = false,
    voicePhase: VoicePhase = VoicePhase.Idle,
    voiceAmplitude: Float = 0f,
    onMicTap: () -> Unit = {},
    onCancelTranscribe: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val imageContentReceiver = remember(imageAttachEnabled, onReceiveImage) {
        object : ReceiveContentListener {
            override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
                if (!imageAttachEnabled || !transferableContent.hasMediaType(MediaType.Image)) {
                    return transferableContent
                }
                return transferableContent.consume { item ->
                    item.uri?.let { uri ->
                        onReceiveImage(uri)
                        true
                    } == true
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = Spacing.base, vertical = Spacing.m),
    ) {
        // Persistent Browser Flow card — the in-chat anchor/control surface for the live session.
        browserSession?.let { session ->
            com.echoflow.ui.components.BrowserSessionCard(
                session = session,
                steps = browserSteps,
                onOpen = onBrowserOpen,
                onFinish = onBrowserFinish,
                onStop = onBrowserStop,
                onPickCandidate = onBrowserPick,
                onConfirmDomain = onBrowserConfirmDomain,
                onConfirmSend = onBrowserConfirmSend,
                onCancel = onBrowserCancel,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
        }

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

        // What this next message will be sent with: the model first, then whatever
        // capabilities are on. Always visible — the model is never implicit.
        //
        // Modes that take over the model pill (Adviser, Fusion, Agent, Deep Research, Data
        // Agent — see `contextModelLabel` in ChatSurface) deliberately get *no* chip here: the
        // pill already names them, and a second chip saying the same thing read as a duplicate.
        // Only capabilities that ride alongside a real model (Web search, Browser Flow, Artifact)
        // still earn a chip, because the pill keeps showing your actual model and the chip is the
        // one signal they are on. Turning these off happens back in the "+" menu.
        // The dictation mic lives right beside the model picker — its one fixed home, never in
        // the composer oval's corners. The capability chips follow to its right. It sits near the
        // leading edge, so it stays put even as chips fill and scroll the row.
        ContextChipRow(Modifier.padding(start = Spacing.s, bottom = Spacing.s)) {
            ModelPill(modelId = modelId, label = modelLabel, onClick = onOpenModelPicker)
            if (sttAvailable) {
                ModelRowMic(phase = voicePhase, onClick = onMicTap)
            }
            // Deep Research owns the pill; its only *extra* control is the depth/cost dial,
            // a real setting rather than a redundant label, so it stays — beside the pill.
            if (deepResearchActive && showEffortPill) {
                EffortPill(effort = exaEffort, onSelect = onSelectEffort)
            }
            if (webSearchChipOn) {
                CapabilityChip(Icons.Default.TravelExplore, "Web search", onRemove = onToggleWebSearch)
            }
            if (browserFlowActive) {
                CapabilityChip(Icons.Default.Language, "Browser Flow", onRemove = onToggleBrowserFlow)
            }
            if (artifactActive) {
                CapabilityChip(Icons.Default.AutoAwesome, "Artifact", onRemove = onToggleArtifact)
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
            // Tint the composer while the chat is captured by a live browser session.
            color = if (browserSession != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        echoAdviserAvailable = echoAdviserAvailable,
                        echoFusionAvailable = echoFusionAvailable,
                        echoAgentAvailable = echoAgentAvailable,
                        browserFlowOn = browserFlowActive,
                        browserFlowAvailable = browserFlowAvailable,
                        artifactOn = artifactActive,
                        onImage = { plusMenuOpen = false; onAttach() },
                        onFiles = { plusMenuOpen = false; onAttachPdf() },
                        onToggleWebSearch = { plusMenuOpen = false; onToggleWebSearch() },
                        onToggleDeepResearch = { plusMenuOpen = false; onToggleDeepResearch() },
                        onToggleDataAgent = { plusMenuOpen = false; onToggleDataAgent() },
                        onToggleEchoAdviser = { plusMenuOpen = false; onToggleEchoAdviser() },
                        onToggleEchoFusion = { plusMenuOpen = false; onToggleEchoFusion() },
                        onToggleEchoAgent = { plusMenuOpen = false; onToggleEchoAgent() },
                        onToggleBrowserFlow = { plusMenuOpen = false; onToggleBrowserFlow() },
                        onToggleArtifact = { plusMenuOpen = false; onToggleArtifact() },
                    )
                }

                // While dictating, the whole text area becomes a live waveform (the pill collapses
                // to a single comfortable line); the text is untouched underneath and returns when
                // the transcript lands. On stop the bars freeze while transcription is in flight.
                if (voicePhase == VoicePhase.Idle) {
                    TextField(
                        value = text,
                        onValueChange = onText,
                        placeholder = {
                            Text(
                                when {
                                    browserSession != null -> "Command the browser…"
                                    browserFlowActive -> "Open a site & say what to do…"
                                    dataAgentActive -> "Describe the data to extract…"
                                    deepResearchActive -> "Research a topic…"
                                    artifactActive -> "Describe an artifact to build…"
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
                        modifier = Modifier
                            .weight(1f)
                            .contentReceiver(imageContentReceiver)
                            .testTag("chat_input_field"),
                    )
                } else {
                    VoiceWaveform(
                        amplitude = voiceAmplitude,
                        active = voicePhase == VoicePhase.Recording,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = Spacing.m),
                    )
                }

                val researchMode = deepResearchActive || dataAgentActive
                // A live research run owns the same Stop control as a streaming reply — not a
                // separate Cancel chip on the timeline card.
                val showStop = isStreaming || researchInProgress
                val transcribing = voicePhase == VoicePhase.Transcribing
                val hasText = text.trim().isNotEmpty()
                val hasContent = hasText || (pendingUri != null && !researchMode)
                // Inert while capturing/transcribing — the mic owns that phase, not send.
                val canSend = hasContent && !showStop && blockedReason == null && voicePhase == VoicePhase.Idle
                SendButton(
                    enabled = canSend,
                    isStreaming = showStop,
                    research = researchMode,
                    transcribing = transcribing,
                    onStop = onStop,
                    onCancelTranscribe = onCancelTranscribe,
                ) {
                    if (canSend) onSend()
                }
            }
        }
    }
}

/** The unified "+" menu: add context (image) or turn on a capability (search / research). */
@Composable
internal fun PlusMenu(
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
    echoAdviserAvailable: Boolean,
    echoFusionAvailable: Boolean,
    echoAgentAvailable: Boolean,
    browserFlowOn: Boolean,
    browserFlowAvailable: Boolean,
    artifactOn: Boolean,
    onImage: () -> Unit,
    onFiles: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleDeepResearch: () -> Unit,
    onToggleDataAgent: () -> Unit,
    onToggleEchoAdviser: () -> Unit,
    onToggleEchoFusion: () -> Unit,
    onToggleEchoAgent: () -> Unit,
    onToggleBrowserFlow: () -> Unit,
    onToggleArtifact: () -> Unit,
) {
    // Bespoke popup anchored above the "+". One surface that blooms out of the anchor corner: a
    // fast scale + fade, no slide. The drop shadow is drawn by the *same* graphics layer as the
    // animation (see below) so it stays rounded and composites cleanly with the fade.
    //
    // Placement (above vs below the "+") is only known after the first popup measure pass, so motion
    // is gated on that: the popup mounts invisibly, [PlusMenuPositionProvider] resolves flip, then
    // the enter animation starts with the transform origin on the correct (anchor) corner.
    var flippedDown by remember { mutableStateOf<Boolean?>(null) }
    var pivotFractionX by remember { mutableStateOf<Float?>(null) }
    val positionProvider = remember {
        PlusMenuPositionProvider(
            gapPx = 14,
            onFlipDown = { flippedDown = it },
            onPivotFractionX = { pivotFractionX = it },
        )
    }
    val motion = remember { MutableTransitionState(false) }
    if (expanded && flippedDown != null) {
        motion.targetState = true
    } else if (!expanded) {
        motion.targetState = false
    }
    LaunchedEffect(motion.isIdle, motion.currentState, expanded) {
        if (motion.isIdle && !motion.currentState && !expanded) {
            flippedDown = null
            pivotFractionX = null
        }
    }
    val keepPopup = expanded || motion.currentState || motion.targetState || !motion.isIdle
    if (keepPopup) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            val transition = updateTransition(motion, label = "plus-menu")
            val reducedMotion = rememberReducedMotion()
            // Near-instant: a snappy pop in, a quicker fade out. Simple, the way a "+" menu should be.
            val enterMs = if (reducedMotion) 0 else 115
            val exitMs = if (reducedMotion) 0 else 80

            // Grow out of the "+": scale + fade with the transform origin on the button. X is the
            // anchor's resolved position inside the popup (from the position provider, so on-screen
            // clamping can't pull the origin off the button); Y is the edge nearest the anchor —
            // bottom when the menu sits above, top when it flips below.
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val transformOrigin = TransformOrigin(
                pivotFractionX = pivotFractionX ?: if (isRtl) 1f else 0f,
                pivotFractionY = if (flippedDown == true) 0f else 1f,
            )
            val scale by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) tween(enterMs, easing = FastOutSlowInEasing)
                    else tween(exitMs, easing = FastOutLinearInEasing)
                },
                label = "scale",
            ) { if (it) 1f else 0.9f }
            val alpha by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) tween(enterMs, easing = FastOutSlowInEasing)
                    else tween(exitMs, easing = FastOutLinearInEasing)
                },
                label = "alpha",
            ) { if (it) 1f else 0f }

            // Never taller than the window (compact/landscape/split-screen): cap the height and let
            // the rows scroll, so lower items like Echo Labs can't be pushed off-screen.
            val maxMenuHeight = (LocalConfiguration.current.screenHeightDp - 24).dp
            val menuShape = RoundedCornerShape(22.dp)
            // Soft, rounded drop shadow drawn by *this* layer (not Surface.shadowElevation) so it
            // follows [menuShape] instead of clipping to a hard rectangle under the fade layer, and
            // so scale/alpha/shadow all composite as one clean surface. Enough presence to lift the
            // menu clear of the input box below it — elevated, not heavy.
            val spotShadow = Color.Black.copy(alpha = 0.44f)
            val ambientShadow = Color.Black.copy(alpha = 0.22f)
            Surface(
                shape = menuShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .testTag("plus_menu_surface")
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                        this.transformOrigin = transformOrigin
                        this.shadowElevation = 16.dp.toPx()
                        this.shape = menuShape
                        this.clip = false
                        this.spotShadowColor = spotShadow
                        this.ambientShadowColor = ambientShadow
                    },
            ) {
                Column(
                    Modifier
                        .heightIn(max = maxMenuHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                ) {
                    if (showImage || showFiles) {
                        MenuSectionLabel("Attach")
                        if (showImage) {
                            PlusMenuRow(Icons.Outlined.AddPhotoAlternate, "Image", on = null, onClick = onImage)
                        }
                        if (showFiles) {
                            PlusMenuRow(Icons.Default.PictureAsPdf, "Files", on = null, onClick = onFiles)
                        }
                        PlusMenuDivider()
                    }
                    MenuSectionLabel("Capabilities")
                    PlusMenuRow(Icons.Default.TravelExplore, "Web search", on = webSearchOn, onClick = onToggleWebSearch)
                    PlusMenuRow(Icons.Default.Science, "Deep Research", on = deepResearchOn, onClick = onToggleDeepResearch)
                    PlusMenuRow(Icons.Default.AutoAwesome, "Artifact", on = artifactOn, onClick = onToggleArtifact)
                    if (dataAgentAvailable) {
                        PlusMenuRow(Icons.Default.Dataset, "Data Agent", on = dataAgentOn, onClick = onToggleDataAgent)
                    }
                    if (browserFlowAvailable) {
                        PlusMenuRow(Icons.Default.Language, "Browser Flow", on = browserFlowOn, onClick = onToggleBrowserFlow)
                    }
                    if (echoAdviserAvailable || echoFusionAvailable || echoAgentAvailable) {
                        PlusMenuDivider()
                        MenuSectionLabel("Echo Labs")
                        if (echoAdviserAvailable) {
                            PlusMenuRow(Icons.Default.Psychology, "Echo Adviser", on = echoAdviserOn, onClick = onToggleEchoAdviser)
                        }
                        if (echoFusionAvailable) {
                            PlusMenuRow(Icons.Default.AccountTree, "Echo Fusion", on = echoFusionOn, onClick = onToggleEchoFusion)
                        }
                        if (echoAgentAvailable) {
                            PlusMenuRow(Icons.Default.Hub, "Echo Agents", on = echoAgentOn, onClick = onToggleEchoAgent)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row of the "+" popup. [on] = null marks a one-shot action (Attach); a non-null value marks a
 * capability toggle, whose leading chip lifts to the accent and gains a trailing check when active.
 */
@Composable
private fun PlusMenuRow(
    icon: ImageVector,
    label: String,
    on: Boolean?,
    onClick: () -> Unit,
) {
    val active = on == true

    // Attach chips are a neutral tonal; an active capability lifts to the accent container.
    val chipColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        on == null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val chipContent = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        on == null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp))
                .background(chipColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(17.dp), tint = chipContent)
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(Icons.Default.Check, "On", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Hairline divider between "+" popup sections. */
@Composable
private fun PlusMenuDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Positions the "+" popup just above the anchor (the "+" button), left edges aligned, and flips it
 * below only if there isn't room above — which at the foot of the screen there almost always is.
 */
internal class PlusMenuPositionProvider(
    private val gapPx: Int,
    private val onFlipDown: (Boolean) -> Unit,
    private val onPivotFractionX: (Float) -> Unit = {},
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Align the popup's leading edge to the anchor: left edge in LTR, right edge in RTL (where
        // the "+" sits on the right), then clamp on-screen.
        val maxX = (windowSize.width - popupContentSize.width - gapPx).coerceAtLeast(gapPx)
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left.coerceIn(gapPx, maxX)
        } else {
            (anchorBounds.right - popupContentSize.width).coerceIn(gapPx, maxX)
        }
        // Where the "+" sits inside the *resolved* (post-clamp) popup box, as a 0..1 fraction. The
        // enter/exit scale grows from this point so it always blooms out of the button — including
        // when horizontal clamping shifts the popup off the anchor's leading edge.
        val anchorLeadingX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right
        val pivotFractionX = if (popupContentSize.width <= 0) {
            if (layoutDirection == LayoutDirection.Ltr) 0f else 1f
        } else {
            ((anchorLeadingX - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        }
        onPivotFractionX(pivotFractionX)
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val flipDown = above < gapPx
        onFlipDown(flipDown)
        val y = if (flipDown) {
            (anchorBounds.bottom + gapPx)
                .coerceIn(gapPx, (windowSize.height - popupContentSize.height - gapPx).coerceAtLeast(gapPx))
        } else {
            above
        }
        return IntOffset(x, y)
    }
}


@Composable
internal fun SendButton(
    enabled: Boolean,
    isStreaming: Boolean,
    research: Boolean = false,
    transcribing: Boolean = false,
    onStop: () -> Unit = {},
    onCancelTranscribe: () -> Unit = {},
    onClick: () -> Unit,
) {
    // Send and stop are the only two actions that change what the app is *doing*, so they're the
    // two that earn a real haptic — a mirrored rising/falling pair, see [rememberActionHaptics].
    val haptics = rememberActionHaptics()
    if (transcribing) {
        // The "working" signal for dictation lives here, on the send slot: the same spinner
        // language as streaming-Stop, but it means "transcribing…". Tap to cancel and keep
        // whatever text was already in the box.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClickLabel = "Cancel transcription") { onCancelTranscribe() }
                .semantics { contentDescription = "Transcribing. Tap to cancel." },
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
    } else if (isStreaming) {
        // Tappable Stop that stays in visual sync with the "Thinking…" row: the same expressive
        // LoadingIndicator keeps spinning so the reply still feels live, with a Stop glyph centered
        // on top so it reads clearly as "tap to stop". Tapping cancels the in-flight reply (cloud
        // stream or on-device generation).
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable {
                    haptics.stop()
                    onStop()
                },
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Icon(
                Icons.Default.Stop,
                "Stop generating",
                Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        // The hero action gets the boldest shape — a "Sunny" that morphs to a rounder cookie on
        // press with an expressive (bouncy) spring. In research mode it starts the investigation.
        ShapedIconButton(
            onClick = {
                haptics.send()
                onClick()
            },
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
internal fun ShapedIconButton(
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

/** A quiet header inside the "+" menu, so its two halves read as different kinds of choice. */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.m, top = Spacing.s, bottom = Spacing.xs),
    )
}
