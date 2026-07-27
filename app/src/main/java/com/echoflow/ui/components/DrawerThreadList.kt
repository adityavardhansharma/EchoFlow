@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.data.ChatThread
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import java.util.Calendar

private val PillShape = RoundedCornerShape(28.dp)

/**
 * One mode's conversations, grouped by when they were last touched.
 *
 * The grouping costs nothing and is the difference between a list and something that looks
 * looked-after — a flat run of thirty titles gives the eye nowhere to land.
 */
@Composable
fun DrawerThreadList(
    threads: List<ChatThread>,
    currentThreadId: String?,
    renderingChatIds: Set<String>,
    onThreadSelected: (ChatThread) -> Unit,
    onRename: (ChatThread) -> Unit,
    onDelete: (ChatThread) -> Unit,
    grouped: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val sections = remember(threads, grouped) {
        if (grouped) ThreadRecency.group(threads) else listOf(null to threads)
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        sections.forEach { (label, sectionThreads) ->
            if (label != null) {
                item(key = "section-$label") {
                    Box(Modifier.padding(top = Spacing.s, bottom = Spacing.xs)) { SectionLabel(label) }
                }
            }
            items(sectionThreads, key = { it.id }) { thread ->
                ThreadPill(
                    thread = thread,
                    selected = thread.id == currentThreadId,
                    rendering = thread.id in renderingChatIds,
                    reducedMotion = reducedMotion,
                    onClick = { onThreadSelected(thread) },
                    onRename = { onRename(thread) },
                    onDelete = { onDelete(thread) },
                )
            }
        }
    }
}

@Composable
private fun ThreadPill(
    thread: ChatThread,
    selected: Boolean,
    rendering: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append(thread.title)
        if (rendering) append(", video rendering")
    }
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.xs).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (thread.mode == AppMode.Imagine) Icons.Default.AutoFixHigh else Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = mutedColor,
            )
            Spacer(Modifier.width(Spacing.m))
            Text(
                thread.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            // Same 6dp breathing dot as the mode switch: one vocabulary for "still working",
            // so a single glance always means the same thing wherever it appears.
            if (rendering) {
                Spacer(Modifier.width(Spacing.s))
                RenderingPulse(color = MaterialTheme.colorScheme.primary, reducedMotion = reducedMotion)
                Spacer(Modifier.width(Spacing.xs))
            }
            if (selected) {
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, "Rename", Modifier.size(18.dp), tint = contentColor)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Buckets conversations by last activity. Pure and calendar-based rather than arithmetic on
 * millisecond spans: "yesterday" means the previous calendar day, not 24-to-48 hours ago, and
 * a conversation touched at 00:30 should not be filed under yesterday at 00:45.
 */
internal object ThreadRecency {
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val THIS_WEEK = "Previous 7 days"
    const val EARLIER = "Older"

    fun group(
        threads: List<ChatThread>,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<String, List<ChatThread>>> {
        val order = listOf(TODAY, YESTERDAY, THIS_WEEK, EARLIER)
        return threads.groupBy { bucketOf(it.updatedAt, now) }
            .toList()
            .sortedBy { (label, _) -> order.indexOf(label) }
    }

    fun bucketOf(timestamp: Long, now: Long): String {
        val startOfToday = startOfDay(now)
        return when {
            timestamp >= startOfToday -> TODAY
            timestamp >= startOfToday - DAY_MS -> YESTERDAY
            timestamp >= startOfToday - 6 * DAY_MS -> THIS_WEEK
            else -> EARLIER
        }
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
