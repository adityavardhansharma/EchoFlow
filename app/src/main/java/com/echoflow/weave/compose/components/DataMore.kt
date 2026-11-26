package com.echoflow.weave.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.echoflow.weave.compose.render.RenderCtx
import com.echoflow.weave.compose.render.RenderNode
import com.echoflow.weave.compose.render.list
import com.echoflow.weave.compose.render.num
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
import com.echoflow.weave.core.eval.WValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/** Data components added in phase 2 (04 §8). */
object DataMore {

    private fun dateOf(v: WValue?): LocalDate? = (v as? WDate)?.v ?: (v as? WStr)?.v?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    @Composable
    fun TimelineR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val items = node.list("items") ?: emptyList()
        val render = node.args["render"] as? WFn
        val time = node.args["time"] as? WFn
        val dot = TokenResolver.tone(node.word("tone") ?: "primary", "high", MaterialTheme.colorScheme, ctx.dark)?.container ?: MaterialTheme.colorScheme.primary
        val line = MaterialTheme.colorScheme.outlineVariant
        @Composable
        fun Content(item: WValue, i: Int) {
            val n = render?.let { ctx.controller.callItem(it, item, i) } as? WNode
            if (n != null) RenderNode(n, ctx.child()) else Text(ctx.fmt(if (item is WRec) item.fields.values.firstOrNull() ?: item else item))
        }
        if (node.word("dir") == "horizontal") {
            Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEachIndexed { i, item ->
                    Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                            Box(Modifier.height(2.dp).weight(1f).background(line))
                        }
                        time?.let { Text(ctx.fmt(ctx.controller.callFn(it, listOf(item))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Content(item, i)
                    }
                }
            }
            return
        }
        Column(modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(dot))
                        if (i < items.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(line))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
                        time?.let { Text(ctx.fmt(ctx.controller.callFn(it, listOf(item))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Content(item, i)
                    }
                }
            }
        }
    }

    @Composable
    fun CalendarR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val events = node.list("events")?.filterIsInstance<WRec>() ?: emptyList()
        val byDate = events.groupBy { dateOf(it["date"]) }
        val today = LocalDate.now(ctx.formatter.zone)
        var month by rememberSaveable { mutableStateOf((dateOf(node.args["month"]) ?: today).withDayOfMonth(1).toString()) }
        val first = LocalDate.parse(month)
        val selected = dateOf(node.args["selected"])
        val onDay = node.args["onDay"]
        fun tap(d: LocalDate) {
            node.places["selected"]?.let { ctx.controller.input(it, WDate(d)) }
            if (onDay is WFn) ctx.controller.callFn(onDay, listOf(WDate(d)))?.let { ctx.fire(it) }
        }
        val view = node.word("view") ?: "month"
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view == "agenda") {
                byDate.keys.filterNotNull().sorted().forEach { d ->
                    Text(ctx.formatter.date(d), style = MaterialTheme.typography.titleSmall)
                    byDate[d]?.forEach { e -> Text("• " + ctx.fmt(e["title"]), style = MaterialTheme.typography.bodyMedium) }
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ month = first.minusMonths(1).toString() }) { Icon(TokenResolver.icon("chevron_left"), "Previous month") }
                Text(first.month.getDisplayName(TextStyle.FULL, ctx.formatter.locale) + " " + first.year, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton({ month = first.plusMonths(1).toString() }) { Icon(TokenResolver.icon("chevron_right"), "Next month") }
            }
            Row {
                DayOfWeek.entries.forEach { Text(it.getDisplayName(TextStyle.NARROW, ctx.formatter.locale), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            val start = if (view == "week") (dateOf(node.args["month"]) ?: today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = if (view == "week") 1 else 6
            for (w in 0 until weeks) {
                Row {
                    for (d in 0 until 7) {
                        val day = start.plusDays((w * 7 + d).toLong())
                        val inMonth = view == "week" || day.month == first.month
                        val isSel = day == selected
                        Column(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(if (isSel) scheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (day == today && !isSel) Modifier.border(1.dp, scheme.primary, CircleShape) else Modifier)
                                .clickable { tap(day) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodySmall, color = when { isSel -> scheme.onPrimary; inMonth -> scheme.onSurface; else -> scheme.outline })
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                byDate[day]?.take(3)?.forEach { e ->
                                    val c = TokenResolver.tone(e["tone"].word() ?: "primary", "high", scheme, ctx.dark)?.container ?: scheme.primary
                                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (isSel) scheme.onPrimary else c))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun HeatcalR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val values = node.list("values")?.filterIsInstance<WRec>()?.mapNotNull { r -> dateOf(r["date"])?.let { it to ((r["value"] as? WNum)?.v ?: 0.0) } }?.toMap() ?: emptyMap()
        val weeks = (node.num("weeks") ?: 17.0).toInt().coerceIn(1, 53)
        val max = values.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val solid = TokenResolver.tone(node.word("tone") ?: "primary", "high", scheme, ctx.dark)?.container ?: scheme.primary
        val end = LocalDate.now(ctx.formatter.zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val start = end.minusWeeks(weeks.toLong()).plusDays(1)
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (w in 0 until weeks) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (d in 0 until 7) {
                    val day = start.plusDays((w * 7 + d).toLong())
                    val v = values[day] ?: 0.0
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(if (v <= 0) scheme.surfaceContainerHigh else solid.copy(alpha = (0.15 + 0.85 * v / max).toFloat())))
                }
            }
        }
    }

    /** Word-level Myers diff on whitespace-split tokens. */
    fun diff(a: List<String>, b: List<String>): List<Pair<Char, String>> {
        val n = a.size; val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<Pair<Char, String>>()
        var i = 0; var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += ' ' to a[i]; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += '-' to a[i]; i++ }
                else -> { out += '+' to b[j]; j++ }
            }
        }
        while (i < n) out += '-' to a[i++]
        while (j < m) out += '+' to b[j++]
        return out
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun DiffR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val scheme = MaterialTheme.colorScheme
        val before = ctx.fmt(node.args["before"]).split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2000)
        val after = ctx.fmt(node.args["after"]).split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2000)
        val ops = remember(before, after) { diff(before, after) }
        val success = TokenResolver.tone("success", "medium", scheme, ctx.dark)?.container ?: scheme.tertiaryContainer
        val text = buildAnnotatedString {
            ops.forEach { (op, w) ->
                when (op) {
                    '-' -> withStyle(SpanStyle(background = scheme.errorContainer, textDecoration = TextDecoration.LineThrough)) { append(w) }
                    '+' -> withStyle(SpanStyle(background = success)) { append(w) }
                    else -> append(w)
                }
                append(' ')
            }
        }
        if (node.word("mode") == "split") Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(buildAnnotatedString { ops.filter { it.first != '+' }.forEach { (op, w) -> if (op == '-') withStyle(SpanStyle(background = scheme.errorContainer)) { append(w) } else append(w); append(' ') } }, Modifier.weight(1f))
            Text(buildAnnotatedString { ops.filter { it.first != '-' }.forEach { (op, w) -> if (op == '+') withStyle(SpanStyle(background = success)) { append(w) } else append(w); append(' ') } }, Modifier.weight(1f))
        } else Text(text, modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
    }

    @Composable
    fun JsonR(node: WNode, ctx: RenderCtx, modifier: Modifier) {
        val open = (node.num("open") ?: 1.0).toInt()
        Column(modifier.fillMaxWidth()) { JsonNode(null, node.args["value"] ?: WNull, 0, open, ctx) }
    }

    @Composable
    private fun JsonNode(key: String?, v: WValue, depth: Int, open: Int, ctx: RenderCtx) {
        var expanded by remember { mutableStateOf(depth < open) }
        val prefix = (key?.let { "\"$it\": " } ?: "")
        val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        val pad = Modifier.padding(start = (12 * depth).dp)
        when (v) {
            is WRec, is WList -> {
                val entries: List<Pair<String?, WValue>> = if (v is WRec) v.fields.entries.map { it.key to it.value } else (v as WList).items.map { null to it }
                val (o, c) = if (v is WRec) "{" to "}" else "[" to "]"
                Text(prefix + o + (if (expanded) "" else " … ${entries.size} $c"), pad.clickable { expanded = !expanded }, style = style)
                if (expanded) { entries.take(200).forEach { (k, x) -> JsonNode(k, x, depth + 1, open, ctx) }; Text(c, pad, style = style) }
            }
            is WStr -> Text(prefix + com.echoflow.weave.core.eval.Formatter.jsonString(v.v), pad, style = style, color = MaterialTheme.colorScheme.tertiary)
            else -> Text(prefix + com.echoflow.weave.core.program.StateCodec.encode(v), pad, style = style, color = MaterialTheme.colorScheme.primary)
        }
    }
}
