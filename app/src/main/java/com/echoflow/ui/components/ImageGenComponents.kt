@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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

/**
 * The image-generation placeholder: a 21×21 field of tiny theme-colored dots resting near
 * silence while one narrow wave (ripple) or sparse streaks (rain) travel through them, with
 * a shuffled-deck status phrase crossfading below. All color comes from the Material scheme,
 * so it re-skins itself with dynamic color and dark mode. On edit turns the previous version
 * shows dimmed and blurred beneath the dots — "your image is being reworked".
 */
@Composable
fun GeneratingImageCard(
    pattern: String,
    previousImagePath: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            previousImagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(14.dp) // no-op below API 31; the alpha dim still reads correctly
                        .alpha(0.35f),
                )
            }
            DotFieldCanvas(pattern = pattern, modifier = Modifier.matchParentSize())
        }
        Spacer(Modifier.height(Spacing.s))
        GeneratingPhraseLine()
    }
}

@Composable
private fun DotFieldCanvas(pattern: String, modifier: Modifier = Modifier) {
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
    Canvas(modifier) {
        // Reading timeMs inside the draw block redraws every frame without recomposing.
        val t = timeMs
        val n = ImageDotField.GRID
        val cell = size.minDimension / n
        val restRadius = cell * 0.16f
        for (row in 0 until n) {
            for (col in 0 until n) {
                val intensity = if (pattern == "rain") {
                    rain.intensityAt(col, row)
                } else {
                    ImageDotField.rippleIntensity(ImageDotField.distanceFromCenter(col, row), t)
                }
                val alpha = 0.15f + 0.85f * intensity
                val radius = restRadius * (1f + 1.35f * intensity)
                val cx = col * cell + cell / 2f
                val cy = row * cell + cell / 2f
                if (intensity > 0.25f) {
                    // The crest morphs circles toward soft squircles — the Expressive accent.
                    val side = radius * 3.4f
                    val corner = side * (0.5f - 0.28f * intensity)
                    drawRoundRect(
                        color = dotColor,
                        alpha = alpha,
                        topLeft = Offset(cx - side / 2f, cy - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                } else {
                    drawCircle(color = dotColor, alpha = alpha, radius = radius, center = Offset(cx, cy))
                }
            }
        }
    }
}

/** Shuffled-deck status phrases: no repeats until all 100 have been shown. */
@Composable
private fun GeneratingPhraseLine(modifier: Modifier = Modifier) {
    val deck = remember { PhraseDeck() }
    var phrase by remember { mutableStateOf(ImageGenPhrases.ALL.first()) }
    LaunchedEffect(Unit) {
        phrase = deck.next()
        while (true) {
            delay(2800)
            phrase = deck.next()
        }
    }
    Crossfade(targetState = phrase, label = "image-gen-phrase") { current ->
        Text(
            current,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

/**
 * A finished generated image, inline in the reply. On first arrival it plays the wipe
 * reveal (a glowing primary-color line sweeps top→bottom unmasking the image) ending in a
 * small spring settle; reloading a persisted chat renders instantly. Tap opens fullscreen.
 */
@Composable
fun GeneratedImageBlock(
    filePath: String,
    animateReveal: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewerOpen by remember { mutableStateOf(false) }
    var revealed by rememberSaveable(filePath) { mutableStateOf(!animateReveal) }
    val progress = remember(filePath) { Animatable(if (revealed) 1f else 0f) }
    LaunchedEffect(filePath) {
        if (!revealed) {
            progress.animateTo(1f, tween(durationMillis = 850, easing = FastOutSlowInEasing))
            revealed = true
        }
    }
    val settle by animateFloatAsState(
        targetValue = if (progress.value >= 1f) 1f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "image-reveal-settle",
    )
    val scanColor = MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .scale(settle)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { viewerOpen = true },
        ) {
            AsyncImage(
                model = File(filePath),
                contentDescription = "Generated image",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .drawWithContent {
                        val reveal = progress.value
                        clipRect(bottom = size.height * reveal) { this@drawWithContent.drawContent() }
                        if (reveal < 1f) {
                            val y = size.height * reveal
                            // Soft glow trail above the line, then the bright 3px scanline.
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, scanColor.copy(alpha = 0.35f)),
                                    startY = (y - 48f).coerceAtLeast(0f),
                                    endY = y,
                                ),
                                topLeft = Offset(0f, (y - 48f).coerceAtLeast(0f)),
                                size = Size(size.width, 48f.coerceAtMost(y)),
                            )
                            drawRect(
                                color = scanColor,
                                topLeft = Offset(0f, y - 1.5f),
                                size = Size(size.width, 3f),
                            )
                        }
                    },
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilledTonalIconButton(
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { saveGeneratedImageToGallery(context, filePath) != null }
                        Toast.makeText(context, if (ok) "Saved to Pictures/EchoFlow" else "Couldn't save the image", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.Download, "Save to gallery", Modifier.size(16.dp)) }
            FilledTonalIconButton(
                onClick = { scope.launch { shareGeneratedImage(context, filePath) } },
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) { Icon(Icons.Default.Share, "Share", Modifier.size(16.dp)) }
        }
    }

    if (viewerOpen) {
        GeneratedImageViewer(filePath = filePath, onDismiss = { viewerOpen = false })
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
