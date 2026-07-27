@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.ImagineMedia
import com.echoflow.ui.components.AspectRatioPopover
import com.echoflow.ui.components.ContextChipRow
import com.echoflow.ui.components.MediaToggle
import com.echoflow.ui.components.ModelPill
import com.echoflow.ui.components.SettingChip
import com.echoflow.ui.theme.Spacing

/**
 * Imagine's prompt bar.
 *
 * Same floating-pill material as Chat's composer, but the row above it carries creation
 * controls instead of capability chips: what to make, how it is framed, which model, and
 * whether it has sound. Framing is promoted here from Settings on purpose — in a creative
 * tool the shape of the thing you are making is a per-prompt decision, not a preference.
 */
@Composable
internal fun ImagineComposer(
    text: String,
    onText: (String) -> Unit,
    media: ImagineMedia,
    onSelectMedia: (ImagineMedia) -> Unit,
    aspectRatio: String,
    supportedRatios: List<String>?,
    onSelectRatio: (String) -> Unit,
    ratioNote: String?,
    modelId: String,
    modelLabel: String,
    costHint: String?,
    onOpenModelPicker: () -> Unit,
    audioSupported: Boolean,
    audioEnabled: Boolean,
    onToggleAudio: () -> Unit,
    pendingUri: String?,
    pendingName: String?,
    onClearAttachment: () -> Unit,
    onAttach: () -> Unit,
    onReceiveImage: (Uri) -> Unit,
    isBusy: Boolean,
    blockedReason: String?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ratioMenuOpen by remember { mutableStateOf(false) }
    val imageReceiver = remember(onReceiveImage) {
        object : ReceiveContentListener {
            override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
                if (!transferableContent.hasMediaType(MediaType.Image)) return transferableContent
                return transferableContent.consume { item ->
                    item.uri?.let { onReceiveImage(it); true } == true
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = Spacing.base, vertical = Spacing.m),
    ) {
        // The reference image: an edit source for images, a first frame for video.
        AnimatedVisibility(visible = pendingUri != null) {
            pendingUri?.let { uri ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = Spacing.s),
                ) {
                    Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            uri, null,
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(Spacing.m))
                        Text(
                            pendingName ?: if (media == ImagineMedia.Video) "First frame" else "Reference",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClearAttachment, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close, "Remove", Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        ContextChipRow(Modifier.padding(start = Spacing.s, bottom = Spacing.s)) {
            MediaToggle(selected = media, onSelect = onSelectMedia)
            Box {
                SettingChip(
                    icon = Icons.Default.AspectRatio,
                    label = aspectRatio,
                    onClick = { ratioMenuOpen = true },
                    description = "Shape: $aspectRatio. Tap to change.",
                )
                AspectRatioPopover(
                    expanded = ratioMenuOpen,
                    selected = aspectRatio,
                    supported = supportedRatios,
                    onSelect = onSelectRatio,
                    onDismiss = { ratioMenuOpen = false },
                    unsupportedNote = ratioNote,
                )
            }
            ModelPill(
                modelId = modelId,
                label = modelLabel,
                supporting = costHint,
                onClick = onOpenModelPicker,
            )
            if (media == ImagineMedia.Video && audioSupported) {
                SettingChip(
                    icon = Icons.Default.GraphicEq,
                    label = if (audioEnabled) "Audio on" else "Audio off",
                    onClick = onToggleAudio,
                    active = audioEnabled,
                    description = if (audioEnabled) "Audio on. Tap to turn off." else "Audio off. Tap to turn on.",
                )
            }
        }

        AnimatedVisibility(visible = blockedReason != null) {
            Text(
                blockedReason.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.base, bottom = Spacing.s),
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                // Imagine's "+" is one thing, so it is a button rather than a menu.
                ShapedIconButton(
                    onClick = onAttach,
                    enabled = true,
                    size = 44.dp,
                    restShape = MaterialShapes.Cookie6Sided,
                    pressedShape = MaterialShapes.Flower,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    pulseOnClick = true,
                ) {
                    Icon(
                        if (media == ImagineMedia.Video) Icons.Default.Movie else Icons.Default.Image,
                        if (media == ImagineMedia.Video) "Add a first frame" else "Add a reference image",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = {
                        Text(
                            if (media == ImagineMedia.Video) "Describe your video…"
                            else "Describe what you want to see…"
                        )
                    },
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .contentReceiver(imageReceiver)
                        .testTag("imagine_input_field"),
                )

                val canSend = text.trim().isNotEmpty() && !isBusy && blockedReason == null
                SendButton(enabled = canSend, isStreaming = isBusy, research = false, onStop = onStop) {
                    if (canSend) onSend()
                }
            }
        }
    }
}
