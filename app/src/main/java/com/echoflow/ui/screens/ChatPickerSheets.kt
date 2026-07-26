
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AgentProfile
import com.echoflow.data.ChatMessage
import com.echoflow.data.CustomProviderCapabilities
import com.echoflow.data.DataAgentCatalog
import com.echoflow.data.DeepResearchCatalog
import com.echoflow.data.DeepResearchModel
import com.echoflow.data.DrEngine
import com.echoflow.data.FusionPanel
import com.echoflow.data.ResearchRun
import com.echoflow.data.ToolEventJson
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ArtifactCard
import com.echoflow.ui.components.CapabilityChip
import com.echoflow.ui.components.DataResultCard
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.EffortPill
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.ReportCard
import com.echoflow.ui.components.ResearchProgressCard
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.components.SubagentCard
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import kotlinx.coroutines.launch



@Composable
internal fun ModelPickerSheet(
    models: List<Pair<String, String>>,
    localModels: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Text("Choose a model", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Manage")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search models") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            val filtered = models.filter { it.second.contains(query, true) || it.first.contains(query, true) }
            val filteredLocal = localModels.filter { it.second.contains(query, true) || it.first.contains(query, true) }
            if (filtered.isEmpty() && filteredLocal.isEmpty()) {
                Text(
                    "No models. Tap Manage to add one in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xl),
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(filtered, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedId, isLocal = false) { onSelect(id) }
                }
                if (filteredLocal.isNotEmpty()) {
                    item(key = "local-section") {
                        Box(Modifier.padding(top = Spacing.m)) { SectionLabel("Local & network") }
                    }
                    items(filteredLocal, key = { it.first }) { (id, name) ->
                        ModelRow(name, id, id == selectedId, isLocal = true) { onSelect(id) }
                    }
                }
            }
        }
    }
}

/**
 * Echo Fusion panel picker: each saved panel (a named roster of 2–8 models) is a selectable
 * card showing its members. The chosen panel deliberates on every message until turned off.
 */
@Composable
internal fun FusionPickerSheet(
    panels: List<FusionPanel>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Choose a fusion panel", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Models answer in parallel, a judge compares them", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }
            if (panels.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text("No panels yet.\nTap Manage to build one in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.xl)) {
                items(panels, key = { it.id }) { panel ->
                    DrEngineRow(
                        name = panel.name,
                        description = panel.names.joinToString(" · ").ifBlank { "${panel.models.size} models" },
                        selected = panel.id == selectedId,
                    ) { onSelect(panel.id) }
                }
            }
        }
    }
}

/**
 * Echo Adviser picker: two zones — the answering model (any cloud model) and which advisor
 * profile it escalates to. Both persist immediately; the sheet stays open so the user can set
 * both before dismissing.
 */
@Composable
internal fun AdvisorPickerSheet(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    profiles: List<AdvisorProfile>,
    selectedProfileId: String,
    onSelectModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Adviser setup", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("A cloud model answers and consults your advisor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Advisor") }
            if (profiles.isEmpty()) {
                Text(
                    "No advisors yet — tap Manage to set one up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.m)) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == selectedProfileId,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                            leadingIcon = { Icon(Icons.Default.Psychology, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Answering model") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                items(models, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedModelId, isLocal = false) { onSelectModel(id) }
                }
            }

            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                Text("Done")
            }
        }
    }
}

/**
 * Echo Agent picker: two zones — the orchestrator (any cloud model, which drives the toolbox)
 * and which agent profile (the worker model it delegates to). Both persist immediately; the
 * sheet stays open so the user can set both before dismissing.
 */
@Composable
internal fun AgentPickerSheet(
    models: List<Pair<String, String>>,
    selectedModelId: String,
    profiles: List<AgentProfile>,
    selectedProfileId: String,
    onSelectModel: (String) -> Unit,
    onSelectProfile: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text("Echo Agents", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Your main model uses tools and hands tasks to your Echo Agent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Manage")
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Echo Agent") }
            if (profiles.isEmpty()) {
                Text(
                    "No Echo Agents yet — tap Manage to set one up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(bottom = Spacing.m)) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = profile.id == selectedProfileId,
                            onClick = { onSelectProfile(profile.id) },
                            label = { Text(profile.name) },
                            leadingIcon = { Icon(Icons.Default.Hub, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            Box(Modifier.padding(bottom = Spacing.s)) { SectionLabel("Main model") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s), contentPadding = PaddingValues(bottom = Spacing.m)) {
                items(models, key = { it.first }) { (id, name) ->
                    ModelRow(name, id, id == selectedModelId, isLocal = false) { onSelectModel(id) }
                }
            }

            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
                Text("Done")
            }
        }
    }
}

/**
 * Engine picker shown in Deep Research mode. Lists built-in provider-native engines (only
 * those whose API key is configured) plus the user's added agentic chat models. Selecting
 * one stores it as the active Deep Research engine.
 */
@Composable
internal fun DeepResearchModelSheet(
    providerEngines: List<DrEngine>,
    agentModels: List<DeepResearchModel>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "Deep Research engine",
    subtitle: String = "Providers run research themselves; chat models orchestrate it",
    showAgentSection: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.base)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Manage")
                }
            }

            if (providerEngines.isEmpty() && (!showAgentSection || agentModels.isEmpty())) {
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Nothing set up yet.\nAdd a provider key (or model) in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                if (providerEngines.isNotEmpty()) {
                    item(key = "provider-section") { Box(Modifier.padding(top = Spacing.s)) { SectionLabel(if (showAgentSection) "Provider research" else "Agents") } }
                    items(providerEngines, key = { it.id }) { engine ->
                        DrEngineRow(engine.name, engine.description, engine.id == selectedId) { onSelect(engine.id) }
                    }
                }
                if (showAgentSection && agentModels.isNotEmpty()) {
                    item(key = "agent-section") { Box(Modifier.padding(top = Spacing.m)) { SectionLabel("Your research models") } }
                    items(agentModels, key = { it.id }) { model ->
                        DrEngineRow(model.name, "Orchestrates searches into a report", model.id == selectedId) { onSelect(model.id) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DrEngineRow(name: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Science, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun ModelRow(name: String, modelId: String, selected: Boolean, isLocal: Boolean = false, onClick: () -> Unit) {
    val displayName = remember(name, isLocal) { modelPickerDisplayName(name, isLocal) }
    val provider = when {
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OPENAI) -> "Direct OpenAI API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_CLAUDE) -> "Direct Claude API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_GEMINI) -> "Direct Gemini API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_CEREBRAS) -> "Direct Cerebras API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_XAI) -> "Direct xAI API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OLLAMA) -> "Ollama API"
        modelId.startsWith(com.echoflow.data.CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE) -> "OpenAI-compatible API"
        isLocal -> "Runs on this device — private & offline"
        modelId.contains("/") -> modelId.substringBefore("/").replaceFirstChar { it.uppercase() }
        else -> "Custom"
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isLocal) {
                        Spacer(Modifier.width(Spacing.s))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Row(
                                Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.OfflineBolt, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(Modifier.width(3.dp))
                                Text("Local", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                    }
                }
                Text(
                    provider,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

internal fun modelPickerDisplayName(name: String, isLocal: Boolean): String {
    if (!isLocal || name.length <= 24) return name

    var clean = name
        .removeSuffix(".gguf")
        .removeSuffix(".GGUF")
        .removeSuffix(".task")
        .removeSuffix(".TASK")
        .removeSuffix(".litertlm")
        .removeSuffix(".LITERTLM")
        .trim()

    val quantOrPrecisionSuffix = Regex(
        pattern = """(?i)(?:[-_](?:q[2-8](?:[-_][a-z0-9]+){0,5}|iq[1-4](?:[-_][a-z0-9]+){0,5}|mixed[-_]?int[48]|int[48]|f16|fp16|bf16))+$"""
    )
    clean = clean.replace(quantOrPrecisionSuffix, "")

    if (clean.length > 24) {
        clean = clean.replace(Regex("""(?i)[-_](?:20\d{2}|2[0-9]{3}|[0-9]{4})(?=$|[-_])"""), "")
    }
    if (clean.length > 30) {
        clean = clean
            .replace(Regex("""(?i)[-_](?:gguf|k[-_]?m|instruct[-_]?gguf)$"""), "")
            .trim('-', '_', ' ')
    }

    return clean.ifBlank { name }.takeIf { it.length >= 8 } ?: name
}
