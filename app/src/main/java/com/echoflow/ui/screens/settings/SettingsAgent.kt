@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

// ── Echo Agent ────────────────────────────────────────────────────────────────────────

@Composable
internal fun EchoAgentPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val profiles by viewModel.agentProfiles.collectAsState()
    val selectedId by viewModel.echoAgentProfileId.collectAsState()
    val enabled by viewModel.echoAgentEnabled.collectAsState()
    var showDirectory by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<OpenRouterModelInfo?>(null) }

    EchoProfilesPage(
        viewModel = viewModel,
        mode = "Echo Agents",
        subtitle = "Your main model hands work to an Echo Agent",
        enabled = enabled,
        onEnabledChange = viewModel::saveEchoAgentEnabled,
        description = "Your main model gets a full toolbox — web search, web fetch, and an Echo Agent (a faster, cheaper model) it can hand self-contained tasks to. It decides which tools to use and in what order, chaining them freely. Pick the main model per chat from the model selector; choose the Echo Agent here. OpenRouter only; each message can add cost.",
        sectionTitle = "Your Echo Agents",
        emptyIcon = Icons.Default.Hub,
        emptyText = "No Echo Agents yet.\nAdd one below — a fast, cheap model works best.",
        rows = profiles.map {
            EchoProfileRow(it.id, it.name, "${it.workerModelName.ifBlank { it.workerModelId }} · up to ${it.maxToolCalls} steps")
        },
        selectedId = selectedId,
        onSelect = viewModel::saveEchoAgentProfile,
        onDelete = viewModel::deleteAgentProfile,
        addLabel = "Add an Echo Agent",
        onAdd = { showDirectory = true; viewModel.loadOpenRouterDirectory() },
        onBack = onBack,
        footnote = "Your Echo Agent can search and fetch the web while doing its task. It never sees the chat history — only the task the main model gives it.",
    )

    if (showDirectory) {
        OpenRouterPickerSheet(
            viewModel,
            onAdd = { info -> pendingModel = info; showDirectory = false },
            onDismiss = { showDirectory = false },
        )
    }
    pendingModel?.let { model ->
        AgentProfileDialog(
            workerName = model.name,
            onDismiss = { pendingModel = null },
            onConfirm = { name, maxCalls ->
                viewModel.addAgentProfile(name, model.id, model.name.substringAfter(": "), maxCalls)
                pendingModel = null
            },
        )
    }
}

/**
 * Finishes an Echo Agent profile: a name plus the agent's per-task tool-call budget (how many
 * tool-calling steps the agent may take while completing one delegated task).
 */
@Composable
internal fun AgentProfileDialog(
    workerName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var maxCalls by remember { mutableStateOf(8) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Hub, null) },
        title = { Text("New Echo Agent") },
        text = {
            Column {
                Text(workerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Fast agent, Cheap drone…") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Echo Agent tool budget", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text("$maxCalls steps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Max tool-calling steps the Echo Agent may take per task",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = maxCalls.toFloat(),
                    onValueChange = { maxCalls = it.toInt().coerceIn(1, 25) },
                    valueRange = 1f..25f,
                    steps = 23,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), maxCalls) },
                enabled = name.trim().isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Local models ──────────────────────────────────────────────────────────────────────
