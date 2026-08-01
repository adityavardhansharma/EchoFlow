@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ChatThread
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import java.util.Calendar

private const val PINNED_SECTION = "Pinned"

// Rows within a section share a slab: large radius on the section's outer corners, tight radius
// where rows meet. This is the same grouped-list vocabulary the Settings screens use, so the
// drawer stops looking like a stack of detached buttons and starts looking looked-after.
private val RowGap = GroupedItemGap

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
    // A single action sheet for the whole list, driven by whichever row was long-pressed. One
    // lifted sheet beats a DropdownMenu per row: less state, and it's the modern mobile pattern
    // for long-press actions.
    var actionTarget by remember { mutableStateOf<ChatThread?>(null) }

    val sections = remember(threads, grouped) {
        val pinned = threads.filter { it.isPinned }
        val unpinned = threads.filter { !it.isPinned }
        val unpinnedSections = if (grouped) ThreadRecency.group(unpinned) else listOf(null to unpinned)
        if (pinned.isEmpty()) unpinnedSections
        else listOf(PINNED_SECTION to pinned) + unpinnedSections
    }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
        sections.forEach { (label, sectionThreads) ->
            if (label != null) {
                item(key = "section-$label") {
                    // Extra top space above the header separates one slab from the next; the
                    // rows inside a slab stay tight at RowGap.
                    Box(Modifier.padding(top = Spacing.m, bottom = Spacing.xs)) { SectionLabel(label) }
                }
            }
            itemsIndexed(sectionThreads, key = { _, t -> t.id }) { index, thread ->
                ThreadRow(
                    thread = thread,
                    selected = thread.id == currentThreadId,
                    rendering = thread.id in renderingChatIds,
                    reducedMotion = reducedMotion,
                    shape = groupedItemShape(index, sectionThreads.size),
                    onClick = { onThreadSelected(thread) },
                    onLongPress = { actionTarget = thread },
                    onPin = { onPin(thread) },
                    onUnpin = { onUnpin(thread) },
                    onRename = { onRename(thread) },
                    onDelete = { onDelete(thread) },
                )
            }
        }
    }

    actionTarget?.let { thread ->
        ThreadActionSheet(
            thread = thread,
            onDismiss = { actionTarget = null },
            onPin = { onPin(thread) },
            onUnpin = { onUnpin(thread) },
            onRename = { onRename(thread) },
            onDelete = { onDelete(thread) },
        )
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThread,
    selected: Boolean,
    rendering: Boolean,
    reducedMotion: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append(thread.title)
        if (thread.isPinned) append(", pinned")
        if (rendering) append(", video rendering")
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .semantics {
                contentDescription = description
                customActions = buildList {
                    add(
                        CustomAccessibilityAction(
                            if (thread.isPinned) "Unpin" else "Pin",
                        ) {
                            if (thread.isPinned) onUnpin() else onPin()
                            true
                        },
                    )
                    add(CustomAccessibilityAction("Rename") { onRename(); true })
                    add(CustomAccessibilityAction("Delete") { onDelete(); true })
                }
            },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base).heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // On the selected row a slim accent bar marks the active conversation — quieter and
            // more modern than stamping the same chat icon on every single row.
            if (selected) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(Spacing.m))
            }
            Text(
                thread.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (thread.isPinned) {
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Filled.PushPin,
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
}

/**
 * Long-press actions for a conversation, as an expressive bottom sheet.
 *
 * A sheet reads as a first-class surface — a titled header naming the conversation, plus big
 * touch-friendly action rows — where the old anchored dropdown read as a leftover from stock
 * Android. Tapping a row slides the sheet out and only then runs the action, so a Rename/Delete
 * dialog never lands on top of a still-animating sheet.
 */
@Composable
private fun ThreadActionSheet(
    thread: ChatThread,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet out first, then dismiss and run the action. Rename and Delete each raise a
    // dialog, so firing the action up front would stack that dialog over a still-animating sheet
    // (double scrim, plus a sheet-exit flash when the dialog later closes). Waiting for the hide
    // animation keeps it to one modal on screen at a time.
    fun act(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismiss()
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.base)
                .padding(bottom = Spacing.xl),
        ) {
            Text(
                thread.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            Text(
                if (thread.isPinned) "Pinned conversation" else "Conversation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            )
            Spacer(Modifier.height(Spacing.s))
            Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
                if (thread.isPinned) {
                    ActionSheetRow(
                        icon = Icons.Outlined.PushPin,
                        label = "Unpin",
                        shape = groupedItemShape(0, 3),
                        chipColor = MaterialTheme.colorScheme.secondaryContainer,
                        chipContent = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { act(onUnpin) },
                    )
                } else {
                    ActionSheetRow(
                        icon = Icons.Filled.PushPin,
                        label = "Pin",
                        shape = groupedItemShape(0, 3),
                        chipColor = MaterialTheme.colorScheme.secondaryContainer,
                        chipContent = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { act(onPin) },
                    )
                }
                ActionSheetRow(
                    icon = Icons.Filled.Edit,
                    label = "Rename",
                    shape = groupedItemShape(1, 3),
                    chipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    chipContent = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { act(onRename) },
                )
                ActionSheetRow(
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete",
                    shape = groupedItemShape(2, 3),
                    chipColor = MaterialTheme.colorScheme.errorContainer,
                    chipContent = MaterialTheme.colorScheme.onErrorContainer,
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { act(onDelete) },
                )
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    shape: androidx.compose.ui.graphics.Shape,
    chipColor: Color,
    chipContent: Color,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(chipColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = chipContent)
            }
            Spacer(Modifier.width(Spacing.base))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
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
