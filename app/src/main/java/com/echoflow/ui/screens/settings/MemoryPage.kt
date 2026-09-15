@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.echoflow.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.data.AppDatabase
import com.echoflow.data.memory.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MemoryViewModel internal constructor(
    application: Application,
    val settings: MemorySettings,
    private val clientFactory: (String, String) -> SupermemoryClient,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, MemorySettings(application), { key, space -> SupermemoryClient(key, space) })
    var connected by mutableStateOf(settings.connected); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var billing by mutableStateOf<MemoryBilling?>(null); private set
    var billingNote by mutableStateOf<String?>(null); private set
    var profile by mutableStateOf(MemoryProfile(emptyList(), emptyList())); private set
    var memories by mutableStateOf<List<RemoteMemory>>(emptyList()); private set
    var suggestions by mutableStateOf<List<RemoteMemory>>(emptyList()); private set
    var hasMore by mutableStateOf(false); private set
    var recall by mutableStateOf(settings.recall)
    var learn by mutableStateOf(settings.learn)
    var local by mutableStateOf(settings.allowLocal)
    private var page = 1
    private fun client() = clientFactory(settings.key, settings.space)
    private fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true; error = null
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is MemoryApiException || e is IllegalArgumentException || e is IllegalStateException) e.message else "Couldn't reach Supermemory. Check your connection and try again." }
            finally { busy = false }
        }
    }
    fun connect(key: String, space: String) = action {
        require(space.matches(Regex("[a-zA-Z0-9_:-]{1,100}"))) { "Choose a valid memory space (letters, numbers, hyphens or underscores)." }
        require(key.isNotBlank()) { "Enter an API key." }
        clientFactory(key.trim(), space).profile()
        settings.connect(key, space); connected = true; learn = false
        loadBilling()
    }
    private suspend fun loadBilling() {
        try { billing = client().billing(); settings.plan = billing!!.plan; billingNote = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { billing = null; billingNote = if (e is MemoryApiException && e.code == 403)
            "This key can't read billing. Memory can still work. View usage in the Supermemory dashboard."
            else "Usage is temporarily unavailable. Your memory settings are unchanged." }
    }
    fun refreshBilling() = action { loadBilling() }
    fun refresh() = action {
        val result = client().list(); memories = result.entries; hasMore = result.hasMore; page = 1
        profile = client().profile()
        suggestions = try { client().reviewQueue() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
    fun more() = action { val result = client().list(page + 1); memories = (memories + result.entries).distinctBy { it.id }; hasMore = result.hasMore; page++ }
    fun search(query: String) = action {
        if (query.isBlank()) { val result = client().list(); memories = result.entries; hasMore = result.hasMore; page = 1 }
        else { memories = client().search(query); hasMore = false }
    }
    fun save(id: String?, text: String, onSaved: () -> Unit = {}) = action {
        require(text.isNotBlank() && text.length <= 4000) { "Use between 1 and 4,000 characters." }
        if (id == null) client().add(text) else client().edit(id, text)
        onSaved()
        val result = client().list(); memories = result.entries; hasMore = result.hasMore; page = 1
        profile = client().profile()
    }
    fun forget(memory: RemoteMemory) = action {
        client().forget(memory.id); memories = memories.filterNot { it.id == memory.id }; profile = client().profile()
    }
    fun review(memory: RemoteMemory, approve: Boolean) = action {
        client().review(memory.id, approve); suggestions = suggestions.filterNot { it.id == memory.id }
        val result = client().list(); memories = result.entries; hasMore = result.hasMore; page = 1
    }
    fun disconnect() = action {
        settings.disconnect(); connected = false; learn = false; billing = null; profile = MemoryProfile(emptyList(), emptyList())
        memories = emptyList(); suggestions = emptyList()
        androidx.work.WorkManager.getInstance(getApplication()).cancelUniqueWork("memory-learning")
        AppDatabase.getDatabase(getApplication()).memorySyncDao().clear()
    }
    fun setLearning(enabled: Boolean) = action {
        learn = enabled; settings.learn = enabled
        if (!enabled) {
            androidx.work.WorkManager.getInstance(getApplication()).cancelUniqueWork("memory-learning")
            AppDatabase.getDatabase(getApplication()).memorySyncDao().clear()
        }
    }
}

@Composable
internal fun MemoryPage(onBack: () -> Unit, onMemories: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var key by remember { mutableStateOf("") } // Never save API keys in instance state.
    var space by rememberSaveable { mutableStateOf(vm.settings.space) }
    var consent by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    LaunchedEffect(vm.connected) { if (vm.connected) key = "" }
    LaunchedEffect(Unit) { if (vm.connected) vm.refreshBilling() }
    val context = LocalContext.current
    SettingsPageScaffold("Memory", "A little continuity, on your terms", onBack) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Supermemory", "EchoBrain").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (tab == 1) {
            MemoryBlock("EchoBrain", "Coming soon") {
                Text("A future memory system built into EchoFlow. Nothing to connect yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            MemoryBlock(if (vm.connected) "Connected to Supermemory" else "Bring your own memory", if (vm.connected) vm.settings.space else "Your account. Your API key.") {
                if (!vm.connected) {
                    Text("Recall relevant details when they're useful, without loading your entire memory into every chat.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(key, { key = it }, label = { Text("Supermemory API key") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(space, { space = it.trim() }, label = { Text("Memory space") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Use the same space to share memory across your devices.") })
                    Text("Connecting sends a profile request. Learning stays off until you enable it.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.connect(key, space) }, enabled = !vm.busy && key.isNotBlank()) { Text("Connect") }
                } else {
                    val billing = vm.billing
                    Text(billing?.plan?.replaceFirstChar { it.uppercase() }?.let { "$it account" } ?: "Account connected", style = MaterialTheme.typography.titleMedium)
                    if (billing?.used != null && billing.limit != null && billing.limit > 0) {
                        LinearProgressIndicator(progress = { (billing.used / billing.limit).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        val currency = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US)
                        Text("${currency.format(billing.used)} used · ${currency.format((billing.limit - billing.used).coerceAtLeast(0.0))} left", style = MaterialTheme.typography.bodySmall)
                    } else if (billing?.used != null) Text("${java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(billing.used)} used · no fixed limit reported", style = MaterialTheme.typography.bodySmall)
                    else Text(vm.billingNote ?: "Your API key doesn't expose a usage balance.", style = MaterialTheme.typography.bodySmall)
                    billing?.reset?.let { Text("Resets $it", style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(onClick = { vm.refreshBilling() }, enabled = !vm.busy) { Text("Refresh") }
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.supermemory.ai"))) }) { Text("Dashboard ↗") }
                    }
                }
            }
            if (vm.connected) {
                Spacer(Modifier.height(16.dp))
                MemoryBlock("In your conversations", "You decide what crosses the boundary") {
                    MemorySwitch("Use memory", "Let supported models recall relevant details and save facts you explicitly ask them to remember.", vm.recall, !vm.busy) { vm.recall = it; vm.settings.recall = it }
                    HorizontalDivider()
                    MemorySwitch("Learn from conversations", "Send new chat text to Supermemory to learn lasting preferences, facts and projects.", vm.learn, !vm.busy) { if (it) consent = true else vm.setLearning(false) }
                    HorizontalDivider()
                    MemorySwitch("Include on-device chats", "Allows eligible local chat text to leave your device for Supermemory.", vm.local, !vm.busy) { vm.local = it; vm.settings.allowLocal = it }
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onMemories, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("My Memories →") }
                Text("Review what's known, add a fact, or forget something.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                Text("Memory is available in standard chats with tool-capable models. Local models and specialised modes may not support automatic recall. Never store passwords or API keys.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.settings.learningNote.isNotBlank()) {
                    Text(vm.settings.learningNote, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { MemoryLearning.schedule(context) }, enabled = vm.learn) { Text("Retry learning") }
                }
                Text("Deleting a chat only removes its local copy. Manage already-uploaded source conversations in Supermemory. Chats used with local models while cloud memory is off are excluded from learning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { disconnect = true }, enabled = !vm.busy) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
            MemoryFeedback(vm)
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Let Supermemory learn?") },
        text = { Text("New user messages and assistant replies are sent to your Supermemory account. Attachments, reasoning and tool results are excluded. Relevant memories may be shared with the model answering you. Existing chat history isn't imported. You can turn this off at any time.") },
        confirmButton = { TextButton(onClick = { vm.setLearning(true); consent = false }) { Text("Enable learning") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("Not now") } })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Supermemory?") },
        text = { Text("Stops future memory requests and removes the key from this device. Existing data stays in your Supermemory account.") },
        confirmButton = { TextButton(onClick = { vm.disconnect(); disconnect = false }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}

@Composable
internal fun MyMemoriesPage(onBack: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<RemoteMemory?>(null) }
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var forget by remember { mutableStateOf<RemoteMemory?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }
    SettingsPageScaffold("My Memories", "Your Supermemory space", onBack) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalButton(onClick = { editor = null; text = ""; editing = true }, enabled = !vm.busy) { Text("Add a memory") }
            TextButton(onClick = { vm.refresh() }, enabled = !vm.busy) { Text("Refresh") }
        }
        Spacer(Modifier.height(16.dp))
        if (vm.profile.stable.isNotEmpty() || vm.profile.recent.isNotEmpty()) MemoryBlock("About you", "A living summary, not a new source of truth") {
            if (vm.profile.stable.isNotEmpty()) { Text("Lasting details", style = MaterialTheme.typography.labelLarge); vm.profile.stable.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            if (vm.profile.recent.isNotEmpty()) { Text("Recently relevant", style = MaterialTheme.typography.labelLarge); vm.profile.recent.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(query, { query = it }, label = { Text("Find a memory") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            TextButton(onClick = { vm.search(query) }, enabled = !vm.busy) { Text("Find") }
        })
        MemoryFeedback(vm)
        vm.suggestions.forEach { memory ->
            MemoryBlock("Suggested connection", "Supermemory inferred this — is it right?") {
                Text(memory.text)
                Row { TextButton(onClick = { vm.review(memory, true) }, enabled = !vm.busy) { Text("Keep") }; TextButton(onClick = { vm.review(memory, false) }, enabled = !vm.busy) { Text("Dismiss") } }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (vm.memories.isEmpty() && !vm.busy && vm.error == null) MemoryBlock("Nothing here yet", "Memory grows with you") {
            Text("Add something you'd like remembered, or enable learning. New conversations can take a little time to become memories.", style = MaterialTheme.typography.bodyMedium)
        }
        vm.memories.forEach { memory ->
            Spacer(Modifier.height(8.dp))
            MemoryBlock(null, null) {
                Text(memory.text, style = MaterialTheme.typography.bodyLarge)
                Row {
                    TextButton(onClick = { expanded = if (expanded == memory.id) null else memory.id }) { Text("Details") }
                    TextButton(onClick = { editor = memory; text = memory.text; editing = true }, enabled = !vm.busy) { Text("Edit") }
                    TextButton(onClick = { forget = memory }, enabled = !vm.busy) { Text("Forget") }
                }
                if (expanded == memory.id) {
                    Text(memory.updated.ifBlank { "Date unavailable" }, style = MaterialTheme.typography.bodySmall)
                    if (memory.sources.isNotEmpty()) Text("Source documents: ${memory.sources.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    memory.history.forEach { Text("Earlier: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (vm.hasMore) TextButton(onClick = { vm.more() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(if (editor == null) "Remember something" else "Edit memory") },
        text = { Column { OutlinedTextField(text, { text = it.take(4000) }, label = { Text("What should be remembered?") }, minLines = 3); MemoryFeedback(vm) } },
        confirmButton = { TextButton(onClick = { vm.save(editor?.id, text.trim()) { editing = false } }, enabled = text.isNotBlank() && !vm.busy) { Text("Save") } }, dismissButton = { TextButton(onClick = { editing = false }, enabled = !vm.busy) { Text("Cancel") } })
    forget?.let { memory -> AlertDialog(onDismissRequest = { forget = null }, title = { Text("Forget this memory?") },
        text = { Text("It will no longer be used as an active memory. Supermemory may retain its history and source conversations. Learning from those sources can recreate a fact; manage source data in your dashboard if needed.") },
        confirmButton = { TextButton(onClick = { vm.forget(memory); forget = null }) { Text("Forget") } }, dismissButton = { TextButton(onClick = { forget = null }) { Text("Keep") } }) }
}

@Composable private fun MemoryFeedback(vm: MemoryViewModel) {
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
}
@Composable private fun MemoryBlock(title: String?, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
@Composable private fun MemorySwitch(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange, enabled = enabled)
    }
}
