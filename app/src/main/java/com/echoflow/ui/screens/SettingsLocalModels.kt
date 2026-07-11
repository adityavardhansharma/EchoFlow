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
internal fun LocalModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val localModelsEnabled by viewModel.localModelsEnabled.collectAsState()
    val ggufEnabled by viewModel.ggufEnabled.collectAsState()
    val hfToken by viewModel.hfAccessToken.collectAsState()
    val localParams by viewModel.localInferenceParams.collectAsState()
    val localModels by viewModel.localModels.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importWarning by viewModel.importWarning.collectAsState()
    val hfModelQuery by viewModel.hfModelQuery.collectAsState()
    val hfSearchResults by viewModel.hfSearchResults.collectAsState()
    val hfSearchLoading by viewModel.hfSearchLoading.collectAsState()
    val hfSearchError by viewModel.hfSearchError.collectAsState()

    var hfTokenInput by remember(hfToken) { mutableStateOf(hfToken) }
    var hfTokenVisible by remember { mutableStateOf(false) }
    var hfTokenOpen by rememberSaveable { mutableStateOf(false) }
    var showAllCatalog by rememberSaveable { mutableStateOf(false) }
    var showHfModelSearch by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    SettingsPageScaffold(title = "Local models", subtitle = "On-device intelligence", onBack = onBack) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Run models on-device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Private, offline and free — runs fully on your phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(checked = localModelsEnabled, onCheckedChange = viewModel::saveLocalModelsEnabled)
            }
        }

        AnimatedVisibility(visible = localModelsEnabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                // Installed models — the thing you came for goes first.
                if (localModels.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xl))
                    PageSection("Installed")
                    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                        localModels.forEachIndexed { index, model ->
                            InstalledModelRow(
                                model = model,
                                index = index,
                                count = localModels.size,
                                onDelete = { viewModel.deleteLocalModel(model) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                PageSection("Get models", "Curated picks below, or search all of Hugging Face")
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilledTonalButton(
                        onClick = { showHfModelSearch = true },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("Search")
                    }
                    FilledTonalButton(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text("Import")
                    }
                }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Search lists LiteRT models (.task / .litertlm). GGUF (.gguf) is import-only — enable it below first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
                importError?.let { error ->
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }

                Spacer(Modifier.height(Spacing.m))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedPolygonShape(MaterialShapes.Gem))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Memory, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                        Spacer(Modifier.width(Spacing.base))
                        Column(Modifier.weight(1f)) {
                            Text("Allow GGUF models", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Experimental llama.cpp format. Off by default — runs CPU-only and quality varies. Lets you import .gguf files (search stays LiteRT/.task only).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Spacing.s))
                        Switch(checked = ggufEnabled, onCheckedChange = viewModel::saveGgufEnabled)
                    }
                }

                Spacer(Modifier.height(Spacing.m))
                val visibleCatalog = if (showAllCatalog) LocalModelCatalog.entries
                else LocalModelCatalog.entries.take(CatalogPreviewCount)
                val groupCount = visibleCatalog.size + 1 // + the show all/fewer row
                Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                    visibleCatalog.forEachIndexed { index, entry ->
                        CatalogModelRow(
                            entry = entry,
                            index = index,
                            count = groupCount,
                            installed = localModels.any { it.id == entry.id },
                            state = downloadStates[entry.id],
                            onDownload = { viewModel.downloadModel(entry) },
                            onCancel = { viewModel.cancelDownload(entry.id) },
                            onRetry = { viewModel.retryDownload(entry) },
                        )
                    }
                    ShowMoreRow(
                        expanded = showAllCatalog,
                        expandText = "Show all ${LocalModelCatalog.entries.size} models",
                        collapseText = "Show fewer",
                        index = groupCount - 1,
                        count = groupCount,
                        onClick = { showAllCatalog = !showAllCatalog },
                    )
                }

                // Hugging Face token, tucked away — only gated models need it.
                Spacer(Modifier.height(Spacing.m))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val tokenChevron by animateFloatAsState(
                            targetValue = if (hfTokenOpen) 180f else 0f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "hfTokenChevron",
                        )
                        Surface(
                            onClick = { hfTokenOpen = !hfTokenOpen },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(Spacing.base),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Hugging Face token", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        if (hfToken.isBlank()) "Only needed for gated models like Gemma 3"
                                        else "Token saved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    Icons.Default.ExpandMore, if (hfTokenOpen) "Collapse" else "Expand",
                                    Modifier.rotate(tokenChevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AnimatedVisibility(visible = hfTokenOpen, enter = sectionEnter(), exit = sectionExit()) {
                            Column(Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.base)) {
                                Text(
                                    "Accept the model license on huggingface.co, then create a " +
                                        "read token under Settings → Access Tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(Spacing.s))
                                OutlinedTextField(
                                    value = hfTokenInput,
                                    onValueChange = { hfTokenInput = it },
                                    placeholder = { Text("hf_…") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    visualTransformation = if (hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
                                            Icon(if (hfTokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hfTokenVisible) "Hide" else "Show")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(Spacing.m))
                                Button(
                                    onClick = { viewModel.saveHfAccessToken(hfTokenInput) },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Save token") }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.xl))
                PageSection("Tuning", "Sampler settings applied to every on-device model")
                InferenceParamsCard(
                    local = true,
                    params = localParams,
                    onChange = { viewModel.saveInferenceParams(local = true, params = it) },
                    onReset = { viewModel.resetInferenceParams(local = true) },
                )
            }
        }
    }

    importWarning?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportWarning,
            icon = { Icon(Icons.Default.Memory, null) },
            title = { Text("This model may be too large") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Import anyway") } },
            dismissButton = { TextButton(onClick = viewModel::dismissImportWarning) { Text("Cancel") } },
        )
    }

    if (showHfModelSearch) {
        HfModelSearchSheet(
            query = hfModelQuery,
            results = hfSearchResults,
            loading = hfSearchLoading,
            error = hfSearchError,
            localModels = localModels,
            downloadStates = downloadStates,
            onQueryChange = viewModel::updateHfModelQuery,
            onSearch = viewModel::searchHfModels,
            onDownload = viewModel::downloadModel,
            onCancel = viewModel::cancelDownload,
            onRetry = viewModel::retryDownload,
            onDismiss = { showHfModelSearch = false },
        )
    }
}

// ── Shared building blocks ────────────────────────────────────────────────────────────

/**
 * Page chrome shared by the hub and every detail page: Expressive flexible large top app
 * bar with an optional subtitle, plus a plain scrollable column so collapsing content
 * shrinks in place and focused text fields are auto-scrolled above the keyboard.
 */
