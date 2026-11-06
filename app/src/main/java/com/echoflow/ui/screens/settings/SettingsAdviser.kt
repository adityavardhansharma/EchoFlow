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
    val profiles by viewModel.advisorProfiles.collectAsState()
    val selectedId by viewModel.echoAdviserProfileId.collectAsState()
    val enabled by viewModel.echoAdviserEnabled.collectAsState()
    var showDirectory by remember { mutableStateOf(false) }
    var pendingModel by remember { mutableStateOf<OpenRouterModelInfo?>(null) }

    EchoProfilesPage(
        viewModel = viewModel,
        mode = "Echo Adviser",
        subtitle = "Escalate hard parts to a stronger model",
        enabled = enabled,
        onEnabledChange = viewModel::saveEchoAdviserEnabled,
        description = "Any cloud model answers, and consults a stronger, domain-specific advisor mid-answer — before committing to an approach or finishing a hard task. Set up advisors for different jobs (coding, maths, research), then pick one per chat from the model selector. OpenRouter only; each consult adds cost.",
        sectionTitle = "Your advisors",
        emptyIcon = Icons.Default.Psychology,
        emptyText = "No advisors yet.\nAdd one below — e.g. a strong model for coding or maths.",
        rows = profiles.map { EchoProfileRow(it.id, it.name, it.modelName.ifBlank { it.modelId }) },
        selectedId = selectedId,
        onSelect = viewModel::saveEchoAdviserProfile,
        onDelete = viewModel::deleteAdvisorProfile,
        addLabel = "Add an advisor",
        onAdd = { showDirectory = true; viewModel.loadOpenRouterDirectory() },
        onBack = onBack,
    )

    if (showDirectory) {
        OpenRouterPickerSheet(
            viewModel,
            onAdd = { info -> pendingModel = info; showDirectory = false },
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
