package com.echoflow.weave.compose.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.echoflow.weave.compose.render.Placeholders
import com.echoflow.weave.compose.render.RenderChildren
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.catalog.PlaceholderKind
import com.echoflow.weave.core.eval.WNode
import kotlin.math.cos
import kotlin.math.sin

/** Visual components, 04 §14. */
object Visual {

    @Composable
    fun GradientR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val colors = node.list("tones")?.mapNotNull { TokenResolver.color(it, scheme, ctx.dark, fill = true) }?.takeIf { it.size >= 2 }
            ?: listOf(scheme.primaryContainer, scheme.tertiaryContainer)
        val angle = Math.toRadians(node.num("angle") ?: 135.0)
        val h = TokenResolver.height(node.args["h"], ctx.screenHeight)
        Box(
            modifier.fillMaxWidth().then(if (h != null) Modifier.height(h) else Modifier).clip(TokenResolver.shape(node.args["shape"], MaterialTheme.shapes.medium))
                .drawBehind {
                    val dx = (cos(angle) * size.width / 2).toFloat(); val dy = (sin(angle) * size.height / 2).toFloat()
                    drawRect(Brush.linearGradient(colors, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy)))
                }.padding(16.dp),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onPrimaryContainer) {
                Column { RenderChildren(node.children, ctx) }
            }
        }
    }

    private val SHADERS = mapOf(
        "aurora" to """
            uniform float2 resolution; uniform float time; layout(color) uniform half4 c0; layout(color) uniform half4 c1; layout(color) uniform half4 c2;
            half4 main(float2 p) { float2 uv = p / resolution; float w = 0.5 + 0.5 * sin(uv.x * 6.0 + time * 0.8 + sin(uv.y * 4.0 + time));
              float v = 0.5 + 0.5 * sin(uv.y * 5.0 - time * 0.6); return mix(mix(c0, c1, w), c2, v * 0.6); }""",
        "mesh" to """
            uniform float2 resolution; uniform float time; layout(color) uniform half4 c0; layout(color) uniform half4 c1; layout(color) uniform half4 c2;
            half4 main(float2 p) { float2 uv = p / resolution; float2 a = float2(0.3 + 0.2 * sin(time * 0.5), 0.3); float2 b = float2(0.7, 0.7 + 0.2 * cos(time * 0.4));
              float da = distance(uv, a); float db = distance(uv, b); return mix(mix(c0, c1, smoothstep(0.0, 0.8, da)), c2, smoothstep(0.6, 0.0, db)); }""",
        "grain" to """
            uniform float2 resolution; uniform float time; layout(color) uniform half4 c0; layout(color) uniform half4 c1; layout(color) uniform half4 c2;
            float h(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233)) + time) * 43758.5453); }
            half4 main(float2 p) { float2 uv = p / resolution; half4 base = mix(c0, c1, uv.y); return base + half4((h(p) - 0.5) * 0.08); }""",
        "ripple" to """
            uniform float2 resolution; uniform float time; layout(color) uniform half4 c0; layout(color) uniform half4 c1; layout(color) uniform half4 c2;
            half4 main(float2 p) { float2 uv = p / resolution - 0.5; float r = length(uv); float w = 0.5 + 0.5 * sin(r * 30.0 - time * 3.0); return mix(c0, c1, w * (1.0 - r)); }""",
        "liquid" to """
            uniform float2 resolution; uniform float time; layout(color) uniform half4 c0; layout(color) uniform half4 c1; layout(color) uniform half4 c2;
            half4 main(float2 p) { float2 uv = p / resolution; float n = sin(uv.x * 3.0 + time) * cos(uv.y * 4.0 - time * 0.7) + sin((uv.x + uv.y) * 5.0 + time * 0.5);
              return mix(mix(c0, c1, 0.5 + 0.25 * n), c2, 0.5 + 0.5 * sin(n + time * 0.3)); }""",
    )

    /** AGSL shaders on API 33+ (04 §14); older devices get the gradient fallback. */
    @Composable
    fun ShaderR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val tones = node.list("tones")?.mapNotNull { TokenResolver.color(it, scheme, ctx.dark, fill = true) }.orEmpty()
        val c = (tones + listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer)).take(3)
        val h = TokenResolver.heightOr(node.args["h"], "m", ctx.screenHeight)
        val shape = TokenResolver.shape(node.args["shape"], MaterialTheme.shapes.medium)
        if (Build.VERSION.SDK_INT < 33) {
            Box(modifier.fillMaxWidth().height(h).clip(shape).background(Brush.linearGradient(c)).padding(16.dp)) { Column { RenderChildren(node.children, ctx) } }
            return
        }
        val src = SHADERS[node.word("kind")] ?: SHADERS.getValue("aurora")
        val shader = remember(src) { runCatching { RuntimeShader(src) }.getOrNull() }
        var time by remember { mutableFloatStateOf(0f) }
        val speed = (node.num("speed") ?: 1.0).toFloat()
        LaunchedEffect(speed) { val start = withFrameNanos { it }; while (true) withFrameNanos { time = (it - start) / 1e9f * speed } }
        Box(
            modifier.fillMaxWidth().height(h).clip(shape).drawBehind {
                val s = shader ?: run { drawRect(Brush.linearGradient(c)); return@drawBehind }
                s.setFloatUniform("resolution", size.width, size.height)
                s.setFloatUniform("time", time)
                s.setColorUniform("c0", c[0].toArgb()); s.setColorUniform("c1", c[1].toArgb()); s.setColorUniform("c2", c[2].toArgb())
                drawRect(ShaderBrush(s))
            }.padding(16.dp),
        ) { Column { RenderChildren(node.children, ctx) } }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    fun polygon(name: String?): RoundedPolygon = when (name) {
        "square" -> MaterialShapes.Square; "slanted" -> MaterialShapes.Slanted; "arch" -> MaterialShapes.Arch; "fan" -> MaterialShapes.Fan
        "oval" -> MaterialShapes.Oval; "pill" -> MaterialShapes.Pill; "triangle" -> MaterialShapes.Triangle; "diamond" -> MaterialShapes.Diamond
        "pentagon" -> MaterialShapes.Pentagon; "gem" -> MaterialShapes.Gem; "sunny" -> MaterialShapes.Sunny; "verySunny" -> MaterialShapes.VerySunny
        "cookie" -> MaterialShapes.Cookie9Sided; "cookie4" -> MaterialShapes.Cookie4Sided; "cookie12" -> MaterialShapes.Cookie12Sided
        "clover" -> MaterialShapes.Clover4Leaf; "clover8" -> MaterialShapes.Clover8Leaf; "burst" -> MaterialShapes.Burst; "softBurst" -> MaterialShapes.SoftBurst
        "boom" -> MaterialShapes.Boom; "flower" -> MaterialShapes.Flower; "puffy" -> MaterialShapes.Puffy; "ghost" -> MaterialShapes.Ghostish
        "bun" -> MaterialShapes.Bun; "heart" -> MaterialShapes.Heart
        else -> MaterialShapes.Circle
    }

    @Composable
    fun ShapeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val size = when (node.word("size")) { "s" -> 32.dp; "l" -> 72.dp; "xl" -> 112.dp; else -> 48.dp }
        val pair = TokenResolver.tone(node.word("tone") ?: "primary", node.word("emphasis") ?: "medium", MaterialTheme.colorScheme, ctx.dark)
        val from = polygon(node.word("kind")); val to = node.word("morphTo")?.let { polygon(it) }
        var morphed by remember { mutableStateOf(false) }
        val p by animateFloatAsState(if (morphed) 1f else 0f, label = "morph")
        val morph = remember(from, to) { to?.let { Morph(from, it) } }
        val fill = pair?.container ?: MaterialTheme.colorScheme.primaryContainer
        Box(modifier.size(size).rotate((node.num("rotate") ?: 0.0).toFloat()).then(if (morph != null) Modifier.clickable { morphed = !morphed } else Modifier), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val path: Path = (morph?.toPath(progress = p) ?: from.toPath()).asComposePath()
                val bounds = path.getBounds()
                val s = this.size.minDimension / maxOf(bounds.width, bounds.height).coerceAtLeast(1e-3f)
                drawContext.canvas.save()
                drawContext.canvas.translate(this.size.width / 2, this.size.height / 2)
                drawContext.canvas.scale(s, s)
                drawContext.canvas.translate(-bounds.center.x, -bounds.center.y)
                drawPath(path, fill)
                drawContext.canvas.restore()
            }
            node.str("icon")?.let { Icon(TokenResolver.icon(it), null, Modifier.size(size * 0.5f), tint = pair?.content ?: MaterialTheme.colorScheme.onPrimaryContainer) }
        }
    }

    @Composable
    fun BlobR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val pair = TokenResolver.tone(node.word("tone") ?: "primary", "medium", MaterialTheme.colorScheme, ctx.dark)
        val seed = ctx.fmt(node.args["seed"])
        val radii = remember(seed) { (0 until 8).map { i -> 0.7 + 0.3 * com.echoflow.weave.core.eval.WeaveRandom.rand(com.echoflow.weave.core.eval.WStr(seed), i.toLong()) } }
        val fill = pair?.container ?: MaterialTheme.colorScheme.primaryContainer
        Canvas(modifier.fillMaxWidth().height(TokenResolver.heightOr(node.args["h"], "m", ctx.screenHeight))) {
            val r = size.minDimension / 2; val c = center
            val pts = radii.mapIndexed { i, k -> val a = i * Math.PI / 4; Offset(c.x + (r * k * cos(a)).toFloat(), c.y + (r * k * sin(a)).toFloat()) }
            val path = Path()
            for (i in pts.indices) {
                val p0 = pts[(i - 1 + pts.size) % pts.size]; val p1 = pts[i]; val p2 = pts[(i + 1) % pts.size]; val p3 = pts[(i + 2) % pts.size]
                if (i == 0) path.moveTo(p1.x, p1.y)
                path.cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
            path.close()
            drawPath(path, fill)
        }
    }

    @Composable
    fun SkeletonR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        when (node.word("kind")) {
            "text" -> Placeholders.Of(PlaceholderKind.TEXT, modifier)
            "chart" -> Placeholders.Of(PlaceholderKind.CHART, modifier)
            "list" -> Placeholders.Of(PlaceholderKind.TABLE, modifier)
            else -> Placeholders.Bar(modifier.fillMaxWidth(), 120.dp, MaterialTheme.shapes.large)
        }
    }
}
