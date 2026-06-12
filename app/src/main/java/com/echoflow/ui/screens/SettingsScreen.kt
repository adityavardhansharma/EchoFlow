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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
import com.echoflow.data.DownloadState
import com.echoflow.data.LocalModel
import com.echoflow.data.LocalModelCatalog
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.SectionLabel
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

/** How many curated models to show before the "Show all" expander. */
private const val CatalogPreviewCount = 3

private const val PageHome = "home"
private const val PageAppearance = "appearance"
private const val PageCloudApi = "cloud_api"
private const val PageWebSearch = "web_search"
private const val PageLocalModels = "local_models"
private const val PageCustomModels = "custom_models"

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
 * Settings is a hub-and-detail flow, like Android system settings: the home page lists
 * each area as a tappable row with a live summary, and every area opens its own focused
 * page. This keeps each screen flat — content sits directly on the surface instead of
 * cards nested in expanding cards.
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
                // Popping back to the hub: hub slides in from the left.
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
            PageCloudApi -> CloudApiPage(viewModel, onBack = { page = PageHome })
            PageWebSearch -> WebSearchPage(viewModel, onBack = { page = PageHome })
            PageLocalModels -> LocalModelsPage(viewModel, onBack = { page = PageHome })
            PageCustomModels -> CustomModelsPage(viewModel, onBack = { page = PageHome })
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

    val themeLabel = when (darkMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }
    val accentLabel = if (themeColor == "dynamic") "Wallpaper"
    else accents.firstOrNull { it.id == themeColor }?.label ?: "Wallpaper"
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

    SettingsPageScaffold(title = "Settings", onBack = onBackClicked) {
        SectionLabel("Personalize")
        SettingsNavRow(
            icon = Icons.Default.Palette,
            polygon = BrandShapes.heroStart, // Sunny
            title = "Appearance",
            subtitle = "$themeLabel theme · $accentLabel accent",
            container = MaterialTheme.colorScheme.primaryContainer,
            onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
            index = 0, count = 1,
            onClick = { onOpen(PageAppearance) },
        )

        Spacer(Modifier.height(Spacing.m))
        SectionLabel("Cloud")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Key,
                polygon = MaterialShapes.Gem,
                title = "OpenRouter API",
                subtitle = if (apiKey.isBlank()) "No key saved" else "API key saved",
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 0, count = 3,
                onClick = { onOpen(PageCloudApi) },
            )
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = BrandShapes.avatarEnd, // Clover4Leaf
                title = "Web search",
                subtitle = searchSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 1, count = 3,
                onClick = { onOpen(PageWebSearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.Memory,
                polygon = BrandShapes.heroEnd, // Cookie12Sided
                title = "Custom models",
                subtitle = if (customModels.isEmpty()) "Add OpenRouter model IDs"
                else "${customModels.size} model" + if (customModels.size == 1) "" else "s",
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 2, count = 3,
                onClick = { onOpen(PageCustomModels) },
            )
        }

        Spacer(Modifier.height(Spacing.m))
        SectionLabel("On-device")
        SettingsNavRow(
            icon = Icons.Default.PhoneAndroid,
            polygon = BrandShapes.avatarStart, // Cookie9Sided
            title = "Local models",
            subtitle = localSubtitle,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
            index = 0, count = 1,
            onClick = { onOpen(PageLocalModels) },
        )
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
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedPolygonShape(polygon)).background(container),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = onContainer) }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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

// ── Detail pages ──────────────────────────────────────────────────────────────────────

@Composable
private fun AppearancePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val darkMode by viewModel.darkMode.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()

    SettingsPageScaffold(title = "Appearance", subtitle = "Theme & accent color", onBack = onBack) {
        Text(
            "Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
        )
        ConnectedToggleRow(
            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
            selected = darkMode,
            onSelect = viewModel::saveDarkMode,
        )

        Spacer(Modifier.height(Spacing.xl))
        Text(
            "Accent color",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
        )
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

@Composable
private fun CloudApiPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val apiKey by viewModel.apiKey.collectAsState()
    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "OpenRouter API", subtitle = "Cloud model access", onBack = onBack) {
        SettingsCard {
            Text("API key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Create a key at openrouter.ai → Keys. It unlocks every cloud model in the picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            if (apiKey.isNotBlank()) {
                Spacer(Modifier.height(Spacing.s))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        "A key is saved on this device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = { viewModel.saveApiKey(keyInput.trim()) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Text("Save key")
            }
        }
    }
}

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
        Text(
            "Provider",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
        )
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

        AnimatedVisibility(visible = webSearchProvider != "off", enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    "Use search with",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
                )
                ConnectedToggleRow(
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
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }
        }

        AnimatedVisibility(visible = webSearchProvider == "openrouter", enter = sectionEnter(), exit = sectionExit()) {
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

        AnimatedVisibility(visible = webSearchProvider in setOf("exa", "parallel", "firecrawl"), enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(Spacing.m))
                SettingsCard {
                    val providerLabel = searchProviders.firstOrNull { it.id == webSearchProvider }?.label ?: ""
                    Text("$providerLabel API key", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    OutlinedTextField(
                        value = searchKeyInput,
                        onValueChange = { searchKeyDrafts[webSearchProvider] = it },
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
                    if (savedSearchKey.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.s))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                "A $providerLabel key is saved on this device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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

                // Get models: search + import side by side, then the curated list.
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    "Get models",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilledTonalButton(
                        onClick = {
                            showHfModelSearch = true
                            if (hfSearchResults.isEmpty()) viewModel.searchHfModels()
                        },
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
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { hfTokenOpen = !hfTokenOpen }
                                .padding(Spacing.base),
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

@Composable
private fun CustomModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val customModels by viewModel.customModels.collectAsState()
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }

    SettingsPageScaffold(title = "Custom models", subtitle = "Your OpenRouter model list", onBack = onBack) {
        SettingsCard {
            Text("Add a model", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Any OpenRouter model ID works — e.g. anthropic/claude-sonnet-4.6",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.s))
            OutlinedTextField(
                value = modelId, onValueChange = { modelId = it },
                label = { Text("Model ID") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.s))
            OutlinedTextField(
                value = modelName, onValueChange = { modelName = it },
                label = { Text("Display name") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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

        if (customModels.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.l))
            Text(
                "Your models",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.m),
            )
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

/**
 * The M3 Expressive connected button group: ToggleButtons that share a slab and morph
 * shape on press/selection, replacing the old custom segmented pill.
 */
@Composable
private fun ConnectedToggleRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { if (it) onSelect(value) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                if (selected == value) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                }
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

/** Bottom-sheet Hugging Face model search. */
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
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
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.m))
            Button(onClick = onSearch, enabled = !loading, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(if (loading) "Searching…" else "Search Hugging Face")
            }
            Spacer(Modifier.height(Spacing.base))
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
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
                    results.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.Search, null,
                            Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            "No mobile-ready model bundles found.\nTry “gemma”, “qwen” or “deepseek”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
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
