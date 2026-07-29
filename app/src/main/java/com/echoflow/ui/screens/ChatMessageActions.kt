@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

@Composable
internal fun UserMessageBubble(
    content: String,
    canEdit: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    attachment: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        ColumnContent(
            content = content,
            attachment = attachment,
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Copy") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    copyPrompt(context, content)
                    onCopy()
                    menuOpen = false
                },
            )
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text("Edit prompt") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnContent(
    content: String,
    attachment: @Composable (() -> Unit)?,
    onLongClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                        onLongClick = onLongClick,
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
    }
}

@Composable
internal fun ReplyVersionBar(
    currentIndex: Int,
    totalVersions: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCopy: () -> Unit,
    showCopy: Boolean,
    modifier: Modifier = Modifier,
) {
    if (totalVersions <= 1 && !showCopy) return
    Row(
        modifier = modifier.padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (totalVersions > 1) {
            // Quiet pager: small chevrons + "1/2" so version history never competes with the answer.
            Surface(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = if (currentIndex > 0) 0.9f else 0.4f,
                ),
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        "Previous answer",
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (currentIndex > 0) 1f else 0.45f,
                        ),
                    )
                }
            }
            Text(
                "${currentIndex + 1}/$totalVersions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
            Surface(
                onClick = onNext,
                enabled = currentIndex < totalVersions - 1,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = if (currentIndex < totalVersions - 1) 0.9f else 0.4f,
                ),
                modifier = Modifier.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        "Next answer",
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (currentIndex < totalVersions - 1) 1f else 0.45f,
                        ),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        if (showCopy) {
            FilledTonalIconButton(
                onClick = onCopy,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Icon(Icons.Default.ContentCopy, "Copy answer", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
internal fun AnimatedReplyContent(
    targetIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    AnimatedContent(
        targetState = targetIndex,
        modifier = modifier,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(120)) togetherWith fadeOut(tween(90))
            } else {
                val forward = targetState > initialState
                val enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetX = { if (forward) it / 12 else -it / 12 },
                    )
                val exit = fadeOut(tween(140)) +
                    slideOutHorizontally(
                        animationSpec = tween(140),
                        targetOffsetX = { if (forward) -it / 16 else it / 16 },
                    )
                enter togetherWith exit
            }
        },
        label = "reply-version",
    ) { index ->
        content(index)
    }
}

private fun copyPrompt(context: Context, prompt: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Message", prompt))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
}
