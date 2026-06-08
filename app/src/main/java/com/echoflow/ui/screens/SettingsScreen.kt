@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import android.os.Build
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

private data class Accent(val id: String, val label: String, val swatch: Color)

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
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsState()
    val customModels by viewModel.customModels.collectAsState()

    var keyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }

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
                        Spacer(Modifier.height(Spacing.l))
                        // ── Web Search Toggle ────────────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Language, null,
                                tint = if (webSearchEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(Spacing.base))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Web search",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (webSearchEnabled) "Enabled — Auto" else "Allow the model to search the web",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(Spacing.s))
                            Switch(
                                checked = webSearchEnabled,
                                onCheckedChange = viewModel::saveWebSearchEnabled,
                            )
                        }

                        if (webSearchEnabled) {
                            Spacer(Modifier.height(Spacing.m))
                            HorizontalDivider()
                            Spacer(Modifier.height(Spacing.m))
                            Text("Auto (recommended)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(Spacing.s))
                            Text(
                                "Select \"Auto\" in your OpenRouter web search settings. " +
                                "The model decides if and when to search based on your prompt. " +
                                "It uses the AI model's native search feature (OpenAI, Anthropic, xAI) " +
                                "when available, otherwise defaults to Exa at \$0.005 per search request.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.m))
                            Text("Pricing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(Spacing.s))
                            Text(
                                "• Native search (OpenAI, Anthropic, xAI) — passed through at provider rates\n" +
                                "• Exa — \$0.005/request (includes up to 10 results; +\$0.001/extra result)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
