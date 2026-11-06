@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

/** One saved Echo Adviser advisor, Fusion panel or Echo Agent, as its settings list shows it. */
internal data class EchoProfileRow(val id: String, val name: String, val subtitle: String)

/**
 * The settings page Echo Adviser, Echo Fusion and Echo Agents share: the mode's on switch,
 * what it does, the OpenRouter key notice, the saved profiles with a default, and an add button.
 */
@Composable
internal fun EchoProfilesPage(
    viewModel: SettingsViewModel,
    mode: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    description: String,
    sectionTitle: String,
    emptyIcon: ImageVector,
    emptyText: String,
    rows: List<EchoProfileRow>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    addLabel: String,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    footnote: String? = null,
) {
    val apiKey by viewModel.apiKey.collectAsState()
    SettingsPageScaffold(title = mode, subtitle = subtitle, onBack = onBack) {
        LabMasterToggle(mode, "Show this mode in the chat + menu", enabled, onEnabledChange)
        Spacer(Modifier.height(Spacing.m))
        FormCard {
            Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (apiKey.isBlank()) {
            Spacer(Modifier.height(Spacing.m))
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key under Models → OpenRouter to use $mode.", error = true)
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection(sectionTitle, "Tap to set the default; pick per chat from the model selector")
        if (rows.isEmpty()) {
            EchoNoticeCard(emptyIcon, emptyText)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                rows.forEach { row ->
                    DrEngineSelectRow(
                        name = row.name,
                        subtitle = row.subtitle,
                        selected = row.id == selectedId,
                        onClick = { onSelect(row.id) },
                        onDelete = { onDelete(row.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        FilledTonalButton(onClick = onAdd, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(addLabel)
        }
        footnote?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

/** The OpenRouter directory sheet, fed from the view model's shared directory search. */
@Composable
internal fun OpenRouterPickerSheet(
    viewModel: SettingsViewModel,
    addedIds: Set<String> = emptySet(),
    onAdd: (OpenRouterModelInfo) -> Unit,
    onRemove: (String) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val query by viewModel.orModelQuery.collectAsState()
    val results by viewModel.orModelResults.collectAsState()
    val loading by viewModel.orDirectoryLoading.collectAsState()
    val error by viewModel.orDirectoryError.collectAsState()
    OpenRouterDirectorySheet(
        query = query,
        results = results,
        loading = loading,
        error = error,
        addedIds = addedIds,
        onQueryChange = viewModel::updateOrModelQuery,
        onRetry = viewModel::loadOpenRouterDirectory,
        onAdd = onAdd,
        onRemove = onRemove,
        onDismiss = onDismiss,
    )
}
