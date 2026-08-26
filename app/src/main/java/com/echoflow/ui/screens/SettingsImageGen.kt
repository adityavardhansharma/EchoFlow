@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

@Composable
internal fun ImageGenSection(viewModel: SettingsViewModel) {
    val imageModels by viewModel.imageModels.collectAsState()
    val selectedId by viewModel.imageGenModelId.collectAsState()
    val orQuery by viewModel.orModelQuery.collectAsState()
    val orImageResults by viewModel.orImageModelResults.collectAsState()
    val orLoading by viewModel.orImageDirectoryLoading.collectAsState()
    val orError by viewModel.orImageDirectoryError.collectAsState()

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
                viewModel.loadOpenRouterImageDirectory()
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
            "Live from OpenRouter’s image listing — dedicated generators such as Muse show up here even when they are not in the chat catalog.",
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
            onRetry = viewModel::loadOpenRouterImageDirectory,
            onAdd = { info -> viewModel.addImageModel(info.id, info.name.substringAfter(": ")) },
            onRemove = viewModel::deleteImageModel,
            onDismiss = { showDirectory = false },
        )
    }
}

