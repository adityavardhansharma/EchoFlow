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
internal fun DirectCloudBrandPage(viewModel: SettingsViewModel, provider: CustomModelProvider, onBack: () -> Unit) {
    val saved by viewModel.customProviderConfig.collectAsState()
    val fetchLoading by viewModel.customProviderFetchLoading.collectAsState()
    val fetchMessage by viewModel.customProviderFetchMessage.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    var showManual by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    val brand = directProviderBrand(provider)
    val enabled = directProviderEnabled(draft, provider)
    val apiKey = directProviderApiKey(draft, provider)
    val manualModel = directProviderManualModel(draft, provider)
    val availableModels = directProviderAvailableModels(draft, provider)
    val selectedModels = directProviderSelectedModels(draft, provider)

    SettingsPageScaffold(title = brand.title, subtitle = brand.subtitle, onBack = onBack) {
        BrandEndpointToggle(
            provider = provider,
            enabled = enabled,
            onToggle = {
                draft = setDirectProviderEnabled(if (it) draft.copy(cloudApisEnabled = true) else draft, provider, it)
                viewModel.saveCustomProviderConfig(draft)
            },
        )

        AnimatedVisibility(visible = enabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("API key")
                FormCard {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { draft = setDirectProviderApiKey(draft, provider, it) },
                        placeholder = { Text(brand.keyPlaceholder) },
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
                    if (directProviderApiKey(saved, provider).isNotBlank()) {
                        Spacer(Modifier.height(Spacing.s))
                        SavedKeyBadge("A key is saved on this device")
                    }
                    Spacer(Modifier.height(Spacing.m))
                    Button(
                        onClick = { viewModel.saveCustomProviderConfig(draft) },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Save key") }
                }

                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Models", "Choose what appears in the chat model picker")
                DirectBrandActions(
                    fetchLoading = fetchLoading == provider,
                    fetchBlocked = fetchLoading != null && fetchLoading != provider,
                    hasManual = manualModel.isNotBlank(),
                    onFetch = {
                        viewModel.saveCustomProviderConfig(draft)
                        viewModel.fetchCustomProviderModels(provider, draft)
                    },
                    onManual = { showManual = true },
                )
                Spacer(Modifier.height(Spacing.m))
                ProviderModelPicker(
                    availableModels = availableModels,
                    selectedModels = selectedModels,
                    emptyText = "No models yet.",
                    onSelectedModels = {
                        draft = setDirectProviderSelectedModels(draft, provider, it)
                        viewModel.saveCustomProviderConfig(draft)
                    },
                )
                ProviderStatusMessage(fetchMessage)

                Spacer(Modifier.height(CustomProviderSectionGap))
                FormCard {
                    Text("Attachments", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        directProviderAttachmentText(provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = !enabled, enter = sectionEnter(), exit = sectionExit()) {
            EndpointOffState("Turn on ${brand.title} to add a key and models.")
        }
    }

    if (showManual) {
        ManualModelDialog(
            title = "Manual ${brand.title} model",
            initial = manualModel,
            placeholder = brand.modelPlaceholder,
            onDismiss = { showManual = false },
            onConfirm = {
                draft = setDirectProviderManualModel(draft, provider, it)
                viewModel.saveCustomProviderConfig(draft)
                showManual = false
            },
        )
    }
}

@Composable
internal fun OllamaEndpointPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val saved by viewModel.customProviderConfig.collectAsState()
    val fetchLoading by viewModel.customProviderFetchLoading.collectAsState()
    val testLoading by viewModel.customProviderTestLoading.collectAsState()
    val fetchMessage by viewModel.customProviderFetchMessage.collectAsState()
    val testMessage by viewModel.customProviderTestMessage.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    var showManual by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Ollama API", subtitle = "Local or LAN models", onBack = onBack) {
        BrandEndpointToggle(
            provider = CustomModelProvider.Ollama,
            enabled = draft.ollamaEnabled,
            onToggle = { draft = draft.copy(ollamaEnabled = it); viewModel.saveCustomProviderConfig(draft) },
        )
        AnimatedVisibility(visible = draft.ollamaEnabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(CustomProviderSectionGap))
                EndpointConnectionCard(
                    title = "Server",
                    baseUrl = draft.ollamaBaseUrl,
                    apiKey = null,
                    placeholder = "http://192.168.1.50:11434",
                    onBaseUrl = { draft = draft.copy(ollamaBaseUrl = it) },
                    onApiKey = {},
                )
                Spacer(Modifier.height(Spacing.m))
                Button(
                    onClick = { viewModel.saveCustomProviderConfig(draft) },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save") }
                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Models")
                EndpointActionsRow(
                    fetchLoading = fetchLoading == CustomModelProvider.Ollama,
                    testLoading = testLoading,
                    onFetch = {
                        viewModel.saveCustomProviderConfig(draft)
                        viewModel.fetchCustomProviderModels(CustomModelProvider.Ollama, draft)
                    },
                    onTest = {
                        viewModel.saveCustomProviderConfig(draft)
                        viewModel.testCustomProvider(draft.copy(ollamaEnabled = true, openAiCompatibleEnabled = false))
                    },
                    onManual = { showManual = true },
                )
                Spacer(Modifier.height(Spacing.m))
                ProviderModelChips(
                    availableModels = draft.ollamaModels,
                    selectedModels = draft.ollamaSelectedModels,
                    emptyText = "No models yet.",
                    onSelectedModels = { draft = draft.copy(ollamaSelectedModels = it); viewModel.saveCustomProviderConfig(draft) },
                )
                ProviderStatusMessage(fetchMessage)
                ProviderStatusMessage(testMessage)

                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Capabilities")
                EndpointCapabilityToggles(
                    images = draft.ollamaImagesEnabled,
                    pdfs = draft.ollamaPdfsEnabled,
                    onImages = { draft = draft.copy(ollamaImagesEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                    onPdfs = { draft = draft.copy(ollamaPdfsEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                )

                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Tool calling", "Let the model run web searches itself")
                EndpointToolCallingToggle(
                    enabled = draft.ollamaToolCallingEnabled,
                    onToggle = { draft = draft.copy(ollamaToolCallingEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                )
            }
        }
        AnimatedVisibility(visible = !draft.ollamaEnabled, enter = sectionEnter(), exit = sectionExit()) {
            EndpointOffState("Turn on Ollama API to configure the server.")
        }
    }

    if (showManual) {
        ManualModelDialog(
            title = "Manual Ollama model",
            initial = draft.ollamaModel,
            placeholder = "llama3.1",
            onDismiss = { showManual = false },
            onConfirm = {
                draft = draft.copy(ollamaModel = it)
                viewModel.saveCustomProviderConfig(draft)
                showManual = false
            },
        )
    }
}

@Composable
internal fun OpenAiCompatibleEndpointPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val saved by viewModel.customProviderConfig.collectAsState()
    val fetchLoading by viewModel.customProviderFetchLoading.collectAsState()
    val testLoading by viewModel.customProviderTestLoading.collectAsState()
    val fetchMessage by viewModel.customProviderFetchMessage.collectAsState()
    val testMessage by viewModel.customProviderTestMessage.collectAsState()
    var draft by remember(saved) { mutableStateOf(saved) }
    var showManual by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "OpenAI-Compatible API", subtitle = "LM Studio, Jan, vLLM and more", onBack = onBack) {
        BrandEndpointToggle(
            provider = CustomModelProvider.OpenAiCompatible,
            enabled = draft.openAiCompatibleEnabled,
            onToggle = { draft = draft.copy(openAiCompatibleEnabled = it); viewModel.saveCustomProviderConfig(draft) },
        )
        AnimatedVisibility(visible = draft.openAiCompatibleEnabled, enter = sectionEnter(), exit = sectionExit()) {
            Column {
                Spacer(Modifier.height(CustomProviderSectionGap))
                EndpointConnectionCard(
                    title = "Endpoint",
                    baseUrl = draft.openAiBaseUrl,
                    apiKey = draft.openAiCompatibleApiKey,
                    placeholder = "http://192.168.1.50:1234/v1",
                    onBaseUrl = { draft = draft.copy(openAiBaseUrl = it) },
                    onApiKey = { draft = draft.copy(openAiCompatibleApiKey = it) },
                )
                Spacer(Modifier.height(Spacing.m))
                Button(
                    onClick = { viewModel.saveCustomProviderConfig(draft) },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save") }
                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Models")
                EndpointActionsRow(
                    fetchLoading = fetchLoading == CustomModelProvider.OpenAiCompatible,
                    testLoading = testLoading,
                    onFetch = {
                        viewModel.saveCustomProviderConfig(draft)
                        viewModel.fetchCustomProviderModels(CustomModelProvider.OpenAiCompatible, draft)
                    },
                    onTest = {
                        viewModel.saveCustomProviderConfig(draft)
                        viewModel.testCustomProvider(draft.copy(openAiCompatibleEnabled = true, ollamaEnabled = false))
                    },
                    onManual = { showManual = true },
                )
                Spacer(Modifier.height(Spacing.m))
                ProviderModelChips(
                    availableModels = draft.openAiCompatibleModels,
                    selectedModels = draft.openAiCompatibleSelectedModels,
                    emptyText = "No models yet.",
                    onSelectedModels = { draft = draft.copy(openAiCompatibleSelectedModels = it); viewModel.saveCustomProviderConfig(draft) },
                )
                ProviderStatusMessage(fetchMessage)
                ProviderStatusMessage(testMessage)

                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Capabilities")
                EndpointCapabilityToggles(
                    images = draft.openAiCompatibleImagesEnabled,
                    pdfs = draft.openAiCompatiblePdfsEnabled,
                    onImages = { draft = draft.copy(openAiCompatibleImagesEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                    onPdfs = { draft = draft.copy(openAiCompatiblePdfsEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                )

                Spacer(Modifier.height(CustomProviderSectionGap))
                PageSection("Tool calling", "Let the model run web searches itself")
                EndpointToolCallingToggle(
                    enabled = draft.openAiCompatibleToolCallingEnabled,
                    onToggle = { draft = draft.copy(openAiCompatibleToolCallingEnabled = it); viewModel.saveCustomProviderConfig(draft) },
                )
            }
        }
        AnimatedVisibility(visible = !draft.openAiCompatibleEnabled, enter = sectionEnter(), exit = sectionExit()) {
            EndpointOffState("Turn on OpenAI-Compatible API to configure the endpoint.")
        }
    }

    if (showManual) {
        ManualModelDialog(
            title = "Manual compatible model",
            initial = draft.openAiCompatibleModel,
            placeholder = "local-model",
            onDismiss = { showManual = false },
            onConfirm = {
                draft = draft.copy(openAiCompatibleModel = it)
                viewModel.saveCustomProviderConfig(draft)
                showManual = false
            },
        )
    }
}

