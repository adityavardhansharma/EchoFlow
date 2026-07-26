@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.echoflow.ui.ImageDotField
import com.echoflow.ui.ImageGenPhrases
import com.echoflow.ui.PhraseDeck
import com.echoflow.ui.RainField
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.hypot

/** The compact side of the placeholder square while the model is still painting. */
private val PlaceholderSize = 216.dp

/** The widest the settled image may render inside the reply. */
private val SettledMaxWidth = 340.dp

/** The tallest the settled image may render; taller aspects are capped and center-cropped. */
private val SettledMaxHeight = 420.dp

/**
 * One generated image through its whole life, in a single composable so every phase can
 * animate into the next (two separate composables would lose identity and hard-cut):
 *
 *   Generating — compact dot-field square, shuffled status phrases.
 *   Stretching — the file landed; the SAME card grows to the exact footprint the finished
 *                image will occupy (its true aspect ratio at bubble width, height-capped),
 *                dots still dancing. The unhurried "something finished" beat.
 *   Revealing  — the scanline sweeps down: image unmasked above the line, dots dissolving
 *                as it passes them, then a small spring settle.
 *   Settled    — plain image with copy/save/share and the fullscreen viewer. Persisted chats
 *                ([animate] = false) start here directly with zero motion.
 */
@Composable
fun GeneratedImageSegment(
    filePath: String?,
    pattern: String,
    previousImagePath: String?,
    animate: Boolean,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerOpen by remember { mutableStateOf(false) }

    // Exact pixel dimensions of the finished PNG — a bounds-only decode, no bitmap loaded.
    val rawAspect by produceState<Float?>(initialValue = null, filePath) {
        value = filePath?.let { path ->
            withContext(Dispatchers.IO) {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight else 1f
            }
        }
    }
    // The display footprint: true aspect at bubble width, capped so height never exceeds
    // SettledMaxHeight (the stretch targets exactly what the settled image will occupy,
    // so the reveal ends with zero snap or reflow).
    val minAspect = SettledMaxWidth.value / SettledMaxHeight.value
    val displayAspect = (rawAspect ?: 1f).coerceAtLeast(minAspect)

    var settled by rememberSaveable(filePath) { mutableStateOf(!animate && filePath != null) }
    val stretch = remember(filePath) { Animatable(if (settled) 1f else 0f) }
    val reveal = remember(filePath) { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(filePath, rawAspect) {
        if (filePath != null && rawAspect != null && !settled) {
            // Tweens, not springs: overshoot on LAYOUT size re-measures past the target and
            // back, which reads as jitter. The bounce lives in the scale settle instead.
            stretch.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
            delay(150) // a breath at full size before the unveil
            reveal.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
            settled = true
        }
    }
    // A small dip-and-spring-back around the reveal — the Expressive settle.
    val settleScale by animateFloatAsState(
        targetValue = if (reveal.value > 0f && reveal.value < 1f) 0.985f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "image-settle",
    )
    val scanColor = MaterialTheme.colorScheme.primary

    // Both dimensions of the card interpolate together from the placeholder square to the
    // settled footprint, driven by the single stretch value.
    val morphMaxWidth = (PlaceholderSize.value + (SettledMaxWidth.value - PlaceholderSize.value) * stretch.value).dp
    val morphAspect = 1f + (displayAspect - 1f) * stretch.value

    // Persisted history starts settled with the aspect still decoding for a few ms — hold the
    // card that beat instead of flashing a square that reflows to the real ratio.
    if (settled && rawAspect == null) return

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = morphMaxWidth)
                .fillMaxWidth()
                .aspectRatio(morphAspect)
                .scale(settleScale)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (settled) Modifier.clickable { viewerOpen = true } else Modifier),
        ) {
            // Edit turns: the version being reworked, dimmed under the dots until the reveal.
            if (previousImagePath != null && reveal.value < 1f) {
                AsyncImage(
                    model = File(previousImagePath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(14.dp) // no-op below API 31; the alpha dim still reads correctly
                        .alpha(0.35f * (1f - reveal.value)),
                )
            }
            // The finished image, unmasked top-to-bottom by the scanline.
            if (filePath != null) {
                AsyncImage(
                    model = File(filePath),
                    contentDescription = "Generated image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
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
                )
            }
            // Dots run through generating AND stretching, then dissolve under the scanline —
            // only dots below the sweep survive, so the field literally becomes the image.
            if (!settled) {
                DotFieldCanvas(
                    pattern = pattern,
                    revealFraction = { reveal.value },
                    stretchFraction = { stretch.value },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        // Status phrases only while the model is still painting; they collapse the moment
        // the stretch begins so the card is the sole focus of the handoff.
        AnimatedVisibility(
            visible = filePath == null,
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.s))
                GeneratingPhraseLine()
            }
        }
        AnimatedVisibility(visible = settled, enter = fadeIn()) {
            Column {
                Spacer(Modifier.height(Spacing.xs))
                GeneratedImageActions(
                    onCopy = onCopy,
                    onDownload = {
                        filePath?.let { path ->
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { saveGeneratedImageToGallery(context, path) != null }
                                Toast.makeText(context, if (ok) "Saved to Pictures/EchoFlow" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onShare = { filePath?.let { scope.launch { shareGeneratedImage(context, it) } } },
                )
            }
        }
    }

    if (viewerOpen && filePath != null) {
        GeneratedImageViewer(filePath = filePath, onDismiss = { viewerOpen = false })
    }
}

@Composable
internal fun GeneratedImageActions(
    onCopy: (() -> Unit)?,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        onCopy?.let { copy ->
            FilledTonalIconButton(
                onClick = copy,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(20.dp)) }
        }
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
 * The 21×21 dot field. All color comes from the Material scheme (dynamic color + dark mode
 * adaptive). [revealFraction] is read lazily in the draw phase: dots above the scanline are
 * gone, dots below it fade as the sweep approaches the bottom.
 *
 * Shared with video generation, which runs the same generating → stretch → reveal
 * choreography onto a clip's aspect ratio.
 */
@Composable
internal fun DotFieldCanvas(
    pattern: String,
    revealFraction: () -> Float,
    stretchFraction: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    var timeMs by remember { mutableLongStateOf(0L) }
    val rain = remember(pattern) { RainField() }
    LaunchedEffect(pattern) {
        var last = 0L
        while (true) {
            withFrameNanos { nanos ->
                val now = nanos / 1_000_000
                val dt = if (last == 0L) 16L else (now - last).coerceIn(1L, 100L)
                last = now
                if (pattern == "rain") rain.step(now, dt)
                timeMs = now
            }
        }
    }
    val dotColor = MaterialTheme.colorScheme.primary
    // Crest dots shift tone within the same hue — brighter on dark themes, deeper on light —
    // matching M3 tonal-palette behaviour instead of only changing opacity.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val crestColor = lerp(dotColor, if (darkTheme) Color.White else Color.Black, 0.18f)
    Canvas(modifier) {
        // Reading timeMs inside the draw block redraws every frame without recomposing.
        val t = timeMs
        val fraction = revealFraction()
        val stretchNow = stretchFraction()
        val revealY = size.height * fraction
        // Global fade in the last quarter of the sweep so the tail end never pops off.
        val fieldAlpha = if (fraction > 0.75f) (1f - fraction) * 4f else 1f
        if (fieldAlpha <= 0f) return@Canvas
        // The grid ADAPTS to the growing canvas: cell size stays constant (derived from the
        // placeholder square) and new rows/columns of dots appear as the card stretches, so
        // the whole surface is always dots — never a bare box with a square patch of them.
        val cell = size.minDimension / ImageDotField.GRID
        val cols = ceil(size.width / cell).toInt()
        val rows = ceil(size.height / cell).toInt()
        val offsetX = (size.width - cols * cell) / 2f
        val offsetY = (size.height - rows * cell) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val restRadius = cell * 0.16f
        // During the stretch, one celebratory crest rides the expanding edge outward — the
        // dots visibly push the card open instead of a plain container resizing under them.
        val maxDistCells = hypot(centerX, centerY) / cell
        val burstPos = if (stretchNow > 0f && stretchNow < 1f) stretchNow * (maxDistCells + 2f) else -100f
        for (row in 0 until rows) {
            val cy = offsetY + row * cell + cell / 2f
            if (fraction > 0f && cy < revealY) continue // dissolved into the image above the line
            for (col in 0 until cols) {
                val cx = offsetX + col * cell + cell / 2f
                val distCells = hypot(cx - centerX, cy - centerY) / cell
                var intensity = if (pattern == "rain") {
                    rain.intensityAt(col % ImageDotField.GRID, row)
                } else {
                    ImageDotField.rippleIntensity(distCells, t)
                }
                if (burstPos > 0f) {
                    intensity = (intensity + ImageDotField.gauss(distCells - burstPos, 1.3f)).coerceAtMost(1f)
                }
                // Every channel — size, opacity, tone, shape — is a CONTINUOUS function of one
                // intensity value. No thresholds: a threshold reads as a per-frame pop.
                val alpha = (0.15f + 0.85f * intensity) * fieldAlpha
                val radius = restRadius * (1f + 1.35f * intensity)
                val side = radius * 2f
                // Corner radius eases from a perfect circle (side/2) toward a soft squircle as
                // the crest passes — the Expressive morph, with no discontinuity at any point.
                val corner = side * (0.5f - 0.24f * intensity * intensity * (3f - 2f * intensity))
                drawRoundRect(
                    color = lerp(dotColor, crestColor, intensity),
                    alpha = alpha,
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
        }
    }
}

/** Shuffled-deck status phrases: no repeats until the whole pool has been shown. */
@Composable
internal fun GeneratingPhraseLine(
    phrases: List<String> = ImageGenPhrases.ALL,
    intervalMs: Long = 2800,
    modifier: Modifier = Modifier,
) {
    val deck = remember(phrases) { PhraseDeck(phrases) }
    var phrase by remember(phrases) { mutableStateOf(phrases.first()) }
    LaunchedEffect(phrases) {
        phrase = deck.next()
        while (true) {
            delay(intervalMs)
            phrase = deck.next()
        }
    }
    Crossfade(targetState = phrase, label = "generating-phrase") { current ->
        Text(
            current,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

/** Fullscreen viewer with save/share, matching the workspace overlays' dark treatment. */
@Composable
fun GeneratedImageViewer(filePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            AsyncImage(
                model = File(filePath),
                contentDescription = "Generated image",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(Spacing.base),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                FilledTonalIconButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveGeneratedImageToGallery(context, filePath) != null }
                        Toast.makeText(context, if (ok) "Saved to Pictures/EchoFlow" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
                    }
                }) { Icon(Icons.Default.Download, "Save to gallery") }
                FilledTonalIconButton(onClick = { scope.launch { shareGeneratedImage(context, filePath) } }) {
                    Icon(Icons.Default.Share, "Share")
                }
            }
        }
    }
}

/** Copies the PNG into MediaStore (Pictures/EchoFlow); returns the new content Uri or null. */
private fun saveGeneratedImageToGallery(context: Context, filePath: String): Uri? = runCatching {
    val file = File(filePath)
    if (!file.exists()) return null
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "EchoFlow_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/EchoFlow")
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    context.contentResolver.openOutputStream(uri)?.use { out ->
        file.inputStream().use { it.copyTo(out) }
    } ?: return null
    uri
}.getOrNull()

/** Shares via a MediaStore copy — no FileProvider needed, works on every API level we ship. */
private suspend fun shareGeneratedImage(context: Context, filePath: String) {
    val uri = withContext(Dispatchers.IO) { saveGeneratedImageToGallery(context, filePath) }
    if (uri == null) {
        Toast.makeText(context, "Couldn't share the image", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share image"))
}
