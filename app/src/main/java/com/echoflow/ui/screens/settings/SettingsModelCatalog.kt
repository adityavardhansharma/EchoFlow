@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.CatalogEntry
import com.echoflow.data.DownloadState
import com.echoflow.data.LocalModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

@Composable
internal fun CatalogModelRow(
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
internal fun HfModelSearchSheet(
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
internal fun InstalledModelRow(
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
