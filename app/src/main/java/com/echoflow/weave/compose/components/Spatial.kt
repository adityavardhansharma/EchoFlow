package com.echoflow.weave.compose.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.nio.ByteBuffer

/**
 * `Map` and `Model3D` (04 §10). Map: OpenStreetMap tiles through osmdroid, keyless (01 §31 open
 * question 4, settled in spec 1.6). Model3D: a glTF binary drawn by Filament's `ModelViewer`, the
 * engine SceneView wraps. Both are Android views, released when they leave composition (05 §6.6).
 */
object Spatial {

    // ---- Map ------------------------------------------------------------------------------------

    private data class Pin(val point: GeoPoint, val title: String?, val tone: String?, val onTap: WValue?)

    private fun point(v: WValue?): GeoPoint? {
        val r = v as? WRec ?: return null
        val lat = (r["lat"] as? WNum)?.v ?: return null
        val lng = ((r["lng"] ?: r["lon"]) as? WNum)?.v ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoPoint(lat, lng)
    }

    /** A place name centre, resolved with the platform geocoder off the main thread; null if it can't. */
    @Suppress("DEPRECATION")
    private suspend fun geocode(context: android.content.Context, place: String): GeoPoint? = withContext(Dispatchers.IO) {
        if (!android.location.Geocoder.isPresent()) return@withContext null
        runCatching {
            android.location.Geocoder(context).getFromLocationName(place, 1)?.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
        }.getOrNull()
    }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    fun MapR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val context = LocalContext.current
        val scheme = MaterialTheme.colorScheme
        val h = TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)
        val zoom = ((node.args["zoom"] as? WNum)?.v ?: 12.0).coerceIn(2.0, 19.0)
        val pins = node.list("markers").orEmpty().mapNotNull { m ->
            val r = m as? WRec ?: return@mapNotNull null
            point(r)?.let { Pin(it, (r["title"] as? WStr)?.v, r["tone"].word(), r["onTap"].takeIf { a -> a != com.echoflow.weave.core.eval.WNull }) }
        }
        val route = node.list("route").orEmpty().mapNotNull { point(it) }
        val placeName = (node.args["center"] as? WStr)?.v
        var center by remember(node.args["center"], pins, route) { mutableStateOf(point(node.args["center"]) ?: pins.firstOrNull()?.point ?: route.firstOrNull()) }
        LaunchedEffect(placeName) { if (center == null && placeName != null) center = geocode(context, placeName) }
        // 04 §10: a non-interactive map doesn't take scroll gestures until it is tapped.
        var active by remember { mutableStateOf(node.bool("interactive", false)) }
        val controller by rememberUpdatedState(ctx.controller)
        val label = node.str("label") ?: listOfNotNull(placeName, pins.takeIf { it.isNotEmpty() }?.joinToString { it.title ?: "pin" }).joinToString(": ").ifEmpty { "Map" }

        Surface(modifier.fillMaxWidth().height(h), shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHigh) {
            Box(Modifier.semantics { contentDescription = label }) {
                AndroidView(
                    factory = { c ->
                        Configuration.getInstance().apply {
                            userAgentValue = c.packageName
                            // tiles cache in the app's own cache dir: no storage permission
                            osmdroidBasePath = java.io.File(c.cacheDir, "osmdroid")
                            osmdroidTileCache = java.io.File(c.cacheDir, "osmdroid/tiles")
                        }
                        MapView(c).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            isTilesScaledToDpi = true
                        }
                    },
                    update = { map ->
                        map.controller.setZoom(zoom)
                        center?.let { map.controller.setCenter(it) }
                        map.overlays.clear()
                        if (route.size >= 2) map.overlays.add(Polyline(map).apply {
                            setPoints(route)
                            outlinePaint.color = scheme.primary.toArgb()
                            outlinePaint.strokeWidth = 4f * map.resources.displayMetrics.density
                        })
                        for (pin in pins) map.overlays.add(Marker(map).apply {
                            position = pin.point
                            title = pin.title
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            val tint = TokenResolver.tone(pin.tone ?: "primary", "high", scheme, ctx.dark)?.container ?: scheme.primary
                            icon = icon?.mutate()?.also { it.setTint(tint.toArgb()) }
                            setOnMarkerClickListener { m, _ ->
                                val action = pin.onTap
                                if (action != null) controller.fire(action) else m.showInfoWindow()
                                true
                            }
                        })
                        // Once activated, the map keeps its gestures instead of scrolling the chat.
                        map.setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                        map.invalidate()
                    },
                    onRelease = { it.onDetach() },
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                )
                if (!active) Box(Modifier.fillMaxSize().clickable(onClickLabel = "Move the map") { active = true })
                if (center == null) Text(
                    placeName ?: "Map", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
                // OpenStreetMap's licence asks for visible attribution.
                Text(
                    "© OpenStreetMap contributors", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                )
            }
        }
    }

    // ---- Model3D --------------------------------------------------------------------------------

    /** 04 §10: https only, at most 10 MB. */
    const val MODEL_MAX_BYTES = 10L * 1024 * 1024

    private val filamentReady by lazy { runCatching { Utils.init() }.isSuccess }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    fun Model3DR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val src = Media.https(node.str("src"))
        val h = TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)
        val autoRotate = node.bool("autoRotate", true)
        var bytes by remember(src) { mutableStateOf<ByteBuffer?>(null) }
        var failed by remember(src) { mutableStateOf(src == null || !filamentReady) }
        LaunchedEffect(src) {
            if (src == null || !filamentReady) return@LaunchedEffect
            val got = com.echoflow.weave.runtime.WeaveTools.fetchBytes(src, MODEL_MAX_BYTES)
            if (got == null) failed = true else bytes = ByteBuffer.allocateDirect(got.size).put(got).also { it.rewind() }
        }
        val scheme = MaterialTheme.colorScheme
        Surface(modifier.fillMaxWidth().height(h), shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHigh) {
            val buffer = bytes
            when {
                failed -> Box(Modifier.fillMaxSize().clickable { src?.let { ctx.controller.requestUrl(it) } }, contentAlignment = Alignment.Center) {
                    Text("Couldn't load the 3D model", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                buffer == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> ModelView(buffer, autoRotate, node.str("label") ?: "3D model", scheme.surfaceContainerHigh.toArgb()) { failed = true }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Composable
    private fun ModelView(buffer: ByteBuffer, autoRotate: Boolean, label: String, clear: Int, onError: () -> Unit) {
        var viewer by remember { mutableStateOf<ModelViewer?>(null) }
        var touching by remember { mutableStateOf(false) }
        AndroidView(
            factory = { c ->
                TextureView(c).apply {
                    isOpaque = false
                    val mv = ModelViewer(this)
                    runCatching {
                        mv.loadModelGlb(buffer)
                        mv.transformToUnitCube()
                    }.onFailure { onError() }
                    val r = android.graphics.Color.red(clear) / 255.0
                    val g = android.graphics.Color.green(clear) / 255.0
                    val b = android.graphics.Color.blue(clear) / 255.0
                    mv.renderer.clearOptions = mv.renderer.clearOptions.apply { clearColor = doubleArrayOf(r, g, b, 1.0); this.clear = true }
                    setOnTouchListener { v, e ->
                        touching = e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
                        v.parent?.requestDisallowInterceptTouchEvent(touching)
                        mv.onTouchEvent(e)
                        true
                    }
                    viewer = mv
                }
            },
            onRelease = { viewer?.destroy(); viewer = null },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = label },
        )
        // Frames only while composed: off-screen blocks leave composition and stop (05 §6.6).
        LaunchedEffect(viewer, autoRotate) {
            val mv = viewer ?: return@LaunchedEffect
            var angle = 0.0
            var last = 0L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    if (autoRotate && !touching && last != 0L) {
                        angle += (now - last) / 1e9 * 0.6
                        mv.asset?.let { asset ->
                            val tm = mv.engine.transformManager
                            val inst = tm.getInstance(asset.root)
                            // transformToUnitCube set a scale+translate; spin about Y on top of it.
                            mv.transformToUnitCube()
                            val base = FloatArray(16).also { tm.getTransform(inst, it) }
                            val rot = FloatArray(16).also { android.opengl.Matrix.setRotateM(it, 0, Math.toDegrees(angle).toFloat(), 0f, 1f, 0f) }
                            val out = FloatArray(16).also { android.opengl.Matrix.multiplyMM(it, 0, rot, 0, base, 0) }
                            tm.setTransform(inst, out)
                        }
                    }
                    last = now
                    mv.render(now)
                }
            }
        }
        DisposableEffect(Unit) { onDispose { viewer?.destroy(); viewer = null } }
    }
}
