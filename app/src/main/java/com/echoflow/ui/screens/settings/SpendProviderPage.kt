@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.data.usage.ListPrices
import com.echoflow.data.usage.OpenRouterKeySpend
import com.echoflow.data.usage.UsageKind
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageRecord
import com.echoflow.data.usage.UsageUnit
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.ProviderIdentity
import com.echoflow.ui.components.ProviderMark
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One provider's spend: its figure for the window, then every request EchoFlow itemised as a log
 * that scrolls sideways. The time column stays pinned while the rest scrolls, and every row
 * shares one scroll position so the columns stay aligned like a spreadsheet.
 */
@Composable
internal fun SpendProviderPage(vm: SpendViewModel, provider: UsageProvider, onBack: () -> Unit) {
    val window by vm.window.collectAsState()
    val records by remember(provider) { vm.records(provider) }.collectAsState(initial = emptyList())
    val openRouter by vm.openRouter.collectAsState()
    val reported = openRouter?.takeIf { provider == UsageProvider.OpenRouter }
    val days = remember(records) { SpendMath.days(records) }
    val columns = remember(provider) { logColumns(provider) }
    val scroll = rememberScrollState()
    var detail by remember { mutableStateOf<UsageRecord?>(null) }
    var hintSeen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scroll.value) { if (scroll.value > 0) hintSeen = true }
    LaunchedEffect(provider) {
        if (provider == UsageProvider.OpenRouter) vm.refresh()
        if (provider == UsageProvider.Deepgram) vm.priceDeepgram()
    }

    SpendLazyScaffold(title = provider.label, subtitle = "Spend · ${window.phrase}", onBack = onBack) {
        item(key = "hero") {
            ProviderHero(provider, window, records, reported, vm.trackingStartedAt)
            Spacer(Modifier.height(Spacing.base))
        }
        item(key = "filter") {
            SpendWindowFilter(selected = window, onSelect = vm::select)
            Spacer(Modifier.height(Spacing.base))
        }
        item(key = "note") {
            providerNote(provider, window, records, reported)?.let {
                SpendNote(it, leading = { Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                Spacer(Modifier.height(Spacing.base))
            }
        }

        if (records.isEmpty()) {
            item(key = "empty") { EmptyLog(provider, window) }
        } else {
            stickyHeader(key = "columns") { LogHeader(columns, scroll, showHint = !hintSeen) }
            days.forEach { (day, rows) ->
                item(key = "day-$day") { DayHeader(day, rows, provider) }
                itemsIndexed(rows, key = { _, r -> r.id }) { index, record ->
                    LogRow(record, columns, scroll, index, rows.size, onClick = { detail = record })
                    if (index < rows.lastIndex) Spacer(Modifier.height(GroupedItemGap))
                }
            }
        }
    }

    detail?.let { RecordSheet(provider, it, onDismiss = { detail = null }) }
}

// ── Hero ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProviderHero(
    provider: UsageProvider,
    window: SpendWindow,
    records: List<UsageRecord>,
    reported: OpenRouterKeySpend?,
    trackingStartedAt: Long?,
) {
    val input = records.mapNotNull { it.inputTokens }.takeIf { it.isNotEmpty() }?.sum()
    val output = records.mapNotNull { it.outputTokens }.takeIf { it.isNotEmpty() }?.sum()
    val timeline = remember(records, window) {
        SpendBuckets.timeline(
            window = window,
            now = System.currentTimeMillis(),
            earliest = listOfNotNull(trackingStartedAt, records.minOfOrNull { it.createdAt }).minOrNull(),
            seriesCount = 1,
            samples = records.mapNotNull { r ->
                val value = when (provider.unit) {
                    UsageUnit.Usd -> r.costUsd
                    UsageUnit.Credits -> r.credits
                    UsageUnit.Tokens -> ((r.inputTokens ?: 0) + (r.outputTokens ?: 0)).toDouble()
                    UsageUnit.Requests -> 1.0
                } ?: return@mapNotNull null
                SpendSample(0, r.createdAt, value)
            },
        )
    }
    val format: (Double) -> String = when (provider.unit) {
        UsageUnit.Usd -> SpendFormat::usd
        UsageUnit.Credits -> { v -> SpendFormat.credits(v) }
        UsageUnit.Tokens, UsageUnit.Requests -> { v -> SpendFormat.compact(v.toLong()) }
    }
    SpendHero(
        label = when (provider.unit) {
            UsageUnit.Usd -> "Spent"
            UsageUnit.Credits -> "Credits used"
            UsageUnit.Tokens -> "Tokens used"
            UsageUnit.Requests -> "Requests"
        },
        pill = window.phrase.replaceFirstChar { it.uppercase() },
        decoration = spendShape(provider),
        figure = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when (provider.unit) {
                        UsageUnit.Usd -> HeroDollars(reported?.let { window.of(it) } ?: records.sumOf { it.costUsd ?: 0.0 })
                        UsageUnit.Credits -> HeroFigure(SpendFormat.credits(records.sumOf { it.credits ?: 0.0 }), "credits")
                        UsageUnit.Tokens -> HeroFigure(SpendFormat.compact((input ?: 0) + (output ?: 0)), "tokens")
                        UsageUnit.Requests -> HeroFigure(SpendFormat.compact(records.size.toLong()), if (records.size == 1) "request" else "requests")
                    }
                }
                SpendMark(provider, size = 52.dp)
            }
        },
    ) {
        Spacer(Modifier.height(Spacing.xl))
        SpendChart(timeline, listOf(MaterialTheme.colorScheme.primary), format, animationKey = window)
        Spacer(Modifier.height(Spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatTile("Requests", SpendFormat.compact(records.size.toLong()))
            when (provider) {
                UsageProvider.Deepgram -> StatTile("Audio", SpendFormat.seconds(records.sumOf { it.audioSeconds ?: 0.0 }))
                UsageProvider.Firecrawl -> StatTile("Per request", SpendFormat.credits(records.sumOf { it.credits ?: 0.0 } / records.size.coerceAtLeast(1)))
                UsageProvider.Exa -> StatTile("Per request", SpendFormat.usd(records.sumOf { it.costUsd ?: 0.0 } / records.size.coerceAtLeast(1)))
                UsageProvider.Parallel -> Unit
                else -> {
                    StatTile("Input", input?.let(SpendFormat::compact) ?: "—")
                    StatTile("Output", output?.let(SpendFormat::compact) ?: "—")
                }
            }
        }
        // Only some keys carry a spending cap; when one does, it's a quiet line, not the headline.
        if (reported?.limit != null && reported.limit > 0) {
            val remaining = (reported.limitRemaining ?: reported.limit).coerceAtLeast(0.0)
            Spacer(Modifier.height(Spacing.base))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Key limit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    "${SpendFormat.usd(remaining)} left of ${SpendFormat.usd(reported.limit)}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            LinearWavyProgressIndicator(
                progress = { (1.0 - remaining / reported.limit).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun providerNote(provider: UsageProvider, window: SpendWindow, records: List<UsageRecord>, reported: OpenRouterKeySpend?): String? = when {
    reported != null -> {
        val gap = SpendMath.unitemised(window.of(reported), records)
        if (gap > 0) "${SpendFormat.usd(gap)} of this wasn't itemised: spent in other apps, before EchoFlow started tracking, or on replies stopped early."
        else "Total reported by OpenRouter for this key, updated ${relativeTime(reported.fetchedAt)}."
    }
    provider == UsageProvider.OpenRouter -> "OpenRouter's own total appears once it's fetched. Until then, this is what EchoFlow itemised."
    provider in ListPrices.priced -> "${provider.label} reports tokens, not prices. Each request is priced at the model's list rate for input, cached input and output."
    provider.unit == UsageUnit.Tokens -> "${provider.label} reports tokens but not prices, so these requests aren't counted in dollars."
    provider == UsageProvider.Deepgram -> "Deepgram prices each request after it settles. If your key can't read usage, the cost stays blank."
    provider == UsageProvider.Parallel -> "Parallel reports what it billed, not a price."
    else -> null
}

@Composable
private fun EmptyLog(provider: UsageProvider, window: SpendWindow) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            SpendMark(provider, size = 56.dp)
            Spacer(Modifier.height(Spacing.m))
            Text("Nothing itemised ${window.phrase}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Requests you make with this ${provider.label} key in EchoFlow will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Log ──────────────────────────────────────────────────────────────────────────────

private val TimeColumnWidth = 76.dp
private val LogRowHeight = 56.dp

/** One log column. [end] right-aligns figures so their digits line up. */
private class LogColumn(
    val title: String,
    val width: Dp,
    val end: Boolean = true,
    val cell: @Composable (UsageRecord) -> Unit,
)

private fun logColumns(provider: UsageProvider): List<LogColumn> {
    val lab = LogColumn("Lab", 136.dp, end = false) { LabCell(it.model) }
    val model = LogColumn("Model", 184.dp, end = false) { TextCell(modelName(provider, it.model), strong = true) }
    val input = LogColumn("Input", 76.dp) { FigureCell(it.inputTokens?.let(SpendFormat::compact)) }
    val output = LogColumn("Output", 76.dp) { FigureCell(it.outputTokens?.let(SpendFormat::compact)) }
    val cached = LogColumn("Cached", 76.dp) { FigureCell(it.cachedTokens?.let(SpendFormat::compact)) }
    val cost = LogColumn("Cost", 88.dp) { FigureCell(it.costUsd?.let(SpendFormat::usd), strong = true) }
    val kind = LogColumn("Type", 104.dp, end = false) { TextCell(kindLabel(it.kind)) }
    return when (provider) {
        UsageProvider.OpenRouter -> listOf(lab, model, input, output, cost)
        UsageProvider.XAi -> listOf(model, input, output, cost)
        UsageProvider.Deepgram -> listOf(model, LogColumn("Audio", 84.dp) { FigureCell(it.audioSeconds?.let(SpendFormat::seconds)) }, cost)
        UsageProvider.Exa -> listOf(kind, model, cost)
        UsageProvider.Firecrawl -> listOf(kind, LogColumn("Credits", 84.dp) { FigureCell(it.credits?.let(SpendFormat::credits), strong = true) })
        UsageProvider.Parallel -> listOf(kind, LogColumn("Billed", 220.dp, end = false) { TextCell(it.detail ?: "—") })
        UsageProvider.OpenAi, UsageProvider.Claude, UsageProvider.Gemini -> listOf(model, input, output, cached, cost)
        else -> listOf(model, input, output, cached)
    }
}

@Composable
private fun LogHeader(columns: List<LogColumn>, scroll: ScrollState, showHint: Boolean) {
    val bg = MaterialTheme.colorScheme.surface
    Surface(color = bg, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(TimeColumnWidth).padding(start = Spacing.base),
                )
                Row(Modifier.weight(1f).fadeTrailingEdge(scroll, bg).horizontalScroll(scroll)) {
                    columns.forEach { column ->
                        Box(
                            Modifier.width(column.width).padding(horizontal = Spacing.s),
                            contentAlignment = if (column.end) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Text(column.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(Spacing.base))
                }
                if (showHint && scroll.canScrollForward) {
                    Icon(
                        Icons.Outlined.SwapHoriz, "Scroll sideways for more columns",
                        Modifier.padding(end = Spacing.xs).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, rows: List<UsageRecord>, provider: UsageProvider) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy", Locale.getDefault()).format(day)
    }
    val subtotal = when (provider.unit) {
        UsageUnit.Usd -> SpendFormat.usd(rows.sumOf { it.costUsd ?: 0.0 })
        UsageUnit.Credits -> SpendFormat.credits(rows.sumOf { it.credits ?: 0.0 }) + " credits"
        UsageUnit.Tokens -> SpendFormat.compact(rows.sumOf { (it.inputTokens ?: 0) + (it.outputTokens ?: 0) }) + " tokens"
        UsageUnit.Requests -> null
    }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.l, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            listOfNotNull(subtotal, SpendFormat.plural(rows.size, "request")).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogRow(record: UsageRecord, columns: List<LogColumn>, scroll: ScrollState, index: Int, count: Int, onClick: () -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceContainer
    Surface(onClick = onClick, shape = groupedItemShape(index, count), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(LogRowHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeOf(record.createdAt),
                style = MaterialTheme.typography.titleSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(TimeColumnWidth).padding(start = Spacing.base),
            )
            Row(
                Modifier.weight(1f).fadeTrailingEdge(scroll, bg).horizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { column ->
                    Box(
                        Modifier.width(column.width).padding(horizontal = Spacing.s),
                        contentAlignment = if (column.end) Alignment.CenterEnd else Alignment.CenterStart,
                    ) { column.cell(record) }
                }
                Spacer(Modifier.width(Spacing.base))
            }
        }
    }
}

@Composable
private fun LabCell(model: String?) {
    if (model == null) { TextCell(null); return }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProviderMark(model, size = 22.dp)
        Spacer(Modifier.width(Spacing.s))
        TextCell(labName(model))
    }
}

@Composable
private fun TextCell(text: String?, strong: Boolean = false) {
    Text(
        text ?: "—",
        style = MaterialTheme.typography.bodyMedium,
        color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant
        else if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FigureCell(text: String?, strong: Boolean = false) {
    Text(
        text ?: "—",
        style = (if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).tabular(),
        color = if (text == null || !strong) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

// ── Detail sheet ─────────────────────────────────────────────────────────────────────

@Composable
private fun RecordSheet(provider: UsageProvider, record: UsageRecord, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).navigationBarsPadding().padding(bottom = Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (provider == UsageProvider.OpenRouter && record.model != null) ProviderMark(record.model, size = 44.dp)
                else SpendMark(provider, size = 44.dp)
                Spacer(Modifier.width(Spacing.base))
                Column(Modifier.weight(1f)) {
                    Text(
                        modelName(provider, record.model) ?: kindLabel(record.kind),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            record.model?.takeIf { provider == UsageProvider.OpenRouter }?.let(::labName),
                            fullTimeOf(record.createdAt),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.l))
            record.costUsd?.let {
                DetailHeadline(SpendFormat.usd(it), if (provider in ListPrices.priced) "at list prices" else "charged by ${provider.label}")
            }
            record.credits?.let { DetailHeadline(SpendFormat.credits(it), "credits") }
            val lines = listOfNotNull(
                "Type" to kindLabel(record.kind),
                record.inputTokens?.let { "Input tokens" to String.format(Locale.US, "%,d", it) },
                record.cachedTokens?.let { "Cached input" to String.format(Locale.US, "%,d", it) },
                record.outputTokens?.let { "Output tokens" to String.format(Locale.US, "%,d", it) },
                record.reasoningTokens?.let { "Reasoning tokens" to String.format(Locale.US, "%,d", it) },
                record.audioSeconds?.let { "Audio" to SpendFormat.seconds(it) },
                record.detail?.let { "Billed" to it },
                (if (record.costUsd == null && provider.unit == UsageUnit.Usd) "Cost" to "Not reported yet" else null),
            )
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(vertical = Spacing.s)) {
                    lines.forEach { (label, value) -> DetailLine(label, value) }
                }
            }
            record.externalId?.let {
                Spacer(Modifier.height(Spacing.m))
                Text("Request ID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DetailHeadline(value: String, caption: String) {
    Row(Modifier.padding(bottom = Spacing.m)) {
        Text(value, style = MaterialTheme.typography.displaySmall.tabular(), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(Spacing.s))
        Text(caption, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.s)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Labels ───────────────────────────────────────────────────────────────────────────

/** The lab behind an OpenRouter model id (`anthropic/claude-…` → Anthropic). */
internal fun labName(model: String): String {
    val identity = ProviderIdentity.of(model)
    if (identity != ProviderIdentity.Other) return identity.label
    val vendor = model.substringBefore('/', missingDelimiterValue = "").ifBlank { return "—" }
    return vendor.split('-', '_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
}

/** The model without its vendor prefix on OpenRouter, where the lab has its own column. */
private fun modelName(provider: UsageProvider, model: String?): String? =
    if (provider == UsageProvider.OpenRouter) model?.substringAfter('/') else model

private fun kindLabel(kind: String): String = when (runCatching { UsageKind.valueOf(kind) }.getOrNull()) {
    UsageKind.Chat -> "Chat"
    UsageKind.Image -> "Image"
    UsageKind.Video -> "Video"
    UsageKind.Transcription -> "Dictation"
    UsageKind.Search -> "Search"
    UsageKind.Research -> "Research"
    UsageKind.Browse -> "Browse"
    else -> "Other"
}

private fun timeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun fullTimeOf(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun relativeTime(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> "on ${shortDate(millis)}"
    }
}
