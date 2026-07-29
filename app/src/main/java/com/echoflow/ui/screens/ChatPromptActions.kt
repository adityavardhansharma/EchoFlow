@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/**
 * User prompt bubble with long-press actions: always Copy; Edit only when this is the last
 * user message in the conversation.
 */
@Composable
internal fun UserPromptBubble(
    content: String,
    canEdit: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    attachment: @Composable (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                attachment?.invoke()
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                            onLongClick = { menuOpen = true },
                        ),
                    shape = RoundedCornerShape(26.dp, 26.dp, 8.dp, 26.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = Spacing.m),
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        onCopy()
                        menuOpen = false
                    },
                )
                if (canEdit) {
                    DropdownMenuItem(
                        text = { Text("Edit prompt") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Quiet answer toolbar: optional ‹ n/m › pager plus copy. Sized small so it never competes
 * with the reply body; uses surface-container tonal chips like the rest of chat chrome.
 */
@Composable
internal fun AnswerActionBar(
    versionIndex: Int,
    versionCount: Int,
    onPreviousVersion: () -> Unit,
    onNextVersion: () -> Unit,
    onCopy: () -> Unit,
    showCopy: Boolean,
    modifier: Modifier = Modifier,
) {
    if (versionCount <= 1 && !showCopy) return

    Row(
        modifier = modifier.padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (versionCount > 1) {
            val canPrev = versionIndex > 0
            val canNext = versionIndex < versionCount - 1
            VersionChevron(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous answer",
                enabled = canPrev,
                onClick = onPreviousVersion,
            )
            Text(
                "${versionIndex + 1}/$versionCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            VersionChevron(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next answer",
                enabled = canNext,
                onClick = onNextVersion,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        if (showCopy) {
            CompactActionButton(
                contentDescription = "Copy answer",
                onClick = onCopy,
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy answer", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun VersionChevron(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    CompactActionButton(
        contentDescription = contentDescription,
        enabled = enabled,
        visualSize = 26.dp,
        onClick = onClick,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else 0.45f,
            ),
        )
    }
}

/**
 * Keeps the visible chip deliberately compact while retaining Material's 48 dp touch target.
 */
@Composable
private fun CompactActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    visualSize: androidx.compose.ui.unit.Dp = 32.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                alpha = if (enabled) 0.9f else 0.4f,
            ),
            modifier = Modifier.size(visualSize),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** Cross-fades (and lightly slides) between reply versions. */
@Composable
internal fun AnimatedAnswerVersion(
    versionIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = versionIndex,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(90))
            } else {
                val forward = targetState > initialState
                val enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { if (forward) it / 14 else -it / 14 },
                    )
                val exit = fadeOut(tween(140)) +
                    slideOutHorizontally(
                        animationSpec = tween(140),
                        targetOffsetX = { if (forward) -it / 18 else it / 18 },
                    )
                enter togetherWith exit
            }
        },
        label = "answer-version",
    ) { index ->
        content(index)
    }
}
