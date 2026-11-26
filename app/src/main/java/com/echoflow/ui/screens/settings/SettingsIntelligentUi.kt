package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.theme.Spacing
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.core.prompt.IntelligentUiLevel

/** The short description of a level (01 §20.5); also the settings row summary. */
internal fun IntelligentUiLevel.summary(): String = when (this) {
    IntelligentUiLevel.OFF -> "Replies stay as text."
    IntelligentUiLevel.BALANCED -> "Adds interactive UI when it clearly helps."
    IntelligentUiLevel.IMMERSIVE -> "Leans into rich, interactive answers."
}

internal fun IntelligentUiLevel.title(): String = when (this) {
    IntelligentUiLevel.OFF -> "Off"
    IntelligentUiLevel.BALANCED -> "Balanced"
    IntelligentUiLevel.IMMERSIVE -> "Immersive"
}

/** Settings → Intelligent UI (05 §11.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntelligentUiPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val level by viewModel.intelligentUiLevel.collectAsState()
    val autoFix by viewModel.echoUiAutoFix.collectAsState()
    val uiModel by viewModel.echoUiModel.collectAsState()
    var explainer by remember { mutableStateOf(false) }
    SettingsPageScaffold(title = "Intelligent UI", subtitle = "Powered by Echo UI", onBack = onBack) {
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            IntelligentUiLevel.entries.forEach { l ->
                val selected = l == level
                OutlinedCard(
                    Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) { viewModel.setIntelligentUiLevel(l) },
                    colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, null)
                        Spacer(Modifier.size(Spacing.m))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                Text(l.title(), style = MaterialTheme.typography.titleMedium)
                                if (l == IntelligentUiLevel.BALANCED) Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text("Default", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                            }
                            Text(l.summary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text("Live UI works with cloud models.", Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val model by viewModel.selectedModel.collectAsState()
        if (!model.startsWith("local/")) {
            PageSection("Live UI capability", "For ${model.substringAfterLast('/')} — what this model is trusted to build.")
            var override by remember(model) { mutableStateOf(viewModel.echoUiTierOverride(model)) }
            val auto = com.echoflow.weave.runtime.tiers.EchoUiModelTiers.tierFor(model, false, null)
            val names = listOf("Off", "Display", "Interactive", "Agentic", "Extreme")
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                (listOf(null to "Automatic (${names[auto]})") + names.mapIndexed { i, n -> i to n }).forEach { (t, label) ->
                    androidx.compose.material3.FilterChip(override == t, { override = t; viewModel.setEchoUiTierOverride(model, t) }, { Text(label) })
                }
            }
        }

        PageSection("Live updates")
        OutlinedTextField(
            uiModel ?: "", { viewModel.setEchoUiModel(it.trim().ifEmpty { null }) },
            Modifier.fillMaxWidth(),
            label = { Text("Model for live updates") },
            placeholder = { Text("Same as the chat") },
            supportingText = { Text("Used when a block asks the model to update itself or fill in a part.") },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-fix broken blocks", style = MaterialTheme.typography.bodyLarge)
                Text("Sends one hidden request to repair a block the model got wrong.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(autoFix, { viewModel.setEchoUiAutoFix(it) })
        }
        TextButton({ explainer = true }) {
            Icon(TokenResolver.icon("help"), null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("What is Intelligent UI?")
        }
    }
    if (explainer) ModalBottomSheet({ explainer = false }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What is Intelligent UI?", style = MaterialTheme.typography.titleLarge)
            Text("When an answer is easier to use than to read — a calculator, a chart, a checklist, a quiz — the model can reply with a small interactive block right in the chat. You can drag, tap and type in it; it remembers what you did.", style = MaterialTheme.typography.bodyMedium)
            Text("Blocks only do things inside the block. Anything that leaves the app, like opening a link or adding a calendar event, always asks you first.", style = MaterialTheme.typography.bodyMedium)
            Text("Off keeps replies as text. You can still ask for UI, or tap “Make interactive” on any reply. Each chat can have its own level from the + menu.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
