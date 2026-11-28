package com.echoflow.weave.compose.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WValue
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Phase 2 chart sugars (04 §12.4): Radar, Funnel, Treemap, Sankey, Candles, BoxPlot, Heatmap. */
object ChartsMore {

    @Composable
    fun Render(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val data = node.list("data")?.filterIsInstance<WRec>() ?: emptyList()
        val call: (WFn, WRec) -> WValue? = { f, r -> ctx.controller.callFn(f, listOf(r)) }
        fun f(r: WRec, k: String) = Charts.field(r, node.args[k] ?: WNull, call)
        fun n(r: WRec, k: String) = (f(r, k) as? WNum)?.v ?: 0.0
        val measurer = rememberTextMeasurer()
        val label = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
        val onLabel = MaterialTheme.typography.labelSmall.copy(color = scheme.surface)
        val h = TokenResolver.heightOr(node.args["h"], if (node.type == "Sankey") "l" else "m", ctx.screenHeight)
        Canvas(modifier.fillMaxWidth().height(h).semantics { contentDescription = "${node.type} chart, ${data.size} points" }) {
            when (node.type) {
                "Radar" -> {
                    val axes = node.list("axes")?.map { ctx.fmt(it) } ?: return@Canvas
                    if (axes.isEmpty()) return@Canvas
                    val r = size.minDimension / 2 * 0.8f; val c = center
                    val maxV = data.flatMap { row -> axes.map { (row[it] as? WNum)?.v ?: 0.0 } }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                    fun pt(i: Int, k: Double) = Math.toRadians(i * 360.0 / axes.size - 90).let { a -> Offset(c.x + (r * k * cos(a)).toFloat(), c.y + (r * k * sin(a)).toFloat()) }
                    for (ring in 1..4) drawPath(Path().apply { axes.indices.forEach { i -> val p = pt(i, ring / 4.0); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }, scheme.outlineVariant, style = Stroke(1.dp.toPx()))
                    axes.forEachIndexed { i, a -> val p = pt(i, 1.1); val t = measurer.measure(a, label); drawText(t, topLeft = Offset(p.x - t.size.width / 2, p.y - t.size.height / 2)) }
                    data.forEachIndexed { si, row ->
                        val col = TokenResolver.series(si, scheme)
                        val path = Path().apply { axes.forEachIndexed { i, a -> val p = pt(i, ((row[a] as? WNum)?.v ?: 0.0) / maxV); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                        drawPath(path, col.copy(alpha = 0.25f)); drawPath(path, col, style = Stroke(2.dp.toPx()))
                    }
                }
                "Funnel" -> {
                    val maxV = data.maxOfOrNull { n(it, "value") }?.takeIf { it > 0 } ?: return@Canvas
                    val bh = size.height / max(1, data.size)
                    data.forEachIndexed { i, r ->
                        val w = (n(r, "value") / maxV * size.width).toFloat()
                        drawRoundRect(TokenResolver.series(i % 4, scheme), Offset((size.width - w) / 2, i * bh + 2), Size(w, bh - 4), CornerRadius(6.dp.toPx()))
                        val t = measurer.measure("${ctx.fmt(f(r, "label"))} · ${ctx.fmt(f(r, "value"))}", onLabel)
                        drawText(t, topLeft = Offset(center.x - t.size.width / 2, i * bh + bh / 2 - t.size.height / 2))
                    }
                }
                "Treemap" -> {
                    val items = data.map { it to n(it, "value").coerceAtLeast(0.0) }.filter { it.second > 0 }.sortedByDescending { it.second }
                    var x = 0f; var y = 0f; var w = size.width; var hh = size.height
                    var remaining = items.sumOf { it.second }
                    items.forEachIndexed { i, (r, v) ->
                        val frac = (v / remaining).toFloat()
                        val horizontal = w >= hh
                        val rw = if (horizontal) w * frac else w; val rh = if (horizontal) hh else hh * frac
                        drawRoundRect(TokenResolver.series(i % 8, scheme), Offset(x + 1, y + 1), Size(rw - 2, rh - 2), CornerRadius(4.dp.toPx()))
                        val t = measurer.measure(ctx.fmt(f(r, "label")), onLabel)
                        if (t.size.width < rw && t.size.height < rh) drawText(t, topLeft = Offset(x + 6, y + 4))
                        if (horizontal) { x += rw; w -= rw } else { y += rh; hh -= rh }
                        remaining -= v
                    }
                }
                "Sankey" -> {
                    val froms = data.map { ctx.fmt(f(it, "from")) }.distinct(); val tos = data.map { ctx.fmt(f(it, "to")) }.distinct()
                    val total = data.sumOf { n(it, "value") }.takeIf { it > 0 } ?: return@Canvas
                    fun ys(keys: List<String>, field: String): Map<String, Pair<Float, Float>> {
                        var y = 0f; val out = HashMap<String, Pair<Float, Float>>()
                        keys.forEach { k -> val v = data.filter { ctx.fmt(f(it, field)) == k }.sumOf { n(it, "value") }; val hgt = (v / total * (size.height - 8 * keys.size)).toFloat(); out[k] = y to hgt; y += hgt + 8 }
                        return out
                    }
                    val ly = ys(froms, "from"); val ry = ys(tos, "to")
                    val lo = HashMap<String, Float>(); val ro = HashMap<String, Float>()
                    val nodeW = 12.dp.toPx()
                    data.forEachIndexed { i, r ->
                        val a = ctx.fmt(f(r, "from")); val b = ctx.fmt(f(r, "to"))
                        val th = (n(r, "value") / total * (size.height - 8 * max(froms.size, tos.size))).toFloat()
                        val y0 = (ly[a]?.first ?: 0f) + (lo[a] ?: 0f); val y1 = (ry[b]?.first ?: 0f) + (ro[b] ?: 0f)
                        lo[a] = (lo[a] ?: 0f) + th; ro[b] = (ro[b] ?: 0f) + th
                        val p = Path().apply {
                            moveTo(nodeW, y0); cubicTo(size.width / 2, y0, size.width / 2, y1, size.width - nodeW, y1)
                            lineTo(size.width - nodeW, y1 + th); cubicTo(size.width / 2, y1 + th, size.width / 2, y0 + th, nodeW, y0 + th); close()
                        }
                        drawPath(p, TokenResolver.series(froms.indexOf(a) % 8, scheme).copy(alpha = 0.35f))
                    }
                    ly.forEach { (k, v) -> drawRect(scheme.primary, Offset(0f, v.first), Size(nodeW, v.second)); drawText(measurer.measure(k, label), topLeft = Offset(nodeW + 4, v.first)) }
                    ry.forEach { (k, v) -> drawRect(scheme.tertiary, Offset(size.width - nodeW, v.first), Size(nodeW, v.second)); val t = measurer.measure(k, label); drawText(t, topLeft = Offset(size.width - nodeW - 4 - t.size.width, v.first)) }
                }
                "Candles", "BoxPlot" -> {
                    val groups: List<Pair<String, List<Double>>> = if (node.type == "Candles") data.map { ctx.fmt(f(it, "x")) to listOf(n(it, "open"), n(it, "high"), n(it, "low"), n(it, "close")) }
                        else data.groupBy { ctx.fmt(f(it, "x")) }.map { (k, rows) ->
                            val v = rows.map { n(it, "y") }.sorted()
                            fun q(p: Double) = if (v.isEmpty()) 0.0 else v[((v.size - 1) * p).toInt()]
                            k to listOf(q(0.0), q(0.25), q(0.5), q(0.75), q(1.0))
                        }
                    val all = groups.flatMap { it.second }
                    val lo = all.minOrNull() ?: return@Canvas; val hi = all.maxOrNull() ?: return@Canvas
                    val span = (hi - lo).takeIf { it > 0 } ?: 1.0
                    fun py(v: Double) = (size.height - (v - lo) / span * size.height).toFloat()
                    val bw = size.width / max(1, groups.size)
                    val up = TokenResolver.semantic(145f, scheme, ctx.dark).solid
                    groups.forEachIndexed { i, (_, v) ->
                        val cx = bw * (i + 0.5f)
                        if (node.type == "Candles") {
                            val (o, hgh, low, cl) = v
                            val col = if (cl >= o) up else scheme.error
                            drawLine(col, Offset(cx, py(hgh)), Offset(cx, py(low)), 1.5f.dp.toPx())
                            drawRect(col, Offset(cx - bw * 0.3f, py(max(o, cl))), Size(bw * 0.6f, max(2f, py(min(o, cl)) - py(max(o, cl)))))
                        } else {
                            drawLine(scheme.onSurfaceVariant, Offset(cx, py(v[0])), Offset(cx, py(v[4])), 1.5f.dp.toPx())
                            drawRect(scheme.primaryContainer, Offset(cx - bw * 0.3f, py(v[3])), Size(bw * 0.6f, py(v[1]) - py(v[3])))
                            drawLine(scheme.primary, Offset(cx - bw * 0.3f, py(v[2])), Offset(cx + bw * 0.3f, py(v[2])), 2.dp.toPx())
                        }
                    }
                }
                "Heatmap" -> {
                    val xs = data.map { ctx.fmt(f(it, "x")) }.distinct(); val ys = data.map { ctx.fmt(f(it, "y")) }.distinct()
                    val vals = data.map { n(it, "value") }; val lo = vals.minOrNull() ?: 0.0; val hi = vals.maxOrNull() ?: 1.0
                    val cw = size.width / max(1, xs.size); val ch = size.height / max(1, ys.size)
                    data.forEach { r ->
                        val k = ((n(r, "value") - lo) / (hi - lo).takeIf { it > 0 }.let { it ?: 1.0 }).toFloat()
                        drawRect(scheme.primary.copy(alpha = 0.1f + 0.9f * k), Offset(xs.indexOf(ctx.fmt(f(r, "x"))) * cw + 1, ys.indexOf(ctx.fmt(f(r, "y"))) * ch + 1), Size(cw - 2, ch - 2))
                    }
                }
            }
        }
    }
}
