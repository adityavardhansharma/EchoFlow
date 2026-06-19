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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.data.AdvisorProfile
import com.echoflow.data.AgentProfile
import com.echoflow.data.CatalogEntry
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
import kotlin.math.roundToInt

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
private const val PageDataAgent = "data_agent"
private const val PageBrowserFlow = "browser_flow"
private const val PageEchoLabs = "echo_labs"
private const val PageEchoAdviser = "echo_adviser"
private const val PageEchoFusion = "echo_fusion"
private const val PageEchoAgent = "echo_agent"

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
            PageEchoLabs -> EchoLabsPage(viewModel, onOpen = { page = it }, onBack = { page = PageHome })
            PageDataAgent -> DataAgentPage(viewModel, onBack = { page = PageEchoLabs })
            PageBrowserFlow -> BrowserFlowPage(viewModel, onBack = { page = PageEchoLabs })
            PageEchoAdviser -> EchoAdviserPage(viewModel, onBack = { page = PageEchoLabs })
            PageEchoFusion -> EchoFusionPage(viewModel, onBack = { page = PageEchoLabs })
            PageEchoAgent -> EchoAgentPage(viewModel, onBack = { page = PageEchoLabs })
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
    val dataAgentEnabled by viewModel.dataAgentEnabled.collectAsState()
    val dataAgentEngine by viewModel.dataAgentEngine.collectAsState()
    val browserFlowEnabled by viewModel.browserFlowEnabled.collectAsState()
    val echoAdviserEnabled by viewModel.echoAdviserEnabled.collectAsState()
    val echoFusionEnabled by viewModel.echoFusionEnabled.collectAsState()
    val echoAgentEnabled by viewModel.echoAgentEnabled.collectAsState()
    val firecrawlKeyHome by viewModel.firecrawlApiKey.collectAsState()
    val advisorProfiles by viewModel.advisorProfiles.collectAsState()
    val fusionPanels by viewModel.fusionPanels.collectAsState()
    val agentProfiles by viewModel.agentProfiles.collectAsState()

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
    val dataAgentSubtitle = when {
        !dataAgentEnabled -> "Off"
        else -> DataAgentCatalog.byId(dataAgentEngine)?.name ?: "On"
    }
    val browserFlowSubtitle = when {
        !browserFlowEnabled -> "Off"
        firecrawlKeyHome.isBlank() -> "On · add a Firecrawl key"
        else -> "Live browser · controlled by chat"
    }
    val labsOnCount = listOf(dataAgentEnabled, echoAdviserEnabled, echoFusionEnabled, echoAgentEnabled, browserFlowEnabled).count { it }
    val echoLabsSubtitle =
        if (labsOnCount == 0) "Experimental modes · all off" else "Experimental modes · $labsOnCount on"
    val echoAdviserSubtitle = when {
        advisorProfiles.isEmpty() -> "Escalate to a stronger model · none set up"
        else -> "${advisorProfiles.size} advisor" + (if (advisorProfiles.size == 1) "" else "s")
    }
    val echoFusionSubtitle = when {
        fusionPanels.isEmpty() -> "A panel deliberates · no panels yet"
        else -> "${fusionPanels.size} panel" + (if (fusionPanels.size == 1) "" else "s")
    }
    val echoAgentSubtitle = when {
        agentProfiles.isEmpty() -> "Main model hands tasks to your Echo Agents · none yet"
        else -> "${agentProfiles.size} Echo Agent" + (if (agentProfiles.size == 1) "" else "s")
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
                index = 0, count = 6,
                onClick = { onOpen(PageAppearance) },
            )
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = MaterialShapes.Gem,
                title = "Cloud models",
                subtitle = cloudSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 1, count = 6,
                onClick = { onOpen(PageCloudModels) },
            )
            SettingsNavRow(
                icon = Icons.Default.PhoneAndroid,
                polygon = BrandShapes.avatarStart, // Cookie9Sided
                title = "Local models",
                subtitle = localSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 2, count = 6,
                onClick = { onOpen(PageLocalModels) },
            )
            SettingsNavRow(
                icon = Icons.Default.Search,
                polygon = BrandShapes.avatarEnd, // Clover4Leaf
                title = "Web search",
                subtitle = searchSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 3, count = 6,
                onClick = { onOpen(PageWebSearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.Science,
                polygon = MaterialShapes.Cookie4Sided,
                title = "Deep Research",
                subtitle = deepResearchSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 4, count = 6,
                onClick = { onOpen(PageDeepResearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.AutoAwesome,
                polygon = MaterialShapes.Pentagon,
                title = "Echo Labs",
                subtitle = echoLabsSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 5, count = 6,
                onClick = { onOpen(PageEchoLabs) },
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
    val cloudParams by viewModel.cloudInferenceParams.collectAsState()
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

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Tuning", "Sampler settings applied to every OpenRouter model")
        InferenceParamsCard(
            local = false,
            params = cloudParams,
            onChange = { viewModel.saveInferenceParams(local = false, params = it) },
            onReset = { viewModel.resetInferenceParams(local = false) },
        )
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

// ── Echo Labs (hub) ───────────────────────────────────────────────────────────────────

@Composable
private fun EchoLabsPage(viewModel: SettingsViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val dataAgentEnabled by viewModel.dataAgentEnabled.collectAsState()
    val dataAgentEngine by viewModel.dataAgentEngine.collectAsState()
    val echoAdviserEnabled by viewModel.echoAdviserEnabled.collectAsState()
    val echoFusionEnabled by viewModel.echoFusionEnabled.collectAsState()
    val echoAgentEnabled by viewModel.echoAgentEnabled.collectAsState()
    val browserFlowEnabled by viewModel.browserFlowEnabled.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()
    val advisorProfiles by viewModel.advisorProfiles.collectAsState()
    val fusionPanels by viewModel.fusionPanels.collectAsState()
    val agentProfiles by viewModel.agentProfiles.collectAsState()

    val dataAgentSubtitle = if (!dataAgentEnabled) "Off" else DataAgentCatalog.byId(dataAgentEngine)?.name ?: "On"
    val adviserSubtitle = if (!echoAdviserEnabled) "Off" else "${advisorProfiles.size} advisor" + (if (advisorProfiles.size == 1) "" else "s")
    val fusionSubtitle = if (!echoFusionEnabled) "Off" else "${fusionPanels.size} panel" + (if (fusionPanels.size == 1) "" else "s")
    val agentSubtitle = if (!echoAgentEnabled) "Off" else "${agentProfiles.size} Echo Agent" + (if (agentProfiles.size == 1) "" else "s")
    val browserSubtitle = when {
        !browserFlowEnabled -> "Off"
        firecrawlKey.isBlank() -> "On · add a Firecrawl key"
        else -> "Live browser · chat-controlled"
    }

    SettingsPageScaffold(title = "Echo Labs", subtitle = "Experimental modes · turn on what you need", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Dataset,
                polygon = BrandShapes.avatarEnd,
                title = "Data Agent",
                subtitle = dataAgentSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 0, count = 4,
                onClick = { onOpen(PageDataAgent) },
            )
            SettingsNavRow(
                icon = Icons.Default.Psychology,
                polygon = MaterialShapes.Cookie7Sided,
                title = "Echo Adviser",
                subtitle = adviserSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 1, count = 4,
                onClick = { onOpen(PageEchoAdviser) },
            )
            SettingsNavRow(
                icon = Icons.Default.AccountTree,
                polygon = BrandShapes.avatarEnd,
                title = "Echo Fusion",
                subtitle = fusionSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 2, count = 4,
                onClick = { onOpen(PageEchoFusion) },
            )
            SettingsNavRow(
                icon = Icons.Default.Hub,
                polygon = MaterialShapes.Pentagon,
                title = "Echo Agents",
                subtitle = agentSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 3, count = 4,
                onClick = { onOpen(PageEchoAgent) },
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Beta", "Experimental · may break · uses Firecrawl credits while open")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = MaterialShapes.Cookie4Sided,
                title = "Browser Flow",
                subtitle = browserSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 0, count = 1,
                onClick = { onOpen(PageBrowserFlow) },
            )
        }

        val artifactsOffline by viewModel.artifactsOffline.collectAsState()
        Spacer(Modifier.height(Spacing.xl))
        PageSection("Artifacts", "How generated web artifacts handle the network")
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Offline artifacts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Build HTML artifacts with no CDN so they render without a connection. " +
                            "Reports and documents already work offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = artifactsOffline,
                    onCheckedChange = { viewModel.saveArtifactsOffline(it) },
                )
            }
        }
    }
}

// ── Data Agent ────────────────────────────────────────────────────────────────────────

@Composable
private fun DataAgentPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
private fun BrowserFlowPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
private fun LabMasterToggle(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
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
private fun EchoAdviserPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
private fun EchoFusionPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
private fun EchoNoticeCard(icon: ImageVector, text: String, error: Boolean = false) {
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
private fun EchoNamePromptDialog(
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
private fun FusionPanelDialog(
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
private fun EchoAgentPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
private fun AgentProfileDialog(
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

@Composable
private fun LocalModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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

/**
 * Collapsible "generation parameters" card used on both the Cloud and Local model pages.
 * One global set of sampler knobs per side; values persist as the user releases each slider.
 * Out-of-range values are clamped per model at generation time, so the sliders here are the
 * user's *preferences*, not necessarily what a given small model will run with.
 */
@Composable
private fun InferenceParamsCard(
    local: Boolean,
    params: InferenceParams,
    onChange: (InferenceParams) -> Unit,
    onReset: () -> Unit,
) {
    var open by rememberSaveable(local) { mutableStateOf(false) }
    // Live draft so dragging is smooth; persisted only when a slider is released.
    var draft by remember(params) { mutableStateOf(params) }

    val defaults = if (local) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS
    val isDefault = params == defaults
    val topKMax = if (local) InferenceLimits.LOCAL_TOP_K_MAX else InferenceLimits.CLOUD_TOP_K_MAX
    val maxTokCeil = if (local) InferenceLimits.LOCAL_MAX_TOKENS_CEIL else InferenceLimits.CLOUD_MAX_TOKENS_CEIL
    val tokenSteps = (maxTokCeil / InferenceLimits.MAX_TOKENS_STEP) - 1

    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "paramsChevron",
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(onClick = { open = !open }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie4Sided))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Spacer(Modifier.width(Spacing.base))
                    Column(Modifier.weight(1f)) {
                        Text("Generation parameters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (isDefault) "Shipped defaults" else "Customized",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Default.ExpandMore, if (open) "Collapse" else "Expand",
                        Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = open, enter = sectionEnter(), exit = sectionExit()) {
                Column(Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.base)) {
                    ParamSliderRow(
                        label = "Temperature",
                        value = draft.temperature,
                        valueRange = InferenceLimits.TEMP_MIN..InferenceLimits.TEMP_MAX,
                        steps = 39,
                        display = { "%.2f".format(it) },
                        editPrefill = { "%.2f".format(it) },
                        editHint = "0.00 – 2.00",
                        editDecimals = true,
                        onValueChange = { draft = draft.copy(temperature = (it * 100).roundToInt() / 100f) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Top P",
                        value = draft.topP,
                        valueRange = InferenceLimits.TOP_P_MIN..InferenceLimits.TOP_P_MAX,
                        steps = 19,
                        display = { "%.2f".format(it) },
                        editPrefill = { "%.2f".format(it) },
                        editHint = "0.00 – 1.00",
                        editDecimals = true,
                        onValueChange = { draft = draft.copy(topP = (it * 100).roundToInt() / 100f) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Top K",
                        value = draft.topK.toFloat(),
                        valueRange = 0f..topKMax.toFloat(),
                        steps = 0,
                        display = { if (it < 1f) "Off" else it.roundToInt().toString() },
                        editPrefill = { it.roundToInt().toString() },
                        editHint = "0 (off) – $topKMax",
                        editDecimals = false,
                        onValueChange = { draft = draft.copy(topK = it.roundToInt()) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Max tokens",
                        value = draft.maxTokens.coerceIn(0, maxTokCeil).toFloat(),
                        valueRange = 0f..maxTokCeil.toFloat(),
                        steps = tokenSteps,
                        display = {
                            val v = it.roundToInt()
                            if (v <= 0) (if (local) "Model default" else "Unlimited") else v.toString()
                        },
                        editPrefill = { it.roundToInt().toString() },
                        editHint = "0 (${if (local) "model default" else "unlimited"}) – $maxTokCeil",
                        editDecimals = false,
                        onValueChange = { draft = draft.copy(maxTokens = it.roundToInt()) },
                        onCommit = { onChange(draft) },
                    )
                    Text(
                        if (local)
                            "Applied to every on-device model. If a value exceeds what the running model supports, it falls back to the default for that model."
                        else
                            "Applied to every OpenRouter model. Top K and Max tokens are only sent when set above their off position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    TextButton(
                        onClick = onReset,
                        enabled = !isDefault,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Reset to defaults")
                    }
                }
            }
        }
    }
}

/**
 * One labeled slider row with a value chip that's both a readout and a tap target: tapping
 * it opens a small dialog for typing an exact number. Either way the value is snapped to the
 * slider's step grid and clamped to [valueRange] before [onCommit] persists it.
 */
@Composable
private fun ParamSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    editPrefill: (Float) -> String,
    editHint: String,
    editDecimals: Boolean,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Surface(
                onClick = { editing = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    display(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = 4.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onCommit,
            valueRange = valueRange,
            steps = steps,
        )
    }

    if (editing) {
        ParamValueDialog(
            label = label,
            initial = editPrefill(value),
            hint = "Allowed: $editHint",
            decimals = editDecimals,
            onDismiss = { editing = false },
            onConfirm = { typed ->
                editing = false
                typed.trim().toFloatOrNull()?.let { raw ->
                    val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(snapToStep(clamped, valueRange, steps))
                    onCommit()
                }
            },
        )
    }
}

/** Snaps a value to the slider's discrete grid (a no-op for continuous sliders). */
private fun snapToStep(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return value
    val stepSize = (range.endInclusive - range.start) / (steps + 1)
    if (stepSize <= 0f) return value
    return range.start + ((value - range.start) / stepSize).roundToInt() * stepSize
}

/** Numeric entry dialog for a single parameter; accepts integers or decimals per [decimals]. */
@Composable
private fun ParamValueDialog(
    label: String,
    initial: String,
    hint: String,
    decimals: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
                "LiteRT-LM bundles from Hugging Face (.task / .litertlm) that run fully on-device. GGUF models are import-only.",
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
