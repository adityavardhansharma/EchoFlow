@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.LocalImageCatalogEntry
import com.echoflow.data.LocalImageDownloadState
import com.echoflow.data.LocalImageModel
import com.echoflow.data.LocalImageModelCatalog
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

/**
 * Image generation settings. A top engine selector splits the page: OpenRouter keeps the
 * existing cloud model list + directory search; On device offers four curated models with the
 * same download experience as local LLMs — no search, no import, no tokens.
 */
@Composable
internal fun ImageGenPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val engine by viewModel.imageGenEngine.collectAsState()

    SettingsPageScaffold(title = "Image generation", subtitle = "Create & edit images in chat", onBack = onBack) {
        ConnectedToggleRow(
            options = listOf(
                SettingsRepository.IMAGE_ENGINE_OPENROUTER to "OpenRouter",
                SettingsRepository.IMAGE_ENGINE_LOCAL to "On device",
            ),
            selected = engine,
            onSelect = viewModel::saveImageGenEngine,
            icons = listOf(Icons.Default.CloudQueue, Icons.Default.PhoneAndroid),
        )
        Spacer(Modifier.height(Spacing.xl))

        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = engine,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "imageEngineSections",
        ) { current ->
            if (current == SettingsRepository.IMAGE_ENGINE_LOCAL) {
                LocalImageEngineSection(viewModel)
            } else {
                OpenRouterImageEngineSection(viewModel)
            }
        }
    }
}

// ── OpenRouter engine (unchanged behaviour) ─────────────────────────────────────────────

@Composable
private fun OpenRouterImageEngineSection(viewModel: SettingsViewModel) {
    val imageModels by viewModel.imageModels.collectAsState()
    val selectedId by viewModel.imageGenModelId.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orImageResults by viewModel.orImageModelResults.collectAsState()
    val orLoading by viewModel.orDirectoryLoading.collectAsState()
    val orError by viewModel.orDirectoryError.collectAsState()

    var showDirectory by remember { mutableStateOf(false) }

    // The shipped default is always offered, ahead of whatever the user has added.
    val entries = remember(imageModels) {
        val default = SettingsRepository.DEFAULT_IMAGE_MODEL_ID to "Gemini 2.5 Flash Image"
        listOf(default) + imageModels
            .filter { it.id != default.first }
            .map { it.id to it.name }
    }

    Column {
        PageSection("How it works", null)
        Text(
            "Turn on “Create image” from the + menu in chat, then describe an image. " +
                "Follow-up messages edit the latest image — “make the sky purple” changes " +
                "just that. Runs on your OpenRouter key; roughly a few cents per image.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Default model", "The model every image turn uses")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            entries.forEachIndexed { index, (id, name) ->
                val selected = id == selectedId
                Surface(
                    onClick = { viewModel.saveImageGenModel(id) },
                    shape = groupedItemShape(index, entries.size),
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
                                .clip(RoundedPolygonShape(MaterialShapes.Flower))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiaryContainer
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Brush, null, Modifier.size(20.dp),
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
                                id,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        } else if (id != SettingsRepository.DEFAULT_IMAGE_MODEL_ID) {
                            IconButton(onClick = { viewModel.deleteImageModel(id) }) {
                                Icon(Icons.Default.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.l))
        Button(
            onClick = {
                showDirectory = true
                viewModel.loadOpenRouterDirectory()
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Browse image models")
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            "Only models that can output images appear in this search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDirectory) {
        OpenRouterDirectorySheet(
            query = orQuery,
            results = orImageResults,
            loading = orLoading,
            error = orError,
            addedIds = imageModels.map { it.id }.toSet() + SettingsRepository.DEFAULT_IMAGE_MODEL_ID,
            onQueryChange = viewModel::updateOrModelQuery,
            onRetry = viewModel::loadOpenRouterDirectory,
            onAdd = { info -> viewModel.addImageModel(info.id, info.name.substringAfter(": ")) },
            onRemove = viewModel::deleteImageModel,
            onDismiss = { showDirectory = false },
        )
    }
}

// ── On-device models ────────────────────────────────────────────────────────────────────

@Composable
private fun LocalImageEngineSection(viewModel: SettingsViewModel) {
    val installed by viewModel.localImageModels.collectAsState()
    val selectedId by viewModel.localImageModelId.collectAsState()
    val states by viewModel.localImageDownloadStates.collectAsState()
    val message by viewModel.localImageMessage.collectAsState()
    val experimentalEnabled by viewModel.experimentalImageModelsEnabled.collectAsState()

    var detailsFor by remember { mutableStateOf<LocalImageCatalogEntry?>(null) }

    Column {
        Text("Private, offline and free", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(2.dp))
        Text(
            "Runs entirely on this phone",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message?.let {
            Spacer(Modifier.height(Spacing.s))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (installed.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xl))
            PageSection("Installed")
            Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                installed.forEachIndexed { index, model ->
                    InstalledLocalImageRow(
                        model = model,
                        index = index,
                        count = installed.size,
                        selected = model.id == selectedId,
                        onSelect = { viewModel.saveLocalImageModel(model.id) },
                        onDelete = { viewModel.deleteLocalImageModel(model) },
                        onDetails = { detailsFor = LocalImageModelCatalog.entryById(model.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedPolygonShape(MaterialShapes.Gem))
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.base))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Show experimental models",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Experimental models may be slower or unavailable on some phones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = experimentalEnabled,
                    onCheckedChange = viewModel::saveExperimentalImageModelsEnabled,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Get models", "Four curated styles, tuned for phones")
        Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
            LocalImageModelCatalog.entries.forEachIndexed { index, entry ->
                LocalImageCatalogRow(
                    entry = entry,
                    index = index,
                    count = LocalImageModelCatalog.entries.size,
                    installed = installed.any { it.id == entry.id },
                    state = states[entry.id],
                    experimentalEnabled = experimentalEnabled,
                    runtimeSupported = viewModel.localImageRuntimeSupported(entry),
                    onDownload = { viewModel.downloadLocalImageModel(entry) },
                    onCancel = { viewModel.cancelLocalImageDownload(entry.id) },
                    onRetry = { viewModel.retryLocalImageDownload(entry) },
                    onDetails = { detailsFor = entry },
                )
            }
        }
    }

    detailsFor?.let { entry ->
        LocalImageModelDetailsDialog(
            entry = entry,
            installedModel = installed.firstOrNull { it.id == entry.id },
            onDismiss = { detailsFor = null },
        )
    }
}

@Composable
private fun InstalledLocalImageRow(
    model: LocalImageModel,
    index: Int,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
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
                    .clip(RoundedPolygonShape(MaterialShapes.Flower))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Brush, null, Modifier.size(20.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Installed · " + Formatter.formatShortFileSize(context, model.installedBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDetails) {
                Icon(Icons.Default.Info, "Model details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LocalImageCatalogRow(
    entry: LocalImageCatalogEntry,
    index: Int,
    count: Int,
    installed: Boolean,
    state: LocalImageDownloadState?,
    experimentalEnabled: Boolean,
    runtimeSupported: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    val experimentalLocked = entry.experimental && !experimentalEnabled
    val downloadLocked = experimentalLocked || !runtimeSupported
    Surface(
        onClick = onDetails,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().alpha(if (experimentalLocked) 0.58f else 1f),
    ) {
        Column(Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(Spacing.s))
                        val badge = if (entry.experimental) "Experimental" else "Recommended"
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        "${entry.category} · ${entry.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    entry.downloadBytes?.let {
                        Text(
                            Formatter.formatShortFileSize(context, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!runtimeSupported) {
                        Text(
                            "Needs Android 8.0 or newer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.s))
                when {
                    installed -> Icon(
                        Icons.Default.CheckCircle, "Installed",
                        Modifier.padding(Spacing.m), tint = MaterialTheme.colorScheme.primary,
                    )
                    state is LocalImageDownloadState.Downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularWavyProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.size(32.dp),
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, "Cancel download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state is LocalImageDownloadState.Verifying || state is LocalImageDownloadState.Installing ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(Spacing.s))
                            Text(
                                if (state is LocalImageDownloadState.Verifying) "Verifying…" else "Installing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    state is LocalImageDownloadState.Failed &&
                        entry.canDownload(experimentalEnabled) && runtimeSupported -> IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, "Retry download", tint = MaterialTheme.colorScheme.error)
                    }
                    !entry.artifactAvailable -> Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            "Coming soon",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        )
                    }
                    downloadLocked -> FilledTonalIconButton(onClick = {}, enabled = false) {
                        Icon(
                            Icons.Default.Download,
                            if (!runtimeSupported) "Android 8.0 is required to download ${entry.name}"
                            else "Enable experimental models to download ${entry.name}",
                        )
                    }
                    else -> FilledTonalIconButton(onClick = onDownload, enabled = entry.canDownload(experimentalEnabled)) {
                        Icon(Icons.Default.Download, "Download ${entry.name}")
                    }
                }
            }
            if (state is LocalImageDownloadState.Downloading) {
                Text(
                    "${Formatter.formatShortFileSize(context, state.downloadedBytes)} of " +
                        Formatter.formatShortFileSize(context, state.totalBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is LocalImageDownloadState.Failed) {
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

/** Subtle details view: name, category, source link, licence, installed size. */
@Composable
private fun LocalImageModelDetailsDialog(
    entry: LocalImageCatalogEntry,
    installedModel: LocalImageModel?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Brush, null) },
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Category: ${entry.category}", style = MaterialTheme.typography.bodyMedium)
                Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                installedModel?.let {
                    Text(
                        "Installed size: " + Formatter.formatShortFileSize(context, it.installedBytes),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val licenceName = when (entry.licenseId) {
                    LocalImageModelCatalog.LICENSE_OPENRAIL_M -> "CreativeML OpenRAIL-M"
                    LocalImageModelCatalog.LICENSE_CIVITAI_MODEL_TERMS -> "CivitAI model terms"
                    else -> entry.licenseId
                }
                Text("Licence: $licenceName", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { uriHandler.openUri(entry.sourceUrl) }) {
                        Text("View source")
                    }
                    TextButton(onClick = { uriHandler.openUri(entry.licenseUrl) }) {
                        Text("View licence")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
