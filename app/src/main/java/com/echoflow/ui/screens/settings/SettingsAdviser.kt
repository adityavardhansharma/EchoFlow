@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

@Composable
internal fun EchoAdviserPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    val profiles by viewModel.advisorProfiles.collectAsState()
    val selectedId by viewModel.echoAdviserProfileId.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<OpenRouterModelInfo?>(null) }

    SettingsPageScaffold(title = "Echo Adviser", subtitle = "Escalate hard parts to a stronger model", onBack = onBack) {
        val adviserEnabled by viewModel.echoAdviserEnabled.collectAsState()
        LabMasterToggle("Echo Adviser", "Show this mode in the chat + menu", adviserEnabled, viewModel::saveEchoAdviserEnabled)
        Spacer(Modifier.height(Spacing.m))
        FormCard {
            Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Any cloud model answers, and consults a stronger, domain-specific advisor mid-answer — before committing to an approach or finishing a hard task. Set up advisors for different jobs (coding, maths, research), then pick one per chat from the model selector. OpenRouter only; each consult adds cost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (apiKey.isBlank()) {
            Spacer(Modifier.height(Spacing.m))
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key under Models → OpenRouter to use Echo Adviser.", error = true)
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Your advisors", "Tap to set the default; pick per chat from the model selector")
        if (profiles.isEmpty()) {
            EchoNoticeCard(Icons.Default.Psychology, "No advisors yet.\nAdd one below — e.g. a strong model for coding or maths.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                profiles.forEach { profile ->
                    DrEngineSelectRow(
                        name = profile.name,
                        subtitle = profile.modelName.ifBlank { profile.modelId },
                        selected = profile.id == selectedId,
                        onClick = { viewModel.saveEchoAdviserProfile(profile.id) },
                        onDelete = { viewModel.deleteAdvisorProfile(profile.id) },
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
            Text("Add an advisor")
        }
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
        EchoNamePromptDialog(
            title = "Name this advisor",
            supporting = model.name,
            defaultName = "",
            placeholder = "Coding, Maths, Research…",
            onDismiss = { pendingModel = null },
            onConfirm = { name ->
                viewModel.addAdvisorProfile(name, model.id, model.name.substringAfter(": "))
                pendingModel = null
            },
        )
    }
}
