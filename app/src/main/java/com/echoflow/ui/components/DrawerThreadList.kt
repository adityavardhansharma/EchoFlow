@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
private const val PINNED_SECTION = "Pinned"

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
    onPin: (ChatThread) -> Unit,
    onUnpin: (ChatThread) -> Unit,
    onRename: (ChatThread) -> Unit,
    onDelete: (ChatThread) -> Unit,
    grouped: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val sections = remember(threads, grouped) {
        val pinned = threads.filter { it.isPinned }
        val unpinned = threads.filter { !it.isPinned }
        val unpinnedSections = if (grouped) ThreadRecency.group(unpinned) else listOf(null to unpinned)
        if (pinned.isEmpty()) unpinnedSections
        else listOf(PINNED_SECTION to pinned) + unpinnedSections
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
                    onPin = { onPin(thread) },
                    onUnpin = { onUnpin(thread) },
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
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append(thread.title)
        if (thread.isPinned) append(", pinned")
        if (rendering) append(", video rendering")
    }
    Box {
        Surface(
            shape = PillShape,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .semantics { contentDescription = description },
        ) {
            Row(
                Modifier.padding(start = Spacing.base, end = Spacing.base).heightIn(min = 52.dp),
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
                if (thread.isPinned) {
                    Spacer(Modifier.width(Spacing.s))
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp),
                        tint = mutedColor,
                    )
                }
                // Same 6dp breathing dot as the mode switch: one vocabulary for "still working",
                // so a single glance always means the same thing wherever it appears.
                if (rendering) {
                    Spacer(Modifier.width(Spacing.s))
                    RenderingPulse(color = MaterialTheme.colorScheme.primary, reducedMotion = reducedMotion)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (thread.isPinned) {
                DropdownMenuItem(
                    text = { Text("Unpin") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        onUnpin()
                        menuOpen = false
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Pin") },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    onClick = {
                        onPin()
                        menuOpen = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onRename()
                    menuOpen = false
                },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onDelete()
                    menuOpen = false
                },
            )
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
