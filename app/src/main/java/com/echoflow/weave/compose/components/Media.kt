package com.echoflow.weave.compose.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.SubcomposeAsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.echoflow.weave.compose.render.Placeholders
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WImage
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay

/** Media and spatial, 04 §10. Network media is https-only. */
object Media {

    fun https(src: String?): String? = src?.trim()?.takeIf { it.startsWith("https://") }

    private fun srcOf(node: WNode, key: String = "src"): String? = when (val v = node.args[key]) {
        is WStr -> https(v.v)
        is WImage -> v.uri
        else -> null
    }

    @Composable
    fun ImageR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = srcOf(node)
        val alt = node.str("alt") ?: "Image"
        var zoom by remember { mutableStateOf(false) }
        val h = TokenResolver.height(node.args["h"], ctx.screenHeight)
        val shape = TokenResolver.shape(node.args["shape"] ?: WStr("m"))
        val m = modifier.fillMaxWidth().then(if (h != null) Modifier.height(h) else Modifier.aspectRatio(16f / 9f)).clip(shape)
            .then(if (node.bool("zoom") && src != null) Modifier.clickable { zoom = true } else Modifier)
        RemoteImage(src, alt, if (node.word("fit") == "contain") ContentScale.Fit else ContentScale.Crop, m)
        if (zoom && src != null) ZoomViewer(listOf(src), 0) { zoom = false }
    }

    @Composable
    fun RemoteImage(src: String?, alt: String, scale: ContentScale, modifier: Modifier) {
        if (src == null) { Broken(alt, modifier); return }
        SubcomposeAsyncImage(
            model = src, contentDescription = alt, contentScale = scale, modifier = modifier,
            loading = { Placeholders.Bar(Modifier.fillMaxSize(), 168.dp) },
            error = { Broken(alt, Modifier.fillMaxSize()) },
        )
    }

    @Composable
    private fun Broken(alt: String, modifier: Modifier) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TokenResolver.icon("broken_image"), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(alt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun ZoomViewer(srcs: List<String>, start: Int, onClose: () -> Unit) {
        Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
                val pager = rememberPagerState(start) { srcs.size }
                HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    RemoteImage(srcs[i], "", ContentScale.Fit, Modifier.fillMaxSize().pointerInputTransform { s, o -> scale = (scale * s).coerceIn(1f, 5f); offset = if (scale == 1f) Offset.Zero else offset + o }
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
                }
                IconButton(onClose, Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(TokenResolver.icon("close"), "Close", tint = MaterialTheme.colorScheme.inverseOnSurface) }
            }
        }
    }

    private fun Modifier.pointerInputTransform(onChange: (Float, Offset) -> Unit) =
        this.then(Modifier.pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> onChange(zoom, pan) } })

    @Composable
    fun GalleryR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val srcs = node.list("srcs")?.mapNotNull { https((it as? WStr)?.v) } ?: emptyList()
        val alts = node.list("alts")?.map { ctx.fmt(it) } ?: emptyList()
        val cols = (node.num("cols") ?: 3.0).toInt().coerceIn(1, 6)
        var open by remember { mutableStateOf<Int?>(null) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            srcs.withIndex().chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { (i, s) ->
                        RemoteImage(s, alts.getOrElse(i) { "Image ${i + 1}" }, ContentScale.Crop,
                            Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.small).clickable(enabled = node.bool("viewer", true)) { open = i })
                    }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        open?.let { ZoomViewer(srcs, it) { open = null } }
    }

    /** Playback stops when the app leaves the foreground (Home, another app), not only on dispose. */
    @Composable
    private fun PauseWhenStopped(player: ExoPlayer?) {
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        DisposableEffect(player, lifecycle) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) player?.pause()
            }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }
    }

    @Composable
    fun VideoR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = srcOf(node)
        val context = LocalContext.current
        if (src == null) { Broken("Video", modifier.fillMaxWidth().aspectRatio(16f / 9f)); return }
        val player = remember(src) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(src)))
                repeatMode = if (node.bool("loop")) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                if (node.bool("autoplay")) { volume = 0f; playWhenReady = true }
                prepare()
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        PauseWhenStopped(player)
        var playing by remember { mutableStateOf(player.playWhenReady) }
        var pos by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(player) { while (true) { delay(250); val d = player.duration.coerceAtLeast(1); pos = player.currentPosition.toFloat() / d } }
        val h = TokenResolver.height(node.args["h"], ctx.screenHeight)
        Column(modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().then(if (h != null) Modifier.height(h) else Modifier.aspectRatio(16f / 9f)).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.scrim)) {
                PlayerSurface(player, Modifier.matchParentSize(), SURFACE_TYPE_TEXTURE_VIEW)
                FilledTonalIconButton({ playing = !playing; player.playWhenReady = playing; if (playing) player.volume = 1f }, Modifier.align(Alignment.Center)) {
                    Icon(TokenResolver.icon(if (playing) "pause" else "play_arrow"), if (playing) "Pause" else "Play")
                }
            }
            Slider(pos, { pos = it; player.seekTo((it * player.duration.coerceAtLeast(0)).toLong()) })
        }
    }

    @Composable
    fun AudioR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = srcOf(node)
        val context = LocalContext.current
        val player = remember(src) { src?.let { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.parse(it))); prepare() } } }
        DisposableEffect(player) { onDispose { player?.release() } }
        PauseWhenStopped(player)
        var playing by remember { mutableStateOf(false) }
        var pos by remember { mutableFloatStateOf(0f) }
        var time by remember { mutableIntStateOf(0) }
        LaunchedEffect(player) { while (player != null) { delay(250); pos = player.currentPosition.toFloat() / player.duration.coerceAtLeast(1); time = (player.currentPosition / 1000).toInt() } }
        val scheme = MaterialTheme.colorScheme
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainer) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton({ playing = !playing; player?.playWhenReady = playing }, enabled = player != null) {
                    Icon(TokenResolver.icon(if (playing) "pause" else "play_arrow"), if (playing) "Pause" else "Play")
                }
                Column(Modifier.weight(1f)) {
                    node.str("title")?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    if (node.bool("waveform", true)) Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        val n = 48; val w = size.width / n
                        for (i in 0 until n) {
                            val amp = 0.3f + 0.7f * ((kotlin.math.sin(i * 1.7) * 0.5 + 0.5) * (kotlin.math.cos(i * 0.31) * 0.5 + 0.5)).toFloat()
                            drawRoundRect(if (i.toFloat() / n <= pos) scheme.primary else scheme.outlineVariant, Offset(i * w + 1, size.height * (1 - amp) / 2), Size(w - 2, size.height * amp), androidx.compose.ui.geometry.CornerRadius(2f))
                        }
                    }
                }
                Text(ctx.formatter.clock(time * 1000L), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    fun MapR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val center = when (val c = node.args["center"]) {
            is WRec -> listOfNotNull((c["lat"] as? WNum)?.v, (c["lng"] as? WNum)?.v).joinToString(", ")
            is WStr -> c.v
            else -> (node.list("markers")?.firstOrNull() as? WRec)?.let { ctx.fmt(it["title"]) } ?: ""
        }
        StaticCard("map", center.ifEmpty { "Map" }, "Open in Maps", modifier, TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)) {
            ctx.controller.hostEffect("map", listOf(node.args["center"] ?: WStr(center)))
        }
    }

    @Composable
    fun Model3DR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = srcOf(node)
        StaticCard("view_in_ar", "3D model", "Open", modifier, TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)) {
            src?.let { ctx.controller.requestUrl(it) }
        }
    }

    @Composable
    private fun StaticCard(icon: String, text: String, action: String, modifier: Modifier, h: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
        Surface(modifier.fillMaxWidth().height(h), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TokenResolver.icon(icon), null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick) { Text(action) }
            }
        }
    }

    @Composable
    fun LottieR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = srcOf(node)
        val h = TokenResolver.heightOr(node.args["h"], "m", ctx.screenHeight)
        if (src == null) { Broken("Animation", modifier.fillMaxWidth().height(h)); return }
        val composition by rememberLottieComposition(LottieCompositionSpec.Url(src))
        LottieAnimation(composition, modifier.fillMaxWidth().height(h), iterations = if (node.bool("loop", true)) LottieConstants.IterateForever else 1)
    }

    @Composable
    fun QrR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val content = ctx.fmt(node.args["content"])
        val size = when (node.word("size")) { "s" -> 120.dp; "l" -> 200.dp; else -> 160.dp }
        val matrix = remember(content) {
            runCatching {
                QRCodeWriter().encode(content.ifEmpty { " " }, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
            }.getOrNull()
        }
        val scheme = MaterialTheme.colorScheme
        Canvas(modifier.size(size)) {
            drawRect(scheme.surface)
            val m = matrix ?: return@Canvas
            val cell = this.size.width / m.width
            for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) drawRect(scheme.onSurface, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
