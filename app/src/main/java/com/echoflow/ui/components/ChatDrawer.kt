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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Add
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

        // Primary action — filled pill. A springy press-scale gives immediate tactile
        // feedback on touch-down, since the drawer closes on release and would otherwise
        // cut the ripple short before the user sees it.
        // Primary action — a clean extended-FAB. On click the "+" spins + pops, and only THEN do
        // we run the action and close the drawer, so the animation is actually seen (the drawer
        // used to slide away instantly and cut it off).
        val scope = rememberCoroutineScope()
        val newChatInteraction = remember { MutableInteractionSource() }
        val newChatScale by pressScale(newChatInteraction)
        val plusAnim = remember { Animatable(0f) }
        Button(
            onClick = {
                scope.launch {
                    plusAnim.snapTo(0f)
                    plusAnim.animateTo(1f, animationSpec = tween(durationMillis = 320))
                    onNewChatClicked()
                    onCloseDrawer?.invoke()
                }
            },
            shapes = ButtonShapes(shape = ButtonRestShape, pressedShape = ButtonPressedShape),
            interactionSource = newChatInteraction,
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.base),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .graphicsLayer { scaleX = newChatScale; scaleY = newChatScale }
                .testTag("drawer_new_chat_button"),
        ) {
            // Fun: a medallion that morphs Sunny → Cookie while the + spins, on click.
            val p = plusAnim.value
            val plusMorph = rememberMorph(MaterialShapes.Sunny, MaterialShapes.Cookie7Sided)
            Box(
                Modifier.size(32.dp).clip(MorphPolygonShape(plusMorph, p)).background(MaterialTheme.colorScheme.onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add, null,
                    Modifier.size(18.dp).rotate(p * 360f).scale(1f + 0.3f * (1f - abs(p - 0.5f) * 2f)),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text(
                if (imagine) "New creation" else "New conversation",
                style = MaterialTheme.typography.titleSmall,
            )
        }

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

        // Settings — an expressive tile: a colored secondary container lifts it off the dark
        // drawer surface, with a tertiary cookie-shaped gear chip that spins on press, plus a
        // springy press-scale and trailing chevron.
        val settingsInteraction = remember { MutableInteractionSource() }
        val settingsScale by pressScale(settingsInteraction)
        val gearAnim = remember { Animatable(0f) }
        Button(
            onClick = {
                scope.launch {
                    gearAnim.snapTo(0f)
                    gearAnim.animateTo(1f, animationSpec = tween(durationMillis = 320))
                    onSettingsClicked()
                    onCloseDrawer?.invoke()
                }
            },
            shapes = ButtonShapes(shape = ButtonRestShape, pressedShape = ButtonPressedShape),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            elevation = null,
            interactionSource = settingsInteraction,
            contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.base),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .graphicsLayer { scaleX = settingsScale; scaleY = settingsScale },
        ) {
            // Fun: a Ghost-ish medallion that morphs to a cookie while the gear spins, on click.
            val g = gearAnim.value
            val gearMorph = rememberMorph(MaterialShapes.Ghostish, MaterialShapes.Cookie7Sided)
            Box(
                Modifier.size(32.dp).clip(MorphPolygonShape(gearMorph, g)).background(MaterialTheme.colorScheme.onTertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Settings, null,
                    Modifier.size(18.dp).rotate(g * 300f),
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Text("Settings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
            )
        }
        Spacer(Modifier.height(Spacing.s))
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
