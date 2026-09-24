@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.memory.MemoryPolicy
import com.echoflow.data.memory.RemoteMemory
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

private const val MEMORY_MAX_CHARS = 4000

// ── Editor ───────────────────────────────────────────────────────────────────────────────

/**
 * Add or edit a memory in a bottom sheet — a writing surface with room, rather than a cramped
 * dialog field. It stays open until the write succeeds, so a failure never loses the draft.
 */
@Composable
internal fun MemoryEditorSheet(
    isNew: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryMark(
                    if (isNew) Icons.Default.AutoAwesome else Icons.Default.Edit,
                    MaterialShapes.Cookie9Sided,
                    container = cs.primaryContainer, onContainer = cs.onPrimaryContainer, size = 48.dp,
                )
                Spacer(Modifier.width(Spacing.base))
                Column {
                    Text(if (isNew) "Add a memory" else "Edit memory", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Keep it specific — one fact, preference or project.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            TextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(MEMORY_MAX_CHARS)) },
                enabled = !busy,
                placeholder = { Text("What should be remembered?") },
                minLines = 4,
                maxLines = 10,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                    disabledContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "Never include passwords or API keys.",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${"%,d".format(draft.length)} / ${"%,d".format(MEMORY_MAX_CHARS)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            error?.let { MemoryBanner(it, MemoryTone.Error) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Cancel")
                }
                Button(onClick = onSave, enabled = !busy && draft.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp)) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(22.dp), color = cs.onPrimary)
                        Spacer(Modifier.width(Spacing.s))
                    }
                    Text("Save memory")
                }
            }
        }
    }
}

// ── Details ──────────────────────────────────────────────────────────────────────────────

/**
 * Everything known about one memory: the text (selectable), when it changed, where it came from
 * and what it used to say — with Edit and Forget right there, so reading leads straight to acting.
 */
@Composable
internal fun MemoryDetailSheet(
    memory: RemoteMemory,
    enabled: Boolean,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxl),
        ) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MemoryMark(
                        Icons.Default.AutoAwesome, MaterialShapes.Flower,
                        container = cs.tertiaryContainer, onContainer = cs.onTertiaryContainer, size = 48.dp,
                    )
                    Spacer(Modifier.width(Spacing.base))
                    Column {
                        Text("Memory", style = MaterialTheme.typography.headlineSmall)
                        Text("Updated ${memoryDate(memory.updated)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            item(key = "text") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                ) {
                    SelectionContainer {
                        Text(memory.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.l))
                    }
                }
            }
            item(key = "actions") {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    FilledTonalButton(onClick = onEdit, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = onForget,
                        enabled = enabled,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                        border = BorderStroke(1.dp, cs.error.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("Forget")
                    }
                }
            }
            item(key = "sources-label") {
                MemorySectionHeader("Sources", modifier = Modifier.padding(top = Spacing.xl))
            }
            if (memory.sources.isEmpty()) {
                item(key = "sources-empty") {
                    Text(
                        "No source information returned by Supermemory.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }
            itemsIndexed(memory.sources) { index, source ->
                Surface(
                    shape = groupedItemShape(index, memory.sources.size),
                    color = cs.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = GroupedItemGap),
                ) {
                    Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Link, null, Modifier.padding(top = 2.dp).size(16.dp), tint = cs.primary)
                        Spacer(Modifier.width(Spacing.m))
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(source, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (memory.history.isNotEmpty()) {
                item(key = "history-label") {
                    MemorySectionHeader("Earlier versions", modifier = Modifier.padding(top = Spacing.xl))
                }
                itemsIndexed(memory.history) { index, previous ->
                    HistoryEntry(previous, last = index == memory.history.lastIndex)
                }
            }
        }
    }
}

/** One earlier version on a quiet timeline: a dot, a connecting rule, then the old text. */
@Composable
private fun HistoryEntry(text: String, last: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(cs.outline))
            if (!last) {
                Box(Modifier.padding(top = Spacing.xs).width(2.dp).weight(1f).background(cs.outlineVariant))
            }
        }
        Spacer(Modifier.width(Spacing.s))
        SelectionContainer(Modifier.weight(1f).padding(bottom = Spacing.base)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

// ── Confirmations ────────────────────────────────────────────────────────────────────────

@Composable
internal fun ForgetMemoryDialog(
    memory: RemoteMemory,
    busy: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Default.DeleteOutline, null, tint = cs.error) },
        title = { Text("Forget this memory?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        memory.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(Spacing.m),
                    )
                }
                Text(
                    "It stops being an active memory. Supermemory can keep its history and sources, and learning from those sources may recreate it. For a fuller removal, manage source data in your dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                )
                error?.let { MemoryBanner(it, MemoryTone.Error) }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError),
            ) { Text("Forget memory") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/** The cleanup plan as figures first, then the caveat — so the scale of the change is obvious. */
@Composable
internal fun CleanupDialog(plan: MemoryPolicy.CleanupPlan, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CleaningServices, null) },
        title = { Text("Clean up memories?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MemoryStat(plan.discard.size, "To forget", Modifier.weight(1f), attention = true)
                    MemoryStat(plan.duplicateGroups, "Duplicate sets", Modifier.weight(1f))
                    MemoryStat(plan.metaMemories, "Assistant-meta", Modifier.weight(1f))
                }
                Text(
                    buildString {
                        append("This forgets ${plan.discard.size} low-quality ")
                        append(if (plan.discard.size == 1) "memory" else "memories")
                        append(". ")
                        if (plan.duplicateGroups > 0) append("One copy from each duplicate set is kept. ")
                        append("Source conversations in Supermemory aren't deleted and may recreate facts later.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy) { Text("Clean up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
