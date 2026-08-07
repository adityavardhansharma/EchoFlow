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
internal fun EchoLabsPage(viewModel: SettingsViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val dataAgentEnabled by viewModel.dataAgentEnabled.collectAsState()
    val dataAgentEngine by viewModel.dataAgentEngine.collectAsState()
    val echoAdviserEnabled by viewModel.echoAdviserEnabled.collectAsState()
    val echoFusionEnabled by viewModel.echoFusionEnabled.collectAsState()
    val echoAgentEnabled by viewModel.echoAgentEnabled.collectAsState()
    val browserFlowEnabled by viewModel.browserFlowEnabled.collectAsState()
    val customProviderConfig by viewModel.customProviderConfig.collectAsState()
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
    val customProviderCount = listOf(
        customProviderConfig.cloudApisEnabled,
        customProviderConfig.ollamaEnabled,
        customProviderConfig.openAiCompatibleEnabled,
    ).count { it }
    val customProviderSubtitle = if (customProviderCount == 0) "Off" else "$customProviderCount provider" + if (customProviderCount == 1) "" else "s"

    SettingsPageScaffold(title = "Echo Labs", subtitle = "Experimental modes · turn on what you need", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Dataset,
                polygon = BrandShapes.avatarEnd,
                title = "Data Agent",
                subtitle = dataAgentSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 0, count = 5,
                onClick = { onOpen(PageDataAgent) },
            )
            SettingsNavRow(
                icon = Icons.Default.Psychology,
                polygon = MaterialShapes.Cookie7Sided,
                title = "Echo Adviser",
                subtitle = adviserSubtitle,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 1, count = 5,
                onClick = { onOpen(PageEchoAdviser) },
            )
            SettingsNavRow(
                icon = Icons.Default.AccountTree,
                polygon = BrandShapes.avatarEnd,
                title = "Echo Fusion",
                subtitle = fusionSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 2, count = 5,
                onClick = { onOpen(PageEchoFusion) },
            )
            SettingsNavRow(
                icon = Icons.Default.Hub,
                polygon = MaterialShapes.Pentagon,
                title = "Echo Agents",
                subtitle = agentSubtitle,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                index = 3, count = 5,
                onClick = { onOpen(PageEchoAgent) },
            )
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = MaterialShapes.Gem,
                title = "Custom API Endpoint",
                subtitle = customProviderSubtitle,
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 4, count = 5,
                onClick = { onOpen(PageCustomProvider) },
            )
        }

        val backupEnabled by viewModel.backupEnabled.collectAsState()
        Spacer(Modifier.height(Spacing.xl))
        PageSection("Privacy & data", "How EchoFlow handles your data, and on-device backup")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Lock,
                polygon = MaterialShapes.Cookie7Sided,
                title = "Privacy",
                subtitle = if (backupEnabled) "Backup on · encrypted on this phone" else "On-device · nothing on our servers",
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                index = 0, count = 1,
                onClick = { onOpen(PagePrivacy) },
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

// ── Custom API Endpoint ──────────────────────────────────────────────────────────────

@Composable
internal fun CustomApiEndpointPage(viewModel: SettingsViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val config by viewModel.customProviderConfig.collectAsState()

    SettingsPageScaffold(title = "Custom API Endpoint", subtitle = "Prerelease model providers", onBack = onBack) {
        FormCard {
            Text("Bring your own endpoint", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Add direct cloud APIs, an Ollama server, or any OpenAI-compatible endpoint. Only models you select here appear in the chat model picker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(CustomProviderSectionGap))
        PageSection("Providers", "Each provider has its own setup page")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            SettingsNavRow(
                icon = Icons.Default.Language,
                polygon = MaterialShapes.Gem,
                title = "Direct Cloud APIs",
                subtitle = endpointSubtitle(config.cloudApisEnabled, "OpenAI · Claude · Gemini · Cerebras · xAI"),
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                index = 0,
                count = 3,
                onClick = { onOpen(PageCustomProviderCloud) },
            )
            DirectBrandNavRow(
                provider = CustomModelProvider.Ollama,
                enabled = config.ollamaEnabled,
                subtitle = config.ollamaBaseUrl.ifBlank { "Local or LAN server" },
                index = 1,
                count = 3,
                onClick = { onOpen(PageCustomProviderOllama) },
            )
            DirectBrandNavRow(
                provider = CustomModelProvider.OpenAiCompatible,
                enabled = config.openAiCompatibleEnabled,
                subtitle = config.openAiBaseUrl.ifBlank { "LM Studio · Jan · vLLM" },
                index = 2,
                count = 3,
                onClick = { onOpen(PageCustomProviderCompatible) },
            )
        }
    }
}

@Composable
internal fun DirectCloudApisPage(viewModel: SettingsViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val saved by viewModel.customProviderConfig.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    val brands = listOf(
        CustomModelProvider.OpenAi to PageCustomProviderOpenAi,
        CustomModelProvider.Claude to PageCustomProviderClaude,
        CustomModelProvider.Gemini to PageCustomProviderGemini,
        CustomModelProvider.Cerebras to PageCustomProviderCerebras,
        CustomModelProvider.XAi to PageCustomProviderXAi,
    )

    SettingsPageScaffold(title = "Direct Cloud APIs", subtitle = "Brand keys and model lists", onBack = onBack) {
        EndpointMasterToggle(
            title = "Direct Cloud APIs",
            subtitle = "Use brand APIs without OpenRouter",
            enabled = draft.cloudApisEnabled,
            onToggle = { draft = draft.copy(cloudApisEnabled = it); viewModel.saveCustomProviderConfig(draft) },
        )

        AnimatedVisibility(visible = draft.cloudApisEnabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Brands", "Open a brand to add its key and choose models")
                Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                    brands.forEachIndexed { index, (provider, page) ->
                        DirectBrandNavRow(
                            provider = provider,
                            enabled = directProviderEnabled(draft, provider),
                            subtitle = directProviderSummary(draft, provider),
                            index = index,
                            count = brands.size,
                            onClick = { onOpen(page) },
                        )
                    }
                }

                Spacer(Modifier.height(CustomProviderSectionGap))
                FormCard {
                    Text("Attachments", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Images and PDFs stay on for direct cloud models. Keep only models you trust selected in each brand page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = !draft.cloudApisEnabled, enter = sectionEnter(), exit = sectionExit()) {
            EndpointOffState("Turn on Direct Cloud APIs to configure brand endpoints.")
        }
    }
}
