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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.remember
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
import com.echoflow.ui.components.ContextChipRow
import com.echoflow.ui.components.MediaToggle
import com.echoflow.ui.components.ModelPill
import com.echoflow.ui.theme.Spacing

/**
 * Imagine's prompt bar.
 *
 * Same floating-pill material as Chat's composer, but the row above it carries the two
 * decisions that change *what you are making*: the medium, and the model. Shape, resolution,
 * audio and the reference image used to sit up here too, and four settings above an empty text
 * box makes a creative tool read as a settings screen. They moved behind the "+", which is
 * where per-request choices belong — one tap away, invisible until wanted.
 */
@Composable
internal fun ImagineComposer(
    text: String,
    onText: (String) -> Unit,
    media: ImagineMedia,
    onSelectMedia: (ImagineMedia) -> Unit,
    modelId: String,
    modelLabel: String,
    onOpenModelPicker: () -> Unit,
    onOpenOptions: () -> Unit,
    pendingUri: String?,
    pendingName: String?,
    onClearAttachment: () -> Unit,
    onReceiveImage: (Uri) -> Unit,
    isBusy: Boolean,
    blockedReason: String?,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            // Name only. Cost, capabilities and resolutions belong in the picker, where you
            // are actually comparing models — beside the prompt they are noise you have
            // already decided about.
            ModelPill(modelId = modelId, label = modelLabel, onClick = onOpenModelPicker)
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
                // Everything this prompt is made *with*. Same meaning as Chat's "+": it adds
                // to the message you are writing.
                ShapedIconButton(
                    onClick = onOpenOptions,
                    enabled = true,
                    size = 44.dp,
                    restShape = MaterialShapes.Cookie6Sided,
                    pressedShape = MaterialShapes.Flower,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    pulseOnClick = true,
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Reference image, shape and quality",
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                TextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = {
                        // Both placeholders are short enough to hold one line at any font
                        // scale. The image copy used to wrap, which quietly made the pill a
                        // different height in each medium — the composer must not resize
                        // when you flip a toggle that changes nothing about the input.
                        Text(
                            if (media == ImagineMedia.Video) "Describe a shot…" else "Describe an image…",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
