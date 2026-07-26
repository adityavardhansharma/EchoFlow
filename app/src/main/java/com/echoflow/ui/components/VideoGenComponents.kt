@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.echoflow.data.GeneratedVideo
import com.echoflow.data.VideoRequestPolicy
import com.echoflow.ui.VideoGenPhrases
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The compact side of the placeholder square while the clip is still rendering. */
private val PlaceholderSize = 216.dp

/** The widest a settled clip may render inside the reply. */
private val SettledMaxWidth = 340.dp

/** The tallest a settled clip may render; taller aspects are capped. */
private val SettledMaxHeight = 420.dp

/** The clip's real dimensions and opening frame, read once from the file. */
private data class VideoPoster(val aspect: Float, val frame: ImageBitmap?)

/**
 * One generated video through its whole life, deliberately reusing the image generator's
 * choreography so the two features feel like one idea:
 *
 *   Rendering  — compact dot-field square, shuffled film-set phrases and the live job state.
 *                A clip takes minutes, not seconds, so the status line is real information.
 *   Stretching — the file landed; the SAME card grows to the clip's true aspect ratio, dots
 *                still dancing.
 *   Revealing  — the scanline sweeps down over the opening frame, then a small spring settle.
 *   Settled    — poster frame with a play button; tapping mounts the player. Persisted chats
 *                ([animate] = false) start here directly with zero motion.
 *
 * [aspectRatio] is what was *asked* for and only frames the placeholder; the stretch always
 * targets the ratio the model actually produced.
 */
@Composable
fun GeneratedVideoSegment(
    videoId: String,
    filePath: String?,
    pattern: String,
    aspectRatio: String,
    status: String,
    animate: Boolean,
    errorMessage: String? = null,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerOpen by remember { mutableStateOf(false) }
    var playing by remember(filePath) { mutableStateOf(false) }

    if (status == GeneratedVideo.STATUS_FAILED || status == GeneratedVideo.STATUS_CANCELLED ||
        status == GeneratedVideo.STATUS_EXPIRED
    ) {
        VideoFailureCard(status, errorMessage, modifier)
        return
    }

    // The clip's real dimensions and opening frame — one retriever pass, no decode of the
    // whole file. Falls back to the requested ratio if the file cannot be read.
    val requestedAspect = VideoRequestPolicy.aspectRatioValue(aspectRatio)
    val poster by produceState<VideoPoster?>(initialValue = null, filePath) {
        value = filePath?.let { path -> withContext(Dispatchers.IO) { readVideoPoster(path, requestedAspect) } }
    }

    val minAspect = SettledMaxWidth.value / SettledMaxHeight.value
    val displayAspect = (poster?.aspect ?: requestedAspect).coerceAtLeast(minAspect)

    var settled by rememberSaveable(filePath) { mutableStateOf(!animate && filePath != null) }
    val stretch = remember(filePath) { Animatable(if (settled) 1f else 0f) }
    val reveal = remember(filePath) { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(filePath, poster) {
        if (filePath != null && poster != null && !settled) {
            // Tweens, not springs: overshoot on LAYOUT size re-measures past the target and
            // back, which reads as jitter. The bounce lives in the scale settle instead.
            stretch.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            delay(150) // a breath at full size before the unveil
            reveal.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
            settled = true
        }
    }
    val settleScale by animateFloatAsState(
        targetValue = if (reveal.value > 0f && reveal.value < 1f) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "video-settle",
    )
    val scanColor = MaterialTheme.colorScheme.primary

    val morphMaxWidth = (PlaceholderSize.value + (SettledMaxWidth.value - PlaceholderSize.value) * stretch.value).dp
    val morphAspect = 1f + (displayAspect - 1f) * stretch.value

    // Persisted history starts settled with the poster still decoding for a few ms — hold the
    // card that beat instead of flashing a square that reflows to the real ratio.
    if (settled && poster == null) return

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = morphMaxWidth)
                .fillMaxWidth()
                .aspectRatio(morphAspect)
                .scale(settleScale)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            if (filePath != null) {
                Box(
                    Modifier
                        .matchParentSize()
                        .drawWithContent {
                            val fraction = reveal.value
                            clipRect(bottom = size.height * fraction) { this@drawWithContent.drawContent() }
                            if (fraction > 0f && fraction < 1f) {
                                val y = size.height * fraction
                                // Density-aware: raw px would be a near-invisible hairline.
                                val lineHeight = 2.5.dp.toPx()
                                val glowHeight = 40.dp.toPx()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, scanColor.copy(alpha = 0.4f)),
                                        startY = (y - glowHeight).coerceAtLeast(0f),
                                        endY = y,
                                    ),
                                    topLeft = Offset(0f, (y - glowHeight).coerceAtLeast(0f)),
                                    size = Size(size.width, glowHeight.coerceAtMost(y)),
                                )
                                drawRect(
                                    color = scanColor,
                                    topLeft = Offset(0f, y - lineHeight / 2f),
                                    size = Size(size.width, lineHeight),
                                )
                            }
                        },
                ) {
                    // The player is only built once the user asks for it: a chat can hold many
                    // clips, and a decoder per card would exhaust the device's codecs.
                    if (playing) {
                        InlineVideoPlayer(filePath, Modifier.matchParentSize())
                    } else {
                        poster?.frame?.let { frame ->
                            Image(
                                bitmap = frame,
                                contentDescription = "Generated video",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        if (settled) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable { playing = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        "Play video",
                                        Modifier.padding(Spacing.m).size(32.dp),
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (!settled) {
                DotFieldCanvas(
                    pattern = pattern,
                    revealFraction = { reveal.value },
                    stretchFraction = { stretch.value },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }

        // Status phrases only while the clip is still rendering; they collapse the moment the
        // stretch begins so the card is the sole focus of the handoff.
        AnimatedVisibility(visible = filePath == null, exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(Spacing.s))
                GeneratingPhraseLine(phrases = VideoGenPhrases.ALL, intervalMs = 4000)
                Spacer(Modifier.height(2.dp))
                Text(
                    videoStatusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = settled, enter = fadeIn()) {
            Column {
                Spacer(Modifier.height(Spacing.xs))
                GeneratedVideoActions(
                    onCopy = onCopy,
                    onFullscreen = { viewerOpen = true },
                    onDownload = {
                        filePath?.let { path ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { saveGeneratedVideoToGallery(context, path) != null }
                                Toast.makeText(
                                    context,
                                    if (ok) "Saved to Movies/EchoFlow" else "Couldn't save the video",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onShare = { filePath?.let { scope.launch { shareGeneratedVideo(context, it) } } },
                )
            }
        }
    }

    if (viewerOpen && filePath != null) {
        GeneratedVideoViewer(filePath = filePath, onDismiss = { viewerOpen = false })
    }
}

@Composable
internal fun GeneratedVideoActions(
    onCopy: (() -> Unit)?,
    onFullscreen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        onCopy?.let { copy ->
            FilledTonalIconButton(
                onClick = copy,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
        }
        FilledTonalIconButton(
            onClick = onFullscreen,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { Icon(Icons.Default.Fullscreen, "Play fullscreen", Modifier.size(20.dp)) }
        FilledTonalIconButton(
            onClick = onDownload,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { Icon(Icons.Default.Download, "Save to gallery", Modifier.size(20.dp)) }
        FilledTonalIconButton(
            onClick = onShare,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { Icon(Icons.Default.Share, "Share", Modifier.size(20.dp)) }
    }
}

/**
 * An ExoPlayer bound to one local file, released with the composable. A TextureView surface
 * is used rather than a SurfaceView so the player clips correctly to the card's rounded
 * corners and scrolls with the message list instead of punching through it.
 */
@Composable
private fun InlineVideoPlayer(filePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val playPause = rememberPlayPauseButtonState(player)
    Box(modifier) {
        PlayerSurface(player, Modifier.matchParentSize(), SURFACE_TYPE_TEXTURE_VIEW)
        Box(
            Modifier.matchParentSize().clickable { playPause.onClick() },
            contentAlignment = Alignment.Center,
        ) {
            // The control only appears while paused; playback stays unobstructed.
            if (playPause.showPlay) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                    Icon(
                        Icons.Default.PlayArrow, "Play", Modifier.padding(Spacing.m).size(32.dp),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Fullscreen playback, matching the workspace overlays' dark treatment. */
@Composable
fun GeneratedVideoViewer(filePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(filePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    val playPause = rememberPlayPauseButtonState(player)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            PlayerSurface(
                player,
                Modifier.fillMaxSize().padding(Spacing.base).clickable { playPause.onClick() },
                SURFACE_TYPE_TEXTURE_VIEW,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                FilledTonalIconButton(onClick = { playPause.onClick() }) {
                    Icon(
                        if (playPause.showPlay) Icons.Default.PlayArrow else Icons.Default.Pause,
                        if (playPause.showPlay) "Play" else "Pause",
                    )
                }
                FilledTonalIconButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveGeneratedVideoToGallery(context, filePath) != null }
                        Toast.makeText(
                            context,
                            if (ok) "Saved to Movies/EchoFlow" else "Couldn't save the video",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Icon(Icons.Default.Download, "Save to gallery") }
                FilledTonalIconButton(onClick = { scope.launch { shareGeneratedVideo(context, filePath) } }) {
                    Icon(Icons.Default.Share, "Share")
                }
            }
        }
    }
}

@Composable
private fun VideoFailureCard(status: String, errorMessage: String?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth().widthIn(max = SettledMaxWidth),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(Spacing.m))
            Column {
                Text(
                    when (status) {
                        GeneratedVideo.STATUS_CANCELLED -> "The video was cancelled"
                        GeneratedVideo.STATUS_EXPIRED -> "The video expired before it downloaded"
                        else -> "The video couldn't be generated"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                errorMessage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

/** Human wording for the job state. Worth showing: a clip renders for minutes. */
internal fun videoStatusLabel(status: String): String = when (status) {
    GeneratedVideo.STATUS_QUEUED -> "Sending to OpenRouter…"
    GeneratedVideo.STATUS_PENDING -> "Queued with the provider…"
    GeneratedVideo.STATUS_IN_PROGRESS -> "Rendering — this takes a few minutes"
    GeneratedVideo.STATUS_DOWNLOADING -> "Downloading the clip…"
    else -> "Working…"
}

/**
 * Reads the clip's dimensions and opening frame in one retriever pass. The frame is what the
 * scanline reveals onto, so the reveal never lands on a black rectangle waiting for a decoder.
 */
private fun readVideoPoster(filePath: String, fallbackAspect: Float): VideoPoster {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(filePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        // Portrait clips are stored landscape with a rotation tag; ignoring it renders them
        // in the wrong box entirely.
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val rotated = rotation == 90 || rotation == 270
        val aspect = if (width > 0 && height > 0) {
            if (rotated) height.toFloat() / width else width.toFloat() / height
        } else {
            fallbackAspect
        }
        VideoPoster(aspect, retriever.frameAtTime?.asImageBitmap())
    } catch (_: Exception) {
        VideoPoster(fallbackAspect, null)
    } finally {
        runCatching { retriever.release() }
    }
}

/** Copies the MP4 into MediaStore (Movies/EchoFlow); returns the new content Uri or null. */
private fun saveGeneratedVideoToGallery(context: Context, filePath: String): Uri? = runCatching {
    val file = File(filePath)
    if (!file.exists()) return null
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "EchoFlow_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/EchoFlow")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out ->
        file.inputStream().use { it.copyTo(out) }
    } ?: return null
    uri
}.getOrNull()

/** Shares via a MediaStore copy — no FileProvider needed, works on every API level we ship. */
private suspend fun shareGeneratedVideo(context: Context, filePath: String) {
    val uri = withContext(Dispatchers.IO) { saveGeneratedVideoToGallery(context, filePath) }
    if (uri == null) {
        Toast.makeText(context, "Couldn't share the video", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}
