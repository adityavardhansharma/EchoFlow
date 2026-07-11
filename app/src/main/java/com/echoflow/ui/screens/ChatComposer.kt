
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
    advisorChipLabel: String?,
    fusionChipLabel: String?,
    agentChipLabel: String?,
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
    imageGenActive: Boolean,
    onToggleImageGen: () -> Unit,
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

        // Active capability chips — always show what's on so behaviour is never hidden.
        AnimatedVisibility(visible = webSearchChipOn || deepResearchActive || dataAgentActive || echoAdviserActive || echoFusionActive || echoAgentActive || browserFlowActive || artifactActive || imageGenActive) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.padding(start = Spacing.s, bottom = Spacing.s),
            ) {
                if (webSearchChipOn) {
                    CapabilityChip(Icons.Default.TravelExplore, "Web search", onRemove = onToggleWebSearch)
                }
                if (browserFlowActive) {
                    CapabilityChip(Icons.Default.Language, "Browser Flow", onRemove = onToggleBrowserFlow)
                }
                if (artifactActive) {
                    CapabilityChip(Icons.Default.AutoAwesome, "Artifact", onRemove = onToggleArtifact)
                }
                if (imageGenActive) {
                    CapabilityChip(Icons.Default.Brush, "Create image", onRemove = onToggleImageGen)
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
                        imageGenOn = imageGenActive,
                        onToggleImageGen = { plusMenuOpen = false; onToggleImageGen() },
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
                                imageGenActive -> "Describe an image — or a change…"
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

                val researchMode = deepResearchActive || dataAgentActive
                val hasText = text.trim().isNotEmpty()
                val hasContent = hasText || (pendingUri != null && !researchMode)
                val canSend = hasContent && !isStreaming && !researchInProgress && blockedReason == null
                SendButton(enabled = canSend, isStreaming = isStreaming, research = researchMode, onStop = onStop) {
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
    imageGenOn: Boolean,
    onToggleImageGen: () -> Unit,
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
        // Artifacts — build a rendered web page, document or report (model picks the type).
        DropdownMenuItem(
            text = { Text("Artifact") },
            leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
            trailingIcon = { if (artifactOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleArtifact,
        )
        // Create image — generate and conversationally edit images (OpenRouter image models).
        DropdownMenuItem(
            text = { Text("Create image") },
            leadingIcon = { Icon(Icons.Default.Brush, null) },
            trailingIcon = { if (imageGenOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
            onClick = onToggleImageGen,
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
        // Browser Flow — a live, stateful browser controlled through chat (needs a Firecrawl key).
        if (browserFlowAvailable) {
            DropdownMenuItem(
                text = { Text("Browser Flow") },
                leadingIcon = { Icon(Icons.Default.Language, null) },
                trailingIcon = { if (browserFlowOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
                onClick = onToggleBrowserFlow,
            )
        }
        // Echo Labs modes — only shown when enabled in Settings → Echo Labs. If the key or a
        // profile/panel is missing, sending surfaces a "set it up" message.
        if (echoAdviserAvailable || echoFusionAvailable || echoAgentAvailable) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
        }
        if (echoAdviserAvailable) {
            DropdownMenuItem(
                text = { Text("Echo Adviser") },
                leadingIcon = { Icon(Icons.Default.Psychology, null) },
                trailingIcon = { if (echoAdviserOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
                onClick = onToggleEchoAdviser,
            )
        }
        if (echoFusionAvailable) {
            DropdownMenuItem(
                text = { Text("Echo Fusion") },
                leadingIcon = { Icon(Icons.Default.AccountTree, null) },
                trailingIcon = { if (echoFusionOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
                onClick = onToggleEchoFusion,
            )
        }
        if (echoAgentAvailable) {
            DropdownMenuItem(
                text = { Text("Echo Agents") },
                leadingIcon = { Icon(Icons.Default.Hub, null) },
                trailingIcon = { if (echoAgentOn) Icon(Icons.Default.Check, "On", tint = MaterialTheme.colorScheme.primary) },
                onClick = onToggleEchoAgent,
            )
        }
    }
}

@Composable
internal fun SendButton(enabled: Boolean, isStreaming: Boolean, research: Boolean = false, onStop: () -> Unit = {}, onClick: () -> Unit) {
    if (isStreaming) {
        // Tappable Stop that stays in visual sync with the "Thinking…" row: the same expressive
        // LoadingIndicator keeps spinning so the reply still feels live, with a Stop glyph centered
        // on top so it reads clearly as "tap to stop". Tapping cancels the in-flight reply (cloud
        // stream or on-device generation).
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onStop),
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
