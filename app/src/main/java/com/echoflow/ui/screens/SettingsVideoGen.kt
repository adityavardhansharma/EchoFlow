@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.OpenRouterVideoModelInfo
import com.echoflow.data.SettingsRepository
import com.echoflow.data.VideoRequestPolicy
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/**
 * Video generation settings. Cloud only — OpenRouter is the sole route, because video needs
 * a datacentre GPU for minutes at a time and there is no on-device equivalent to offer.
 *
 * The page deliberately has no length control: the model decides how long each clip runs.
 * What the user does choose is framing (aspect ratio, resolution) and audio, and all three
 * are reconciled against the selected model's declared capabilities — an unsupported value
 * would be a hard 400 from OpenRouter, so unsupported chips are disabled rather than shown
 * as choices that silently fail.
 */
@Composable
internal fun VideoGenSection(viewModel: SettingsViewModel) {
    val videoModels by viewModel.videoModels.collectAsState()
    val selectedId by viewModel.videoGenModelId.collectAsState()
    val aspectRatio by viewModel.videoAspectRatio.collectAsState()
    val resolution by viewModel.videoResolution.collectAsState()
    val audioEnabled by viewModel.videoAudioEnabled.collectAsState()
    val capabilities by viewModel.selectedVideoModelCapabilities.collectAsState()

    val query by viewModel.videoModelQuery.collectAsState()
    val results by viewModel.videoModelResults.collectAsState()
    val loading by viewModel.videoDirectoryLoading.collectAsState()
    val error by viewModel.videoDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }

    // Capabilities drive the whole page, so fetch the directory on open rather than waiting
    // for the user to go looking for the search sheet.
    LaunchedEffect(Unit) { viewModel.loadVideoModelDirectory() }

    // The shipped default is always offered, ahead of whatever the user has added.
    val entries = remember(videoModels) {
        val default = SettingsRepository.DEFAULT_VIDEO_MODEL_ID to SettingsRepository.DEFAULT_VIDEO_MODEL_NAME
        listOf(default) + videoModels.filter { it.id != default.first }.map { it.id to it.name }
    }

    Column {
        PageSection("How it works", null)
        Text(
            "Turn on “Create video” from the + menu in chat, then describe a clip. Generation " +
                "runs on OpenRouter and takes a few minutes — you can leave the chat or the app " +
                "and it keeps going. The model decides how long the clip should be.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Default model", "The model every video turn uses")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            entries.forEachIndexed { index, (id, name) ->
                VideoModelRow(
                    id = id,
                    name = name,
                    index = index,
                    count = entries.size,
                    selected = id == selectedId,
                    priceHint = results.firstOrNull { it.id == id }?.priceHint(resolution, audioEnabled),
                    onSelect = { viewModel.saveVideoGenModel(id) },
                    onDelete = { viewModel.deleteVideoModel(id) }
                        .takeIf { id != SettingsRepository.DEFAULT_VIDEO_MODEL_ID },
                )
            }
        }

        Spacer(Modifier.height(Spacing.l))
        Button(
            onClick = {
                showDirectory = true
                viewModel.loadVideoModelDirectory()
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Browse video models")
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            "Every model OpenRouter can generate video with, with what it costs per second.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Shape", "How the clip is framed")
        CapabilityChipRow(
            values = VideoRequestPolicy.aspectRatioOffering(capabilities?.aspectRatios),
            selected = aspectRatio,
            supported = capabilities?.aspectRatios,
            onSelect = viewModel::saveVideoAspectRatio,
        )

        Spacer(Modifier.height(Spacing.l))
        PageSection("Resolution", "Higher costs more per second")
        CapabilityChipRow(
            values = VideoRequestPolicy.resolutionOffering(capabilities?.resolutions),
            selected = resolution,
            supported = capabilities?.resolutions,
            onSelect = viewModel::saveVideoResolution,
        )

        Spacer(Modifier.height(Spacing.l))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Generate audio",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        when {
                            capabilities == null -> "Roughly doubles the cost per second."
                            capabilities?.supportsAudio == true ->
                                "Adds dialogue and ambience. Roughly doubles the cost per second."
                            else -> "The selected model always generates silent clips."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = audioEnabled && capabilities?.supportsAudio != false,
                    onCheckedChange = viewModel::saveVideoAudioEnabled,
                    enabled = capabilities?.supportsAudio != false,
                )
            }
        }

        Spacer(Modifier.height(Spacing.l))
        Text(
            "Clip length is always the model's own call — EchoFlow never asks for a duration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDirectory) {
        VideoModelDirectorySheet(
            query = query,
            results = results,
            loading = loading,
            error = error,
            resolution = resolution,
            audioEnabled = audioEnabled,
            addedIds = videoModels.map { it.id }.toSet() + SettingsRepository.DEFAULT_VIDEO_MODEL_ID,
            onQueryChange = viewModel::updateVideoModelQuery,
            onRetry = viewModel::loadVideoModelDirectory,
            onAdd = { info -> viewModel.addVideoModel(info.id, info.name.substringAfter(": ")) },
            onRemove = viewModel::deleteVideoModel,
            onDismiss = { showDirectory = false },
        )
    }
}

/**
 * A row of framing choices where a value the selected model does not offer is disabled, not
 * hidden — hiding it would make the page silently rearrange every time the model changes.
 * A null [supported] means the directory has not loaded yet, so nothing is disabled.
 */
@Composable
private fun CapabilityChipRow(
    values: List<String>,
    selected: String,
    supported: List<String>?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        values.forEach { value ->
            // An empty capability set means the model takes framing from its input instead of
            // a parameter, so every choice stays live and is simply not sent.
            val enabled = supported.isNullOrEmpty() || value in supported
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(value) },
            )
        }
    }
}

@Composable
private fun VideoModelRow(
    id: String,
    name: String,
    index: Int,
    count: Int,
    selected: Boolean,
    priceHint: String?,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Surface(
        onClick = onSelect,
        shape = groupedItemShape(index, count),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedPolygonShape(MaterialShapes.Cookie6Sided))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Movie, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    priceHint ?: id,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
            } else if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Live video-model directory: search-as-you-type over OpenRouter's video endpoint, showing
 * the capabilities and per-second price that actually decide the choice.
 */
@Composable
internal fun VideoModelDirectorySheet(
    query: String,
    results: List<OpenRouterVideoModelInfo>,
    loading: Boolean,
    error: String?,
    resolution: String,
    audioEnabled: Boolean,
    addedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAdd: (OpenRouterVideoModelInfo) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Full height from the moment it opens — the sheet must not jump taller when results
        // stream in.
        modifier = Modifier.fillMaxHeight(0.94f),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.base)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text("Video models", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
                placeholder = { Text("Veo, Sora, Kling, Seedance…") },
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
                            "Loading video models…",
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
                            "No video model matches “$query”.",
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
                            VideoDirectoryRow(
                                info = info,
                                index = index,
                                count = results.size,
                                added = info.id in addedIds,
                                resolution = resolution,
                                audioEnabled = audioEnabled,
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
private fun VideoDirectoryRow(
    info: OpenRouterVideoModelInfo,
    index: Int,
    count: Int,
    added: Boolean,
    resolution: String,
    audioEnabled: Boolean,
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    info.priceHint(resolution, audioEnabled)?.let { InfoPill(it, added) }
                    info.resolutions.takeIf { it.isNotEmpty() }?.let { InfoPill(it.joinToString(" · "), added) }
                    if (info.supportsAudio) InfoPill("Audio", added)
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
