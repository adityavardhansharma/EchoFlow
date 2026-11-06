@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

// ── Echo Fusion ───────────────────────────────────────────────────────────────────────

@Composable
internal fun EchoFusionPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val panels by viewModel.fusionPanels.collectAsState()
    val selectedId by viewModel.echoFusionPanelId.collectAsState()
    val enabled by viewModel.echoFusionEnabled.collectAsState()
    var showDirectory by remember { mutableStateOf(false) }
    val pendingModels = remember { mutableStateMapOf<String, String>() } // id -> display name
    var nameDialog by remember { mutableStateOf(false) }

    EchoProfilesPage(
        viewModel = viewModel,
        mode = "Echo Fusion",
        subtitle = "A panel of models deliberates",
        enabled = enabled,
        onEnabledChange = viewModel::saveEchoFusionEnabled,
        description = "Several models answer the same question in parallel and a judge compares them — surfacing where they agree, where they disagree, and what they all missed. Best for research, comparisons and high-stakes questions. OpenRouter only; cost-heavy — every message runs the whole panel plus a judge.",
        sectionTitle = "Your panels",
        emptyIcon = Icons.Default.AccountTree,
        emptyText = "No panels yet.\nBuild one below from 2–8 models.",
        rows = panels.map { EchoProfileRow(it.id, it.name, it.names.joinToString(" · ").ifBlank { "${it.models.size} models" }) },
        selectedId = selectedId,
        onSelect = viewModel::saveEchoFusionPanel,
        onDelete = viewModel::deleteFusionPanel,
        addLabel = "New panel",
        onAdd = { pendingModels.clear(); showDirectory = true; viewModel.loadOpenRouterDirectory() },
        onBack = onBack,
        footnote = "A panel needs 2–8 models. The first model judges unless you change it later.",
    )

    if (showDirectory) {
        OpenRouterPickerSheet(
            viewModel,
            addedIds = pendingModels.keys.toSet(),
            onAdd = { info -> if (pendingModels.size < 8) pendingModels[info.id] = info.name.substringAfter(": ") },
            onRemove = { id -> pendingModels.remove(id) },
            onDismiss = { showDirectory = false; if (pendingModels.size >= 2) nameDialog = true },
        )
    }
    if (nameDialog) {
        FusionPanelDialog(
            models = pendingModels.toList(),
            onDismiss = { nameDialog = false; pendingModels.clear() },
            onConfirm = { name, judgeId ->
                viewModel.addFusionPanel(name, pendingModels.toList(), judgeModelId = judgeId)
                nameDialog = false
                pendingModels.clear()
            },
        )
    }
}

/**
 * Finishes a fusion panel: a name plus a **judge** picker — the model that compares the panel
 * and writes the final answer (defaults to the first/strongest member).
 */
@Composable
internal fun FusionPanelDialog(
    models: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var judgeId by remember { mutableStateOf(models.firstOrNull()?.first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountTree, null) },
        title = { Text("New fusion panel") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Heavy hitters, Fast trio…") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
                Text("Judge", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Compares the panel and writes the final answer",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
                Column(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
                ) {
                    models.forEach { (id, label) ->
                        val selected = judgeId == id
                        Surface(
                            onClick = { judgeId = id },
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(end = Spacing.m),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = { judgeId = id })
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), judgeId) },
                enabled = name.trim().isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
