
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.ui.theme.Spacing

/**
 * The staged attachments, one pill each (up to the message limit). A pill narrates its own state:
 * a morphing [LoadingIndicator] while its doc is parsed on-device, a tick once ready, a tap-to-retry
 * arrow if the parse failed. Every pill carries a ✕ to drop that file from the turn. Images show a
 * thumbnail and are ready at once (no local parse).
 */
@Composable
internal fun AttachmentChips(
    attachments: List<com.echoflow.ui.PendingAttachment>,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        attachments.forEach { attachment ->
            key(attachment.id) {
                AttachmentChip(
                    attachment = attachment,
                    onRemove = { onRemove(attachment.id) },
                    onRetry = { onRetry(attachment.id) },
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: com.echoflow.ui.PendingAttachment,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    val failed = attachment.isFailed
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (failed) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val onContainer = if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
            // Leading slot: image thumbnail, or a state glyph for a doc (loader → tick → retry).
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                when {
                    attachment.isImage ->
                        AsyncImage(
                            attachment.uri,
                            null,
                            Modifier.size(26.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Crop,
                        )
                    attachment.isProcessing ->
                        LoadingIndicator(modifier = Modifier.size(20.dp), color = onContainer)
                    failed ->
                        Icon(
                            Icons.Default.Refresh,
                            "Retry",
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onRetry)
                                .semantics { contentDescription = "Retry reading ${attachment.name}" },
                            tint = onContainer,
                        )
                    else ->
                        Icon(Icons.Default.CheckCircle, "Ready", Modifier.size(20.dp), tint = onContainer)
                }
            }
            Spacer(Modifier.width(Spacing.s))
            Text(
                attachment.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, "Remove ${attachment.name}", Modifier.size(16.dp), tint = onContainer)
            }
        }
    }
}
