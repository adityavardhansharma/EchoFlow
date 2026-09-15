@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.theme.Spacing

// ── Browser Flow ──────────────────────────────────────────────────────────────────────

@Composable
internal fun BrowserFlowPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val enabled by viewModel.browserFlowEnabled.collectAsState()
    val idleMinutes by viewModel.browserIdleMinutes.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()
    var showBetaWarning by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Browser Flow", subtitle = "A live browser, controlled by chat", onBack = onBack) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Browser Flow", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(Spacing.s))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                "BETA",
                                Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Text(
                        "Open a real website and steer it with chat messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = enabled,
                    // Turning it on is a beta opt-in — confirm first; off is immediate.
                    onCheckedChange = { on -> if (on) showBetaWarning = true else viewModel.saveBrowserFlowEnabled(false) },
                )
            }
        }

        if (showBetaWarning) {
            AlertDialog(
                onDismissRequest = { showBetaWarning = false },
                icon = { Icon(Icons.Default.Science, null) },
                title = { Text("Browser Flow is in beta") },
                text = {
                    Text(
                        "This drives a real remote browser through Firecrawl. It's experimental and may " +
                            "break or behave unexpectedly, and it uses Firecrawl credits (~7 per minute while " +
                            "a session is open). It never automates payments or checkout, and asks before " +
                            "sending messages. Turn it on?",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.saveBrowserFlowEnabled(true); showBetaWarning = false }) {
                        Text("Turn on")
                    }
                },
                dismissButton = { TextButton(onClick = { showBetaWarning = false }) { Text("Cancel") } },
            )
        }

        AnimatedVisibility(visible = enabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                FormCard {
                    Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Tell it to open a site, then keep sending instructions — it controls the same live " +
                            "Firecrawl browser until you Finish or Stop. You can watch and take over any time. " +
                            "Sessions are temporary (no saved logins). It never automates payments or checkout, " +
                            "and asks before sending messages. Uses Firecrawl credits (~${com.echoflow.data.BrowserSession.CREDITS_PER_MINUTE} per minute while open).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (firecrawlKey.isBlank()) {
                    Spacer(Modifier.height(Spacing.xl))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Key, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.s))
                            Text(
                                "Add your Firecrawl API key in Settings → Web search to use Browser Flow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                PageSection("Auto-close when idle", "Closes the session after this long with no activity (saves credits)")
                ConnectedToggleRow(
                    options = listOf("2" to "2 min", "3" to "3 min", "5" to "5 min", "0" to "Off"),
                    selected = idleMinutes.toString(),
                    onSelect = { viewModel.saveBrowserIdleMinutes(it.toInt()) },
                )
            }
        }
    }
}
