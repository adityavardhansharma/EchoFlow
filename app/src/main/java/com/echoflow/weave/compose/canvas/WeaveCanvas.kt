package com.echoflow.weave.compose.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WValue
import kotlin.math.max
import kotlin.math.min

/** `Canvas` (04 §13): marks in view units mapped onto the component, with tap/drag/pinch. */
object WeaveCanvas {

    private data class Mapping(val sx: Float, val ox: Float, val oy: Float) {
        fun x(v: Double) = (ox + v * sx).toFloat()
        fun y(v: Double) = (oy + v * sx).toFloat()
        fun len(v: Double) = (v * sx).toFloat()
        fun toView(p: Offset) = Pair((p.x - ox) / sx, (p.y - oy) / sx)
        fun toViewD(p: Offset) = Pair(((p.x - ox) / sx).toDouble(), ((p.y - oy) / sx).toDouble())
    }

    private fun mapping(view: List<Double>, w: Float, h: Float, cover: Boolean): Mapping {
        val vx = view.getOrElse(0) { 0.0 }; val vy = view.getOrElse(1) { 0.0 }
        val vw = view.getOrElse(2) { 100.0 }.coerceAtLeast(1e-6); val vh = view.getOrElse(3) { 60.0 }.coerceAtLeast(1e-6)
        val s = if (cover) max(w / vw, h / vh) else min(w / vw, h / vh)
        val ox = (w - vw * s) / 2 - vx * s; val oy = (h - vh * s) / 2 - vy * s
        return Mapping(s.toFloat(), ox.toFloat(), oy.toFloat())
    }

    @Composable
    fun CanvasR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val view = (node.args["view"] as? WList)?.items?.mapNotNull { (it as? WNum)?.v } ?: listOf(0.0, 0.0, 100.0, 60.0)
        val cover = node.word("fit") == "cover"
        val h = TokenResolver.heightOr(node.args["h"], "l", ctx.screenHeight)
        val measurer = rememberTextMeasurer()
        val marks = node.children
        val painters = marks.filter { it.type == "icon" }.associate { it.str("name").orEmpty() to rememberVectorPainter(TokenResolver.icon(it.str("name") ?: it.word("name"))) }
        fun call(key: String, p: WRec) {
            val f = node.args[key] as? WFn ?: return
            ctx.controller.callFn(f, listOf(p))?.let { ctx.fire(it) }
        }
        Canvas(
            modifier.fillMaxWidth().height(h).semantics { contentDescription = node.str("label") ?: "Drawing" }
                .pointerInput(marks, view) {
                    detectTapGestures { off ->
                        val m = mapping(view, size.width.toFloat(), size.height.toFloat(), cover)
                        val (x, y) = m.toViewD(off)
                        // topmost mark with onTap whose bounds (at least 48 dp) contain the point
                        val hit = marks.asReversed().firstOrNull { it.args["onTap"] != null && bounds(it, m).inflate(max(0f, (48.dp.toPx() - bounds(it, m).width) / 2)).contains(off) }
                        if (hit != null) ctx.fire(hit.args["onTap"]) else call("onTap", WRec.of("x" to WNum.of(x), "y" to WNum.of(y)))
                    }
                }
                .pointerInput(view) {
                    if (node.args["onDrag"] == null && node.args["onDragEnd"] == null) return@pointerInput
                    var last = Offset.Zero
                    detectDragGestures(
                        onDragStart = { o ->
                            last = o
                            val m = mapping(view, size.width.toFloat(), size.height.toFloat(), cover); val (x, y) = m.toViewD(o)
                            call("onDrag", WRec.of("x" to WNum.of(x), "y" to WNum.of(y), "dx" to WNum(0.0), "dy" to WNum(0.0), "start" to WBool.TRUE))
                        },
                        onDragEnd = {
                            val m = mapping(view, size.width.toFloat(), size.height.toFloat(), cover); val (x, y) = m.toViewD(last)
                            call("onDragEnd", WRec.of("x" to WNum.of(x), "y" to WNum.of(y))); ctx.controller.endDrag()
                        },
                    ) { c, d ->
                        c.consume(); last = c.position
                        val m = mapping(view, size.width.toFloat(), size.height.toFloat(), cover); val (x, y) = m.toViewD(c.position)
                        call("onDrag", WRec.of("x" to WNum.of(x), "y" to WNum.of(y), "dx" to WNum.of(d.x / m.sx.toDouble()), "dy" to WNum.of(d.y / m.sx.toDouble()), "start" to WBool.FALSE))
                    }
                }
                .pointerInput(view) {
                    if (node.args["onPinch"] == null) return@pointerInput
                    detectTransformGestures { centroid, _, zoom, _ ->
                        val m = mapping(view, size.width.toFloat(), size.height.toFloat(), cover); val (x, y) = m.toViewD(centroid)
                        call("onPinch", WRec.of("scale" to WNum.of(zoom.toDouble()), "cx" to WNum.of(x), "cy" to WNum.of(y)))
                    }
                },
        ) {
            val m = mapping(view, size.width, size.height, cover)
            marks.forEach { drawMark(it, m, ctx, scheme, measurer, painters) }
        }
    }

    private fun d(n: WNode, k: String, def: Double = 0.0) = n.num(k) ?: def

    private fun bounds(n: WNode, m: Mapping): Rect = when (n.type) {
        "circle" -> Rect(Offset(m.x(d(n, "x")), m.y(d(n, "y"))), m.len(d(n, "r")))
        "rect", "image" -> Rect(m.x(d(n, "x")), m.y(d(n, "y")), m.x(d(n, "x") + d(n, "w")), m.y(d(n, "y") + d(n, "h")))
        "line" -> Rect(min(m.x(d(n, "x1")), m.x(d(n, "x2"))), min(m.y(d(n, "y1")), m.y(d(n, "y2"))), max(m.x(d(n, "x1")), m.x(d(n, "x2"))), max(m.y(d(n, "y1")), m.y(d(n, "y2"))))
        else -> Rect(Offset(m.x(d(n, "x")), m.y(d(n, "y"))), 12f)
    }

    private fun DrawScope.drawMark(n: WNode, m: Mapping, ctx: RenderCtx, scheme: androidx.compose.material3.ColorScheme, measurer: TextMeasurer, painters: Map<String, androidx.compose.ui.graphics.vector.VectorPainter>) {
        fun color(v: WValue?) = TokenResolver.color(v, scheme, ctx.dark, fill = true)
        val fill = color(n.args["fill"]); val stroke = color(n.args["stroke"])
        val strokeW = when (val w = n.args["width"]) { is WNum -> m.len(w.v); else -> when (w.word()) { "m" -> 2.dp.toPx(); "l" -> 3.dp.toPx(); else -> 1.dp.toPx() } }
        val defFill = if (fill == null && stroke == null) scheme.primary else fill
        when (n.type) {
            "circle" -> {
                val c = Offset(m.x(d(n, "x")), m.y(d(n, "y"))); val r = m.len(d(n, "r"))
                defFill?.let { drawCircle(it, r, c) }; stroke?.let { drawCircle(it, r, c, style = Stroke(strokeW)) }
            }
            "rect" -> {
                val tl = Offset(m.x(d(n, "x")), m.y(d(n, "y"))); val sz = Size(m.len(d(n, "w")), m.len(d(n, "h"))); val r = CornerRadius(m.len(d(n, "radius")))
                defFill?.let { drawRoundRect(it, tl, sz, r) }; stroke?.let { drawRoundRect(it, tl, sz, r, style = Stroke(strokeW)) }
            }
            "line" -> drawLine(
                stroke ?: scheme.onSurface, Offset(m.x(d(n, "x1")), m.y(d(n, "y1"))), Offset(m.x(d(n, "x2")), m.y(d(n, "y2"))), strokeW,
                cap = if (n.word("cap") == "butt") StrokeCap.Butt else StrokeCap.Round,
                pathEffect = if (n.args["dashed"] == WBool.TRUE) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null,
            )
            "path" -> {
                val p = runCatching { PathParser.createPathFromPathData(n.str("d") ?: "").asComposePath() }.getOrNull() ?: return
                translate(m.ox, m.oy) { scale(m.sx, m.sx, Offset.Zero) {
                    defFill?.let { drawPath(p, it) }; stroke?.let { drawPath(p, it, style = Stroke(strokeW / m.sx)) }
                } }
            }
            "poly" -> {
                val pts = (n.args["points"] as? WList)?.items?.mapNotNull { pt -> (pt as? WList)?.items?.let { xy -> Offset(m.x((xy.getOrNull(0) as? WNum)?.v ?: 0.0), m.y((xy.getOrNull(1) as? WNum)?.v ?: 0.0)) } } ?: return
                if (pts.size < 2) return
                val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; if (n.args["closed"] != WBool.FALSE) close() }
                if (n.args["closed"] != WBool.FALSE) defFill?.let { drawPath(p, it) }
                (stroke ?: if (n.args["closed"] == WBool.FALSE) scheme.onSurface else null)?.let { drawPath(p, it, style = Stroke(strokeW)) }
            }
            "label" -> {
                val style = TokenResolver.text(n.args["style"], androidx.compose.material3.Typography(), "labelMedium")
                val c = color(n.args["color"]) ?: TokenResolver.toneText(n.word("tone"), scheme, ctx.dark) ?: scheme.onSurface
                val t = measurer.measure(ctx.fmt(n.args["content"]), style.copy(color = c))
                val ax = when (n.word("anchor")) { "start" -> 0f; "end" -> t.size.width.toFloat(); else -> t.size.width / 2f }
                drawText(t, topLeft = Offset(m.x(d(n, "x")) - ax, m.y(d(n, "y")) - t.size.height / 2f))
            }
            "icon" -> {
                val p = painters[n.str("name").orEmpty()] ?: return
                val s = when (n.word("size")) { "s" -> 18.dp; "l" -> 32.dp; else -> 24.dp }.toPx()
                translate(m.x(d(n, "x")) - s / 2, m.y(d(n, "y")) - s / 2) {
                    with(p) { draw(Size(s, s), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TokenResolver.toneText(n.word("tone"), scheme, ctx.dark) ?: scheme.onSurface)) }
                }
            }
            "image" -> drawRoundRect(scheme.surfaceContainerHigh, Offset(m.x(d(n, "x")), m.y(d(n, "y"))), Size(m.len(d(n, "w")), m.len(d(n, "h"))), CornerRadius(4f))
            "group" -> {
                val t = (n.args["translate"] as? WList)?.items?.mapNotNull { (it as? WNum)?.v } ?: listOf(0.0, 0.0)
                val o = (n.args["origin"] as? WList)?.items?.mapNotNull { (it as? WNum)?.v } ?: listOf(0.0, 0.0)
                val pivot = Offset(m.x(o.getOrElse(0) { 0.0 }), m.y(o.getOrElse(1) { 0.0 }))
                translate(m.len(t.getOrElse(0) { 0.0 }), m.len(t.getOrElse(1) { 0.0 })) {
                    rotate(d(n, "rotate").toFloat(), pivot) {
                        scale(d(n, "scale", 1.0).toFloat(), d(n, "scale", 1.0).toFloat(), pivot) {
                            n.children.forEach { drawMark(it, m, ctx, scheme, measurer, painters) }
                        }
                    }
                }
            }
        }
    }
}
