@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.echoflow.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.*
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.RichMarkdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun QuickToolsScreen(
    chat: ChatViewModel, settings: SettingsViewModel, incoming: SharedInput?,
    onConsumed: (Boolean) -> Unit, onClose: () -> Unit, onManageModels: () -> Unit,
) {
    val controller = chat.quickTasks
    val current by controller.current.collectAsState()
    val history by controller.history.collectAsState()
    val busy by controller.busy.collectAsState()
    val error by controller.error.collectAsState()
    val selected by settings.selectedModel.collectAsState()
    val cloudModels by settings.customModels.collectAsState()
    val directModels by settings.customProviderModels.collectAsState()
    val localModels by settings.localModels.collectAsState()
    val localEnabled by settings.localModelsEnabled.collectAsState()
    val apiKey by settings.apiKey.collectAsState()
    val config by settings.customProviderConfig.collectAsState()
    val directoryLoading by settings.orDirectoryLoading.collectAsState()
    val directoryError by settings.orDirectoryError.collectAsState()
    val projects by chat.projects.collectAsState()
    val choices = (DefaultChatModels.BUILT_IN + cloudModels.map { it.id to it.name } +
        directModels.map { it.id to "${it.group}: ${it.name}" } +
        if (localEnabled) localModels.map { it.id to "On device: ${it.name}" } else emptyList()).distinctBy { it.first }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var action by rememberSaveable(incoming?.id) { mutableStateOf("Explain") }
    var language by rememberSaveable(incoming?.id) { mutableStateOf("English") }
    var prompt by rememberSaveable(incoming?.id) { mutableStateOf(if (incoming == null) "" else QuickTaskPolicy.instruction("Explain", "English")) }
    var compare by rememberSaveable(incoming?.id) { mutableStateOf(incoming == null) }
    var first by rememberSaveable(incoming?.id) { mutableStateOf(selected) }
    var second by rememberSaveable(incoming?.id) { mutableStateOf("") }
    var judge by rememberSaveable { mutableStateOf(selected) }
    var picker by remember { mutableIntStateOf(0) }
    var firstIssue by remember { mutableStateOf<String?>("Checking model…") }
    var secondIssue by remember { mutableStateOf<String?>("Choose a second model.") }
    var judgeIssue by remember { mutableStateOf<String?>(null) }
    var readLink by rememberSaveable(incoming?.id) { mutableStateOf(false) }
    var projectId by rememberSaveable(incoming?.id) { mutableStateOf<String?>(null) }
    var newProject by rememberSaveable(incoming?.id) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var fullSource by remember { mutableStateOf(false) }
    val blankId = rememberSaveable { UUID.randomUUID().toString() }
    val input = incoming ?: SharedInput(blankId)
    fun taskModel(id: String) = TaskModel(id, choices.firstOrNull { it.first == id }?.second ?: id)
    LaunchedEffect(incoming?.id) { if (incoming?.hasImages == true) settings.loadOpenRouterDirectory() }
    LaunchedEffect(first, second, judge, input, apiKey, config, localModels, localEnabled, directoryLoading) {
        firstIssue = "Checking model…"; secondIssue = "Checking model…"
        firstIssue = chat.sharedModelIssue(first, input)
        secondIssue = chat.sharedModelIssue(second, input)
        judgeIssue = chat.sharedModelIssue(judge, SharedInput("analysis"))
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (incoming != null) onConsumed(true); onClose() }, enabled = !saving && (incoming == null || !busy)) {
                    Text(if (incoming != null) "Discard share" else "Close")
                }
                if (incoming == null) TextButton(onClick = { historyOpen = !historyOpen }) { Text(if (historyOpen) "Back" else "History") }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (incoming != null) "Share to EchoFlow" else "Compare models", style = MaterialTheme.typography.headlineSmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text("Task running")
                        TextButton(onClick = controller::cancel) { Text("Stop") }
                    }
                }
                when {
                    historyOpen && incoming == null -> {
                        if (history.isEmpty()) Text("Your shared tasks and comparisons will appear here.")
                        history.forEach { task ->
                            OutlinedCard(onClick = { controller.select(task); historyOpen = false }, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(task.prompt.take(120), fontWeight = FontWeight.SemiBold)
                                    Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(task.createdAt))} · ${task.status}")
                                    Text(QuickTaskJson.answers(task.answersJson).joinToString(" vs ") { it.model.name })
                                    TextButton(onClick = { controller.delete(task) }, enabled = !busy) { Text("Delete saved result") }
                                }
                            }
                        }
                        Button(onClick = { controller.newTask(); prompt = ""; compare = true; historyOpen = false }, enabled = !busy) { Text("New comparison") }
                    }
                    incoming == null && current != null -> {
                        val task = current!!
                        val answers = QuickTaskJson.answers(task.answersJson)
                        SelectionContainer { Text(task.prompt) }
                        TextButton(onClick = { fullSource = true }) { Text("Review the input sent to both models") }
                        if (task.status == "interrupted") Text("This task was interrupted when the app closed. Partial answers are preserved. Run a new comparison to try again.")
                        Text("Answers use the same supplied input. Links and factual claims in model answers are not independently verified.", style = MaterialTheme.typography.bodySmall)
                        if (answers.size == 2) Text("Swipe sideways to compare both answers.", style = MaterialTheme.typography.labelMedium)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardWidth = if (answers.size == 1) maxWidth else if (maxWidth >= 720.dp) (maxWidth - 12.dp) / 2 else (maxWidth - 24.dp).coerceAtLeast(260.dp)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                answers.forEach { answer ->
                                    OutlinedCard(Modifier.width(cardWidth)) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(answer.model.name, fontWeight = FontWeight.Bold)
                                            Text(answer.model.id, style = MaterialTheme.typography.labelSmall)
                                            Text("${if (task.status == "interrupted" && answer.status in setOf("queued", "running")) "interrupted" else answer.status} · ${"%.1f".format(answer.elapsedMs / 1000.0)} s")
                                            Text(answer.costUsd?.let { "Reported cost: $${"%.6f".format(java.util.Locale.US, it)}" }
                                                ?: if (answer.model.id.startsWith("local/")) "On device · no API charge" else "Cost not reported", style = MaterialTheme.typography.bodySmall)
                                            answer.outputTokens?.let { Text("Tokens: ${answer.inputTokens ?: "—"} in · $it out", style = MaterialTheme.typography.bodySmall) }
                                            answer.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                            SelectionContainer { RichMarkdown(answer.text.ifBlank { if (task.status == "interrupted") "No answer saved." else "Waiting for this model…" }) }
                                            TextButton(onClick = { clipboard.setText(AnnotatedString(answer.text)) }, enabled = answer.text.isNotBlank()) { Text("Copy answer") }
                                            if (answers.size == 2) OutlinedButton(onClick = { controller.prefer(answer.model.id) }, enabled = answer.status == "finished") {
                                                Text(if (task.preferredModelId == answer.model.id) "Your preferred answer ✓" else "I prefer this answer")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (answers.size == 2 && answers.all { it.status == "finished" }) {
                            Text("Analyze disagreements", style = MaterialTheme.typography.titleMedium)
                            Text("An additional model request identifies conflicting claims and differences. It does not verify facts or pick a factual winner. Provider charges may apply.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { picker = 3 }, enabled = !busy) { Text("Analysis model: ${taskModel(judge).name.ifBlank { "Choose model" }}") }
                            judgeIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { controller.analyze(taskModel(judge)) }, enabled = !busy && judgeIssue == null) { Text("Analyze differences") }
                        }
                        task.analysis?.let {
                            Text("Analysis by ${task.analysisModel}", style = MaterialTheme.typography.titleMedium)
                            SelectionContainer { RichMarkdown(it) }
                        }
                        Button(onClick = { controller.newTask(); prompt = ""; compare = true }, enabled = !busy) { Text("New comparison") }
                    }
                    else -> {
                        if (incoming != null) {
                            SelectionContainer { Text(incoming.text.take(1200).ifBlank { "Shared files" }) }
                            incoming.files.forEach { file ->
                                if (file.mime.startsWith("image/")) AsyncImage(file.uri, file.name, Modifier.fillMaxWidth().height(140.dp))
                                Text(file.name + if (file.text != null) " · text extracted on device" else "")
                            }
                            TextButton(onClick = { fullSource = true }) { Text("Review all shared text") }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickTaskPolicy.actions.forEach { option -> FilterChip(selected = action == option,
                                    onClick = { action = option; prompt = QuickTaskPolicy.instruction(option, language) }, label = { Text(option) }) }
                            }
                        }
                        if (action == "Save to project" && incoming != null) {
                            Text("Saved on your device. No model request is needed.")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = projectId == null, onClick = { projectId = null }, label = { Text("New project") })
                                projects.forEach { project -> FilterChip(selected = projectId == project.id, onClick = { projectId = project.id }, label = { Text(project.name) }) }
                            }
                            if (projectId == null) OutlinedTextField(newProject, { newProject = it }, label = { Text("Project name") }, modifier = Modifier.fillMaxWidth())
                            saveMessage?.let { Text(it) }
                            Button(enabled = !saving && (projectId != null || newProject.isNotBlank()), onClick = {
                                saving = true; saveMessage = null
                                scope.launch {
                                    try { chat.saveSharedToProject(incoming, projectId, newProject); onConsumed(true); onClose() }
                                    catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { saveMessage = e.message ?: "Could not save the share." }
                                    finally { saving = false }
                                }
                            }) { Text(if (saving) "Saving…" else "Save to project") }
                        } else {
                            if (action == "Translate" && incoming != null) OutlinedTextField(language,
                                { language = it; prompt = QuickTaskPolicy.instruction(action, it) }, label = { Text("Translate into") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(prompt, { prompt = it }, label = { Text(if (incoming == null) "Ask both models" else "Your instructions") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                            if (incoming != null) Row { Checkbox(compare, { compare = it }); Text("Compare answers from two models", Modifier.padding(top = 12.dp)) }
                            OutlinedButton(onClick = { picker = 1 }, enabled = !busy) { Text("${if (compare) "Model A" else "Model"}: ${taskModel(first).name.ifBlank { "Choose model" }}") }
                            firstIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (compare) {
                                OutlinedButton(onClick = { picker = 2 }, enabled = !busy) { Text("Model B: ${taskModel(second).name.ifBlank { "Choose model" }}") }
                                secondIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (first == second && first.isNotBlank()) Text("Choose two different models.", color = MaterialTheme.colorScheme.error)
                                Text("Both models receive the same input. Two on-device models run one after the other to limit memory use.", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = onManageModels) { Text("Configure models and API keys") }
                            if (incoming?.hasImages == true) {
                                if (directoryLoading) Text("Checking OpenRouter image capabilities…")
                                directoryError?.let { Text(it); TextButton(onClick = settings::loadOpenRouterDirectory) { Text("Retry model details") } }
                            }
                            SharedLinkReader.link(input.text)?.let { link ->
                                Row { Checkbox(readLink, { readLink = it }); Text("Read the linked page before sending", Modifier.padding(top = 12.dp)) }
                                Text(if (readLink) "EchoFlow will fetch $link and include a text excerpt. Sign-in and JavaScript pages may not be readable."
                                    else "Only the shared link and text will be sent; the page will not be opened.", style = MaterialTheme.typography.bodySmall)
                            }
                            val ids = if (compare) listOf(first, second) else listOf(first)
                            Text(if (ids.all { it.startsWith("local/") }) "These model requests run on your device."
                                else "Your content will be sent to the selected providers or endpoints. Their usage charges may apply.", style = MaterialTheme.typography.bodySmall)
                            Button(enabled = !busy && !saving && prompt.isNotBlank() && firstIssue == null && (!compare || (secondIssue == null && first != second)), onClick = {
                                controller.start(input, prompt, ids.map(::taskModel), readLink) { if (incoming != null) onConsumed(false) }
                            }) { Text(if (compare) "Send to both models" else "Send to selected model") }
                        }
                    }
                }
            }
        }
    }
    if (picker != 0) ModelPickerSheet(models = choices.filterNot { it.first.startsWith("local/") }, localModels = choices.filter { it.first.startsWith("local/") },
        selectedId = when (picker) { 1 -> first; 2 -> second; else -> judge }, onSelect = { id -> when (picker) { 1 -> first = id; 2 -> second = id; else -> judge = id }; picker = 0 },
        onManage = { picker = 0; onManageModels() }, onDismiss = { picker = 0 })
    if (fullSource) AlertDialog(onDismissRequest = { fullSource = false }, title = { Text("Shared reference material") },
        text = { SelectionContainer { Text((incoming ?: current?.let { QuickTaskJson.input(it.inputJson) } ?: input).modelText(), Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { fullSource = false }) { Text("Done") } })
}
