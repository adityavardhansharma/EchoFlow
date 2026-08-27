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
import com.echoflow.data.ClientSearchProviders
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
internal fun WebSearchPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val webSearchProvider by viewModel.webSearchProvider.collectAsState()
    val webSearchScope by viewModel.webSearchScope.collectAsState()
    val exaKey by viewModel.exaApiKey.collectAsState()
    val parallelKey by viewModel.parallelApiKey.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()
    val monidKey by viewModel.monidApiKey.collectAsState()

    val savedSearchKey = when (webSearchProvider) {
        ClientSearchProviders.EXA -> exaKey
        ClientSearchProviders.PARALLEL -> parallelKey
        ClientSearchProviders.FIRECRAWL -> firecrawlKey
        ClientSearchProviders.MONID -> monidKey
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
                        "OpenRouter search only works with cloud models. Local models need Exa, Parallel, Firecrawl or Monid.",
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

        AnimatedVisibility(visible = webSearchProvider in ClientSearchProviders.asSet, enter = sectionEnter(), exit = sectionExit()) {
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
internal fun ProviderRow(
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
        MaterialShapes.Cookie4Sided,
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
internal fun DeepResearchPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val selectedEngineId by viewModel.deepResearchModelId.collectAsState()
    val searchProvider by viewModel.deepResearchSearchProvider.collectAsState()
    val maxSearches by viewModel.deepResearchMaxSearches.collectAsState()
    val maxSources by viewModel.deepResearchMaxSources.collectAsState()
    val drModels by viewModel.deepResearchModels.collectAsState()
    val exaKey by viewModel.exaApiKey.collectAsState()
    val parallelKey by viewModel.parallelApiKey.collectAsState()
    val firecrawlKey by viewModel.firecrawlApiKey.collectAsState()
    val monidKey by viewModel.monidApiKey.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orResults by viewModel.orModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }

    fun keyFor(provider: String): String = when (provider) {
        ClientSearchProviders.EXA -> exaKey
        ClientSearchProviders.PARALLEL -> parallelKey
        ClientSearchProviders.FIRECRAWL -> firecrawlKey
        ClientSearchProviders.MONID -> monidKey
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
                    "provider (including Monid), and writes a cited report.\n\n" +
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
                        "Nothing available yet.\nAdd an Exa, Parallel, Firecrawl or Monid key in Web search,\nor add a research model below.",
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
            options = listOf(
                "auto" to "Auto",
                ClientSearchProviders.EXA to "Exa",
                ClientSearchProviders.PARALLEL to "Parallel",
                ClientSearchProviders.FIRECRAWL to "Firecrawl",
                ClientSearchProviders.MONID to "Monid",
            ),
            selected = searchProvider,
            onSelect = viewModel::saveDeepResearchSearchProvider,
        )
        Spacer(Modifier.height(Spacing.s))
        // Per-selection feedback: show whether the *chosen* provider actually has a key.
        val autoResolved = ClientSearchProviders.ids.firstOrNull { keyFor(it).isNotBlank() }
        val searchMissing = if (searchProvider == "auto") autoResolved == null else keyFor(searchProvider).isBlank()
        val searchStatus = when {
            searchProvider == "auto" && autoResolved == null -> "No search keys yet — add one in Settings → Web search"
            searchProvider == "auto" -> "Using ${ClientSearchProviders.displayName(autoResolved!!)} — first key found"
            searchMissing -> "No ${ClientSearchProviders.displayName(searchProvider)} key — add it in Settings → Web search"
            else -> "${ClientSearchProviders.displayName(searchProvider)} key found"
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
internal fun DrEngineSelectRow(
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
