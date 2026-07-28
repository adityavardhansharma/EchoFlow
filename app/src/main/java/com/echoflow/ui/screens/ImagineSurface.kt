@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.data.ImagineMedia
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel

/**
 * The Imagine surface: describe something, watch it appear, refine it.
 *
 * Structurally a timeline like Chat — which keeps the entire streaming, segment and
 * persistence spine intact — but presented as a contact sheet rather than a conversation. The
 * media is the subject, the prompt is its caption, and the controls that shape it live in the
 * composer instead of a settings page.
 */
@Composable
internal fun ImagineSurface(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    topBarInset: Dp,
) {
    val messages by chatViewModel.currentMessages.collectAsState()
    val isStreaming by chatViewModel.isStreaming.collectAsState()
    val activeSegments by chatViewModel.activeSegments.collectAsState()
    val progressLoading by chatViewModel.apiProgressLoading.collectAsState()
    val currentThreadId by chatViewModel.currentChatThreadId.collectAsState()
    val pendingUri by chatViewModel.pendingAttachmentUri.collectAsState()
    val pendingName by chatViewModel.pendingAttachmentName.collectAsState()

    val media by settingsViewModel.imagineMedia.collectAsState()
    val imageModels by settingsViewModel.imageModels.collectAsState()
    val videoModels by settingsViewModel.videoModels.collectAsState()
    val imageModelId by settingsViewModel.imageGenModelId.collectAsState()
    val videoModelId by settingsViewModel.videoGenModelId.collectAsState()
    val imageRatio by settingsViewModel.imageAspectRatio.collectAsState()
    val videoRatio by settingsViewModel.videoAspectRatio.collectAsState()
    val videoResolution by settingsViewModel.videoResolution.collectAsState()
    val audioEnabled by settingsViewModel.videoAudioEnabled.collectAsState()
    val videoCapabilities by settingsViewModel.selectedVideoModelCapabilities.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    // Capabilities drive the ratio chip and the picker's cards, so load them as the surface
    // opens rather than waiting for the user to go looking for the picker.
    LaunchedEffect(Unit) { settingsViewModel.loadVideoModelDirectory() }

    val isVideo = media == ImagineMedia.Video
    val modelEntries = remember(media, imageModels, videoModels) {
        val default = if (isVideo) {
            SettingsRepository.DEFAULT_VIDEO_MODEL_ID to SettingsRepository.DEFAULT_VIDEO_MODEL_NAME
        } else {
            SettingsRepository.DEFAULT_IMAGE_MODEL_ID to "Gemini 2.5 Flash Image"
        }
        val added = if (isVideo) videoModels.map { it.id to it.name } else imageModels.map { it.id to it.name }
        listOf(default) + added.filter { it.first != default.first }
    }
    val modelId = if (isVideo) videoModelId else imageModelId
    val modelLabel = modelEntries.firstOrNull { it.first == modelId }?.second ?: modelId
    val ratio = if (isVideo) videoRatio else imageRatio

    // Video framing is a hard contract with the provider; image framing is an instruction in
    // the prompt, so every ratio stays available there.
    val supportedRatios = if (isVideo) videoCapabilities?.aspectRatios else null
    val ratioNote = if (isVideo && !videoCapabilities?.aspectRatios.isNullOrEmpty()) {
        "$modelLabel supports " + videoCapabilities!!.aspectRatios.joinToString(", ")
    } else {
        null
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) chatViewModel.setPendingAttachment(uri) },
    )

    val density = LocalDensity.current
    var composerHeightPx by remember { mutableStateOf(0) }
    val bottomInset = if (composerHeightPx > 0) with(density) { composerHeightPx.toDp() } else 120.dp

    Box(Modifier.fillMaxSize()) {
        if (messages.isEmpty() && !isStreaming && !progressLoading) {
            ImagineEmptyState(
                media = media,
                topInset = topBarInset,
                bottomInset = bottomInset,
                onPrompt = { textInput = it },
            )
        } else {
            key(currentThreadId) {
                ImagineCanvas(
                    messages = messages,
                    isStreaming = isStreaming,
                    segments = activeSegments,
                    progressLoading = progressLoading,
                    topInset = topBarInset,
                    bottomInset = bottomInset,
                    observeVideo = chatViewModel::observeVideo,
                    onUseAsReference = chatViewModel::useMediaAsReference,
                    onRetry = { textInput = it },
                )
            }
        }

        ImagineComposer(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { composerHeightPx = it.height },
            text = textInput,
            onText = { textInput = it },
            media = media,
            onSelectMedia = settingsViewModel::saveImagineMedia,
            modelId = modelId,
            modelLabel = modelLabel,
            onOpenModelPicker = { showModelPicker = true },
            onOpenOptions = { showOptions = true },
            pendingUri = pendingUri?.toString(),
            pendingName = pendingName,
            onClearAttachment = { chatViewModel.clearPendingAttachment() },
            onReceiveImage = { uri -> chatViewModel.setPendingPastedImage(uri) },
            isBusy = isStreaming || progressLoading,
            blockedReason = null,
            onSend = {
                val prompt = textInput
                textInput = ""
                chatViewModel.sendImagineMessage(prompt, media)
            },
            onStop = { chatViewModel.stopStreaming() },
        )
    }

    if (showOptions) {
        ImagineOptionsSheet(
            options = ImagineOptions(
                media = media,
                aspectRatio = ratio,
                supportedRatios = supportedRatios,
                ratioNote = ratioNote,
                resolution = videoResolution,
                // Image models take no resolution argument at all, so the section is absent
                // rather than showing a control the request would silently drop.
                supportedResolutions = if (isVideo) videoCapabilities?.resolutions.orEmpty() else emptyList(),
                priceFor = { candidate -> videoCapabilities?.priceHint(candidate, audioEnabled) },
                audioSupported = videoCapabilities?.supportsAudio == true,
                audioEnabled = audioEnabled,
                attachmentUri = pendingUri?.toString(),
                attachmentName = pendingName,
            ),
            onSelectRatio = {
                if (isVideo) settingsViewModel.saveVideoAspectRatio(it) else settingsViewModel.saveImageAspectRatio(it)
            },
            onSelectResolution = settingsViewModel::saveVideoResolution,
            onToggleAudio = { settingsViewModel.saveVideoAudioEnabled(!audioEnabled) },
            onAttach = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClearAttachment = { chatViewModel.clearPendingAttachment() },
            onDismiss = { showOptions = false },
        )
    }

    if (showModelPicker) {
        ImagineModelPickerSheet(
            media = media,
            onSelectMedia = settingsViewModel::saveImagineMedia,
            models = modelEntries,
            selectedId = modelId,
            capabilitiesFor = settingsViewModel::videoCapabilitiesFor,
            resolution = videoResolution,
            audioEnabled = audioEnabled,
            onSelect = {
                if (isVideo) settingsViewModel.saveVideoGenModel(it) else settingsViewModel.saveImageGenModel(it)
                showModelPicker = false
            },
            onManage = { showModelPicker = false; onSettingsClicked() },
            onDismiss = { showModelPicker = false },
        )
    }
}
