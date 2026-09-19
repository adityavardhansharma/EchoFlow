@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppDatabase
import com.echoflow.data.ChatMessage
import com.echoflow.data.jev.JevDecision
import com.echoflow.data.jev.JevStore
import com.echoflow.data.jev.JevThresholds
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Jev Router (Echo Labs opt-in). A TypeSafe System One classifier that reads each
 * cloud-chat prompt and decides — with fixed thresholds — whether to pre-recall
 * memory, force web search, or save a fact, before the main model runs.
 *
 * Strictly Labs-only: chat UI is untouched. This page is the only surface that
 * mentions Jev — master switch, key, last probabilities, and per-turn history.
 */
@Composable
internal fun JevPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val enabled by viewModel.jevEnabled.collectAsState()
    val savedKey by viewModel.jevApiKey.collectAsState()
    val last by JevStore.last.collectAsState()

    SettingsPageScaffold(title = "Jev Router", subtitle = "Classify prompts before the main model", onBack = onBack) {
        LabMasterToggle(
            title = "Jev Router",
            subtitle = "Cloud chats only · fixed thresholds · chat looks the same",
            enabled = enabled,
            onToggle = viewModel::saveJevEnabled,
        )
        Spacer(Modifier.height(Spacing.m))
        FormCard {
            Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Before your model answers, Jev reads the prompt and scores three questions: " +
                    "does personal memory matter (≥ ${JevThresholds.ACT}), does something need " +
                    "remembering, and could the answer have changed since training. High scores act " +
                    "directly — memory is recalled up front and save/web instructions are enforced — " +
                    "so small models that ignore tool prompts still behave. Anything below the bar " +
                    "keeps today's behaviour: the main model decides.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Cloud chats only: on-device models, Ollama and OpenAI-compatible endpoints never " +
                    "call Jev. Each classified prompt is sent to TypeSafe's API; nothing else leaves " +
                    "your device for Jev, and the key is stored only on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("TypeSafe API key", "From console.typesafe.ai · stored only on this phone")
        var keyInput by remember(savedKey) { mutableStateOf("") }
        var keyVisible by remember { mutableStateOf(false) }
        FormCard {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text("Paste your TypeSafe key") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            if (keyVisible) "Hide" else "Show",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (savedKey.isNotBlank()) {
                Spacer(Modifier.height(Spacing.s))
                SavedKeyBadge("A TypeSafe key is saved on this device")
            }
            Spacer(Modifier.height(Spacing.m))
            FilledTonalButton(
                onClick = {
                    viewModel.saveJevApiKey(keyInput)
                    keyInput = ""
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save key") }
        }

        AnimatedVisibility(visible = enabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                PageSection("Last classification", "Live · this session")
                if (last != null) {
                    JevDecisionCard(decision = last!!, chatLabel = null)
                } else {
                    EchoNoticeCard(
                        icon = Icons.Default.Key,
                        text = "No classified turn yet. Send a cloud-chat message with Jev on and its probabilities will appear here.",
                    )
                }

                Spacer(Modifier.height(Spacing.xl))
                JevHistorySection()
            }
        }
    }
}

@Composable
private fun JevHistorySection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun load() {
        scope.launch {
            loading = true
            val rows = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context).messageDao().getRecentJevDecisions(30)
            }
            items = rows
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    PageSection("History", "Saved per assistant message · newest first")
    if (loading && items.isEmpty()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.m))
    }
    if (!loading && items.isEmpty()) {
        EchoNoticeCard(
            icon = Icons.Default.Refresh,
            text = "No saved classifications yet.",
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        items.forEach { message ->
            JevDecision.fromJson(message.jevJson)?.let { decision ->
                JevDecisionCard(
                    decision = decision,
                    chatLabel = "chat ${message.chatId.take(8)} · ${formatTime(message.createdAt)}",
                )
            }
        }
    }
    if (items.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.m))
        OutlinedButton(
            onClick = ::load,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Refresh")
        }
    }
}

@Composable
internal fun JevDecisionCard(decision: JevDecision, chatLabel: String?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            if (chatLabel != null) {
                Text(
                    chatLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.s))
            }
            JevProbRow("Memory", decision.needsMemory, decision.memoryRecalled)
            Spacer(Modifier.height(Spacing.s))
            JevProbRow("Web", decision.needsWeb, decision.webForced)
            Spacer(Modifier.height(Spacing.s))
            JevProbRow("Save", decision.needsSave, decision.saveTriggered)
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "route: ${decision.routeChoice}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "conf ${formatProb(decision.routeConfidence)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (decision.routeProbabilities.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    decision.routeProbabilities.entries.sortedByDescending { it.value }
                        .joinToString(" · ") { "${it.key} ${formatProb(it.value)}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                "model ${decision.modelVersion.ifBlank { "—" }} · ${decision.latencyMs}ms · ${formatTime(decision.createdAt)}" +
                    if (decision.fallback) " · fallback" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JevProbRow(label: String, value: Double, acted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(64.dp),
        )
        LinearProgressIndicator(
            progress = { value.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            formatProb(value) + if (acted) " ✓" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (acted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatProb(value: Double): String = "%.2f".format(value.coerceIn(0.0, 1.0))

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "—"
    return SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(millis))
}
