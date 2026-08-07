@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.data.ChatThread
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs

private val PillShape = RoundedCornerShape(28.dp)

// Expressive button morph: a clean full capsule at rest that springs to a soft squircle on press
// (the canonical Material 3 Expressive button interaction). Both are CornerBasedShapes so the
// ButtonShapes morph interpolates smoothly.
private val ButtonRestShape = RoundedCornerShape(percent = 50)
private val ButtonPressedShape = RoundedCornerShape(percent = 30)


@Composable
fun ChatDrawerContent(
    mode: AppMode,
    allThreads: List<ChatThread>,
    currentThreadId: String?,
    renderingChatIds: Set<String>,
    otherModeMatchCount: Int,
    onThreadSelected: (String) -> Unit,
    onNewChatClicked: () -> Unit,
    onDeleteThread: (ChatThread) -> Unit,
    onRenameThread: (ChatThread, String) -> Unit,
    onPinThread: (ChatThread) -> Unit,
    onUnpinThread: (ChatThread) -> Unit,
    onSettingsClicked: () -> Unit,
    onCloseDrawer: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChange: ((String) -> Unit)? = null,
) {
    val imagine = mode == AppMode.Imagine
    var threadToRename by remember { mutableStateOf<ChatThread?>(null) }
    var threadToDelete by remember { mutableStateOf<ChatThread?>(null) }

    threadToRename?.let { thread ->
        var renameText by remember { mutableStateOf(thread.title) }
        AlertDialog(
            onDismissRequest = { threadToRename = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field"),
                )
            },
            confirmButton = { TextButton(onClick = { onRenameThread(thread, renameText); threadToRename = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { threadToRename = null }) { Text("Cancel") } },
        )
    }

    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
            title = { Text("Delete conversation?") },
            text = { Text("This conversation will be permanently removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDeleteThread(thread); threadToDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { threadToDelete = null }) { Text("Cancel") } },
        )
    }

    Column(
        Modifier
            .fillMaxHeight()
            .widthIn(max = 340.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.m),
    ) {
        // Brand header — same mark as the rest of the app for cohesion.
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.s, top = Spacing.base, bottom = Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark(size = 40.dp)
            Spacer(Modifier.width(Spacing.m))
            Text("EchoFlow", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }

        val scope = rememberCoroutineScope()

        // Search across every conversation — titles and message text.
        if (onSearchQueryChange != null) {
            Spacer(Modifier.height(Spacing.m))
            DrawerSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = if (imagine) "Search creations" else "Search conversations",
            )
        }

        Spacer(Modifier.height(Spacing.l))
        if (searchQuery.isNotBlank()) SectionLabel("Results")

        when {
            searchQuery.isNotBlank() && allThreads.isEmpty() ->
                DrawerEmptyState(
                    icon = Icons.Default.Search,
                    message = "Nothing here matches “$searchQuery”",
                    modifier = Modifier.weight(1f),
                )
            allThreads.isEmpty() ->
                // Names where the other mode's history went. This is the one way the split can
                // feel like data loss, and one sentence pre-empts the whole question.
                DrawerEmptyState(
                    icon = if (imagine) Icons.Default.AutoFixHigh else Icons.AutoMirrored.Filled.Chat,
                    message = if (imagine) {
                        "No creations yet.\nYour older image chats stayed in Chat."
                    } else {
                        "No conversations yet."
                    },
                    modifier = Modifier.weight(1f),
                )
            else -> DrawerThreadList(
                threads = allThreads,
                currentThreadId = currentThreadId,
                renderingChatIds = renderingChatIds,
                onThreadSelected = { onThreadSelected(it.id); onCloseDrawer?.invoke() },
                onPin = onPinThread,
                onUnpin = onUnpinThread,
                onRename = { threadToRename = it },
                onDelete = { threadToDelete = it },
                grouped = searchQuery.isBlank(),
                modifier = Modifier.weight(1f),
            )
        }

        if (otherModeMatchCount > 0) {
            Text(
                "$otherModeMatchCount also in ${if (imagine) "Chat" else "Imagine"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)

        DrawerFooterRow(
            newChatLabel = if (imagine) "New Canvas" else "New Chat",
            onNewChatClicked = {
                onNewChatClicked()
                onCloseDrawer?.invoke()
            },
            onSettingsClicked = {
                onSettingsClicked()
                onCloseDrawer?.invoke()
            },
            scope = scope,
        )
        Spacer(Modifier.height(Spacing.s))
    }
}

/**
 * Footer row: compact "New Chat" / "New Canvas" on the left, circular ghost settings on the right.
 */
@Composable
private fun DrawerFooterRow(
    newChatLabel: String,
    onNewChatClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val newChatInteraction = remember { MutableInteractionSource() }
        val newChatScale by pressScale(newChatInteraction)
        val plusAnim = remember { Animatable(0f) }
        Button(
            onClick = {
                scope.launch {
                    plusAnim.snapTo(0f)
                    plusAnim.animateTo(1f, animationSpec = tween(durationMillis = 320))
                    onNewChatClicked()
                }
            },
            shapes = ButtonShapes(shape = ButtonRestShape, pressedShape = ButtonPressedShape),
            interactionSource = newChatInteraction,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .graphicsLayer { scaleX = newChatScale; scaleY = newChatScale }
                .testTag("drawer_new_chat_button"),
        ) {
            val p = plusAnim.value
            val plusMorph = rememberMorph(MaterialShapes.Sunny, MaterialShapes.Cookie7Sided)
            Box(
                Modifier.size(28.dp).clip(MorphPolygonShape(plusMorph, p)).background(MaterialTheme.colorScheme.onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Create, null,
                    Modifier.size(16.dp).scale(1f + 0.3f * (1f - abs(p - 0.5f) * 2f)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Text(newChatLabel, style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.weight(1f))

        val settingsInteraction = remember { MutableInteractionSource() }
        val settingsScale by pressScale(settingsInteraction)
        val gearAnim = remember { Animatable(0f) }
        IconButton(
            onClick = {
                scope.launch {
                    gearAnim.snapTo(0f)
                    gearAnim.animateTo(1f, animationSpec = tween(durationMillis = 320))
                    onSettingsClicked()
                }
            },
            interactionSource = settingsInteraction,
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = settingsScale; scaleY = settingsScale }
                .testTag("drawer_settings_button"),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            shape = CircleShape,
        ) {
            val g = gearAnim.value
            val gearMorph = rememberMorph(MaterialShapes.Ghostish, MaterialShapes.Cookie7Sided)
            Box(
                Modifier.size(32.dp).clip(MorphPolygonShape(gearMorph, g)).background(MaterialTheme.colorScheme.onTertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Settings, "Settings",
                    Modifier.size(18.dp).rotate(g * 300f),
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
        }
    }
}

/**
 * Compact pill search field tuned for the drawer's dark surface.
 *
 * The text field only exists in the composition after the user taps the pill: drawers
 * move focus into their content when they open, and a permanently-focusable field would
 * grab that focus and pop the keyboard on every drawer swipe.
 */
@Composable
private fun DrawerSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Surface(
        onClick = { editing = true },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.m))
            if (editing) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                // Leave edit mode when focus moves away (e.g. the drawer
                                // closes) so reopening the drawer never re-grabs focus.
                                editing = false
                                hadFocus = false
                            }
                        },
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Text(
                    query.ifEmpty { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        editing = false
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close, "Clear search", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Expressive press feedback: a gentle, springy scale-down while the element is held, returning with
 * a slight bounce on release. Pass the same [interactionSource] to the clickable so press state is
 * shared. Returns a [State] so callers read it via `by`.
 */
@Composable
private fun pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f, // M3 pressed state is a subtle ~0.98 scale-down
) = run {
    val pressed by interactionSource.collectIsPressedAsState()
    animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )
}

/** Shared empty/no-results state for the drawer list. */
@Composable
private fun DrawerEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
