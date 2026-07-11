@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.R
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AgentProfile
import com.echoflow.data.CatalogEntry
import com.echoflow.data.CustomModelProvider
import com.echoflow.data.CustomProviderConfig
import com.echoflow.data.DataAgentCatalog
import com.echoflow.data.FusionPanel
import com.echoflow.data.DeepResearchCatalog
import com.echoflow.data.DownloadState
import com.echoflow.data.InferenceLimits
import com.echoflow.data.InferenceParams
import com.echoflow.data.LocalModel
import com.echoflow.data.LocalModelCatalog
import com.echoflow.data.OpenRouterModelInfo
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import kotlin.math.roundToInt

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

// ── Echo Adviser ──────────────────────────────────────────────────────────────────────

/** Master on/off card shown at the top of each Echo Labs feature page. */
@Composable
internal fun LabMasterToggle(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

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
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key in Cloud models to use Echo Adviser.", error = true)
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

// ── Echo Fusion ───────────────────────────────────────────────────────────────────────

@Composable
internal fun EchoFusionPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    val panels by viewModel.fusionPanels.collectAsState()
    val selectedId by viewModel.echoFusionPanelId.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }
    val pendingModels = remember { mutableStateMapOf<String, String>() } // id -> display name
    var nameDialog by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Echo Fusion", subtitle = "A panel of models deliberates", onBack = onBack) {
        val fusionEnabled by viewModel.echoFusionEnabled.collectAsState()
        LabMasterToggle("Echo Fusion", "Show this mode in the chat + menu", fusionEnabled, viewModel::saveEchoFusionEnabled)
        Spacer(Modifier.height(Spacing.m))
        FormCard {
            Text("What it does", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Several models answer the same question in parallel and a judge compares them — surfacing where they agree, where they disagree, and what they all missed. Best for research, comparisons and high-stakes questions. OpenRouter only; cost-heavy — every message runs the whole panel plus a judge.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (apiKey.isBlank()) {
            Spacer(Modifier.height(Spacing.m))
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key in Cloud models to use Echo Fusion.", error = true)
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Your panels", "Tap to set the default; pick per chat from the model selector")
        if (panels.isEmpty()) {
            EchoNoticeCard(Icons.Default.AccountTree, "No panels yet.\nBuild one below from 2–8 models.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                panels.forEach { panel ->
                    DrEngineSelectRow(
                        name = panel.name,
                        subtitle = panel.names.joinToString(" · ").ifBlank { "${panel.models.size} models" },
                        selected = panel.id == selectedId,
                        onClick = { viewModel.saveEchoFusionPanel(panel.id) },
                        onDelete = { viewModel.deleteFusionPanel(panel.id) },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        FilledTonalButton(
            onClick = { pendingModels.clear(); showDirectory = true; viewModel.loadOpenRouterDirectory() },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("New panel")
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            "A panel needs 2–8 models. The first model judges unless you change it later.",
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
            addedIds = pendingModels.keys.toSet(),
            onQueryChange = viewModel::updateOrModelQuery,
            onRetry = viewModel::loadOpenRouterDirectory,
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

/** Small info/empty slab used on the Echo Adviser/Fusion pages. */
@Composable
internal fun EchoNoticeCard(icon: ImageVector, text: String, error: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(28.dp), tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.s))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Name-entry dialog used to finish creating an advisor profile or a fusion panel. */
@Composable
internal fun EchoNamePromptDialog(
    title: String,
    supporting: String,
    defaultName: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, null) },
        title = { Text(title) },
        text = {
            Column {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
            EchoNoticeCard(Icons.Default.Key, "Add your OpenRouter key in Cloud models to use Echo Agents.", error = true)
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

