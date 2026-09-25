@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.usage.ListPrices
import com.echoflow.data.usage.UsageProvider
import com.echoflow.data.usage.UsageUnit
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Spend: every provider with a saved key, what it cost in the chosen window, and one combined
 * dollar total. Providers that bill in credits or report only tokens are listed apart and never
 * added to the total.
 */
@Composable
internal fun SpendHomePage(vm: SpendViewModel, onOpen: (UsageProvider) -> Unit, onBack: () -> Unit) {
    val home by vm.home.collectAsState()
    val window by vm.window.collectAsState()
    val timeline by vm.homeTimeline.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    SettingsPageScaffold(title = "Spend", subtitle = "What your saved keys have spent", onBack = onBack) {
        SpendWindowFilter(selected = window, onSelect = vm::select)
        Spacer(Modifier.height(Spacing.base))

        val colors = shareColors()
        SpendHero(
            label = "Total spend",
            pill = window.phrase.replaceFirstChar { it.uppercase() },
            decoration = MaterialShapes.Cookie12Sided,
            figure = { HeroDollars(home.totalUsd) },
        ) {
            Spacer(Modifier.height(Spacing.xl))
            timeline?.let { SpendChart(it, colors, SpendFormat::usd, animationKey = window) }
            val unplaced = home.totalUsd - (timeline?.totals?.sum() ?: 0.0)
            Spacer(Modifier.height(Spacing.m))
            Text(
                when {
                    home.isEmpty -> "Save an API key in Models or Custom and its spend shows up here."
                    home.dollars.isEmpty() -> "None of your providers bill in dollars. Their usage is listed below."
                    unplaced > 0.005 -> "${SpendFormat.usd(unplaced)} of OpenRouter's total was spent before EchoFlow itemised it, so it isn't in the bars."
                    else -> "Across " + SpendFormat.plural(home.dollars.size, "provider") + " that bill in dollars"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (home.dollars.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xl))
            PageSection("In dollars", "Added up in the total")
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                home.dollars.forEachIndexed { index, row ->
                    SpendProviderRow(
                        row = row,
                        trailing = SpendFormat.usd(row.usd ?: 0.0),
                        share = if (home.totalUsd > 0) (row.usd ?: 0.0) / home.totalUsd else null,
                        dot = colors[index.coerceAtMost(colors.lastIndex)],
                        index = index,
                        count = home.dollars.size,
                        onClick = { onOpen(row.provider) },
                    )
                }
            }
        }

        if (home.ownUnits.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xl))
            PageSection("In their own units", "Credits and tokens aren't added to the total")
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                home.ownUnits.forEachIndexed { index, row ->
                    SpendProviderRow(
                        row = row,
                        trailing = ownUnitsFigure(row),
                        share = null,
                        dot = null,
                        index = index,
                        count = home.ownUnits.size,
                        onClick = { onOpen(row.provider) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        SpendNote(
            buildString {
                append("Days, weeks and months follow UTC, the same as OpenRouter. ")
                vm.trackingStartedAt?.let {
                    append("EchoFlow has itemised requests on this phone since ${shortDate(it)}; ")
                    append("the log never leaves it.")
                }
            },
            leading = { Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        vm.refreshNote?.let {
            Spacer(Modifier.height(Spacing.s))
            SpendNote("OpenRouter figures couldn't refresh: $it Showing the last ones fetched.")
        }
    }
}

@Composable
private fun SpendProviderRow(
    row: SpendRow,
    trailing: String,
    share: Double?,
    dot: Color?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpendMark(row.provider, size = 44.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    row.provider.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    rowSummary(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (share != null && dot != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "${(share * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun rowSummary(row: SpendRow): String = when {
    row.providerReported -> "Reported by OpenRouter"
    row.requests == 0 -> "Nothing itemised yet"
    row.provider in ListPrices.priced -> SpendFormat.plural(row.requests, "request") + " · list prices"
    row.provider.unit == UsageUnit.Tokens && row.inputTokens != null ->
        "${SpendFormat.compact(row.inputTokens)} in · ${SpendFormat.compact(row.outputTokens ?: 0)} out"
    else -> SpendFormat.plural(row.requests, "request")
}

private fun ownUnitsFigure(row: SpendRow): String = when (row.provider.unit) {
    UsageUnit.Credits -> SpendFormat.credits(row.credits ?: 0.0) + " credits"
    UsageUnit.Requests -> SpendFormat.plural(row.requests, "request")
    else -> SpendFormat.compact((row.inputTokens ?: 0) + (row.outputTokens ?: 0)) + " tokens"
}

internal fun shortDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
