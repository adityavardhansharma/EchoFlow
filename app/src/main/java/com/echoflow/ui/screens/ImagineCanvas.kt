@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.data.ChatMessage
import com.echoflow.data.GeneratedVideo
import com.echoflow.data.ImagineMedia
import com.echoflow.data.ImaginePrompts
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ToolEventJson
import com.echoflow.data.VideoRequestPolicy
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.GeneratedImageSegment
import com.echoflow.ui.components.MediaAction
import com.echoflow.ui.components.GeneratedVideoSegment
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.flow.Flow

/** Imagine's content column. Wider than a chat bubble — here the media is the subject. */
internal val ImagineMediaWidth = 480.dp

/**
 * The contact sheet: every generation in this session, newest at the bottom.
 *
 * Structurally the same timeline as Chat, so the streaming and persistence spine is shared,
 * but with the conversation furniture stripped out. There is no assistant header and no user
 * bubble — the prompt becomes the caption under its own result, because in a creative tool the
 * thing you made should never be the smaller half of the screen.
 */
@Composable
internal fun ImagineCanvas(
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    segments: List<StreamSegment>,
    progressLoading: Boolean,
    topInset: Dp,
    bottomInset: Dp,
    observeVideo: (String) -> Flow<GeneratedVideo?>,
    onUseAsReference: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var autoFollow by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 && (last.offset + last.size) <= info.viewportEndOffset + 8
        }
    }
    LaunchedEffect(atBottom, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) autoFollow = atBottom
    }
    LaunchedEffect(messages.size, progressLoading) {
        if (autoFollow) {
            val index = listState.layoutInfo.totalItemsCount - 1
            if (index >= 0) runCatching { listState.scrollToItem(index, Int.MAX_VALUE) }
        }
    }
    LaunchedEffect(autoFollow, isStreaming, progressLoading) {
        if (autoFollow && (isStreaming || progressLoading)) {
            while (true) {
                withFrameNanos { it }
                if (!listState.isScrollInProgress) {
                    val index = listState.layoutInfo.totalItemsCount - 1
                    if (index >= 0) runCatching { listState.scrollToItem(index, Int.MAX_VALUE) }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            start = Spacing.base, end = Spacing.base,
            top = topInset, bottom = bottomInset,
        ),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            ImagineResult(
                message = message,
                prompt = promptBehind(messages, index),
                observeVideo = observeVideo,
                onUseAsReference = onUseAsReference,
                onRetry = onRetry,
            )
        }
        if (segments.isNotEmpty()) {
            item(key = "streaming") {
                LiveImagineResult(segments, prompt = promptBehind(messages, messages.size))
            }
        }
    }
}

/** One finished generation: media first, its prompt as the caption beneath. */
@Composable
private fun ImagineResult(
    message: ChatMessage,
    prompt: String?,
    observeVideo: (String) -> Flow<GeneratedVideo?>,
    onUseAsReference: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    if (message.role == "user") return // the prompt shows as its result's caption instead

    val persisted = remember(message.id) { ToolEventJson.segmentsFromJson(message.segmentsJson) }
    Column(
        Modifier.fillMaxWidth().widthIn(max = ImagineMediaWidth),
        horizontalAlignment = Alignment.Start,
    ) {
        persisted.forEach { segment ->
            when (segment.type) {
                "image" -> segment.image?.let { ref ->
                    GeneratedImageSegment(
                        filePath = ref.filePath,
                        pattern = "ripple",
                        previousImagePath = null,
                        animate = false,
                        maxWidth = ImagineMediaWidth,
                        actions = listOf(referenceAction { onUseAsReference(ref.filePath) }),
                    )
                }
                "video" -> segment.video?.let { ref ->
                    val live by remember(ref.videoId) { observeVideo(ref.videoId) }.collectAsState(initial = null)
                    val filePath = live?.filePath ?: ref.filePath
                    GeneratedVideoSegment(
                        videoId = ref.videoId,
                        filePath = filePath,
                        pattern = "ripple",
                        aspectRatio = live?.aspectRatio ?: VideoRequestPolicy.DEFAULT_ASPECT_RATIO,
                        status = live?.status ?: if (filePath != null) {
                            GeneratedVideo.STATUS_COMPLETED
                        } else {
                            GeneratedVideo.STATUS_IN_PROGRESS
                        },
                        animate = ref.filePath == null,
                        errorMessage = live?.error,
                        maxWidth = ImagineMediaWidth,
                        // No Reference here. Every path that consumes an attachment wants an
                        // image — image generation sends an image part, and video sends a
                        // first frame as an `image_url` data URL — so offering it on a clip
                        // put an MP4 into the composer that the next request would either
                        // reject or quietly ignore, while the chip claimed otherwise.
                        actions = emptyList(),
                        onRetry = live?.prompt?.let { prompt -> { onRetry(prompt) } },
                    )
                }
                else -> Unit
            }
        }
        promptCaptionOf(persisted, message, prompt)?.let { caption ->
            Spacer(Modifier.height(Spacing.s))
            ImaginePromptLine(caption, onReuse = onRetry)
        }
    }
}

/** The in-flight generation, rendered with the full choreography. */
@Composable
private fun LiveImagineResult(segments: List<StreamSegment>, prompt: String?) {
    Column(
        Modifier.fillMaxWidth().widthIn(max = ImagineMediaWidth),
        horizontalAlignment = Alignment.Start,
    ) {
        segments.forEach { segment ->
            when (segment) {
                is StreamSegment.Image -> GeneratedImageSegment(
                    filePath = segment.filePath,
                    pattern = segment.pattern,
                    previousImagePath = segment.previousImagePath,
                    animate = true,
                    maxWidth = ImagineMediaWidth,
                )
                is StreamSegment.Video -> GeneratedVideoSegment(
                    videoId = segment.videoId,
                    filePath = segment.filePath,
                    pattern = segment.pattern,
                    aspectRatio = segment.aspectRatio,
                    status = segment.status,
                    animate = true,
                    errorMessage = segment.error,
                    maxWidth = ImagineMediaWidth,
                )
                else -> Unit
            }
        }
        // The prompt is already known while the render is running, so the caption is in place
        // from the first frame instead of appearing after the fact and shifting the layout.
        prompt?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            )
        }
    }
}

/** Sends this result back to the composer as the reference for the next thing you make. */
private fun referenceAction(onClick: () -> Unit) =
    MediaAction(Icons.Default.AddPhotoAlternate, "Reference", onClick)


/**
 * The caption for a result: the prompt that produced it, falling back to whatever the model
 * said if the turn has no prompt to point at (a resumed job, or a reply with no user message
 * behind it). The prompt wins because it is the reusable half — a model's own commentary is
 * read once, while the sentence you wrote is the thing you will want to edit and send again.
 */
private fun promptCaptionOf(
    persisted: List<PersistedSegment>,
    message: ChatMessage,
    prompt: String?,
): String? = prompt?.takeIf { it.isNotBlank() }
    ?: persisted.firstOrNull { it.type == "text" }?.text?.takeIf { it.isNotBlank() }
    ?: message.content.takeIf { it.isNotBlank() }

/**
 * The user turn that produced the message at [index] — the nearest one behind it.
 *
 * Walking backwards rather than assuming `index - 1` because a turn can carry more than one
 * assistant message, and a retried generation leaves the original prompt further back than the
 * immediately preceding row.
 */
private fun promptBehind(messages: List<ChatMessage>, index: Int): String? =
    (index - 1 downTo 0).asSequence()
        .map { messages[it] }
        .firstOrNull { it.role == "user" }
        ?.content
        ?.takeIf { it.isNotBlank() }

/**
 * Imagine's opening screen: a canvas that is already alive.
 *
 * The field fills the surface edge to edge and answers a finger, so the very first gesture a
 * user makes here gets a reply. Type sits inside it rather than on top of it — one line, and
 * an example that fills the composer when tapped. Nothing else: the screen's job is to make
 * the next action obvious, and the next action is to type.
 */
@Composable
internal fun ImagineEmptyState(
    media: ImagineMedia,
    topInset: Dp,
    bottomInset: Dp,
    onPrompt: (String) -> Unit,
) {
    // Drawn once per visit to the blank canvas, and again if the medium changes — a video
    // prompt has to describe motion, so the image pool would read as advice to ignore.
    val headline = remember(media) { ImaginePrompts.headline(media) }
    val example = remember(media) { ImaginePrompts.prompt(media) }

    Box(Modifier.fillMaxSize()) {
        ImagineField(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                headline,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.m))
            // Low-contrast and quiet: the field is the thing being looked at, and this is a
            // way in for anyone who would rather start from something than from nothing.
            Text(
                "Try “$example”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPrompt(example) }
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
    }
}
