@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

@Composable
internal fun MemoryConversationSection(vm: MemoryViewModel, onEnableLearning: () -> Unit) {
    PageSection("In your conversations", "Choose how Supermemory helps")
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        MemoryPreferenceRow(
            title = "Use memory",
            detail = "Let supported models recall relevant details and save durable personal facts you clearly state.",
            checked = vm.recall,
            enabled = !vm.busy,
            index = 0,
            onChange = vm::updateRecall,
        )
        MemoryPreferenceRow(
            title = "Learn from conversations",
            detail = "Send new chat text to Supermemory to learn lasting preferences, facts and projects.",
            checked = vm.learn,
            enabled = !vm.busy,
            index = 1,
            onChange = { if (it) onEnableLearning() else vm.setLearning(false) },
        )
        MemoryPreferenceRow(
            title = "Include on-device chats",
            detail = "Allow eligible local chat text to leave your device for Supermemory.",
            checked = vm.local,
            enabled = !vm.busy,
            index = 2,
            onChange = vm::updateLocal,
        )
    }
    AnimatedVisibility(visible = vm.learn, enter = sectionEnter(), exit = sectionExit()) {
        Column {
            Spacer(Modifier.height(Spacing.xl))
            PageSection("Learning", "Activity from eligible conversations")
            FormCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Learning status", style = MaterialTheme.typography.titleSmall)
                        Text(
                            vm.learningStatus.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = vm::refreshLearningStatus, enabled = !vm.busy) {
                        Icon(Icons.Default.Refresh, "Refresh learning status")
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                FilledTonalButton(
                    onClick = vm::learnNow,
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Learn queued chats now") }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Process eligible completed chats without waiting for the normal batch threshold. Unchanged chats aren't uploaded twice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (vm.learningNote.isNotBlank()) {
        Spacer(Modifier.height(Spacing.m))
        Text(vm.learningNote, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = vm::retryLearning, enabled = vm.learn && !vm.busy) { Text("Retry learning") }
    }
}

@Composable
private fun MemoryPreferenceRow(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean,
    index: Int,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = groupedItemShape(index, 3),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
