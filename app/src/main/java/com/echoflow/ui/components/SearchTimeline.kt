@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.Citation
import com.echoflow.data.SearchSource
import com.echoflow.ui.theme.Spacing

private fun hostOf(url: String): String =
    runCatching { android.net.Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

private fun faviconUrl(url: String): String =
    "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""}&sz=64"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }
}

/**
 * One web-search step in the assistant's reply timeline (t3.chat style): an animated
 * "Searching…" row while the query runs, then a collapsed summary with overlapping
 * favicons that expands into the tappable source list.
 */
@Composable
fun SearchActivityCard(
    query: String,
    sources: List<SearchSource>,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "search-chevron")
    val context = LocalContext.current

    Surface(
        onClick = { if (!active && sources.isNotEmpty()) expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Language, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.width(Spacing.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (active) "Searching the web" else "Searched the web",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (query.isNotBlank()) {
                        Text(
                            "“$query”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!active && sources.isNotEmpty()) {
                    Spacer(Modifier.width(Spacing.s))
                    FaviconStack(sources)
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "${sources.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                        Modifier.size(20.dp).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!active) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "No results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(
                    Modifier.padding(top = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
                ) {
                    sources.forEachIndexed { index, source ->
                        Surface(
                            onClick = { openUrl(context, source.url) },
                            shape = groupedItemShape(index, sources.size, large = 14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = faviconUrl(source.url),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(Spacing.m))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        source.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        hostOf(source.url),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    source.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                                        Text(
                                            snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Up to five overlapping favicons, the compact "who answered" visual. */
@Composable
private fun FaviconStack(sources: List<SearchSource>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        sources.take(5).forEach { source ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(20.dp),
            ) {
                AsyncImage(
                    model = faviconUrl(source.url),
                    contentDescription = null,
                    modifier = Modifier.padding(2.dp).clip(CircleShape),
                )
            }
        }
    }
}

/** Compact source chips under a finished answer; each opens the page. */
@Composable
fun SourcesRow(citations: List<Citation>, modifier: Modifier = Modifier) {
    if (citations.isEmpty()) return
    val context = LocalContext.current
    Column(modifier) {
        Text(
            "Sources",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            citations.forEach { citation ->
                Surface(
                    onClick = { openUrl(context, citation.url) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = Spacing.m, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = faviconUrl(citation.url),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            hostOf(citation.url),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
