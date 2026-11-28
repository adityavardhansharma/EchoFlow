package com.echoflow.weave.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.bool
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
import com.echoflow.weave.compose.render.str
import com.echoflow.weave.compose.render.word
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.compose.tokens.word
import com.echoflow.weave.core.eval.PlainText
import com.echoflow.weave.core.eval.WDateTime
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WTime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Numbers and status, 04 §6. */
object Numbers {

    @Composable
    fun MetricR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val label = ctx.fmt(node.args["label"])
        val raw = node.args["value"]
        val animate = node.bool("animate", true) && raw is WNum
        val target = (raw as? WNum)?.v?.toFloat() ?: 0f
        val shown by animateFloatAsState(if (animate) target else 0f, label = "metric")
        val fmt = node.str("format")
        val valueText = if (animate && shown != target) {
            (node.str("prefix") ?: "") + ctx.fmt(WNum(shown.toDouble()), fmt) + (node.str("suffix") ?: "")
        } else PlainText.metricValue(node, ctx.formatter)
        val style = when (node.word("size")) {
            "s" -> MaterialTheme.typography.titleMedium; "l" -> MaterialTheme.typography.displaySmall; else -> MaterialTheme.typography.headlineSmall
        }
        val delta = node.num("delta")
        val a11y = buildString {
            append("$label: ${PlainText.metricValue(node, ctx.formatter)}")
            if (delta != null) append(if (delta >= 0) ", up " else ", down ").append(ctx.fmt(WNum(abs(delta)), node.str("deltaFormat") ?: "percent"))
        }
        Column(modifier.semantics(mergeDescendants = true) { contentDescription = a11y }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(valueText, style = style, color = TokenResolver.toneText(node.word("tone"), scheme, ctx.dark) ?: scheme.onSurface)
                if (delta != null) DeltaChip(delta, node.str("deltaFormat") ?: "percent", node.word("deltaGood") ?: "up", ctx)
            }
            node.list("trend")?.mapNotNull { (it as? WNum)?.v }?.takeIf { it.size >= 2 }?.let { Sparkline(it, scheme.primary, Modifier.fillMaxWidth().height(24.dp)) }
        }
    }

    @Composable
    private fun DeltaChip(delta: Double, format: String, good: String, ctx: RenderCtx) {
        val scheme = MaterialTheme.colorScheme
        val tone = when {
            delta == 0.0 -> "muted"
            (delta > 0) == (good == "up") -> "success"
            else -> "error"
        }
        val pair = TokenResolver.tone(tone, "medium", scheme, ctx.dark)
        val icon = when { delta > 0 -> "arrow_upward"; delta < 0 -> "arrow_downward"; else -> "remove" }
        androidx.compose.material3.Surface(shape = CircleShape, color = pair?.container ?: scheme.surfaceContainer, contentColor = pair?.content ?: scheme.onSurface) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(TokenResolver.icon(icon), null, Modifier.size(14.dp))
                Text(ctx.fmt(WNum(abs(delta)), format), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    /** A small line without axes (04 §12.4 Sparkline, also Metric `trend`). */
    @Composable
    fun Sparkline(values: List<Double>, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
        Canvas(modifier) {
            if (values.size < 2) return@Canvas
            val lo = values.min(); val hi = values.max(); val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (values.size - 1)
                val y = size.height - ((v - lo) / span * (size.height - 4.dp.toPx())).toFloat() - 2.dp.toPx()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val last = values.last()
            drawCircle(color, 3.dp.toPx(), Offset(size.width, size.height - ((last - lo) / span * (size.height - 4.dp.toPx())).toFloat() - 2.dp.toPx()))
        }
    }

    @Composable
    fun StatR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val kids = node.children
            val cols = if (kids.size > 3 || maxWidth < (kids.size * 110).dp) 2 else maxOf(1, kids.size)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                kids.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { RenderNode(it, ctx.child(), Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun ProgressR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val v = node.num("value")?.toFloat()?.coerceIn(0f, 1f)
        val color = TokenResolver.tone(node.word("tone") ?: "primary", "high", scheme, ctx.dark)?.container ?: scheme.primary
        val shown by animateFloatAsState(v ?: 0f, label = "progress")
        val label = node.str("label")
        val valueText = if (node.bool("showValue") && v != null) ctx.fmt(WNum(v.toDouble()), "percent") else null
        when (node.word("kind") ?: "wavy") {
            "circular" -> Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (v == null) CircularWavyProgressIndicator(Modifier.size(48.dp), color = color, trackColor = scheme.surfaceContainerHighest)
                else CircularWavyProgressIndicator(progress = { shown }, modifier = Modifier.size(48.dp), color = color, trackColor = scheme.surfaceContainerHighest)
                Column { label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }; valueText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
            else -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label != null || valueText != null) Row(Modifier.fillMaxWidth()) {
                    Text(label ?: "", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    valueText?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                }
                val linear = node.word("kind") == "linear"
                when {
                    v == null && linear -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = color, trackColor = scheme.surfaceContainerHighest)
                    v == null -> LinearWavyProgressIndicator(Modifier.fillMaxWidth(), color = color, trackColor = scheme.surfaceContainerHighest)
                    linear -> LinearProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = scheme.surfaceContainerHighest)
                    else -> LinearWavyProgressIndicator(progress = { shown }, modifier = Modifier.fillMaxWidth(), color = color, trackColor = scheme.surfaceContainerHighest)
                }
            }
        }
    }

    @Composable
    fun GaugeR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val min = node.num("min") ?: 0.0; val max = (node.num("max") ?: 100.0).let { if (it <= min) min + 1 else it }
        val value = node.num("value") ?: min
        val frac by animateFloatAsState(((value - min) / (max - min)).toFloat().coerceIn(0f, 1f), spring(), label = "gauge")
        val (d, stroke) = when (node.word("size")) { "s" -> 120.dp to 8.dp; "l" -> 200.dp to 16.dp; else -> 160.dp to 12.dp }
        val bands = node.list("bands")?.mapNotNull { b -> (b as? WRec)?.let { r -> ((r["to"] as? WNum)?.v ?: return@let null) to r["tone"].word() } } ?: emptyList()
        val bandFor = bands.firstOrNull { value <= it.first }?.second
        val valueColor = TokenResolver.tone(bandFor, "high", scheme, ctx.dark)?.container ?: scheme.primary
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(d), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(d)) {
                    val sw = stroke.toPx()
                    val tl = Offset(sw / 2, sw / 2); val sz = Size(size.width - sw, size.height - sw)
                    drawArc(scheme.surfaceContainerHighest, 150f, 240f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    var from = min
                    for ((to, tone) in bands) {
                        val c = TokenResolver.tone(tone, "medium", scheme, ctx.dark)?.container ?: continue
                        val a0 = ((from - min) / (max - min)).toFloat().coerceIn(0f, 1f); val a1 = ((to - min) / (max - min)).toFloat().coerceIn(0f, 1f)
                        drawArc(c, 150f + 240f * a0, 240f * (a1 - a0), false, tl, sz, style = Stroke(sw))
                        from = to
                    }
                    drawArc(valueColor, 150f, 240f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Text(ctx.fmt(WNum(value), node.str("format")), style = MaterialTheme.typography.headlineSmall)
            }
            node.str("label")?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant) }
        }
    }

    @Composable
    fun RatingR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val max = (node.num("max") ?: 5.0).toInt().coerceIn(1, 10)
        val v = Math.round((node.num("value") ?: 0.0) * 2) / 2.0
        Row(modifier.semantics(mergeDescendants = true) { contentDescription = "Rated ${ctx.fmt(WNum(v))} out of $max" }) {
            for (i in 1..max) {
                val icon = when { v >= i -> "star"; v >= i - 0.5 -> "star_half"; else -> "star_outline" }
                Icon(TokenResolver.icon(icon), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
        }
    }

    @Composable
    fun StepsR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val labels = node.list("labels")?.map { ctx.fmt(it) } ?: emptyList()
        val current = (node.num("current") ?: 0.0).toInt()
        if (labels.size > 5) {
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Step ${current + 1} of ${labels.size}" + (labels.getOrNull(current)?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { (current + 1f) / labels.size }, modifier = Modifier.fillMaxWidth())
            }
            return
        }
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            labels.forEachIndexed { i, l ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val done = i < current; val now = i == current
                    Box(
                        Modifier.size(24.dp).then(
                            when {
                                done -> Modifier.border(12.dp, scheme.primary, CircleShape)
                                now -> Modifier.border(2.dp, scheme.primary, CircleShape)
                                else -> Modifier.border(2.dp, scheme.outlineVariant, CircleShape)
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(TokenResolver.icon("check"), null, Modifier.size(16.dp), tint = scheme.onPrimary)
                        else Text("${i + 1}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(l, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }

    /** Timer keeps its own remaining time while `running` (04 §6); `onDone` runs once at 0. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun TimerR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val total = ((node.num("seconds") ?: 60.0) * 1000).toLong().coerceAtLeast(0)
        var remaining by rememberSaveable(total) { mutableLongStateOf(total) }
        var done by rememberSaveable(total) { mutableStateOf(false) }
        val running = node.bool("running")
        LaunchedEffect(running, total) {
            while (running && remaining > 0) {
                delay(250); remaining = (remaining - 250).coerceAtLeast(0)
            }
            if (remaining == 0L && !done && total > 0) {
                done = true
                ctx.fire(node.args["onDone"])
                ctx.controller.hostEffect("haptic", listOf(com.echoflow.weave.core.eval.WStr("heavy")))
            }
        }
        val text = ctx.formatter.clock(remaining)
        if ((node.word("style") ?: "ring") == "digits") Text(text, modifier, style = MaterialTheme.typography.displaySmall)
        else Box(modifier.size(160.dp), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(progress = { if (total == 0L) 0f else remaining.toFloat() / total }, modifier = Modifier.size(160.dp))
            Text(text, style = MaterialTheme.typography.headlineMedium)
        }
    }

    @Composable
    fun ClockR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        var now by remember { mutableStateOf(Instant.now()) }
        val bound = node.args["time"]
        LaunchedEffect(bound) { while (bound == null || bound == com.echoflow.weave.core.eval.WNull) { delay(1000); now = Instant.now() } }
        val zone = node.str("zone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ctx.formatter.zone
        val local = when (bound) {
            is WDateTime -> bound.v.atZone(zone).toLocalTime()
            is WTime -> bound.v
            else -> now.atZone(zone).toLocalTime()
        }
        if ((node.word("style") ?: "digital") == "analog") {
            val scheme = MaterialTheme.colorScheme
            Canvas(modifier.size(120.dp)) {
                val r = size.minDimension / 2; val c = center
                for (h in 0 until 12) {
                    val a = Math.toRadians(h * 30.0 - 90)
                    drawLine(scheme.outlineVariant, Offset(c.x + (r * 0.82f * cos(a)).toFloat(), c.y + (r * 0.82f * sin(a)).toFloat()), Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), 2.dp.toPx())
                }
                fun hand(deg: Double, len: Float, w: Float, col: androidx.compose.ui.graphics.Color) {
                    val a = Math.toRadians(deg - 90)
                    drawLine(col, c, Offset(c.x + (r * len * cos(a)).toFloat(), c.y + (r * len * sin(a)).toFloat()), w, cap = StrokeCap.Round)
                }
                hand((local.hour % 12 + local.minute / 60.0) * 30, 0.5f, 4.dp.toPx(), scheme.onSurface)
                hand(local.minute * 6.0, 0.75f, 3.dp.toPx(), scheme.onSurface)
                hand(local.second * 6.0, 0.85f, 1.5.dp.toPx(), scheme.primary)
            }
        } else Text(ctx.formatter.time(java.time.LocalDateTime.of(java.time.LocalDate.now(zone), local)), modifier, style = MaterialTheme.typography.headlineMedium)
    }
}
