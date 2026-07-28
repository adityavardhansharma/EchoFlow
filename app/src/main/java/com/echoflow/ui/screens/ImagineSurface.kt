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
import com.echoflow.data.armsCarryIntoVideo
import com.echoflow.data.CarryDecision
import com.echoflow.data.FirstFrameSupport
import com.echoflow.data.carryImageIntoVideoDecision
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel

/**
 * Hands the image you were just looking at to video, as its opening frame.
 *
 * Flipping Image → Video almost always means "now animate that". Making the user find the
 * file they generated ten seconds ago, in a system picker, among ten thousand camera photos,
 * to hand the app back something it already had, is the kind of gap that makes a two-mode tool
 * feel like two apps.
 *
 * Every guard here exists to keep it from being presumptuous:
 *
 *  - it fires only on the *transition*, so clearing the attachment is final rather than
 *    something the next recomposition undoes;
 *  - it never displaces an attachment already in the composer, since a deliberate choice
 *    outranks a guess;
 *  - it stays silent when the chosen model cannot take a first frame, because attaching
 *    something the request will drop is worse than attaching nothing.
 *
 * The switch is instant; the things it depends on are not. Model capabilities come off the
 * network and the image comes out of a database, so flipping to Video quickly used to be
 * judged against `false` and `null`, answer "no", and never ask again — the transition was
 * spent and the first frame silently never appeared. So the transition **arms** the hand-off
 * and a second effect performs it when the data lands.
 *
 * An armed hand-off has to expire, or it becomes a trap. It belongs to one moment in one
 * conversation, so it is dropped on a real no, on going back to Image, and on opening another
 * conversation — otherwise it sits waiting and fires on some later, unrelated change, which is
 * a worse bug than never firing at all.
 *
 * The label says where the file came from. An auto-attachment that cannot explain itself is
 * indistinguishable from a bug.
 */
@Composable
private fun CarryImageIntoVideo(
    media: ImagineMedia,
    threadId: String?,
    lastImagePath: String?,
    firstFrame: FirstFrameSupport,
    alreadyAttached: Boolean,
    onCarry: (String) -> Unit,
) {
    var previous by remember { mutableStateOf(media) }
    // Null means disarmed. Holding the conversation it was armed in — rather than a bare flag
    // plus a separate effect watching the thread — keeps the two from racing on a frame where
    // both change.
    var armedIn by remember { mutableStateOf<ArmedCarry?>(null) }

    LaunchedEffect(media) {
        if (armsCarryIntoVideo(previous, media)) armedIn = ArmedCarry(threadId)
        // Going back to Image ends the moment.
        if (media == ImagineMedia.Image) armedIn = null
        previous = media
    }

    LaunchedEffect(armedIn, threadId, lastImagePath, firstFrame, alreadyAttached) {
        val armed = armedIn ?: return@LaunchedEffect
        // Opening another conversation ends it too: its last image is not the one you were
        // looking at when you flipped the switch.
        if (armed.threadId != threadId) {
            armedIn = null
            return@LaunchedEffect
        }
        when (carryImageIntoVideoDecision(lastImagePath, firstFrame, alreadyAttached)) {
            CarryDecision.Wait -> Unit // nothing has answered yet; stay armed and ask again
            CarryDecision.Drop -> armedIn = null
            CarryDecision.Carry -> {
                lastImagePath?.let(onCarry)
                armedIn = null
            }
        }
    }
}

/** An armed hand-off, and the conversation it was armed in. */
private data class ArmedCarry(val threadId: String?)

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
    val lastImagePath by chatViewModel.lastGeneratedImagePath.collectAsState()

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

    // Capabilities drive the options sheet and the picker's cards, so load them as the surface
    // opens rather than waiting for the user to go looking for the picker.
    LaunchedEffect(Unit) { settingsViewModel.loadVideoModelDirectory() }

    val isVideo = media == ImagineMedia.Video

    CarryImageIntoVideo(
        media = media,
        threadId = currentThreadId,
        lastImagePath = lastImagePath,
        // Null capabilities means the directory has not answered yet, which is emphatically
        // not the same as a model that has told us it cannot take a first frame.
        firstFrame = when (val caps = videoCapabilities) {
            null -> FirstFrameSupport.Unknown
            else -> if (caps.supportsFirstFrame) FirstFrameSupport.Supported else FirstFrameSupport.Unsupported
        },
        alreadyAttached = pendingUri != null,
        onCarry = { chatViewModel.useMediaAsReference(it, label = "First frame · your last image") },
    )
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
                priceFor = { candidate -> videoCapabilities?.pricePerSecond(candidate, audioEnabled) },
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
