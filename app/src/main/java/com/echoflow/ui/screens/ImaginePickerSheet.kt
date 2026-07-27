@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ImagineMedia
import com.echoflow.data.OpenRouterVideoModelInfo
import com.echoflow.ui.components.MediaToggle
import com.echoflow.ui.components.ProviderIdentity
import com.echoflow.ui.components.ProviderMark
import com.echoflow.ui.theme.Spacing

/**
 * Imagine's model picker: **cards**, not rows.
 *
 * In Chat a model is a specification and you compare numbers. In Imagine the model *is* the
 * aesthetic — choosing Veo over Kling over Seedance is a creative decision, closer to picking
 * film stock than picking a spec — so a list of ids is the wrong genre entirely.
 *
 * The media switch lives inside the sheet and mirrors the composer's, so "Video, then Kling"
 * is one gesture rather than a mode change followed by a second trip.
 */
@Composable
internal fun ImagineModelPickerSheet(
    media: ImagineMedia,
    onSelectMedia: (ImagineMedia) -> Unit,
    models: List<Pair<String, String>>,
    selectedId: String,
    capabilitiesFor: (String) -> OpenRouterVideoModelInfo?,
    resolution: String,
    audioEnabled: Boolean,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxHeight(0.9f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.base).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Choose a look",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "The model sets the style as much as the prompt does",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onManage) {
                    Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Manage")
                }
            }

            MediaToggle(selected = media, onSelect = onSelectMedia)
            Spacer(Modifier.height(Spacing.base))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(models, key = { it.first }) { (id, name) ->
                    ImagineModelCard(
                        modelId = id,
                        name = name,
                        selected = id == selectedId,
                        capabilities = if (media == ImagineMedia.Video) capabilitiesFor(id) else null,
                        resolution = resolution,
                        audioEnabled = audioEnabled,
                        onClick = { onSelect(id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagineModelCard(
    modelId: String,
    name: String,
    selected: Boolean,
    capabilities: OpenRouterVideoModelInfo?,
    resolution: String,
    audioEnabled: Boolean,
    onClick: () -> Unit,
) {
    val identity = remember(modelId) { ProviderIdentity.of(modelId) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // A tonal band in the provider's own colour stands in for a sample frame: it makes
            // the grid scannable by vendor at a glance, and it degrades honestly — an empty
            // rectangle would promise a preview the app cannot yet show.
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(identity.container, MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ProviderMark(modelId = modelId, size = 40.dp)
                if (selected) {
                    Icon(
                        Icons.Default.CheckCircle, "Selected",
                        Modifier.align(Alignment.TopEnd).padding(Spacing.s).size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.padding(Spacing.m)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                capabilities?.priceHint(resolution, audioEnabled)?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (capabilities != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        capabilities.resolutions.take(3).forEach { CapabilityTag(it, selected) }
                        if (capabilities.supportsAudio) CapabilityTag("Audio", selected, Icons.Default.GraphicEq)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityTag(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
            if (icon != null) {
                Icon(icon, null, Modifier.size(11.dp), tint = tint)
                Spacer(Modifier.width(2.dp))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}
