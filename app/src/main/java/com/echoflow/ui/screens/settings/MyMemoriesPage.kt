@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.echoflow.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.data.memory.RemoteMemory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun MyMemoriesPage(onBack: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf(vm.activeQuery) }
    var editor by remember { mutableStateOf<RemoteMemory?>(null) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var forgetting by remember { mutableStateOf<RemoteMemory?>(null) }
    var detail by remember { mutableStateOf<RemoteMemory?>(null) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    fun edit(memory: RemoteMemory?) {
        vm.clearFeedback(); editor = memory; draft = memory?.text.orEmpty(); editing = true
    }
    fun search() { focus.clearFocus(); vm.search(query) }
    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(tab, vm.activeQuery) { listState.scrollToItem(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(title = { Text("My Memories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.refresh() }, enabled = !vm.busy && vm.connected) { Icon(Icons.Default.Refresh, "Refresh memories") }
                    IconButton(onClick = { edit(null) }, enabled = !vm.busy && vm.connected) { Icon(Icons.Default.Add, "Add a memory") }
                })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Memories", "Profile", "Suggestions").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, maxLines = 1) })
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!vm.connected) {
                    item { MemoryEmpty("Connect your memory", "Add a Supermemory API key in Memory settings to see your saved memories.") }
                    return@LazyColumn
                }
                if (tab == 0) item {
                    OutlinedTextField(value = query, onValueChange = { query = it.take(2000) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Search by meaning…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { if (!vm.busy) search() }),
                        trailingIcon = {
                            Row {
                                if (query.isNotEmpty() || vm.activeQuery.isNotEmpty()) IconButton(onClick = { query = ""; vm.search(""); focus.clearFocus() }, enabled = !vm.busy) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                                IconButton(onClick = { search() }, enabled = !vm.busy) { Icon(Icons.Default.ArrowForward, "Search memories") }
                            }
                        })
                }
                vm.error?.let { message -> item {
                    MemoryNotice(message, error = true)
                    TextButton(onClick = { if (tab == 0) vm.search(query) else vm.refresh() }, enabled = !vm.busy) { Text("Try again") }
                } }
                vm.notice?.let { message -> item { MemoryNotice(message) } }
                when (tab) {
                    0 -> {
                        item {
                            Text(if (vm.activeQuery.isBlank()) "SAVED MEMORIES" else "MATCHES FOR “${vm.activeQuery}”",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (vm.memories.isEmpty() && vm.loaded && !vm.busy && vm.error == null) item {
                            if (vm.activeQuery.isNotBlank()) MemoryEmpty("No matching memories", "Try a person's name, a project, or a different way of describing it.")
                            else {
                                MemoryEmpty("A little continuity starts here", "Save a preference, a project, or something you'd like to pick up next time. Learning can also build memory from new conversations.")
                                FilledTonalButton(onClick = { edit(null) }) { Text("Add your first memory") }
                            }
                        }
                        if (!vm.loaded && vm.busy) item { Text("Loading your memories…", style = MaterialTheme.typography.bodyMedium) }
                        items(vm.memories, key = { "memory:${it.id}" }) { memory ->
                            MemoryListRow(memory, enabled = !vm.busy, onDetails = { detail = memory },
                                onEdit = { edit(memory) }, onForget = { vm.clearFeedback(); forgetting = memory })
                        }
                        if (vm.hasMore) item {
                            TextButton(onClick = vm::more, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Load more memories") }
                        }
                        if (vm.activeQuery.isNotBlank() && vm.memories.isNotEmpty()) item {
                            Text("Showing the closest matches, not your entire library.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    1 -> {
                        item { Text("A summary, not another memory", style = MaterialTheme.typography.titleMedium)
                            Text("Supermemory puts these details together from your memories. To correct a fact, edit the saved memory it came from.",
                                modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        vm.profileNote?.let { message -> item { MemoryNotice(message) } }
                        item { ProfileGroup("Lasting details", "Preferences and facts that stay useful", vm.profile.stable) }
                        item { ProfileGroup("Recently relevant", "Projects and context that may change", vm.profile.recent) }
                    }
                    2 -> {
                        item { Text("You have the final say", style = MaterialTheme.typography.titleMedium)
                            Text("These are connections inferred by Supermemory. Keep only what feels accurate.",
                                modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        vm.suggestionsNote?.let { message -> item { MemoryNotice(message) } }
                        if (vm.suggestions.isEmpty() && !vm.busy && vm.suggestionsNote == null && vm.error == null) item {
                            MemoryEmpty("Nothing to review", "New suggestions will appear here when Supermemory finds a useful connection.")
                        }
                        items(vm.suggestions, key = { "suggestion:${it.id}" }) { memory ->
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(memory.text, style = MaterialTheme.typography.bodyLarge)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { vm.review(memory, false) }, enabled = !vm.busy) { Text("Dismiss") }
                                        FilledTonalButton(onClick = { vm.review(memory, true) }, enabled = !vm.busy) { Text("Keep") }
                                    }
                                }
                            }
                        }
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
            vm.error?.let { MemoryNotice(it, error = true) }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { vm.save(editor?.id, draft) { editing = false } }, enabled = !vm.busy && draft.isNotBlank()) { Text("Save memory") } },
        dismissButton = { TextButton(onClick = { editing = false }, enabled = !vm.busy) { Text("Cancel") } })

    forgetting?.let { memory -> AlertDialog(onDismissRequest = { if (!vm.busy) forgetting = null },
        title = { Text("Forget this memory?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(memory.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("It will no longer be an active memory. Supermemory can retain its history and sources; learning from those sources may recreate it. Manage source data in your dashboard for a more complete removal.", style = MaterialTheme.typography.bodySmall)
            vm.error?.let { MemoryNotice(it, error = true) }
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
}

@Composable
private fun MemoryListRow(memory: RemoteMemory, enabled: Boolean, onDetails: () -> Unit, onEdit: () -> Unit, onForget: () -> Unit) {
    var menu by remember(memory.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "View memory details", onClick = onDetails).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(memory.text, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Text(memoryDate(memory.updated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Memory options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("View details") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, enabled = enabled, onClick = { menu = false; onEdit() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Forget", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, enabled = enabled, onClick = { menu = false; onForget() })
                }
            }
        }
    }
}

@Composable
private fun ProfileGroup(title: String, subtitle: String, facts: List<String>) {
    var expanded by rememberSaveable(title) { mutableStateOf(true) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse $title" else "Expand $title") }
        }
        if (expanded) {
            if (facts.isEmpty()) Text("No details yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            facts.forEach { fact ->
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer { Text(fact, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun MemoryEmpty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoryNotice(message: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun memoryDate(raw: String): String = runCatching {
    val date = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate() }.getOrElse { LocalDate.parse(raw) }
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrDefault("Date unavailable")
