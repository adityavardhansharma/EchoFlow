@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ImagineMedia
import com.echoflow.data.OpenRouterVideoModelInfo
import com.echoflow.ui.components.MediaToggle
import com.echoflow.ui.components.ProviderIdentity
import com.echoflow.ui.components.ProviderMark
import com.echoflow.ui.theme.Spacing

/**
 * Imagine's model picker.
 *
 * This was a grid of cards, each with a tonal gradient band above the name. The band was
 * standing in for a sample frame the app cannot produce, and a rectangle standing in for an
 * image it will never fill does not read as restraint — it reads as a screen that failed to
 * load. Two columns of them, mostly empty, with three or four models in the list.
 *
 * So the preview goes and the **logo becomes the subject**. A row is wide enough to say the
 * things that actually decide this — who made it, what it costs, what it can do — in plain
 * text on two lines, with no chips to decorate them. It is also honest about the list being
 * short: four full-width rows look deliberate where four half-empty cards look unfinished.
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                items(models, key = { it.first }) { (id, name) ->
                    ImagineModelRow(
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
private fun ImagineModelRow(
    modelId: String,
    name: String,
    selected: Boolean,
    capabilities: OpenRouterVideoModelInfo?,
    resolution: String,
    audioEnabled: Boolean,
    onClick: () -> Unit,
) {
    val identity = remember(modelId) { ProviderIdentity.of(modelId) }
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "model-row-container",
    )
    val onContainer = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val support = onContainer.copy(alpha = 0.7f)

    // Who made it, then what it costs: the two things that decide the choice, in the order
    // people ask them.
    val subtitle = remember(identity, capabilities, resolution, audioEnabled) {
        listOfNotNull(identity.label, capabilities?.pricePerSecond(resolution, audioEnabled))
            .joinToString(" · ")
    }
    // Capabilities as one interpuncted line rather than a wrap of chips. Chips promise you can
    // press them; these are facts about the model, and nothing here is pressable.
    val abilities = remember(capabilities) {
        capabilities?.let { caps ->
            (caps.resolutions + listOfNotNull("Audio".takeIf { caps.supportsAudio }))
                .takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The logo is the row's anchor now that there is no preview to look at, so it is
            // sized to be recognised across a room rather than squinted at.
            ProviderMark(modelId = modelId, size = 44.dp)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = support,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                abilities?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = support,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Only the chosen row carries a mark. An unselected placeholder circle in every
            // row would turn a short list into a form.
            if (selected) {
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Default.CheckCircle, "Selected",
                    Modifier.size(22.dp),
                    tint = onContainer,
                )
            }
        }
    }
}
