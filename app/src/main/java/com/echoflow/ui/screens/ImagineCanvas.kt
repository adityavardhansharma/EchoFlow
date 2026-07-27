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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ToolEventJson
import com.echoflow.data.VideoRequestPolicy
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.GeneratedImageSegment
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
    onAskAbout: (String) -> Unit,
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
        items(messages, key = { it.id }) { message ->
            ImagineResult(
                message = message,
                observeVideo = observeVideo,
                onAskAbout = onAskAbout,
                onRetry = onRetry,
            )
        }
        if (segments.isNotEmpty()) {
            item(key = "streaming") {
                LiveImagineResult(segments)
            }
        }
    }
}

/** One finished generation: media first, its prompt as the caption beneath. */
@Composable
private fun ImagineResult(
    message: ChatMessage,
    observeVideo: (String) -> Flow<GeneratedVideo?>,
    onAskAbout: (String) -> Unit,
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
                        actions = { AskAboutButton { onAskAbout(ref.filePath) } },
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
                        actions = filePath?.let { path -> { AskAboutButton { onAskAbout(path) } } },
                        onRetry = live?.prompt?.let { prompt -> { onRetry(prompt) } },
                    )
                }
                else -> Unit
            }
        }
        promptCaptionOf(persisted, message)?.let { caption ->
            Spacer(Modifier.height(Spacing.s))
            PromptCaption(caption)
        }
    }
}

/** The in-flight generation, rendered with the full choreography. */
@Composable
private fun LiveImagineResult(segments: List<StreamSegment>) {
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
                is StreamSegment.Text -> {
                    if (segment.text.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.s))
                        PromptCaption(segment.text)
                    }
                }
                else -> Unit
            }
        }
    }
}

/** Expands on tap: one line keeps the sheet scannable, the full text is a tap away. */
@Composable
private fun PromptCaption(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded },
    )
}

@Composable
private fun AskAboutButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, "Ask about this in Chat", Modifier.size(20.dp))
    }
}


/**
 * The caption for a result: the assistant's own sentence if it wrote one, otherwise nothing —
 * the user's prompt is already the message directly above in the source data, and repeating it
 * under every image would double the reading for no gain.
 */
private fun promptCaptionOf(persisted: List<PersistedSegment>, message: ChatMessage): String? =
    persisted.firstOrNull { it.type == "text" }?.text?.takeIf { it.isNotBlank() }
        ?: message.content.takeIf { it.isNotBlank() }

/** The frame's long side; both dimensions derive from the live ratio inside this bound. */
private val EmptyFrameBounds = 288.dp

/**
 * Imagine's opening screen: an empty frame.
 *
 * No mascot, no feature tour, no marketing copy — the one image every creative tool in
 * history shares is the blank canvas, so the blank canvas is the whole screen. It earns its
 * keep twice over:
 *
 *  - The frame is drawn at the ratio currently selected in the composer and re-proportions
 *    on a spring when that changes, so the empty state is a live preview of the shape you
 *    are about to make — and the shape control teaches itself.
 *  - The dots inside are the generation texture at rest. The app's story then reads in
 *    order: still dots, dancing dots, picture.
 */
@Composable
internal fun ImagineEmptyState(
    media: ImagineMedia,
    aspectRatio: String,
    topInset: Dp,
    bottomInset: Dp,
    onPrompt: (String) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val aspect by animateFloatAsState(
        targetValue = VideoRequestPolicy.aspectRatioValue(aspectRatio),
        animationSpec = if (reducedMotion) tween(0) else spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "empty-frame-aspect",
    )
    // Fit the live ratio inside a square bound. Width and height are both continuous
    // functions of the animated value, so the frame morphs through 1:1 without a jump.
    val frameWidth = if (aspect >= 1f) EmptyFrameBounds else EmptyFrameBounds * aspect
    val frameHeight = if (aspect >= 1f) EmptyFrameBounds / aspect else EmptyFrameBounds

    val example = if (media == ImagineMedia.Video) {
        "Slow dolly through a greenhouse at sunrise"
    } else {
        "A lighthouse mid-storm, painted in gouache"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            RestingDots(Modifier.matchParentSize())
            // The frame IS the ratio — naming it links this shape to the chip that changes it.
            Text(
                aspectRatio,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            "It begins with a sentence.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            "Try “$example”",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onPrompt(example) },
        )
    }
}

/**
 * The generation texture at rest: a sparse, motionless grid of dots. Deliberately static —
 * this screen's only movement is the frame answering the ratio control.
 */
@Composable
private fun RestingDots(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    Canvas(modifier) {
        val cell = 20.dp.toPx()
        val radius = 1.6.dp.toPx()
        val cols = (size.width / cell).toInt()
        val rows = (size.height / cell).toInt()
        val originX = (size.width - cols * cell) / 2f + cell / 2f
        val originY = (size.height - rows * cell) / 2f + cell / 2f
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                drawCircle(color, radius, Offset(originX + col * cell, originY + row * cell))
            }
        }
    }
}

