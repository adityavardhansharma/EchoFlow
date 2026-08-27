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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Brush
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
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

internal data class Accent(val id: String, val label: String, val swatch: Color)

internal data class SearchProviderOption(val id: String, val label: String, val description: String)

internal val searchProviders = listOf(
    SearchProviderOption("off", "Off", "Models answer from their training data only"),
    SearchProviderOption("openrouter", "OpenRouter", "Server-side search for OpenRouter cloud models"),
    SearchProviderOption("exa", "Exa", "Semantic search — works with any model, ~\$5 per 1k searches"),
    SearchProviderOption("parallel", "Parallel", "Objective-based dense excerpts — works with any model"),
    SearchProviderOption("firecrawl", "Firecrawl", "Search plus full-page markdown — works with any model"),
    SearchProviderOption("monid", "Monid", "Live web search via Monid — works with any model"),
)

internal val accents = listOf(
    Accent("monochrome", "Mono", Color(0xFF1B1B1F)),
    Accent("ocean", "Ocean", Color(0xFF1660A8)),
    Accent("forest", "Forest", Color(0xFF1C6E2E)),
    Accent("sunset", "Sunset", Color(0xFF9A4500)),
    Accent("lavender", "Lavender", Color(0xFF6750A4)),
    Accent("rose", "Rose", Color(0xFFB01B49)),
)

/** How many curated models to show before the "Show all" expander. */
internal const val CatalogPreviewCount = 3

internal const val PageHome = "home"
internal const val PageAppearance = "appearance"
internal const val PageModels = "models"
internal const val PageCloudModels = "cloud_models"
internal const val PageWebSearch = "web_search"
internal const val PageLocalModels = "local_models"
internal const val PageDeepResearch = "deep_research"
internal const val PageImagine = "imagine"
internal const val PageSpeechToText = "speech_to_text"
internal const val PageDataAgent = "data_agent"
internal const val PageBrowserFlow = "browser_flow"
internal const val PageEchoLabs = "echo_labs"
internal const val PageEchoAdviser = "echo_adviser"
internal const val PageEchoFusion = "echo_fusion"
internal const val PageEchoAgent = "echo_agent"
internal const val PageCustomProvider = "custom_provider"
internal const val PageCustomProviderCloud = "custom_provider_cloud"
internal const val PageCustomProviderOpenAi = "custom_provider_openai"
internal const val PageCustomProviderClaude = "custom_provider_claude"
internal const val PageCustomProviderGemini = "custom_provider_gemini"
internal const val PageCustomProviderCerebras = "custom_provider_cerebras"
internal const val PageCustomProviderXAi = "custom_provider_xai"
internal const val PageCustomProviderOllama = "custom_provider_ollama"
internal const val PageCustomProviderCompatible = "custom_provider_compatible"
internal const val PageLicenses = "open_source_licenses"
internal val CustomProviderSectionGap = 28.dp

/** Parent page when backing out of [page]; null on the settings hub. */
internal fun settingsParentPage(page: String): String? = when (page) {
    PageHome -> null
    PageAppearance, PageModels, PageCloudModels, PageWebSearch, PageLocalModels,
    PageDeepResearch, PageImagine, PageSpeechToText, PageEchoLabs, PageCustomProviderCloud,
    -> PageHome
    PageDataAgent, PageBrowserFlow, PageEchoAdviser, PageEchoFusion,
    PageEchoAgent, PageCustomProvider, PageLicenses,
    -> PageEchoLabs
    PageCustomProviderOllama, PageCustomProviderCompatible -> PageCustomProvider
    PageCustomProviderOpenAi, PageCustomProviderClaude, PageCustomProviderGemini,
    PageCustomProviderCerebras, PageCustomProviderXAi,
    -> PageCustomProviderCloud
    else -> PageHome
}

/** Theme-driven Expressive motion for revealing/hiding blocks inside a page. */
@Composable
internal fun sectionEnter(): EnterTransition = expandVertically(
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    expandFrom = Alignment.Top,
) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())

@Composable
internal fun sectionExit(): ExitTransition = shrinkVertically(
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
    val navigateBack: () -> Unit = {
        settingsParentPage(page)?.let { page = it }
    }
    BackHandler(enabled = settingsParentPage(page) != null, onBack = navigateBack)

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
            PageAppearance -> AppearancePage(viewModel, onBack = navigateBack)
            PageModels -> ModelsPage(viewModel, onBack = navigateBack)
            PageCloudModels -> CloudModelsPage(viewModel, onBack = navigateBack)
            PageWebSearch -> WebSearchPage(viewModel, onBack = navigateBack)
            PageLocalModels -> LocalModelsPage(viewModel, onBack = navigateBack)
            PageDeepResearch -> DeepResearchPage(viewModel, onBack = navigateBack)
            PageImagine -> ImaginePage(viewModel, onBack = navigateBack)
            PageSpeechToText -> SpeechToTextPage(
                viewModel,
                onOpenCloudModels = { page = PageCloudModels },
                onBack = navigateBack,
            )
            PageEchoLabs -> EchoLabsPage(viewModel, onOpen = { page = it }, onBack = navigateBack)
            PageDataAgent -> DataAgentPage(viewModel, onBack = navigateBack)
            PageBrowserFlow -> BrowserFlowPage(viewModel, onBack = navigateBack)
            PageEchoAdviser -> EchoAdviserPage(viewModel, onBack = navigateBack)
            PageEchoFusion -> EchoFusionPage(viewModel, onBack = navigateBack)
            PageEchoAgent -> EchoAgentPage(viewModel, onBack = navigateBack)
            PageCustomProvider -> CustomApiEndpointPage(viewModel, onOpen = { page = it }, onBack = navigateBack)
            PageCustomProviderCloud -> DirectCloudApisPage(viewModel, onOpen = { page = it }, onBack = navigateBack)
            PageCustomProviderOpenAi -> DirectCloudBrandPage(viewModel, CustomModelProvider.OpenAi, onBack = navigateBack)
            PageCustomProviderClaude -> DirectCloudBrandPage(viewModel, CustomModelProvider.Claude, onBack = navigateBack)
            PageCustomProviderGemini -> DirectCloudBrandPage(viewModel, CustomModelProvider.Gemini, onBack = navigateBack)
            PageCustomProviderCerebras -> DirectCloudBrandPage(viewModel, CustomModelProvider.Cerebras, onBack = navigateBack)
            PageCustomProviderXAi -> DirectCloudBrandPage(viewModel, CustomModelProvider.XAi, onBack = navigateBack)
            PageCustomProviderOllama -> OllamaEndpointPage(viewModel, onBack = navigateBack)
            PageCustomProviderCompatible -> OpenAiCompatibleEndpointPage(viewModel, onBack = navigateBack)
            PageLicenses -> OpenSourceLicensesPage(onBack = navigateBack)
            else -> SettingsHomePage(viewModel, onBackClicked = onBackClicked, onOpen = { page = it })
        }
    }
}

// ── Models (cloud + on-device) ────────────────────────────────────────────────────────

/**
 * One home for the models that answer you: an OpenRouter / On-device toggle over the two
 * existing catalogs, each rendered embedded (no nested app bar). Brand APIs keep their own
 * top-level Custom entry; Ollama and OpenAI-compatible stay under Echo Labs.
 */
@Composable
internal fun ModelsPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(PageCloudModels) }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    SettingsPageScaffold(title = "Models", subtitle = "OpenRouter & on-device", onBack = onBack) {
        ConnectedToggleRow(
            options = listOf(
                PageCloudModels to "OpenRouter",
                PageLocalModels to "On-device",
            ),
            selected = tab,
            onSelect = { tab = it },
            icons = listOf(Icons.Default.Language, Icons.Default.PhoneAndroid),
        )
        Spacer(Modifier.height(Spacing.xl))
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "modelsTab",
        ) { current ->
            when (current) {
                PageLocalModels -> LocalModelsPage(viewModel, onBack = onBack, embedded = true)
                else -> CloudModelsPage(viewModel, onBack = onBack, embedded = true)
            }
        }
    }
}

// ── Hub ───────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SettingsHomePage(
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
    val customProviderConfig by viewModel.customProviderConfig.collectAsState()
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
    val imageGenModelId by viewModel.imageGenModelId.collectAsState()
    val imageModelsHome by viewModel.imageModels.collectAsState()
    val imageGenSubtitle = imageModelsHome.firstOrNull { it.id == imageGenModelId }?.name
        ?: if (imageGenModelId == com.echoflow.data.SettingsRepository.DEFAULT_IMAGE_MODEL_ID) "Gemini 2.5 Flash Image"
        else imageGenModelId
    val videoGenModelId by viewModel.videoGenModelId.collectAsState()
    val videoModelsHome by viewModel.videoModels.collectAsState()
    val videoModelName = videoModelsHome.firstOrNull { it.id == videoGenModelId }?.name
        ?: if (videoGenModelId == com.echoflow.data.SettingsRepository.DEFAULT_VIDEO_MODEL_ID) {
            com.echoflow.data.SettingsRepository.DEFAULT_VIDEO_MODEL_NAME
        } else {
            videoGenModelId
        }
    val imagineSubtitle = "$imageGenSubtitle · $videoModelName"
    val sttCloudModelId by viewModel.sttCloudModel.collectAsState()
    val sttSubtitle = com.echoflow.data.SttCatalog.resolve(sttCloudModelId).name
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
    val labsOnCount = listOf(
        dataAgentEnabled,
        echoAdviserEnabled,
        echoFusionEnabled,
        echoAgentEnabled,
        browserFlowEnabled,
        customProviderConfig.ollamaEnabled,
        customProviderConfig.openAiCompatibleEnabled,
    ).count { it }
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
                index = 0, count = 8,
                onClick = { onOpen(PageAppearance) },
            )
            SettingsNavRow(
                icon = Icons.Default.Hub,
                polygon = MaterialShapes.Gem,
                title = "Models",
                subtitle = "OpenRouter & on-device",
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 1, count = 8,
                onClick = { onOpen(PageModels) },
            )
            SettingsNavRow(
                icon = Icons.Default.Key,
                polygon = BrandShapes.avatarStart, // Cookie9Sided
                title = "Custom",
                subtitle = "OpenAI · Claude · Gemini · Cerebras · xAI",
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 2, count = 8,
                onClick = { onOpen(PageCustomProviderCloud) },
            )
            SettingsNavRow(
                icon = Icons.Default.Search,
                polygon = BrandShapes.avatarEnd, // Clover4Leaf
                title = "Web search",
                subtitle = searchSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 3, count = 8,
                onClick = { onOpen(PageWebSearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.Science,
                polygon = MaterialShapes.Cookie4Sided,
                title = "Deep Research",
                subtitle = deepResearchSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 4, count = 8,
                onClick = { onOpen(PageDeepResearch) },
            )
            SettingsNavRow(
                icon = Icons.Default.AutoFixHigh,
                polygon = MaterialShapes.Flower,
                title = "Imagine",
                subtitle = imagineSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 5, count = 8,
                onClick = { onOpen(PageImagine) },
            )
            SettingsNavRow(
                icon = Icons.Default.Mic,
                polygon = MaterialShapes.Cookie6Sided,
                title = "Speech to text",
                subtitle = sttSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 6, count = 8,
                onClick = { onOpen(PageSpeechToText) },
            )
            SettingsNavRow(
                icon = Icons.Default.AutoAwesome,
                polygon = MaterialShapes.Pentagon,
                title = "Echo Labs",
                subtitle = echoLabsSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 7, count = 8,
                onClick = { onOpen(PageEchoLabs) },
            )
        }
    }
}

/** One hub row: shaped icon badge, title, live summary, chevron. */
@Composable
internal fun SettingsNavRow(
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
