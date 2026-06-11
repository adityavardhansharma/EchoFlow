@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.CatalogEntry
import com.echoflow.data.DownloadState
import com.echoflow.data.LocalModel
import com.echoflow.data.LocalModelCatalog
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

private data class Accent(val id: String, val label: String, val swatch: Color)

private data class SearchProviderOption(val id: String, val label: String, val description: String)

private val searchProviders = listOf(
    SearchProviderOption("off", "Off", "Models answer from their training data only"),
    SearchProviderOption("openrouter", "OpenRouter", "Server-side search for OpenRouter cloud models"),
    SearchProviderOption("exa", "Exa", "Semantic search — works with any model, ~\$5 per 1k searches"),
    SearchProviderOption("parallel", "Parallel", "Objective-based dense excerpts — works with any model"),
    SearchProviderOption("firecrawl", "Firecrawl", "Search plus full-page markdown — works with any model"),
)

private val accents = listOf(
    Accent("monochrome", "Mono", Color(0xFF1B1B1F)),
    Accent("ocean", "Ocean", Color(0xFF1660A8)),
    Accent("forest", "Forest", Color(0xFF1C6E2E)),
    Accent("sunset", "Sunset", Color(0xFF9A4500)),
    Accent("lavender", "Lavender", Color(0xFF6750A4)),
    Accent("rose", "Rose", Color(0xFFB01B49)),
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClicked: () -> Unit,
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val customModels by viewModel.customModels.collectAsState()

    val webSearchProvider by viewModel.webSearchProvider.collectAsState()
    val webSearchScope by viewModel.webSearchScope.collectAsState()
    val exaKey by viewModel.exaApiKey.collectAsState()
    val parallelKey by viewModel.parallelApiKey.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()

    val localModelsEnabled by viewModel.localModelsEnabled.collectAsState()
    val hfToken by viewModel.hfAccessToken.collectAsState()
    val localModels by viewModel.localModels.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val hfModelQuery by viewModel.hfModelQuery.collectAsState()
    val hfSearchResults by viewModel.hfSearchResults.collectAsState()
    val hfSearchLoading by viewModel.hfSearchLoading.collectAsState()
    val hfSearchError by viewModel.hfSearchError.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }

    val savedSearchKey = when (webSearchProvider) {
        "exa" -> exaKey
        "parallel" -> parallelKey
        "firecrawl" -> firecrawlKey
        else -> ""
    }
    var searchKeyInput by remember(webSearchProvider, savedSearchKey) { mutableStateOf(savedSearchKey) }
    var searchKeyVisible by remember { mutableStateOf(false) }
    var hfTokenInput by remember(hfToken) { mutableStateOf(hfToken) }
    var hfTokenVisible by remember { mutableStateOf(false) }
    var showHfModelSearch by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBackClicked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            // ── Appearance ────────────────────────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Icons.Default.Palette, "Appearance", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    SettingsCard {
                        Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(Spacing.m))
                        SegmentedToggle(
                            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                            selected = darkMode,
                            onSelect = viewModel::saveDarkMode,
                        )
                        Spacer(Modifier.height(Spacing.l))
                        Text("Accent color", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(Spacing.m))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
                            verticalArrangement = Arrangement.spacedBy(Spacing.base),
                        ) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                WallpaperSwatch(selected = themeColor == "dynamic") { viewModel.saveThemeColor("dynamic") }
                            }
                            accents.forEach { accent ->
                                AccentSwatch(accent, selected = themeColor == accent.id) { viewModel.saveThemeColor(accent.id) }
                            }
                        }
                    }
                }
            }

            // ── OpenRouter API ────────────────────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Icons.Default.Key, "OpenRouter API", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    SettingsCard {
                        Text("API key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(Spacing.s))
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("sk-or-v1-…") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (keyVisible) "Hide" else "Show")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.m))
                        Button(onClick = { viewModel.saveApiKey(keyInput.trim()) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                            Text("Save key")
                        }
                    }
                }
            }

            // ── Web search ────────────────────────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Icons.Default.Language, "Web search", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                        searchProviders.forEachIndexed { index, option ->
                            SearchProviderRow(
                                option = option,
                                index = index,
                                count = searchProviders.size,
                                selected = webSearchProvider == option.id,
                                onSelect = { viewModel.saveWebSearchProvider(option.id) },
                            )
                        }
                    }

                    AnimatedVisibility(visible = webSearchProvider != "off") {
                        Column {
                            Spacer(Modifier.height(Spacing.m))
                            SettingsCard {
                                Text("Use search with", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(Spacing.s))
                                SegmentedToggle(
                                    options = listOf(
                                        "both" to "Both",
                                        "cloud" to "Cloud",
                                        "local" to "Local"
                                    ),
                                    selected = webSearchScope,
                                    onSelect = viewModel::saveWebSearchScope,
                                )
                                if (webSearchProvider == "openrouter" && webSearchScope != "cloud") {
                                    Spacer(Modifier.height(Spacing.s))
                                    Text(
                                        "OpenRouter search only works with cloud models. Local models need Exa, Parallel or Firecrawl.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = webSearchProvider == "openrouter") {
                        Column {
                            Spacer(Modifier.height(Spacing.m))
                            SettingsCard {
                                Text("How OpenRouter search works", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(Spacing.s))
                                Text(
                                    "Search runs server-side on OpenRouter: the model decides if and when " +
                                        "to search — zero, one or several times per answer — and the cost is " +
                                        "billed through your OpenRouter account.\n\n" +
                                        "This only works with OpenRouter cloud models. For on-device models, " +
                                        "pick Exa, Parallel or Firecrawl instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = webSearchProvider in setOf("exa", "parallel", "firecrawl")) {
                        Column {
                            Spacer(Modifier.height(Spacing.m))
                            SettingsCard {
                                val providerLabel = searchProviders.firstOrNull { it.id == webSearchProvider }?.label ?: ""
                                Text("$providerLabel API key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(Spacing.s))
                                OutlinedTextField(
                                    value = searchKeyInput,
                                    onValueChange = { searchKeyInput = it },
                                    placeholder = { Text("Paste your $providerLabel key") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    visualTransformation = if (searchKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { searchKeyVisible = !searchKeyVisible }) {
                                            Icon(if (searchKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (searchKeyVisible) "Hide" else "Show")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(Spacing.m))
                                Button(
                                    onClick = { viewModel.saveSearchApiKey(webSearchProvider, searchKeyInput) },
                                    shape = CircleShape,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Save key") }
                                Spacer(Modifier.height(Spacing.m))
                                Text(
                                    "Offered to every model — including on-device ones — as a search tool " +
                                        "it can call while answering. The key is stored only on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Local models ──────────────────────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Icons.Default.PhoneAndroid, "Local models", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    SettingsCard {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Run models on-device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Private, offline and free — powered by Google AI Edge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(Spacing.s))
                            Switch(checked = localModelsEnabled, onCheckedChange = viewModel::saveLocalModelsEnabled)
                        }
                    }

                    AnimatedVisibility(visible = localModelsEnabled) {
                        Column {
                            // HuggingFace token
                            Spacer(Modifier.height(Spacing.m))
                            SettingsCard {
                                Text("Hugging Face token", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    "Only needed for license-gated models (Gemma). Accept the model " +
                                        "license on huggingface.co, then create a read token under " +
                                        "Settings → Access Tokens.",
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

                            // Curated downloads
                            Spacer(Modifier.height(Spacing.l))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, bottom = Spacing.m),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Download a model",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = {
                                        showHfModelSearch = true
                                        if (hfSearchResults.isEmpty()) viewModel.searchHfModels()
                                    },
                                    shape = CircleShape,
                                ) {
                                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(Spacing.s))
                                    Text("Search")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                                LocalModelCatalog.entries.forEachIndexed { index, entry ->
                                    CatalogModelRow(
                                        entry = entry,
                                        index = index,
                                        count = LocalModelCatalog.entries.size,
                                        installed = localModels.any { it.id == entry.id },
                                        state = downloadStates[entry.id],
                                        onDownload = { viewModel.downloadModel(entry) },
                                        onCancel = { viewModel.cancelDownload(entry.id) },
                                        onRetry = { viewModel.retryDownload(entry) },
                                    )
                                }
                            }

                            // Import
                            Spacer(Modifier.height(Spacing.l))
                            FilledTonalButton(
                                onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.FileOpen, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(Spacing.s))
                                Text("Import from file")
                            }
                            Text(
                                "Pick a .task or .litertlm model file you've downloaded yourself.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.s),
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

                            // Installed models
                            if (localModels.isNotEmpty()) {
                                Spacer(Modifier.height(Spacing.l))
                                Text(
                                    "Installed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
                                )
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
                        }
                    }
                }
            }

            // ── Models ────────────────────────────────────────────────────────────────────
            item {
                Column {
                    SettingsSectionHeader(Icons.Default.Add, "Models", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    SettingsCard {
                        Text("Add a custom model", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(Spacing.s))
                        OutlinedTextField(
                            value = modelId, onValueChange = { modelId = it },
                            label = { Text("Model ID") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.s))
                        OutlinedTextField(
                            value = modelName, onValueChange = { modelName = it },
                            label = { Text("Display name") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.m))
                        FilledTonalButton(
                            onClick = {
                                if (modelId.trim().isNotEmpty()) { viewModel.addCustomModel(modelId.trim(), modelName.trim()); modelId = ""; modelName = "" }
                            },
                            shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(Spacing.s)); Text("Add model")
                        }
                    }
                }
            }

            if (customModels.isNotEmpty()) {
                item {
                    Column {
                        SettingsSectionHeader(Icons.Default.Memory, "Your models", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                            customModels.forEachIndexed { index, model ->
                                Surface(
                                    shape = groupedItemShape(index, customModels.size),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier.size(40.dp).clip(RoundedPolygonShape(BrandShapes.avatarStart)).background(MaterialTheme.colorScheme.tertiaryContainer),
                                            contentAlignment = Alignment.Center,
                                        ) { Icon(Icons.Default.Memory, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                                        Spacer(Modifier.width(Spacing.base))
                                        Column(Modifier.weight(1f)) {
                                            Text(model.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                            Text(model.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { viewModel.deleteCustomModel(model.id) }) {
                                            Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.xl)) }
        }
    }

    if (showHfModelSearch) {
        HfModelSearchDialog(
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

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String, container: Color, onContainer: Color) {
    Row(
        Modifier.padding(start = Spacing.xs, bottom = Spacing.m, top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedPolygonShape(BrandShapes.avatarStart)).background(container),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(18.dp), tint = onContainer) }
        Spacer(Modifier.width(Spacing.m))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), content = content)
    }
}

/** Custom connected segmented control — the M3 Expressive replacement for segmented buttons. */
@Composable
private fun SegmentedToggle(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            options.forEach { (value, label) ->
                val isSel = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    shape = CircleShape,
                    color = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchProviderRow(
    option: SearchProviderOption,
    index: Int,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = groupedItemShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    option.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Spacer(Modifier.width(Spacing.s))
                Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CatalogModelRow(
    entry: CatalogEntry,
    index: Int,
    count: Int,
    installed: Boolean,
    state: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        if (entry.requiresAuth) {
                            Spacer(Modifier.width(Spacing.s))
                            Icon(
                                Icons.Default.Lock, "Requires HuggingFace token",
                                Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            Formatter.formatShortFileSize(context, entry.approxSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                when {
                    installed -> Icon(
                        Icons.Default.CheckCircle, "Installed",
                        Modifier.padding(Spacing.m), tint = MaterialTheme.colorScheme.primary,
                    )
                    state is DownloadState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularWavyProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.size(32.dp),
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, "Cancel download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state is DownloadState.Failed -> IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, "Retry download", tint = MaterialTheme.colorScheme.error)
                    }
                    else -> FilledTonalIconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, "Download ${entry.name}")
                    }
                }
            }
            if (state is DownloadState.Downloading) {
                Text(
                    "${Formatter.formatShortFileSize(context, state.bytes)} of ${Formatter.formatShortFileSize(context, state.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is DownloadState.Failed) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HfModelSearchDialog(
    query: String,
    results: List<CatalogEntry>,
    loading: Boolean,
    error: String?,
    localModels: List<LocalModel>,
    downloadStates: Map<String, DownloadState>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDownload: (CatalogEntry) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (CatalogEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Search mobile models") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        placeholder = { Text("Gemma 4, Qwen3...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    FilledTonalIconButton(onClick = onSearch, enabled = !loading) {
                        Icon(Icons.Default.Search, "Search Hugging Face")
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                Text(
                    "Showing LiteRT-LM mobile bundles from Hugging Face that end in .litertlm or .task.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.m))
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                    error != null -> Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    results.isEmpty() -> Text(
                        "No mobile-ready LiteRT model bundles found for this search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
                    ) {
                        items(results.size) { index ->
                            val entry = results[index]
                            CatalogModelRow(
                                entry = entry,
                                index = index,
                                count = results.size,
                                installed = localModels.any { it.id == entry.id },
                                state = downloadStates[entry.id],
                                onDownload = { onDownload(entry) },
                                onCancel = { onCancel(entry.id) },
                                onRetry = { onRetry(entry) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun InstalledModelRow(
    model: LocalModel,
    index: Int,
    count: Int,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedPolygonShape(BrandShapes.avatarStart)).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    (if (model.source == "imported") "Imported · " else "Downloaded · ") +
                        Formatter.formatShortFileSize(context, model.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedPolygonShape(BrandShapes.avatarStart)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(shape).background(accent.swatch)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(Spacing.xs))
        Text(accent.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WallpaperSwatch(selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedPolygonShape(BrandShapes.heroStart)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(shape).background(MaterialTheme.colorScheme.primaryContainer)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.Palette, "Wallpaper colors",
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text("Wallpaper", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
