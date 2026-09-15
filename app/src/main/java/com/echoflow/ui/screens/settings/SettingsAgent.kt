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
    val apiKey by viewModel.apiKey.collectAsState()
    val profiles by viewModel.agentProfiles.collectAsState()
    val selectedId by viewModel.echoAgentProfileId.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<OpenRouterModelInfo?>(null) }

    SettingsPageScaffold(title = "Echo Agents", subtitle = "Your main model hands work to an Echo Agent", onBack = onBack) {
        val agentEnabled by viewModel.echoAgentEnabled.collectAsState()
        LabMasterToggle("Echo Agents", "Show this mode in the chat + menu", agentEnabled, viewModel::saveEchoAgentEnabled)
        Spacer(Modifier.height(Spacing.m))
        FormCard {
            Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Your main model gets a full toolbox — web search, web fetch, and an Echo Agent (a faster, cheaper model) it can hand self-contained tasks to. It decides which tools to use and in what order, chaining them freely. Pick the main model per chat from the model selector; choose the Echo Agent here. OpenRouter only; each message can add cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (apiKey.isBlank()) {
            Spacer(Modifier.height(Spacing.m))
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key under Models → OpenRouter to use Echo Agents.", error = true)
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Your Echo Agents", "Tap to set the default; pick per chat from the model selector")
        if (profiles.isEmpty()) {
            EchoNoticeCard(Icons.Default.Hub, "No Echo Agents yet.\nAdd one below — a fast, cheap model works best.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                profiles.forEach { profile ->
                    DrEngineSelectRow(
                        name = profile.name,
                        subtitle = "${profile.workerModelName.ifBlank { profile.workerModelId }} · up to ${profile.maxToolCalls} steps",
                        selected = profile.id == selectedId,
                        onClick = { viewModel.saveEchoAgentProfile(profile.id) },
                        onDelete = { viewModel.deleteAgentProfile(profile.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        FilledTonalButton(
            onClick = { showDirectory = true; viewModel.loadOpenRouterDirectory() },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Add an Echo Agent")
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            "Your Echo Agent can search and fetch the web while doing its task. It never sees the chat history — only the task the main model gives it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }

    if (showDirectory) {
        OpenRouterDirectorySheet(
            query = orQuery,
            results = orResults,
            loading = orLoading,
            error = orError,
            addedIds = emptySet(),
            onQueryChange = viewModel::updateOrModelQuery,
            onRetry = viewModel::loadOpenRouterDirectory,
            onAdd = { info -> pendingModel = info; showDirectory = false },
            onRemove = {},
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
