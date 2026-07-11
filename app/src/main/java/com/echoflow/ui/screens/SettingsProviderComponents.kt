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

internal data class DirectProviderBrand(
    val title: String,
    val subtitle: String,
    val keyPlaceholder: String,
    val modelPlaceholder: String,
    val logoRes: Int,
    val color: Color,
)

internal fun directProviderBrand(provider: CustomModelProvider): DirectProviderBrand = when (provider) {
    CustomModelProvider.OpenAi -> DirectProviderBrand("OpenAI", "Direct OpenAI API", "sk-...", "gpt-4.1-mini", R.drawable.logo_openai, Color(0xFF10A37F))
    CustomModelProvider.Claude -> DirectProviderBrand("Claude", "Direct Anthropic API", "sk-ant-...", "claude-sonnet-4-5", R.drawable.logo_claude, Color(0xFFD97757))
    CustomModelProvider.Gemini -> DirectProviderBrand("Gemini", "Direct Google API", "AIza...", "gemini-2.5-flash", R.drawable.logo_gemini, Color(0xFF4285F4))
    CustomModelProvider.Cerebras -> DirectProviderBrand("Cerebras", "Direct Cerebras API", "csk-...", "llama3.3-70b", R.drawable.logo_cerebras, Color(0xFFF15A29))
    CustomModelProvider.Ollama -> DirectProviderBrand("Ollama API", "Use a local or LAN Ollama server", "Optional for local servers", "llama3.1", R.drawable.logo_ollama, Color(0xFF2B2B2B))
    CustomModelProvider.OpenAiCompatible -> DirectProviderBrand("OpenAI-Compatible API", "Use LM Studio, Jan, vLLM or similar", "Optional for local servers", "local-model", R.drawable.logo_compatible, Color(0xFF5B6472))
}

@Composable
internal fun DirectBrandNavRow(
    provider: CustomModelProvider,
    enabled: Boolean,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val brand = directProviderBrand(provider)
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderLogo(brand = brand, size = 46.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        brand.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Surface(
                        shape = CircleShape,
                        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            if (enabled) "On" else "Off",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BrandEndpointToggle(provider: CustomModelProvider, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val brand = directProviderBrand(provider)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(brand = brand, size = 52.dp, animated = enabled)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    brand.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    brand.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * The brand chip used across every custom-endpoint page: the provider's monochrome mark on a
 * brand-colored polygon. In list rows it stays still; as a page hero ([animated]) it gently
 * morphs Cookie4 → Cookie9 and breathes, the signature M3 Expressive "alive" treatment.
 */
@Composable
internal fun ProviderLogo(brand: DirectProviderBrand, size: Dp = 48.dp, animated: Boolean = false) {
    if (animated) {
        val morph = rememberMorph(MaterialShapes.Cookie4Sided, MaterialShapes.Cookie9Sided)
        val progress by rememberMorphProgress(durationMillis = 5200)
        BrandChip(brand, size, MorphPolygonShape(morph, progress), 1f + 0.045f * progress)
    } else {
        BrandChip(brand, size, RoundedPolygonShape(MaterialShapes.Cookie4Sided), 1f)
    }
}

@Composable
internal fun BrandChip(brand: DirectProviderBrand, size: Dp, shape: Shape, scale: Float) {
    Surface(shape = shape, color = brand.color, modifier = Modifier.size(size).scale(scale)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(brand.logoRes),
                contentDescription = brand.title,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
internal fun DirectBrandActions(
    fetchLoading: Boolean,
    fetchBlocked: Boolean,
    hasManual: Boolean,
    onFetch: () -> Unit,
    onManual: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onFetch,
            enabled = !fetchLoading && !fetchBlocked,
            shape = CircleShape,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            if (fetchLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Fetch", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FilledTonalButton(
            onClick = onManual,
            shape = CircleShape,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(if (hasManual) "Edit" else "Manual", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun EndpointMasterToggle(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
internal fun EndpointConnectionCard(
    title: String,
    baseUrl: String,
    apiKey: String?,
    placeholder: String,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }
    FormCard {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.s))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = { Text("Base URL") },
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        if (apiKey != null) {
            Spacer(Modifier.height(Spacing.s))
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKey,
                label = { Text("API key") },
                placeholder = { Text("Optional for local servers") },
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
        }
        Spacer(Modifier.height(Spacing.m))
        Text(
            "Plain HTTP is allowed for localhost and private LAN addresses only. Use HTTPS for internet-facing servers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun EndpointActionsRow(fetchLoading: Boolean, testLoading: Boolean, onFetch: () -> Unit, onTest: () -> Unit, onManual: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onFetch, enabled = !fetchLoading, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (fetchLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Fetch")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = onManual, shape = CircleShape, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Manual")
            }
            FilledTonalButton(onClick = onTest, enabled = !testLoading, shape = CircleShape, modifier = Modifier.weight(1f).height(52.dp)) {
                if (testLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Test")
                }
            }
        }
    }
}

@Composable
internal fun EndpointCapabilityToggles(images: Boolean, pdfs: Boolean, onImages: (Boolean) -> Unit, onPdfs: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        CapabilityRow("Images", "Allow image attachments for compatible vision models", images, 0, 2, onImages)
        CapabilityRow("PDFs", "Allow PDF attachments only when your endpoint supports them", pdfs, 1, 2, onPdfs)
    }
}

/**
 * Per-endpoint switch for native tool calling (the model runs web_search itself). Off by default
 * because it only works on models that actually support tool/function calls — when off, the app
 * falls back to running one search and pasting the results in.
 */
@Composable
internal fun EndpointToolCallingToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tool calling", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Only turn on if your selected model supports tool/function calls. The model will run web searches itself when it needs to. When off, the app searches once and feeds the results in. Needs a web search provider set up in Settings → Web search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
internal fun CapabilityRow(title: String, subtitle: String, checked: Boolean, index: Int, count: Int, onChange: (Boolean) -> Unit) {
    Surface(shape = groupedItemShape(index, count), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
internal fun ProviderModelChips(
    availableModels: String,
    selectedModels: String,
    emptyText: String,
    onSelectedModels: (String) -> Unit,
) = ProviderModelPicker(availableModels, selectedModels, emptyText, onSelectedModels)

@Composable
internal fun ProviderModelPicker(
    availableModels: String,
    selectedModels: String,
    emptyText: String,
    onSelectedModels: (String) -> Unit,
) {
    val available = remember(availableModels, selectedModels) {
        (availableModels.lineSequence() + selectedModels.lineSequence())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
    val selected = selectedModels.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    if (available.isEmpty()) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.base),
            )
        }
        return
    }
    Text("Models in chat", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.s))
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        available.forEachIndexed { index, model ->
            val checked = model in selected
            Surface(
                onClick = {
                    val next = if (checked) selected - model else selected + model
                    onSelectedModels(next.joinToString("\n"))
                },
                shape = groupedItemShape(index, available.size),
                color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            model,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (checked) "Shown in chat" else "Hidden from chat",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(Spacing.s))
                    if (checked) {
                        Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        FilledTonalIconButton(onClick = {
                            val next = selected + model
                            onSelectedModels(next.joinToString("\n"))
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, "Show $model in chat", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProviderStatusMessage(message: String?) {
    if (message == null) return
    Spacer(Modifier.height(Spacing.s))
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = if (message.contains("Fetched", ignoreCase = true) || message.contains("works", ignoreCase = true)) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
internal fun ManualModelDialog(title: String, initial: String, placeholder: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Add, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun EndpointOffState(message: String) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.base),
        )
    }
}

internal fun endpointSubtitle(enabled: Boolean, detail: String): String =
    if (!enabled) "Off" else detail

internal fun directProviderEnabled(config: CustomProviderConfig, provider: CustomModelProvider): Boolean = when (provider) {
    CustomModelProvider.OpenAi -> config.openAiEnabled
    CustomModelProvider.Claude -> config.claudeEnabled
    CustomModelProvider.Gemini -> config.geminiEnabled
    CustomModelProvider.Cerebras -> config.cerebrasEnabled
    else -> false
}

internal fun setDirectProviderEnabled(config: CustomProviderConfig, provider: CustomModelProvider, enabled: Boolean): CustomProviderConfig = when (provider) {
    CustomModelProvider.OpenAi -> config.copy(openAiEnabled = enabled)
    CustomModelProvider.Claude -> config.copy(claudeEnabled = enabled)
    CustomModelProvider.Gemini -> config.copy(geminiEnabled = enabled)
    CustomModelProvider.Cerebras -> config.copy(cerebrasEnabled = enabled)
    else -> config
}

internal fun directProviderApiKey(config: CustomProviderConfig, provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.OpenAi -> config.openAiApiKey
    CustomModelProvider.Claude -> config.claudeApiKey
    CustomModelProvider.Gemini -> config.geminiApiKey
    CustomModelProvider.Cerebras -> config.cerebrasApiKey
    else -> ""
}

internal fun setDirectProviderApiKey(config: CustomProviderConfig, provider: CustomModelProvider, value: String): CustomProviderConfig = when (provider) {
    CustomModelProvider.OpenAi -> config.copy(openAiApiKey = value)
    CustomModelProvider.Claude -> config.copy(claudeApiKey = value)
    CustomModelProvider.Gemini -> config.copy(geminiApiKey = value)
    CustomModelProvider.Cerebras -> config.copy(cerebrasApiKey = value)
    else -> config
}

internal fun directProviderManualModel(config: CustomProviderConfig, provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.OpenAi -> config.openAiModel
    CustomModelProvider.Claude -> config.claudeModel
    CustomModelProvider.Gemini -> config.geminiModel
    CustomModelProvider.Cerebras -> config.cerebrasModel
    else -> ""
}

internal fun setDirectProviderManualModel(config: CustomProviderConfig, provider: CustomModelProvider, value: String): CustomProviderConfig = when (provider) {
    CustomModelProvider.OpenAi -> config.copy(openAiModel = value)
    CustomModelProvider.Claude -> config.copy(claudeModel = value)
    CustomModelProvider.Gemini -> config.copy(geminiModel = value)
    CustomModelProvider.Cerebras -> config.copy(cerebrasModel = value)
    else -> config
}

internal fun directProviderAvailableModels(config: CustomProviderConfig, provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.OpenAi -> config.openAiModels
    CustomModelProvider.Claude -> config.claudeModels
    CustomModelProvider.Gemini -> config.geminiModels
    CustomModelProvider.Cerebras -> config.cerebrasModels
    else -> ""
}

internal fun directProviderSelectedModels(config: CustomProviderConfig, provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.OpenAi -> config.openAiSelectedModels
    CustomModelProvider.Claude -> config.claudeSelectedModels
    CustomModelProvider.Gemini -> config.geminiSelectedModels
    CustomModelProvider.Cerebras -> config.cerebrasSelectedModels
    else -> ""
}

internal fun setDirectProviderSelectedModels(config: CustomProviderConfig, provider: CustomModelProvider, value: String): CustomProviderConfig = when (provider) {
    CustomModelProvider.OpenAi -> config.copy(openAiSelectedModels = value)
    CustomModelProvider.Claude -> config.copy(claudeSelectedModels = value)
    CustomModelProvider.Gemini -> config.copy(geminiSelectedModels = value)
    CustomModelProvider.Cerebras -> config.copy(cerebrasSelectedModels = value)
    else -> config
}

internal fun directProviderSummary(config: CustomProviderConfig, provider: CustomModelProvider): String {
    if (!directProviderEnabled(config, provider)) return "Off"
    val count = (
        listOf(directProviderManualModel(config, provider).trim()).filter { it.isNotEmpty() } +
            directProviderSelectedModels(config, provider).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        ).distinct().count()
    return if (count == 0) "On · no chat models" else "On · $count chat model" + if (count == 1) "" else "s"
}

internal fun directProviderAttachmentText(provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.Cerebras -> "Images are available for Cerebras Gemma models. GPT OSS and GLM models are text-only; PDFs are off."
    else -> "Image and PDF attachments are enabled for selected ${providerLabel(provider)} models."
}

internal fun providerLabel(provider: CustomModelProvider): String = when (provider) {
    CustomModelProvider.OpenAi -> "OpenAI"
    CustomModelProvider.Claude -> "Claude"
    CustomModelProvider.Gemini -> "Gemini"
    CustomModelProvider.Cerebras -> "Cerebras"
    CustomModelProvider.Ollama -> "Ollama"
    CustomModelProvider.OpenAiCompatible -> "OpenAI-compatible"
}

// ── Data Agent ────────────────────────────────────────────────────────────────────────

