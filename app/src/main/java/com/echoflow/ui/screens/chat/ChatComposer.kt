
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.CapabilityChip
import com.echoflow.ui.components.ContextChipRow
import com.echoflow.ui.components.EffortPill
import com.echoflow.ui.components.ModelPill
import com.echoflow.ui.components.ProjectPill
import com.echoflow.ui.theme.Spacing

@Composable
internal fun InputToolbar(
    text: String,
    onText: (String) -> Unit,
    attachments: List<com.echoflow.ui.PendingAttachment>,
    attachmentLimit: Int,
    onRemoveAttachment: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onAttach: () -> Unit,
    onAttachPdf: () -> Unit,
    onReceiveImage: (Uri) -> Unit,
    imageAttachEnabled: Boolean,
    pdfAttachEnabled: Boolean,
    requireExtractedDocs: Boolean = false,
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
    projectName: String? = null,
    projectColorIndex: Int = 0,
    onOpenProject: () -> Unit = {},
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

        AnimatedVisibility(visible = attachments.isNotEmpty()) {
            AttachmentChips(
                attachments = attachments,
                onRemove = onRemoveAttachment,
                onRetry = onRetryAttachment,
                modifier = Modifier.padding(bottom = Spacing.s),
            )
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
            // The project this chat belongs to, right beside the mic — its standing context, one tap
            // from its home. Present only for a project chat.
            if (projectName != null) {
                ProjectPill(name = projectName, colorIndex = projectColorIndex, onClick = onOpenProject)
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
                val hasContent = hasText || (attachments.isNotEmpty() && !researchMode)
                // Local path: a Ready chip with no Markdown is an unparsed cloud PDF / legacy
                // attachment — hold send until extract finishes (or the user removes it).
                val attachmentsSettled = attachments.all { att ->
                    att.state == com.echoflow.ui.PendingAttachment.State.Ready &&
                        (!requireExtractedDocs || att.isImage || !att.extractedText.isNullOrBlank())
                }
                // Inert while capturing/transcribing — the mic owns that phase, not send.
                val canSend = hasContent && attachmentsSettled && !showStop && blockedReason == null && voicePhase == VoicePhase.Idle
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
