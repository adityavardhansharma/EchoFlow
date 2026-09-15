@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.DataAgentCatalog
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing

@Composable
internal fun DataAgentPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val enabled by viewModel.dataAgentEnabled.collectAsState()
    val selectedEngine by viewModel.dataAgentEngine.collectAsState()
    val maxCredits by viewModel.dataAgentMaxCredits.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()

    SettingsPageScaffold(title = "Data Agent", subtitle = "Extract data from the web", onBack = onBack) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Data Agent", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Collect data from the web into a clean table",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(checked = enabled, onCheckedChange = viewModel::saveDataAgentEnabled)
            }
        }

        AnimatedVisibility(visible = enabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                FormCard {
                    Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Tell it the data you want — pricing, specs, lists, contacts — and the Firecrawl " +
                            "agent searches the web and returns it as a table. Use Deep Research instead when " +
                            "you want an explanation or report. It runs in the background and uses Firecrawl " +
                            "credits, so a spending limit is enforced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(Spacing.xl))
                PageSection("Agent model", "Faster is cheaper; Accurate handles complex, multi-site tasks")
                if (firecrawlKey.isBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Key, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.s))
                            Text(
                                "Add your Firecrawl API key in Settings → Web search to use the Data Agent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                        DataAgentCatalog.engines.forEach { engine ->
                            DrEngineSelectRow(
                                name = engine.name,
                                subtitle = engine.description,
                                selected = engine.id == selectedEngine,
                                onClick = { viewModel.saveDataAgentEngine(engine.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                PageSection("Spending limit", "Caps how many Firecrawl credits a single run may use")
                ConnectedToggleRow(
                    options = listOf("1000" to "Low", "2500" to "Standard", "10000" to "High"),
                    selected = maxCredits.toString(),
                    onSelect = { viewModel.saveDataAgentMaxCredits(it.toInt()) },
                )
            }
        }
    }
}
