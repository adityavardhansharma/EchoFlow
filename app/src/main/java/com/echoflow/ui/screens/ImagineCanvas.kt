@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.echoflow.ui.components.DotFieldCanvas
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.theme.Spacing
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
 * One way in: a prompt worth stealing, the shape it wants, and a tone to wear. Tapping sets
 * the ratio as well as the text — which is how the shape control introduces itself, since
 * almost nobody opens that chip unprompted.
 */
private data class ImagineStarter(val prompt: String, val ratio: String, val tone: Int)

/**
 * Imagine's opening screen.
 *
 * The hero is a **live dot field** — the exact texture that marks a generation in progress,
 * running at rest. It costs nothing (the canvas already exists) and it means the surface
 * teaches its own language before anything is made: the second time the user sees those dots,
 * they already read as "something is happening".
 *
 * Starters are shaped tiles rather than sentences in grey pills, each drawn at the ratio it
 * would produce, so the first thing on screen is a row of pictures-to-be.
 */
@Composable
internal fun ImagineEmptyState(
    media: ImagineMedia,
    topInset: Dp,
    bottomInset: Dp,
    onStarter: (String, String) -> Unit,
) {
    val video = media == ImagineMedia.Video
    // Alternates per entry, exactly as a real generation does: never quite the same room twice.
    val pattern = remember(media) { listOf("ripple", "rain").random() }
    val starters = remember(media) {
        if (video) {
            listOf(
                ImagineStarter("A paper boat running a rain-filled gutter", "16:9", 0),
                ImagineStarter("Neon bleeding into a wet street", "9:16", 1),
                ImagineStarter("Slow dolly through a greenhouse at sunrise", "21:9", 2),
            )
        } else {
            listOf(
                ImagineStarter("A lighthouse mid-storm, painted in gouache", "16:9", 0),
                ImagineStarter("An astronaut rendered in stained glass", "9:16", 1),
                ImagineStarter("Isometric cutaway of a tiny bookshop", "1:1", 2),
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = topInset, bottom = bottomInset)
            .padding(horizontal = Spacing.base),
        verticalArrangement = Arrangement.Center,
    ) {
        // Type sits *inside* the texture over a scrim — an editorial cover, not an icon with a
        // caption underneath it.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DotFieldCanvas(
                pattern = pattern,
                revealFraction = { 0f },
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
            ) {
                Text(
                    "Make something.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (video) "Describe a clip. The model decides how long it runs."
                    else "Describe an image, then talk it into shape.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SectionLabel("Start from")
        Spacer(Modifier.height(Spacing.m))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            starters.forEach { starter ->
                StarterTile(starter) { onStarter(starter.prompt, starter.ratio) }
            }
        }
    }
}

/** Every tile is this tall; width follows the ratio. A contact sheet, not a ragged stack. */
private val StarterTileHeight = 132.dp

/** A prompt shown as the shape it would produce, rather than a sentence in a grey pill. */
@Composable
private fun StarterTile(starter: ImagineStarter, onClick: () -> Unit) {
    val container = when (starter.tone) {
        0 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    // Uniform height, width derived from the ratio — the way a strip of frames actually
    // reads. Sizing by width instead would let a 9:16 tile tower over a 16:9 one and leave
    // every caption sitting at a different height.
    val ratio = VideoRequestPolicy.aspectRatioValue(starter.ratio)
    val tileWidth = (StarterTileHeight.value * ratio).dp.coerceIn(96.dp, 228.dp)

    Column(
        Modifier
            .width(tileWidth)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(bottom = Spacing.xs),
    ) {
        Box(
            Modifier
                .width(tileWidth)
                .height(StarterTileHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(container, MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                ),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Text(
                starter.ratio,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.s),
            )
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            starter.prompt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            // Fixed at two lines so the row keeps a straight baseline whatever the prompts say.
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
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

