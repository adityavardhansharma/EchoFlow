package com.echoflow.weave.compose.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.rec
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.weaveEquals
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** `Chart` and its sugars (04 §12): custom Canvas drawing so output is identical everywhere. */
object Charts {

    /** One drawable series of one mark. */
    private class Series(val mark: WNode, val name: String, val rows: List<WRec>, val color: Color)

    /** Expands sugar components into `Chart` + marks (04 §12.4). */
    fun expand(node: WNode): WNode {
        if (node.type == "Chart") return node
        val data = node.args["data"] ?: WList.EMPTY
        fun fields(v: WValue?): List<WValue> = when (v) { is WList -> v.items; null, WNull -> emptyList(); else -> listOf(v) }
        val chartArgs = LinkedHashMap<String, WValue>()
        chartArgs["data"] = data
        node.args["h"]?.let { chartArgs["h"] = it }
        listOf("legend", "tooltip", "select", "x2", "y2").forEach { k -> node.args[k]?.let { chartArgs[k] = it } }
        val x = node.args["x"] ?: WNull
        val marks = ArrayList<WNode>()
        fun mark(type: String, vararg a: Pair<String, WValue>) = WNode(type, linkedMapOf(*a).filterValues { it != WNull }, emptyList(), "", node.sourceStmt)
        when (node.type) {
            "LineChart" -> fields(node.args["y"]).forEach { y -> marks += mark("line", "x" to x, "y" to y, "series" to (node.args["series"] ?: WNull), "label" to y) }
            "AreaChart" -> fields(node.args["y"]).forEach { y -> marks += mark("area", "x" to x, "y" to y, "stack" to (node.args["stack"] ?: WNull), "label" to y) }
            "BarChart" -> {
                val ys = fields(node.args["y"])
                ys.forEach { y -> marks += mark("bar", "x" to x, "y" to y, "stack" to (node.args["stack"] ?: WNull), "horizontal" to (node.args["horizontal"] ?: WNull), "label" to y, "groupCount" to WNum.of(ys.size)) }
            }
            "PieChart", "DonutChart" -> {
                chartArgs["polar"] = com.echoflow.weave.core.eval.WBool.TRUE
                if (node.type == "DonutChart") chartArgs["inner"] = WNum(0.6)
                marks += mark("arc", "value" to (node.args["value"] ?: WNull), "label" to (node.args["label"] ?: WNull))
            }
            "ScatterChart" -> marks += mark("point", "x" to x, "y" to (node.args["y"] ?: WNull), "size" to (node.args["size"] ?: WNull))
            "Histogram" -> {
                val values = (node.args["values"] as? WList)?.items?.mapNotNull { (it as? WNum)?.v } ?: emptyList()
                val bins = (node.args["bins"] as? WNum)?.v?.toInt() ?: (ceil(log2(values.size.coerceAtLeast(1).toDouble())) + 1).toInt()
                val rows = histogram(values, bins.coerceIn(1, 60))
                chartArgs["data"] = WList(rows)
                marks += mark("bar", "x" to WStr("bin"), "y" to WStr("count"))
            }
            "Waterfall" -> {
                val rows = (data as? WList)?.items?.filterIsInstance<WRec>() ?: emptyList()
                var run = 0.0
                val out = rows.map { r ->
                    val v = (field(r, node.args["y"] ?: WNull) as? WNum)?.v ?: 0.0
                    val lo = run; run += v
                    WRec.of("x" to field(r, x), "lo" to WNum.of(min(lo, run)), "hi" to WNum.of(max(lo, run)), "tone" to WSym(if (v >= 0) "success" else "error"))
                }
                chartArgs["data"] = WList(out)
                marks += mark("bar", "x" to WStr("x"), "y" to WStr("hi"), "y0" to WStr("lo"), "toneField" to WStr("tone"))
            }
        }
        return WNode("Chart", chartArgs, marks, node.key, node.sourceStmt)
    }

    private fun log2(d: Double) = ln(d) / ln(2.0)

    private fun histogram(values: List<Double>, bins: Int): List<WValue> {
        if (values.isEmpty()) return emptyList()
        val lo = values.min(); val hi = values.max(); val w = ((hi - lo) / bins).takeIf { it > 0 } ?: 1.0
        val counts = IntArray(bins)
        values.forEach { v -> counts[((v - lo) / w).toInt().coerceIn(0, bins - 1)]++ }
        return counts.mapIndexed { i, c -> WRec.of("bin" to WStr(fmtShort(lo + i * w) + "–" + fmtShort(lo + (i + 1) * w)), "count" to WNum.of(c)) }
    }

    private fun fmtShort(d: Double) = if (abs(d - Math.round(d)) < 1e-9) Math.round(d).toString() else String.format(java.util.Locale.US, "%.1f", d)

    fun field(r: WRec, f: WValue, call: ((WFn, WRec) -> WValue?)? = null): WValue = when (f) {
        is WStr -> r[f.v]
        is WSym -> r[f.name]
        is WFn -> call?.invoke(f, r) ?: WNull
        else -> WNull
    }

    private fun numOf(v: WValue): Double? = when (v) {
        is WNum -> v.v
        is WDate -> v.v.toEpochDay().toDouble()
        else -> null
    }

    /** Nice ticks (04 §12.2 step 3). */
    fun nice(lo0: Double, hi0: Double, target: Int = 5): Triple<Double, Double, Double> {
        var lo = lo0; var hi = hi0
        if (hi - lo < 1e-12) { hi = lo + 1; if (lo0 == 0.0) lo = 0.0 }
        val raw = (hi - lo) / target
        val mag = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
        return Triple(floor(lo / step) * step, ceil(hi / step) * step, step)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChartR(node0: WNode, ctx: RenderCtx, modifier: Modifier) {
        val node = expand(node0)
        val scheme = MaterialTheme.colorScheme
        val data = node.list("data")?.filterIsInstance<WRec>() ?: emptyList()
        val h = TokenResolver.heightOr(node.args["h"], "m", ctx.screenHeight)
        val call: (WFn, WRec) -> WValue? = { f, r -> ctx.controller.callFn(f, listOf(r)) }
        val marks = node.children.filter { it.type in MARKS }
        // series (04 §12.3): one per mark, or split by `series`
        var colorIx = 0
        val series = ArrayList<Series>()
        for (m in marks) {
            if (m.type in setOf("rule", "band")) continue
            val split = m.args["series"]
            val explicit = TokenResolver.tone(m.word("tone"), "high", scheme, ctx.dark)?.container
            if (split != null && split != WNull) {
                data.groupBy { ctx.fmt(field(it, split, call)) }.forEach { (k, rows) -> series += Series(m, k, rows, explicit ?: TokenResolver.series(colorIx++, scheme)) }
            } else series += Series(m, ctx.fmt(m.args["label"] ?: m.args["y"]), data, explicit ?: TokenResolver.series(colorIx++, scheme))
        }
        var hidden by remember { mutableStateOf(setOf<String>()) }
        val visibleSeries = series.filter { it.name !in hidden }
        val polar = node.bool("polar") || marks.any { it.type == "arc" }
        val selectPlace = node.places["select"]
        val selected = node.args["select"]
        val progress = remember { Animatable(if (ctx.animateEnter && node.bool("animate", true)) 0f else 1f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(500)) }
        var cursor by remember { mutableStateOf<Int?>(null) }
        val measurer = rememberTextMeasurer()
        val label = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
        val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.inverseOnSurface)

        // x domain
        val xField = marks.firstOrNull { it.args["x"] != null }?.args?.get("x") ?: WNull
        val xs = data.map { field(it, xField, call) }
        val linearX = xs.isNotEmpty() && xs.all { numOf(it) != null }
        val cats = if (linearX) emptyList() else xs.map { ctx.fmt(it) }.distinct()
        val xLo = if (linearX) xs.mapNotNull { numOf(it) }.min() else 0.0
        val xHi = if (linearX) xs.mapNotNull { numOf(it) }.max() else 1.0
        // y domain
        val yAxis = node.rec("y")
        val yValues = ArrayList<Double>()
        for (s in visibleSeries) {
            val m = s.mark
            if (m.type == "arc") continue
            val yf = m.args["y"] ?: continue
            if (m.bool("stack")) {
                // stacked: sum per x across stacked series of the same mark type
                val stacked = visibleSeries.filter { it.mark.type == m.type && it.mark.bool("stack") }
                cats.ifEmpty { xs.map { ctx.fmt(it) }.distinct() }.forEach { c ->
                    yValues += stacked.sumOf { ss -> ss.rows.filter { ctx.fmt(field(it, xField, call)) == c }.sumOf { r -> (field(r, ss.mark.args["y"] ?: WNull, call) as? WNum)?.v ?: 0.0 } }
                }
            } else s.rows.forEach { r -> (field(r, yf, call) as? WNum)?.v?.let { yValues += it } }
            m.args["y0"]?.let { y0 -> s.rows.forEach { r -> (field(r, y0, call) as? WNum)?.v?.let { yValues += it } } }
        }
        marks.filter { it.type == "rule" }.forEach { r -> (r.args["y"] as? WNum)?.v?.let { yValues += it } }
        val rawLo = (yAxis?.get("min") as? WNum)?.v ?: min(0.0, yValues.minOrNull() ?: 0.0)
        val rawHi = (yAxis?.get("max") as? WNum)?.v ?: (yValues.maxOrNull() ?: 1.0)
        val (yLo, yHi, yStep) = nice(rawLo, rawHi, ((yAxis?.get("ticks") as? WNum)?.v?.toInt() ?: 5).coerceIn(2, 12))
        val yFormat = yAxis?.get("format").word() ?: "compact"
        val xFormat = node.rec("x")?.get("format").word()

        val description = buildString {
            append("Chart")
            if (series.isNotEmpty()) append(" of ").append(series.joinToString { it.name })
            append(", ${data.size} points")
        }

        Column(modifier.fillMaxWidth().semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val showLegend = when (node.word("legend") ?: "auto") { "none" -> false; "auto" -> series.size >= 2 || polar; else -> true }
            Canvas(
                Modifier.fillMaxWidth().height(h)
                    .pointerInput(data, cats, linearX, polar) {
                        if (polar) {
                            detectTapGestures { off -> if (selectPlace != null) {
                                val idx = arcIndexAt(off, size.width.toFloat(), size.height.toFloat(), data, marks.first { it.type == "arc" }, call)
                                val rec = idx?.let { data.getOrNull(it) }
                                ctx.controller.input(selectPlace, if (rec == null || (selected != null && weaveEquals(selected, rec))) WNull else rec)
                            } }
                        } else detectTapGestures { off ->
                            val i = nearestIndex(off.x, size.width.toFloat(), data.size, cats.size, linearX, xs, xLo, xHi)
                            if (selectPlace != null && i != null) {
                                val rec = data.getOrNull(i)
                                ctx.controller.input(selectPlace, if (rec == null || (selected != null && weaveEquals(selected, rec))) WNull else rec)
                            } else cursor = i
                        }
                    }
                    .pointerInput(data, cats, linearX) {
                        if (!polar && node.bool("scrub", true)) detectHorizontalDragGestures(onDragEnd = { cursor = null }) { change, _ ->
                            val i = nearestIndex(change.position.x, size.width.toFloat(), data.size, cats.size, linearX, xs, xLo, xHi)
                            if (i != cursor) { cursor = i; ctx.controller.hostEffect("haptic", listOf(WStr("tick"))) }
                        }
                    },
            ) {
                val p = progress.value
                if (polar) {
                    val arc = marks.firstOrNull { it.type == "arc" } ?: return@Canvas
                    drawPie(data, arc, call, scheme.surface, (node.num("inner") ?: 0.0).toFloat(), p, { TokenResolver.series(it, scheme) }, selected)
                    return@Canvas
                }
                val leftW = (0..4).maxOf { k -> measurer.measure(ctx.fmt(WNum(yLo + (yHi - yLo) * k / 4.0), yFormat), label).size.width }.toFloat() + 8.dp.toPx()
                val bottomH = measurer.measure("0", label).size.height + 8.dp.toPx()
                val plot = androidx.compose.ui.geometry.Rect(leftW, 8.dp.toPx(), size.width - 8.dp.toPx(), size.height - bottomH)
                fun py(v: Double) = (plot.bottom - (v - yLo) / (yHi - yLo) * plot.height).toFloat()
                // a numeric or date x has no categories: bars take the closest spacing between x
                // values as their band, so a BarChart over years still draws bars
                val bandW = if (cats.isNotEmpty()) plot.width / cats.size else {
                    val ns = xs.mapNotNull { numOf(it) }.distinct().sorted()
                    val gap = ns.zipWithNext { a, b -> b - a }.filter { it > 0 }.minOrNull()
                    when {
                        ns.isEmpty() -> 0f
                        gap == null || xHi == xLo -> plot.width / 4f
                        else -> (gap / (xHi - xLo) * plot.width).toFloat().coerceAtMost(plot.width / 4f)
                    }
                }
                // with bars on a numeric axis, inset it by half a bar so the end bars stay inside
                val hasBars = marks.any { it.type == "bar" }
                val inset = if (linearX && hasBars && cats.isEmpty()) bandW / 2f else 0f
                fun px(v: WValue): Float = if (linearX) {
                    val n = numOf(v) ?: xLo
                    if (xHi == xLo) plot.center.x
                    else (plot.left + inset + (n - xLo) / (xHi - xLo) * (plot.width - 2 * inset)).toFloat()
                } else plot.left + bandW * (cats.indexOf(ctx.fmt(v)).coerceAtLeast(0) + 0.5f)
                // grid + y labels
                var t = yLo
                while (t <= yHi + yStep / 2) {
                    val y = py(t)
                    drawLine(scheme.outlineVariant.copy(alpha = 0.6f), Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
                    val tl = measurer.measure(ctx.fmt(WNum(t), yFormat), label)
                    drawText(tl, topLeft = Offset(leftW - 8.dp.toPx() - tl.size.width, y - tl.size.height / 2f))
                    t += yStep
                }
                // x labels (thinned)
                val labels = if (linearX) xs.distinctBy { numOf(it) } else cats.map { WStr(it) }
                if (labels.isNotEmpty()) {
                    val measured = labels.map { measurer.measure(ctx.fmt(it, xFormat), label) }
                    val widest = measured.maxOf { it.size.width } + 8.dp.toPx()
                    val every = max(1, ceil(widest * labels.size / plot.width).toInt())
                    labels.forEachIndexed { i, l -> if (i % every == 0) { val m = measured[i]; drawText(m, topLeft = Offset(px(l) - m.size.width / 2f, plot.bottom + 4.dp.toPx())) } }
                }
                // bands and rules
                marks.filter { it.type == "band" }.forEach { b ->
                    val c = TokenResolver.tone(b.word("tone") ?: "neutral", "high", scheme, ctx.dark)?.container ?: scheme.onSurface
                    if ((b.word("axis") ?: "x") == "y") {
                        val a = py((b.args["from"] as? WNum)?.v ?: yLo); val z = py((b.args["to"] as? WNum)?.v ?: yHi)
                        drawRect(c.copy(alpha = 0.12f), Offset(plot.left, min(a, z)), Size(plot.width, abs(a - z)))
                    } else {
                        val a = px(b.args["from"] ?: WNull); val z = px(b.args["to"] ?: WNull)
                        drawRect(c.copy(alpha = 0.12f), Offset(min(a, z), plot.top), Size(abs(a - z), plot.height))
                    }
                }
                // series
                val stackBase = HashMap<String, Double>()
                val barMarks = visibleSeries.filter { it.mark.type == "bar" && !it.mark.bool("stack") }
                visibleSeries.forEach { s ->
                    val m = s.mark
                    val yf = m.args["y"] ?: WNull
                    val pts = s.rows.mapNotNull { r ->
                        val yv = (field(r, yf, call) as? WNum)?.v ?: return@mapNotNull null
                        val key = ctx.fmt(field(r, xField, call))
                        val base = if (m.bool("stack")) stackBase.getOrDefault("${m.type}:$key", 0.0) else ((m.args["y0"]?.let { field(r, it, call) } as? WNum)?.v ?: 0.0)
                        val top = if (m.bool("stack")) base + yv else yv
                        if (m.bool("stack")) stackBase["${m.type}:$key"] = top
                        Triple(r, px(field(r, m.args["x"] ?: xField, call)), Pair(base, top))
                    }
                    val dim = selected != null && selected != WNull
                    when (m.type) {
                        "line", "area" -> {
                            val pp = pts.map { Offset(it.second, py(it.third.second)) }.sortedBy { it.x }
                            if (pp.isEmpty()) return@forEach
                            val curve = m.word("curve") ?: "smooth"
                            val path = linePath(pp, curve)
                            if (m.type == "area") {
                                val fill = Path().apply { addPath(path); lineTo(pp.last().x, py(0.0.coerceIn(yLo, yHi))); lineTo(pp.first().x, py(0.0.coerceIn(yLo, yHi))); close() }
                                drawPath(fill, s.color.copy(alpha = ((m.num("opacity") ?: 0.3) * p).toFloat()))
                            }
                            val w = when (m.word("width")) { "s" -> 1.5f; "l" -> 4f; else -> if (m.type == "area") 2f else 2.5f }.dp.toPx()
                            clipPathProgress(p, plot) {
                                drawPath(path, s.color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (m.bool("dashed")) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null))
                            }
                            if (m.bool("points")) pp.forEach { drawCircle(s.color, 4.dp.toPx(), it) }
                        }
                        "bar" -> {
                            val groups = max(1, barMarks.size)
                            val gi = barMarks.indexOf(s).coerceAtLeast(0)
                            val inner = bandW * 0.8f
                            val bw = if (m.bool("stack")) inner else inner / groups
                            val toneField = m.args["toneField"]
                            pts.forEach { (r, x, bt) ->
                                val left = x - inner / 2 + (if (m.bool("stack")) 0f else gi * bw)
                                val top = py(bt.second * p + bt.first * (1 - p)); val bottom = py(bt.first)
                                val c0 = toneField?.let { TokenResolver.tone(field(r, it, call).word(), "high", scheme, ctx.dark)?.container } ?: s.color
                                val c = if (dim && !weaveEquals(selected ?: WNull, r)) c0.copy(alpha = 0.4f) else c0
                                val rad = 6.dp.toPx().coerceAtMost(bw / 2)
                                drawPath(Path().apply { addRoundRect(RoundRect(left, min(top, bottom), left + bw, max(top, bottom), topLeftCornerRadius = CornerRadius(rad), topRightCornerRadius = CornerRadius(rad))) }, c)
                                if (m.bool("labels")) {
                                    val tl = measurer.measure(ctx.fmt(WNum(bt.second - bt.first)), label)
                                    drawText(tl, topLeft = Offset(left + bw / 2 - tl.size.width / 2, min(top, bottom) - tl.size.height - 2.dp.toPx()))
                                }
                            }
                        }
                        "point" -> pts.forEach { (r, x, bt) ->
                            val sizeV = m.args["size"]
                            val radius = when (sizeV) {
                                is WNum -> sizeV.v.toFloat() / 2
                                null, WNull -> 3f
                                else -> {
                                    val all = s.rows.mapNotNull { (field(it, sizeV, call) as? WNum)?.v }
                                    val v = (field(r, sizeV, call) as? WNum)?.v ?: 0.0
                                    val mx = all.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                                    (2 + 8 * sqrt(v / mx)).toFloat()
                                }
                            }.dp.toPx()
                            val c = if (dim && !weaveEquals(selected ?: WNull, r)) s.color.copy(alpha = 0.4f) else s.color
                            drawCircle(c, radius * p, Offset(x, py(bt.second)))
                        }
                        "text" -> pts.forEach { (r, x, bt) ->
                            val tl = measurer.measure(ctx.fmt(field(r, m.args["content"] ?: WNull, call)), label)
                            drawText(tl, topLeft = Offset(x - tl.size.width / 2, py(bt.second) - tl.size.height - 2.dp.toPx()))
                        }
                    }
                }
                marks.filter { it.type == "rule" }.forEach { r ->
                    val c = TokenResolver.tone(r.word("tone") ?: "muted", "low", scheme, ctx.dark)?.content ?: scheme.onSurfaceVariant
                    val effect = if (r.bool("dashed", true)) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
                    (r.args["y"] as? WNum)?.v?.let { y ->
                        drawLine(c, Offset(plot.left, py(y)), Offset(plot.right, py(y)), 1.dp.toPx(), pathEffect = effect)
                        r.str("label")?.let { l -> val tl = measurer.measure(l, label); drawText(tl, topLeft = Offset(plot.right - tl.size.width, py(y) - tl.size.height - 2.dp.toPx())) }
                    }
                    r.args["x"]?.takeIf { it != WNull }?.let { xv ->
                        drawLine(c, Offset(px(xv), plot.top), Offset(px(xv), plot.bottom), 1.dp.toPx(), pathEffect = effect)
                        r.str("label")?.let { l -> val tl = measurer.measure(l, label); drawText(tl, topLeft = Offset(px(xv) + 4.dp.toPx(), plot.top)) }
                    }
                }
                // tooltip cursor (04 §12.5)
                val ci = cursor
                if (ci != null && node.bool("tooltip", true)) {
                    val r = data.getOrNull(ci) ?: return@Canvas
                    val x = px(field(r, xField, call))
                    drawLine(scheme.onSurface, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
                    val lines = listOf(ctx.fmt(field(r, xField, call), xFormat)) + visibleSeries.mapNotNull { s ->
                        val rr = s.rows.firstOrNull { ctx.fmt(field(it, xField, call)) == ctx.fmt(field(r, xField, call)) } ?: return@mapNotNull null
                        "${s.name}: " + ctx.fmt(field(rr, s.mark.args["y"] ?: WNull, call), yFormat.takeIf { it != "compact" })
                    }
                    drawTooltip(measurer, lines, tooltipStyle, scheme.inverseSurface, x, plot)
                }
            }
            if (showLegend) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (polar) {
                    val arc = marks.firstOrNull { it.type == "arc" }
                    data.forEachIndexed { i, r -> LegendChip(ctx.fmt(field(r, arc?.args?.get("label") ?: WNull, call)), TokenResolver.series(i, scheme), true) {} }
                } else series.forEach { s ->
                    LegendChip(s.name, s.color, s.name !in hidden) { hidden = if (s.name in hidden) hidden - s.name else hidden + s.name }
                }
            }
        }
    }

    @Composable
    private fun LegendChip(name: String, color: Color, on: Boolean, onClick: () -> Unit) {
        Row(Modifier.clickable(onClick = onClick).alpha(if (on) 1f else 0.4f).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    }

    private val MARKS = setOf("line", "area", "bar", "point", "rule", "band", "text", "arc", "range", "cell", "link")

    private fun nearestIndex(x: Float, width: Float, n: Int, cats: Int, linear: Boolean, xs: List<WValue>, lo: Double, hi: Double): Int? {
        if (n == 0) return null
        if (!linear) {
            val band = ((x / width) * cats).toInt().coerceIn(0, max(0, cats - 1))
            return band.coerceAtMost(n - 1)
        }
        val target = lo + (x / width) * (hi - lo)
        return xs.indices.minByOrNull { abs((numOf(xs[it]) ?: lo) - target) }
    }

    /** Monotone cubic (Fritsch–Carlson) for `smooth`, steps for `step`. */
    private fun linePath(p: List<Offset>, curve: String): Path {
        val path = Path()
        if (p.isEmpty()) return path
        path.moveTo(p[0].x, p[0].y)
        when {
            curve == "step" -> for (i in 1 until p.size) { path.lineTo(p[i].x, p[i - 1].y); path.lineTo(p[i].x, p[i].y) }
            curve == "linear" || p.size < 3 -> for (i in 1 until p.size) path.lineTo(p[i].x, p[i].y)
            else -> {
                val n = p.size
                val d = DoubleArray(n - 1) { i -> ((p[i + 1].y - p[i].y) / (p[i + 1].x - p[i].x).coerceAtLeast(1e-6f)).toDouble() }
                val m = DoubleArray(n)
                m[0] = d[0]; m[n - 1] = d[n - 2]
                for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0) 0.0 else (d[i - 1] + d[i]) / 2
                for (i in 0 until n - 1) {
                    if (d[i] == 0.0) { m[i] = 0.0; m[i + 1] = 0.0; continue }
                    val a = m[i] / d[i]; val b = m[i + 1] / d[i]
                    val s = a * a + b * b
                    if (s > 9) { val t = 3 / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
                }
                for (i in 0 until n - 1) {
                    val dx = (p[i + 1].x - p[i].x) / 3
                    path.cubicTo(p[i].x + dx, (p[i].y + m[i] * dx).toFloat(), p[i + 1].x - dx, (p[i + 1].y - m[i + 1] * dx).toFloat(), p[i + 1].x, p[i + 1].y)
                }
            }
        }
        return path
    }

    private inline fun DrawScope.clipPathProgress(p: Float, plot: androidx.compose.ui.geometry.Rect, block: DrawScope.() -> Unit) {
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, plot.left + plot.width * p + 4f, size.height)
        block()
        drawContext.canvas.restore()
    }

    private fun DrawScope.drawTooltip(measurer: TextMeasurer, lines: List<String>, style: TextStyle, bg: Color, x: Float, plot: androidx.compose.ui.geometry.Rect) {
        val measured = lines.map { measurer.measure(it, style) }
        val w = measured.maxOf { it.size.width } + 16.dp.toPx()
        val h = measured.sumOf { it.size.height } + 12.dp.toPx()
        val left = (x + 8.dp.toPx()).let { if (it + w > plot.right) x - 8.dp.toPx() - w else it }
        drawRoundRect(bg, Offset(left, plot.top), Size(w, h), CornerRadius(8.dp.toPx()))
        var y = plot.top + 6.dp.toPx()
        measured.forEach { drawText(it, topLeft = Offset(left + 8.dp.toPx(), y)); y += it.size.height }
    }

    private fun DrawScope.drawPie(data: List<WRec>, arc: WNode, call: (WFn, WRec) -> WValue?, gap: Color, inner: Float, p: Float, palette: (Int) -> Color, selected: WValue?) {
        val values = data.map { (field(it, arc.args["value"] ?: WNull, call) as? WNum)?.v?.coerceAtLeast(0.0) ?: 0.0 }
        val total = values.sum().takeIf { it > 0 } ?: return
        val d = min(size.width, size.height)
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = (v / total * 360 * p).toFloat()
            val c0 = palette(i)
            val c = if (selected != null && selected != WNull && !weaveEquals(selected, data[i])) c0.copy(alpha = 0.4f) else c0
            if (inner > 0f) drawArc(c, start, sweep, false, tl + Offset(d * (1 - inner) / 4, d * (1 - inner) / 4), Size(d - d * (1 - inner) / 2, d - d * (1 - inner) / 2), style = Stroke(d * (1 - inner) / 2))
            else drawArc(c, start, sweep, true, tl, Size(d, d))
            drawArc(gap, start, sweep, inner <= 0f, tl, Size(d, d), style = Stroke(2.dp.toPx()))
            start += sweep
        }
    }

    private fun arcIndexAt(off: Offset, w: Float, h: Float, data: List<WRec>, arc: WNode, call: (WFn, WRec) -> WValue?): Int? {
        val c = Offset(w / 2, h / 2)
        val a = Math.toDegrees(kotlin.math.atan2((off.y - c.y).toDouble(), (off.x - c.x).toDouble())).let { (it + 90 + 360) % 360 }
        val values = data.map { (field(it, arc.args["value"] ?: WNull, call) as? WNum)?.v?.coerceAtLeast(0.0) ?: 0.0 }
        val total = values.sum().takeIf { it > 0 } ?: return null
        var acc = 0.0
        values.forEachIndexed { i, v -> acc += v / total * 360; if (a <= acc) return i }
        return null
    }
}
