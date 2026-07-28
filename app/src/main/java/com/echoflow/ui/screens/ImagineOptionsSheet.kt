@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.ImagineMedia
import com.echoflow.ui.components.AspectRatioGrid
import com.echoflow.ui.components.SectionLabel
import com.echoflow.ui.theme.Spacing

/** Everything the "+" opens: what this prompt is made *with*, as opposed to what it says. */
internal data class ImagineOptions(
    val media: ImagineMedia,
    val aspectRatio: String,
    val supportedRatios: List<String>?,
    val ratioNote: String?,
    val resolution: String,
    val supportedResolutions: List<String>,
    val priceFor: (resolution: String) -> String?,
    val audioSupported: Boolean,
    val audioEnabled: Boolean,
    val attachmentUri: String?,
    val attachmentName: String?,
)

/**
 * The composer's "+" sheet.
 *
 * Four controls used to sit permanently above the prompt as chips — shape, model, audio,
 * attach — and a creative tool that shows you four settings before you have had a thought is
 * a settings screen with a text box on it. Only the two decisions that change *what you are
 * making* stay outside: the medium, and the model. Everything that shapes a single request
 * lives behind the "+", one tap away and out of sight until asked for.
 *
 * That also settles the "+" itself. It used to attach an image and nothing else, which is a
 * lot of prominence for one action and gave the app two different plus buttons meaning two
 * different things. Now the rule holds everywhere: **+ adds to this message, the pencil starts
 * a new one.**
 *
 * Resolution appears only when the model actually offers a choice — a single-option model gets
 * a plain line of text rather than a picker with one button, and a model that declares nothing
 * gets no section at all. Prices sit under each option because resolution is the setting most
 * likely to quietly triple a bill, and the moment to know that is while choosing.
 */
@Composable
internal fun ImagineOptionsSheet(
    options: ImagineOptions,
    onSelectRatio: (String) -> Unit,
    onSelectResolution: (String) -> Unit,
    onToggleAudio: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isVideo = options.media == ImagineMedia.Video
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.base)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Section(if (isVideo) "First frame" else "Reference") {
                AttachmentRow(
                    uri = options.attachmentUri,
                    name = options.attachmentName,
                    emptyLabel = if (isVideo) "Start from an image" else "Add a reference image",
                    onAttach = onAttach,
                    onClear = onClearAttachment,
                )
            }

            Section("Shape") {
                AspectRatioGrid(
                    selected = options.aspectRatio,
                    supported = options.supportedRatios,
                    onSelect = onSelectRatio,
                    unsupportedNote = options.ratioNote,
                )
            }

            if (isVideo && options.supportedResolutions.isNotEmpty()) {
                Section("Resolution") {
                    ResolutionOptions(
                        selected = options.resolution,
                        supported = options.supportedResolutions,
                        priceFor = options.priceFor,
                        onSelect = onSelectResolution,
                    )
                }
            }

            if (isVideo && options.audioSupported) {
                Section("Sound") {
                    ToggleRow(
                        label = if (options.audioEnabled) "Audio on" else "Audio off",
                        checked = options.audioEnabled,
                        onToggle = onToggleAudio,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(Spacing.s))
        content()
    }
}

@Composable
private fun AttachmentRow(
    uri: String?,
    name: String?,
    emptyLabel: String,
    onAttach: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        onClick = if (uri == null) onAttach else ({}),
        enabled = uri == null,
        shape = MaterialTheme.shapes.large,
        color = if (uri == null) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            if (uri == null) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).padding(Spacing.s),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AsyncImage(
                    uri, null,
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                if (uri == null) emptyLabel else name.orEmpty().ifBlank { "Attached" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uri == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier.weight(1f),
            )
            if (uri != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Close, "Remove", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionOptions(
    selected: String,
    supported: List<String>,
    priceFor: (String) -> String?,
    onSelect: (String) -> Unit,
) {
    // One option is information, not a decision. Rendering it as a chip you cannot meaningfully
    // press invites people to press it and wonder what they did wrong.
    if (supported.size == 1) {
        val only = supported.single()
        Text(
            listOfNotNull(only, priceFor(only)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        supported.forEach { resolution ->
            val isSelected = resolution == selected
            Surface(
                onClick = { onSelect(resolution) },
                shape = MaterialTheme.shapes.large,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.s)) {
                    Text(
                        resolution,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    priceFor(resolution)?.let { price ->
                        Text(
                            price,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
