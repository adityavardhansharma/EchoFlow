@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.data.memory.RemoteMemory
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TabMemories = "memories"
private const val TabProfile = "profile"
private const val TabReview = "review"

/**
 * The Supermemory library. A collapsing large app bar with the add action as an extended FAB; a
 * connected toggle for Memories / Profile / Review (the same control as the Memory page); a pill
 * search; and memories as a grouped, connected list you can long-press for actions.
 */
@Composable
internal fun MyMemoriesPage(onBack: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(TabMemories) }
    var query by rememberSaveable { mutableStateOf(vm.activeQuery) }
    var editor by remember { mutableStateOf<RemoteMemory?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var forgetting by remember { mutableStateOf<RemoteMemory?>(null) }
    var detail by remember { mutableStateOf<RemoteMemory?>(null) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    fun edit(memory: RemoteMemory?) {
        vm.clearFeedback(); editor = memory; draft = memory?.text.orEmpty(); editing = true
    }
    fun search() { focus.clearFocus(); vm.search(query) }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(tab, vm.activeQuery) { listState.scrollToItem(0) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("My Memories") },
                subtitle = { Text(if (vm.connected) "Supermemory · ${vm.settings.space}" else "Not connected") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = vm::scanCleanup, enabled = !vm.busy && vm.connected) {
                        Icon(Icons.Default.CleaningServices, "Find duplicate and low-quality memories")
                    }
                    IconButton(onClick = { vm.refresh() }, enabled = !vm.busy && vm.connected) {
                        Icon(Icons.Default.Refresh, "Refresh memories")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (vm.connected && tab == TabMemories) {
                MediumExtendedFloatingActionButton(
                    text = { Text("Add memory") },
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = { if (!vm.busy) edit(null) },
                    expanded = fabExpanded,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 128.dp),
        ) {
            if (!vm.connected) {
                item(key = "disconnected") {
                    MemoryEmptyState(
                        Icons.Default.LinkOff, MaterialShapes.Cookie9Sided,
                        title = "Connect your memory",
                        body = "Add a Supermemory API key in Memory settings to see your saved memories.",
                    )
                }
                return@LazyColumn
            }
            item(key = "tabs") {
                ConnectedToggleRow(
                    options = listOf(TabMemories to "Memories", TabProfile to "Profile", TabReview to "Review"),
                    selected = tab,
                    onSelect = { tab = it },
                )
            }
            item(key = "busy") { MemoryBusyLine(vm.busy, Modifier.padding(top = Spacing.xs)) }
            vm.error?.let { message ->
                item(key = "error") {
                    Column(Modifier.padding(top = Spacing.m)) {
                        MemoryBanner(message, MemoryTone.Error)
                        TextButton(
                            onClick = { if (tab == TabMemories) vm.search(query) else vm.refresh() },
                            enabled = !vm.busy,
                        ) { Text("Try again") }
                    }
                }
            }
            vm.notice?.let { message ->
                item(key = "notice") {
                    MemoryBanner(message, MemoryTone.Success, Modifier.padding(top = Spacing.m), onDismiss = vm::clearFeedback)
                }
            }
            when (tab) {
                TabMemories -> {
                    item(key = "search") {
                        MemorySearchField(
                            query = query,
                            onQueryChange = { query = it.take(2000) },
                            showClear = query.isNotEmpty() || vm.activeQuery.isNotEmpty(),
                            enabled = !vm.busy,
                            onSearch = { search() },
                            onClear = { query = ""; vm.search(""); focus.clearFocus() },
                            modifier = Modifier.padding(top = Spacing.base, bottom = Spacing.l),
                        )
                    }
                    item(key = "memories-label") {
                        MemorySectionHeader(
                            if (vm.activeQuery.isBlank()) "Saved memories" else "Matches for “${vm.activeQuery}”",
                            supporting = if (vm.activeQuery.isNotBlank() && vm.memories.isNotEmpty()) "The closest matches, not your entire library" else null,
                        )
                    }
                    if (!vm.loaded && vm.busy) item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(56.dp))
                        }
                    }
                    if (vm.memories.isEmpty() && vm.loaded && !vm.busy && vm.error == null) item(key = "empty") {
                        if (vm.activeQuery.isNotBlank()) {
                            MemoryEmptyState(
                                Icons.Default.Search, MaterialShapes.Cookie6Sided,
                                title = "No matching memories",
                                body = "Try a person's name, a project, or a different way of describing it.",
                            )
                        } else {
                            MemoryEmptyState(
                                Icons.Default.AutoAwesome, MaterialShapes.Flower,
                                title = "A little continuity starts here",
                                body = "Save a preference, a project, or something you'd like to pick up next time. Learning can also build memory from new conversations.",
                                action = {
                                    Button(onClick = { edit(null) }, modifier = Modifier.height(48.dp)) {
                                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(Spacing.s))
                                        Text("Add your first memory")
                                    }
                                },
                            )
                        }
                    }
                    itemsIndexed(vm.memories, key = { _, it -> "memory:${it.id}" }) { index, memory ->
                        MemoryListRow(
                            memory = memory,
                            shape = groupedItemShape(index, vm.memories.size),
                            enabled = !vm.busy,
                            onDetails = { detail = memory },
                            onEdit = { edit(memory) },
                            onForget = { vm.clearFeedback(); forgetting = memory },
                            modifier = Modifier.padding(bottom = GroupedItemGap).animateItem(),
                        )
                    }
                    if (vm.hasMore) item(key = "more") {
                        OutlinedButton(
                            onClick = vm::more,
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(48.dp),
                        ) { Text("Load more memories") }
                    }
                }
                TabProfile -> {
                    item(key = "profile-intro") {
                        MemoryBanner(
                            "A summary Supermemory builds from your memories — not another memory. To correct a detail, edit the memory it came from.",
                            MemoryTone.Info,
                            Modifier.padding(top = Spacing.base, bottom = Spacing.l),
                        )
                    }
                    vm.profileNote?.let { message ->
                        item(key = "profile-note") { MemoryBanner(message, MemoryTone.Info, Modifier.padding(bottom = Spacing.l)) }
                    }
                    item(key = "profile-stable") {
                        ProfileGroup("Lasting details", "Preferences and facts that stay useful", Icons.Outlined.Lightbulb, vm.profile.stable)
                    }
                    item(key = "profile-recent") {
                        ProfileGroup("Recently relevant", "Projects and context that may change", Icons.Outlined.Update, vm.profile.recent)
                    }
                }
                else -> {
                    item(key = "review-intro") {
                        MemorySectionHeader(
                            "Suggestions to review",
                            supporting = "Connections Supermemory inferred. Keep only what feels accurate.",
                            modifier = Modifier.padding(top = Spacing.base),
                        )
                    }
                    vm.suggestionsNote?.let { message ->
                        item(key = "review-note") { MemoryBanner(message, MemoryTone.Info, Modifier.padding(bottom = Spacing.m)) }
                    }
                    if (vm.suggestions.isEmpty() && !vm.busy && vm.suggestionsNote == null && vm.error == null) item(key = "review-empty") {
                        MemoryEmptyState(
                            Icons.Default.TaskAlt, MaterialShapes.SoftBurst,
                            title = "Nothing to review",
                            body = "New suggestions appear here when Supermemory finds a useful connection.",
                        )
                    }
                    items(vm.suggestions, key = { "suggestion:${it.id}" }) { memory ->
                        SuggestionCard(
                            memory = memory,
                            enabled = !vm.busy,
                            onKeep = { vm.review(memory, true) },
                            onDismiss = { vm.review(memory, false) },
                            modifier = Modifier.padding(bottom = Spacing.m).animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (editing) AlertDialog(onDismissRequest = { if (!vm.busy) editing = false },
        title = { Text(if (editor == null) "Add a memory" else "Edit memory") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Keep it specific. Never include passwords or API keys.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(draft, { draft = it.take(4000) }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy,
                label = { Text("What should be remembered?") }, minLines = 3, maxLines = 8,
                supportingText = { Text("${draft.length} / 4,000") })
            vm.error?.let { MemoryBanner(it, MemoryTone.Error) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { vm.save(editor?.id, draft) { editing = false } }, enabled = !vm.busy && draft.isNotBlank()) { Text("Save memory") } },
        dismissButton = { TextButton(onClick = { editing = false }, enabled = !vm.busy) { Text("Cancel") } })

    forgetting?.let { memory -> AlertDialog(onDismissRequest = { if (!vm.busy) forgetting = null },
        title = { Text("Forget this memory?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(memory.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("It will no longer be an active memory. Supermemory can retain its history and sources; learning from those sources may recreate it. Manage source data in your dashboard for a more complete removal.", style = MaterialTheme.typography.bodySmall)
            vm.error?.let { MemoryBanner(it, MemoryTone.Error) }
        } },
        confirmButton = { TextButton(onClick = { vm.forget(memory) { forgetting = null } }, enabled = !vm.busy) { Text("Forget memory", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { forgetting = null }, enabled = !vm.busy) { Text("Cancel") } }) }

    detail?.let { memory ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Memory details", style = MaterialTheme.typography.headlineSmall) }
                item { SelectionContainer { Text(memory.text, style = MaterialTheme.typography.bodyLarge) } }
                item { Text("Updated ${memoryDate(memory.updated)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { HorizontalDivider(); Text("Sources", modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall) }
                if (memory.sources.isEmpty()) item { Text("No source information returned by Supermemory.", style = MaterialTheme.typography.bodyMedium) }
                items(memory.sources) { source -> SelectionContainer { Text(source, style = MaterialTheme.typography.bodySmall) } }
                if (memory.history.isNotEmpty()) {
                    item { Text("Earlier versions", style = MaterialTheme.typography.titleSmall) }
                    items(memory.history) { previous -> SelectionContainer { Text(previous, style = MaterialTheme.typography.bodyMedium) } }
                }
            }
        }
    }
    vm.cleanupPlan?.takeUnless { it.isEmpty }?.let { plan ->
        AlertDialog(
            onDismissRequest = vm::dismissCleanup,
            title = { Text("Clean up memories?") },
            text = {
                Text(buildString {
                    append("This will forget ${plan.discard.size} low-quality ")
                    append(if (plan.discard.size == 1) "memory" else "memories")
                    append(". ")
                    if (plan.duplicateGroups > 0) append("One copy from each of ${plan.duplicateGroups} duplicate groups will be kept. ")
                    if (plan.metaMemories > 0) append("${plan.metaMemories} assistant-meta memories will be removed. ")
                    append("Source conversations in Supermemory are not deleted and may recreate facts later.")
                })
            },
            confirmButton = { TextButton(onClick = vm::applyCleanup, enabled = !vm.busy) { Text("Clean up") } },
            dismissButton = { TextButton(onClick = vm::dismissCleanup) { Text("Cancel") } },
        )
    }
}


/** The library search: a filled pill rather than an outlined form field, searching by meaning. */
@Composable
private fun MemorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    showClear: Boolean,
    enabled: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text("Search by meaning…") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            Row {
                if (showClear) {
                    IconButton(onClick = onClear, enabled = enabled) { Icon(Icons.Default.Close, "Clear search") }
                }
                if (query.isNotBlank()) {
                    FilledIconButton(onClick = onSearch, enabled = enabled, modifier = Modifier.padding(end = Spacing.xs)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Search memories")
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (enabled) onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * One memory in the connected list. Tap opens its details; long-press (or the overflow button)
 * offers edit and forget. The footer carries when it changed and how many sources back it.
 */
@Composable
private fun MemoryListRow(
    memory: RemoteMemory,
    shape: androidx.compose.ui.graphics.Shape,
    enabled: Boolean,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember(memory.id) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(shape = shape, color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = "View memory details",
                        onClick = onDetails,
                        onLongClickLabel = "Memory options",
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                        },
                    )
                    .padding(start = Spacing.base, top = Spacing.m, bottom = Spacing.m, end = Spacing.xs),
            ) {
                Column(Modifier.weight(1f).padding(top = Spacing.xs)) {
                    Text(memory.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            buildString {
                                append(memoryDate(memory.updated))
                                if (memory.sources.isNotEmpty()) {
                                    append(" · ")
                                    append(if (memory.sources.size == 1) "1 source" else "${memory.sources.size} sources")
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Memory options", tint = cs.onSurfaceVariant) }
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("View details") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menu = false; onDetails() })
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, enabled = enabled, onClick = { menu = false; onEdit() })
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                DropdownMenuItem(
                    text = { Text("Forget", color = cs.error) },
                    leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = cs.error) },
                    enabled = enabled,
                    onClick = { menu = false; onForget() },
                )
            }
        }
    }
}

/** A profile group: a collapsible header with its count, then its facts as one connected list. */
@Composable
private fun ProfileGroup(title: String, subtitle: String, icon: ImageVector, facts: List<String>) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    val chevron by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "profileChevron",
    )
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(bottom = Spacing.xl)) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(vertical = Spacing.s, horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (facts.isNotEmpty()) {
                            Spacer(Modifier.width(Spacing.s))
                            Surface(shape = CircleShape, color = cs.surfaceContainerHighest) {
                                Text(
                                    facts.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(
                    Icons.Default.ExpandMore,
                    if (expanded) "Collapse $title" else "Expand $title",
                    Modifier.rotate(chevron),
                    tint = cs.onSurfaceVariant,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
                if (facts.isEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No details yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.base),
                        )
                    }
                }
                facts.forEachIndexed { index, fact ->
                    Surface(shape = groupedItemShape(index, facts.size), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(fact, modifier = Modifier.padding(Spacing.base), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** A suggestion to review: its text, and Dismiss / Keep given equal weight so neither is the default. */
@Composable
private fun SuggestionCard(
    memory: RemoteMemory,
    enabled: Boolean,
    onKeep: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.Top) {
                MemoryMark(
                    Icons.Default.TipsAndUpdates, MaterialShapes.SoftBurst,
                    container = cs.tertiaryContainer, onContainer = cs.onTertiaryContainer, size = 36.dp,
                )
                Spacer(Modifier.width(Spacing.m))
                Text(memory.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 6.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.base),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Dismiss")
                }
                FilledTonalButton(onClick = onKeep, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Keep")
                }
            }
        }
    }
}

internal fun memoryDate(raw: String): String = runCatching {
    val date = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrElse { LocalDate.parse(raw) }
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault("Date unavailable")
