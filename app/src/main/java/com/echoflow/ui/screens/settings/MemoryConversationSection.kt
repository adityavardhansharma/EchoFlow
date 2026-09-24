@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

/** The three conversation controls, then — while learning is on — the learning pipeline. */
@Composable
internal fun MemoryConversationSection(vm: MemoryViewModel, onEnableLearning: () -> Unit) {
    MemorySectionHeader("In your conversations", supporting = "Choose how Supermemory helps")
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        MemorySwitchRow(
            icon = Icons.Default.AutoAwesome,
            title = "Use memory",
            detail = "Let supported models recall relevant details and save durable personal facts you clearly state.",
            checked = vm.recall,
            enabled = !vm.busy,
            index = 0, count = 3,
            onChange = vm::updateRecall,
        )
        MemorySwitchRow(
            icon = Icons.Default.School,
            title = "Learn from conversations",
            detail = "Send new chat text to Supermemory to learn lasting preferences, facts and projects.",
            checked = vm.learn,
            enabled = !vm.busy,
            index = 1, count = 3,
            onChange = { if (it) onEnableLearning() else vm.setLearning(false) },
        )
        MemorySwitchRow(
            icon = Icons.Default.PhoneAndroid,
            title = "Include on-device chats",
            detail = "Allow eligible local chat text to leave your device for Supermemory.",
            checked = vm.local,
            enabled = !vm.busy,
            index = 2, count = 3,
            onChange = vm::updateLocal,
        )
    }
    AnimatedVisibility(visible = vm.learn, enter = sectionEnter(), exit = sectionExit()) {
        Column {
            Spacer(Modifier.height(Spacing.xl))
            LearningCard(vm)
        }
    }
    if (vm.learningNote.isNotBlank()) {
        Spacer(Modifier.height(Spacing.m))
        MemoryBanner(vm.learningNote, MemoryTone.Error)
        TextButton(onClick = vm::retryLearning, enabled = vm.learn && !vm.busy) { Text("Retry learning") }
    }
}

/**
 * The learning pipeline as four live counts rather than a sentence, so "is it working?" is
 * answered at a glance, with the manual flush as the card's one action.
 */
@Composable
private fun LearningCard(vm: MemoryViewModel) {
    val status = vm.learningStatus
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Learning", style = MaterialTheme.typography.titleMedium)
                    Text(
                        status.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = vm::refreshLearningStatus, enabled = !vm.busy) {
                    Icon(Icons.Default.Refresh, "Refresh learning status")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.base),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                MemoryStat(status.queued, "Queued", Modifier.weight(1f))
                MemoryStat(status.processing, "Processing", Modifier.weight(1f))
                MemoryStat(status.ready, "Ready", Modifier.weight(1f))
                MemoryStat(status.unavailable, "Attention", Modifier.weight(1f), attention = true)
            }
            Button(
                onClick = vm::learnNow,
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.base).height(52.dp),
            ) {
                Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Learn queued chats now")
            }
            Text(
                "Processes eligible completed chats without waiting for the usual batch. Unchanged chats aren't uploaded twice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs, end = Spacing.xs),
            )
        }
    }
}
