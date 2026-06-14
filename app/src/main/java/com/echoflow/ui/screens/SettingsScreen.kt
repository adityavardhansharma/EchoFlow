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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.data.CatalogEntry
import com.echoflow.data.DeepResearchCatalog
import com.echoflow.data.DownloadState
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

/** How many curated models to show before the "Show all" expander. */
private const val CatalogPreviewCount = 3

private const val PageHome = "home"
private const val PageAppearance = "appearance"
private const val PageCloudModels = "cloud_models"
private const val PageWebSearch = "web_search"
private const val PageLocalModels = "local_models"
private const val PageDeepResearch = "deep_research"

/** Theme-driven Expressive motion for revealing/hiding blocks inside a page. */
@Composable
private fun sectionEnter(): EnterTransition = expandVertically(
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    expandFrom = Alignment.Top,
) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())

@Composable
private fun sectionExit(): ExitTransition = shrinkVertically(
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    shrinkTowards = Alignment.Top,
) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

/**
 * Settings is a hub-and-detail flow: four focused destinations, each opening its own
 * page. Every page is built from the same visual system — a flexible large app bar,
 * section headers, and grouped slabs sitting directly on the surface — so nothing nests
 * cards inside cards.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClicked: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(PageHome) }
    BackHandler(enabled = page != PageHome) { page = PageHome }

    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState == PageHome) {
                (slideInHorizontally(spatial) { -it / 5 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { it / 5 } + fadeOut(effects))
            } else {
                (slideInHorizontally(spatial) { it / 5 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { -it / 5 } + fadeOut(effects))
            }
        },
        label = "settingsPages",
    ) { current ->
        when (current) {
            PageAppearance -> AppearancePage(viewModel, onBack = { page = PageHome })
            PageCloudModels -> CloudModelsPage(viewModel, onBack = { page = PageHome })
            PageWebSearch -> WebSearchPage(viewModel, onBack = { page = PageHome })
            PageLocalModels -> LocalModelsPage(viewModel, onBack = { page = PageHome })
            PageDeepResearch -> DeepResearchPage(viewModel, onBack = { page = PageHome })
            else -> SettingsHomePage(viewModel, onBackClicked = onBackClicked, onOpen = { page = it })
        }
    }
}

// ── Hub ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHomePage(
    viewModel: SettingsViewModel,
    onBackClicked: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val apiKey by viewModel.apiKey.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val webSearchProvider by viewModel.webSearchProvider.collectAsState()
    val webSearchScope by viewModel.webSearchScope.collectAsState()
    val localModelsEnabled by viewModel.localModelsEnabled.collectAsState()
    val localModels by viewModel.localModels.collectAsState()
    val customModels by viewModel.customModels.collectAsState()
    val deepResearchModelId by viewModel.deepResearchModelId.collectAsState()
    val deepResearchModels by viewModel.deepResearchModels.collectAsState()

    val themeLabel = when (darkMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }
    val accentLabel = if (themeColor == "dynamic") "Wallpaper"
    else accents.firstOrNull { it.id == themeColor }?.label ?: "Wallpaper"

    val cloudSubtitle = when {
        apiKey.isBlank() -> "Add your OpenRouter key to get started"
        customModels.isEmpty() -> "Key saved · default model only"
        else -> "Key saved · ${customModels.size} model" + (if (customModels.size == 1) "" else "s") + " added"
    }
    val searchSubtitle = if (webSearchProvider == "off") "Off" else buildString {
        append(searchProviders.firstOrNull { it.id == webSearchProvider }?.label ?: webSearchProvider)
        append(" · ")
        append(
            when (webSearchScope) {
                "cloud" -> "Cloud models"
                "local" -> "Local models"
                else -> "All models"
            }
        )
    }
    val localSubtitle = when {
        !localModelsEnabled -> "Off"
        localModels.isEmpty() -> "On · No models installed yet"
        else -> "On · ${localModels.size} installed"
    }
    val deepResearchSubtitle = when {
        deepResearchModelId.isBlank() -> "No engine selected"
        else -> DeepResearchCatalog.providerEngineById(deepResearchModelId)?.name
            ?: deepResearchModels.firstOrNull { it.id == deepResearchModelId }?.name
            ?: deepResearchModelId
    }

    SettingsPageScaffold(title = "Settings", subtitle = "Make EchoFlow yours", onBack = onBackClicked) {
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Palette,
                polygon = BrandShapes.heroStart, // Sunny
                title = "Appearance",
                subtitle = "$themeLabel theme · $accentLabel accent",
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 0, count = 5,
                onClick = { onOpen(PageAppearance) },
            )
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = MaterialShapes.Gem,
                title = "Cloud models",
                subtitle = cloudSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 1, count = 5,
                onClick = { onOpen(PageCloudModels) },
            )
            SettingsNavRow(
                icon = Icons.Default.Search,
                polygon = BrandShapes.avatarEnd, // Clover4Leaf
                title = "Web search",
                subtitle = searchSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 2, count = 5,
                onClick = { onOpen(PageWebSearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.Science,
                polygon = MaterialShapes.Cookie4Sided,
                title = "Deep Research",
                subtitle = deepResearchSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 3, count = 5,
                onClick = { onOpen(PageDeepResearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.PhoneAndroid,
                polygon = BrandShapes.avatarStart, // Cookie9Sided
                title = "Local models",
                subtitle = localSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 4, count = 5,
                onClick = { onOpen(PageLocalModels) },
            )
        }
    }
}

/** One hub row: shaped icon badge, title, live summary, chevron. */
@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    polygon: RoundedPolygon,
    title: String,
    subtitle: String,
    container: Color,
    onContainer: Color,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedPolygonShape(polygon)).background(container),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(22.dp), tint = onContainer) }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Appearance ────────────────────────────────────────────────────────────────────────

@Composable
private fun AppearancePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val darkMode by viewModel.darkMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()

    SettingsPageScaffold(title = "Appearance", subtitle = "Theme & accent color", onBack = onBack) {
        PageSection("Theme")
        ConnectedToggleRow(
            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
            icons = listOf(Icons.Default.BrightnessAuto, Icons.Default.LightMode, Icons.Default.DarkMode),
            selected = darkMode,
            onSelect = viewModel::saveDarkMode,
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Accent color", "Wallpaper follows your Material You palette")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MorphSwatch(
                    label = "Wallpaper",
                    color = MaterialTheme.colorScheme.primaryContainer,
                    idleIcon = Icons.Default.Palette,
                    idleIconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    selected = themeColor == "dynamic",
                ) { viewModel.saveThemeColor("dynamic") }
            }
            accents.forEach { accent ->
                MorphSwatch(
                    label = accent.label,
                    color = accent.swatch,
                    idleIcon = null,
                    idleIconTint = Color.White,
                    selected = themeColor == accent.id,
                ) { viewModel.saveThemeColor(accent.id) }
            }
        }
    }
}

/**
 * Accent swatch with an Expressive selection state: the polygon morphs Cookie → Sunny
 * with a spring, scales up slightly, and the check pops in.
 */
@Composable
private fun MorphSwatch(
    label: String,
    color: Color,
    idleIcon: ImageVector?,
    idleIconTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val morph = rememberMorph(BrandShapes.avatarStart, BrandShapes.heroStart)
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchMorph",
    )
    val swatchScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "swatchScale",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = MorphPolygonShape(morph, progress),
            color = color,
            modifier = Modifier.size(56.dp).scale(swatchScale),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    selected -> Icon(
                        Icons.Default.Check, "Selected",
                        Modifier.size(24.dp).scale(progress),
                        tint = idleIconTint,
                    )
                    idleIcon != null -> Icon(idleIcon, null, Modifier.size(22.dp), tint = idleIconTint)
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Cloud models (OpenRouter key + model list) ────────────────────────────────────────

@Composable
private fun CloudModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    val customModels by viewModel.customModels.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var showModelDirectory by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Cloud models", subtitle = "OpenRouter key & model list", onBack = onBack) {
        PageSection("API key", "One key from openrouter.ai unlocks every cloud model")
        FormCard {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text("sk-or-v1-…") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (keyVisible) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (apiKey.isNotBlank()) {
                Spacer(Modifier.height(Spacing.s))
                SavedKeyBadge("A key is saved on this device")
            }
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = { viewModel.saveApiKey(keyInput.trim()) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text("Save key")
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Your models", "Live pricing and context windows from the directory")
        Button(
            onClick = {
                showModelDirectory = true
                viewModel.loadOpenRouterDirectory()
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Browse the model directory")
        }

        Spacer(Modifier.height(Spacing.m))
        if (customModels.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Memory, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "No models added yet.\nSearch the directory to build your model picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                customModels.forEachIndexed { index, model ->
                    Surface(
                        shape = groupedItemShape(index, customModels.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedPolygonShape(MaterialShapes.Gem)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Memory, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                            Spacer(Modifier.width(Spacing.base))
                            Column(Modifier.weight(1f)) {
                                Text(model.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    model.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { viewModel.deleteCustomModel(model.id) }) {
                                Icon(Icons.Default.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModelDirectory) {
        OpenRouterDirectorySheet(
            query = orQuery,
            results = orResults,
            loading = orLoading,
            error = orError,
            addedIds = customModels.map { it.id }.toSet(),
            onQueryChange = viewModel::updateOrModelQuery,
            onRetry = viewModel::loadOpenRouterDirectory,
            onAdd = { info -> viewModel.addCustomModel(info.id, info.name.substringAfter(": ")) },
            onRemove = viewModel::deleteCustomModel,
            onDismiss = { showModelDirectory = false },
        )
    }
}

/** Live OpenRouter directory: search-as-you-type with pricing and context windows. */
@Composable
private fun OpenRouterDirectorySheet(
    query: String,
    results: List<OpenRouterModelInfo>,
    loading: Boolean,
    error: String?,
    addedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAdd: (OpenRouterModelInfo) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Full height from the moment it opens — the sheet must not jump taller when
        // results stream in.
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.base)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text("Model directory", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Live from openrouter.ai — tap a model to add it to your picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.base))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Claude, GPT, Gemini, DeepSeek…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularWavyProgressIndicator()
                        Spacer(Modifier.height(Spacing.m))
                        Text(
                            "Loading the directory…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error != null -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Spacing.m))
                        FilledTonalButton(onClick = onRetry, shape = CircleShape) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.s))
                            Text("Try again")
                        }
                    }
                    results.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "Nothing in the directory matches “$query”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
                    ) {
                        items(results.size) { index ->
                            val info = results[index]
                            OrModelRow(
                                info = info,
                                index = index,
                                count = results.size,
                                added = info.id in addedIds,
                                onAdd = { onAdd(info) },
                                onRemove = { onRemove(info.id) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun OrModelRow(
    info: OpenRouterModelInfo,
    index: Int,
    count: Int,
    added: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = { if (added) onRemove() else onAdd() },
        shape = groupedItemShape(index, count),
        color = if (added) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.m, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    info.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (added) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    info.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (added) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    info.contextLength?.let { ctx ->
                        InfoPill(text = formatContext(ctx), added = added)
                    }
                    InfoPill(text = formatPricing(info), added = added)
                }
            }
            Spacer(Modifier.width(Spacing.s))
            if (added) {
                Icon(Icons.Default.CheckCircle, "Added", tint = MaterialTheme.colorScheme.primary)
            } else {
                FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Add, "Add ${info.name}", Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, added: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (added) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (added) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 3.dp),
        )
    }
}

private fun formatContext(tokens: Int): String = when {
    tokens >= 1_000_000 -> "%.1fM ctx".format(tokens / 1_000_000.0).replace(".0M", "M")
    tokens >= 1_000 -> "${tokens / 1_000}K ctx"
    else -> "$tokens ctx"
}

private fun formatPricing(info: OpenRouterModelInfo): String {
    if (info.isFree) return "Free"
    fun fmt(v: Double?): String? = v?.let {
        when {
            it >= 10 -> "$%.0f".format(it)
            it >= 1 -> "$%.1f".format(it)
            else -> "$%.2f".format(it)
        }
    }
    val inP = fmt(info.promptPricePerM)
    val outP = fmt(info.completionPricePerM)
    return when {
        inP != null && outP != null -> "$inP in · $outP out /M"
        inP != null -> "$inP /M"
        else -> "Pricing varies"
    }
}

// ── Web search ────────────────────────────────────────────────────────────────────────

@Composable
private fun WebSearchPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val webSearchProvider by viewModel.webSearchProvider.collectAsState()
    val webSearchScope by viewModel.webSearchScope.collectAsState()
    val exaKey by viewModel.exaApiKey.collectAsState()
    val parallelKey by viewModel.parallelApiKey.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()

    val savedSearchKey = when (webSearchProvider) {
        "exa" -> exaKey
        "parallel" -> parallelKey
        "firecrawl" -> firecrawlKey
        else -> ""
    }
    // Unsaved edits are kept per provider, so switching providers never loses what you
    // typed — and a provider with no draft always falls back to its key saved on disk.
    val searchKeyDrafts = remember { mutableStateMapOf<String, String>() }
    val searchKeyInput = searchKeyDrafts[webSearchProvider] ?: savedSearchKey
    var searchKeyVisible by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Web search", subtitle = "Live answers for every model", onBack = onBack) {
        PageSection("Provider")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            searchProviders.forEachIndexed { index, option ->
                ProviderRow(
                    option = option,
                    index = index,
                    count = searchProviders.size,
                    selected = webSearchProvider == option.id,
                    onSelect = { viewModel.saveWebSearchProvider(option.id) },
                )
            }
        }

        AnimatedVisibility(visible = webSearchProvider != "off", enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                PageSection("Use search with")
                ConnectedToggleRow(
                    options = listOf("both" to "Both", "cloud" to "Cloud", "local" to "Local"),
                    selected = webSearchScope,
                    onSelect = viewModel::saveWebSearchScope,
                )
                if (webSearchProvider == "openrouter" && webSearchScope != "cloud") {
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "OpenRouter search only works with cloud models. Local models need Exa, Parallel or Firecrawl.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }
        }

        AnimatedVisibility(visible = webSearchProvider == "openrouter", enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.m))
                FormCard {
                    Text("How it works", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Search runs server-side on OpenRouter: the model decides if and when " +
                            "to search — zero, one or several times per answer — and the cost is " +
                            "billed through your OpenRouter account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(visible = webSearchProvider in setOf("exa", "parallel", "firecrawl"), enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                val providerLabel = searchProviders.firstOrNull { it.id == webSearchProvider }?.label ?: ""
                PageSection(
                    "$providerLabel API key",
                    "Offered to every model — even on-device ones — as a search tool. Stored only on this phone.",
                )
                FormCard {
                    OutlinedTextField(
                        value = searchKeyInput,
                        onValueChange = { searchKeyDrafts[webSearchProvider] = it },
                        placeholder = { Text("Paste your $providerLabel key") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        visualTransformation = if (searchKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Key, null) },
                        trailingIcon = {
                            IconButton(onClick = { searchKeyVisible = !searchKeyVisible }) {
                                Icon(if (searchKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (searchKeyVisible) "Hide" else "Show")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (savedSearchKey.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.s))
                        SavedKeyBadge("A $providerLabel key is saved on this device")
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Button(
                        onClick = {
                            viewModel.saveSearchApiKey(webSearchProvider, searchKeyInput)
                            searchKeyDrafts.remove(webSearchProvider)
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save key") }
                }
            }
        }
    }
}

/** Provider choice with a shaped monogram badge; selected state fills with primary. */
@Composable
private fun ProviderRow(
    option: SearchProviderOption,
    index: Int,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val badgeShapes = listOf(
        MaterialShapes.Circle,
        BrandShapes.heroEnd, // Cookie12Sided
        BrandShapes.heroStart, // Sunny
        BrandShapes.avatarEnd, // Clover4Leaf
        BrandShapes.avatarStart, // Cookie9Sided
    )
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
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedPolygonShape(badgeShapes[index % badgeShapes.size]))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (option.id == "off") {
                    Icon(
                        Icons.Default.SearchOff, null, Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        option.label.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.base))
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

// ── Deep Research ─────────────────────────────────────────────────────────────────────

@Composable
private fun DeepResearchPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val selectedEngineId by viewModel.deepResearchModelId.collectAsState()
    val searchProvider by viewModel.deepResearchSearchProvider.collectAsState()
    val maxSearches by viewModel.deepResearchMaxSearches.collectAsState()
    val maxSources by viewModel.deepResearchMaxSources.collectAsState()
    val drModels by viewModel.deepResearchModels.collectAsState()
    val exaKey by viewModel.exaApiKey.collectAsState()
    val parallelKey by viewModel.parallelApiKey.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }

    fun keyFor(provider: String): String = when (provider) {
        "exa" -> exaKey
        "parallel" -> parallelKey
        "firecrawl" -> firecrawlKey
        else -> ""
    }
    // Provider-native engines appear purely based on which provider keys exist — never tied
    // to the chat-model search-provider choice below.
    val providerEngines = DeepResearchCatalog.providerEngines.filter { keyFor(it.provider).isNotBlank() }

    SettingsPageScaffold(title = "Deep Research", subtitle = "Multi-step investigation mode", onBack = onBack) {
        FormCard {
            Text("Two ways to research", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "1. A provider (Exa, Parallel, Firecrawl) researches on its own — add that " +
                    "provider's key in Web search and its engines show up below.\n" +
                    "2. A chat model orchestrates — it plans searches, runs them through a search " +
                    "provider, and writes a cited report.\n\n" +
                    "Pick a default engine below. You can also switch it from the “+” menu in chat. " +
                    "Runs happen in the background and can take a few minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Default engine", "What runs when you start Deep Research")

        if (providerEngines.isEmpty() && drModels.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Nothing available yet.\nAdd an Exa, Parallel or Firecrawl key in Web search,\nor add a research model below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (providerEngines.isNotEmpty()) {
            SectionLabel("Run directly by a provider")
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                providerEngines.forEach { engine ->
                    DrEngineSelectRow(
                        name = engine.name,
                        subtitle = engine.description,
                        selected = engine.id == selectedEngineId,
                        onClick = { viewModel.saveDeepResearchModel(engine.id) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
        }

        SectionLabel("Run by a chat model")
        if (drModels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                drModels.forEach { model ->
                    DrEngineSelectRow(
                        name = model.name,
                        subtitle = model.id,
                        selected = model.id == selectedEngineId,
                        onClick = { viewModel.saveDeepResearchModel(model.id) },
                        onDelete = { viewModel.deleteDeepResearchModel(model.id) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
        }
        FilledTonalButton(
            onClick = { showDirectory = true; viewModel.loadOpenRouterDirectory() },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Add a research model")
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Chat-model options", "Only used when a chat model runs the research")

        SectionLabel("Search provider")
        ConnectedToggleRow(
            options = listOf("auto" to "Auto", "exa" to "Exa", "parallel" to "Parallel", "firecrawl" to "Firecrawl"),
            selected = searchProvider,
            onSelect = viewModel::saveDeepResearchSearchProvider,
        )
        Spacer(Modifier.height(Spacing.s))
        // Per-selection feedback: show whether the *chosen* provider actually has a key.
        val autoResolved = listOf("exa", "parallel", "firecrawl").firstOrNull { keyFor(it).isNotBlank() }
        val searchMissing = if (searchProvider == "auto") autoResolved == null else keyFor(searchProvider).isBlank()
        val searchStatus = when {
            searchProvider == "auto" && autoResolved == null -> "No search keys yet — add one in Settings → Web search"
            searchProvider == "auto" -> "Using ${autoResolved!!.replaceFirstChar { it.uppercase() }} — first key found"
            searchMissing -> "No ${searchProvider.replaceFirstChar { it.uppercase() }} key — add it in Settings → Web search"
            else -> "${searchProvider.replaceFirstChar { it.uppercase() }} key found"
        }
        Text(
            searchStatus,
            style = MaterialTheme.typography.bodySmall,
            color = if (searchMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )

        Spacer(Modifier.height(Spacing.base))
        SectionLabel("Maximum searches")
        ConnectedToggleRow(
            options = listOf("3" to "3", "5" to "5", "10" to "10"),
            selected = maxSearches.toString(),
            onSelect = { viewModel.saveDeepResearchMaxSearches(it.toInt()) },
        )

        Spacer(Modifier.height(Spacing.base))
        SectionLabel("Maximum sources")
        ConnectedToggleRow(
            options = listOf("10" to "10", "20" to "20", "50" to "50"),
            selected = maxSources.toString(),
            onSelect = { viewModel.saveDeepResearchMaxSources(it.toInt()) },
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("On-device")
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedPolygonShape(BrandShapes.avatarStart)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(Spacing.base))
                Column(Modifier.weight(1f)) {
                    Text("Local Deep Research", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Run research fully on-device — coming soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        "Soon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = 4.dp),
                    )
                }
            }
        }
    }

    if (showDirectory) {
        OpenRouterDirectorySheet(
            query = orQuery,
            results = orResults,
            loading = orLoading,
            error = orError,
            addedIds = drModels.map { it.id }.toSet(),
            onQueryChange = viewModel::updateOrModelQuery,
            onRetry = viewModel::loadOpenRouterDirectory,
            onAdd = { info -> viewModel.addDeepResearchModel(info.id, info.name.substringAfter(": ")) },
            onRemove = viewModel::deleteDeepResearchModel,
            onDismiss = { showDirectory = false },
        )
    }
}

/**
 * A selectable Deep Research engine row (provider engine or chat model). Tapping it makes
 * that engine the default; the optional trash affordance removes an added chat model.
 */
@Composable
private fun DrEngineSelectRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie4Sided)).background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Science, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, "Selected", Modifier.padding(horizontal = Spacing.xs), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Local models ──────────────────────────────────────────────────────────────────────

@Composable
private fun LocalModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val localModelsEnabled by viewModel.localModelsEnabled.collectAsState()
    val hfToken by viewModel.hfAccessToken.collectAsState()
    val localModels by viewModel.localModels.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val importError by viewModel.importError.collectAsState()
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
            }
        }
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
@Composable
private fun SettingsPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = subtitle?.let { { Text(it) } },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.base, vertical = Spacing.s),
        ) {
            content()
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** Section header used on every page: title plus optional supporting line. */
@Composable
private fun PageSection(title: String, supporting: String? = null) {
    Column(Modifier.padding(start = Spacing.xs, bottom = Spacing.m)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (supporting != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), content = content)
    }
}

@Composable
private fun SavedKeyBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The M3 Expressive connected button group: ToggleButtons that share a slab and morph
 * shape on press/selection.
 */
@Composable
private fun ConnectedToggleRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val checked = selected == value
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (it) onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                when {
                    checked -> Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    icons != null -> Icon(icons[index], null, Modifier.size(16.dp))
                }
                if (checked || icons != null) Spacer(Modifier.width(Spacing.xs))
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}

/** Last row of a grouped list that expands/collapses the rest of the group. */
@Composable
private fun ShowMoreRow(
    expanded: Boolean,
    expandText: String,
    collapseText: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "showMoreChevron",
    )
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                if (expanded) collapseText else expandText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                Icons.Default.ExpandMore, null,
                Modifier.size(18.dp).rotate(chevron),
                tint = MaterialTheme.colorScheme.primary,
            )
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

/** Bottom-sheet Hugging Face model search. Opens idle — nothing runs until you search. */
@Composable
private fun HfModelSearchSheet(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hasSearched by remember { mutableStateOf(false) }
    val runSearch = {
        hasSearched = true
        onSearch()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Full height from the start, matching the OpenRouter directory sheet.
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.base)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text("Search mobile models", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "LiteRT-LM bundles from Hugging Face (.task / .litertlm) that run fully on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.base))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Gemma 4, Qwen3…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Clear") }
                    }
                },
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = runSearch, enabled = !loading, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(if (loading) "Searching…" else "Search Hugging Face")
            }
            Spacer(Modifier.height(Spacing.base))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    loading -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularWavyProgressIndicator()
                        Spacer(Modifier.height(Spacing.m))
                        Text(
                            "Searching Hugging Face…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    error != null -> Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(Spacing.base),
                        )
                    }
                    results.isEmpty() && !hasSearched -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "Type a model name and search —\ntry “gemma”, “qwen” or “deepseek”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    results.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "No mobile-ready model bundles found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
            Spacer(Modifier.height(Spacing.xl))
        }
    }
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
                    when (model.source) {
                        "imported" -> "Imported · "
                        "recovered" -> "Found on device · "
                        else -> "Downloaded · "
                    } + Formatter.formatShortFileSize(context, model.sizeBytes),
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
