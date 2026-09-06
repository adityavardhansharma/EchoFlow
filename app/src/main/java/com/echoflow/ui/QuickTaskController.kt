package com.echoflow.ui

import com.echoflow.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal class QuickTaskController(
    private val dao: QuickTaskDao,
    private val scope: CoroutineScope,
    private val prepare: suspend (String, String, SharedInput) -> Flow<StreamChunk>,
) {
    val open = MutableStateFlow(false)
    val current = MutableStateFlow<QuickTask?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val history = dao.observeAll().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val writes = Mutex()
    private val localRuns = Mutex()
    private var job: Job? = null
    private val startup = scope.launch { dao.interruptOrphans() }

    fun show() { open.value = true }
    fun select(task: QuickTask) { current.value = task; open.value = true }
    fun newTask() { current.value = null; error.value = null; open.value = true }
    fun close() { open.value = false }
    fun cancel() { job?.cancel() }

    fun start(input: SharedInput, prompt: String, models: List<TaskModel>, readLink: Boolean = false, onAccepted: () -> Unit = {}) {
        if (busy.value) { error.value = "A task is already running. Stop it or wait for it to finish."; return }
        busy.value = true; error.value = null
        job = scope.launch {
            var taskId: String? = null
            try {
                startup.join()
                require(prompt.isNotBlank()) { "Enter a task." }
                require(models.size in 1..2 && models.map { it.id }.distinct().size == models.size) { "Choose two different models to compare." }
                // Validate BOTH models before either can incur a provider charge.
                models.forEach { prepare(it.id, prompt, input) }
                val actualInput = if (readLink) SharedLinkReader().read(input) else input
                val flows = models.map { prepare(it.id, prompt, actualInput) }
                val task = QuickTask(UUID.randomUUID().toString(), prompt.trim(), QuickTaskJson.input(actualInput),
                    QuickTaskJson.answers(models.map { TaskAnswer(it) }), System.currentTimeMillis())
                dao.save(task); taskId = task.id; current.value = task; open.value = true; onAccepted()
                supervisorScope {
                    models.indices.map { index -> launch {
                        val execute: suspend () -> Unit = { collectAnswer(task.id, index, flows[index]) }
                        if (models[index].id.startsWith("local/")) localRuns.withLock { execute() } else execute()
                    } }.joinAll()
                }
                mutate(task.id) { it.copy(status = "finished") }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { taskId?.let { id -> mutate(id) { task ->
                    task.copy(status = "cancelled", answersJson = QuickTaskJson.answers(QuickTaskJson.answers(task.answersJson).map {
                        if (it.status in setOf("running", "queued")) it.copy(status = "cancelled") else it
                    }))
                } } }
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Could not start this task."
                taskId?.let { id -> mutate(id) { it.copy(status = "interrupted") } }
            } finally { busy.value = false }
        }
    }

    private suspend fun collectAnswer(id: String, index: Int, source: Flow<StreamChunk>) {
        val started = android.os.SystemClock.elapsedRealtime()
        var text = ""
        var cost: Double? = null
        var inputTokens: Int? = null
        var outputTokens: Int? = null
        var lastSaved = 0L
        suspend fun save(status: String, error: String? = null) = mutate(id) { task ->
            val answers = QuickTaskJson.answers(task.answersJson).toMutableList()
            answers[index] = answers[index].copy(text = text, status = status, error = error,
                elapsedMs = android.os.SystemClock.elapsedRealtime() - started, costUsd = cost,
                inputTokens = inputTokens, outputTokens = outputTokens)
            task.copy(answersJson = QuickTaskJson.answers(answers))
        }
        try {
            save("running")
            source.collect { chunk ->
                when (chunk) {
                    is StreamChunk.Content -> {
                        require(text.length + chunk.text.length <= 128_000) { "Answer exceeded the output limit." }
                        text += chunk.text
                    }
                    is StreamChunk.Usage -> { cost = chunk.costUsd; inputTokens = chunk.inputTokens; outputTokens = chunk.outputTokens }
                    else -> Unit
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastSaved >= 250) { save("running"); lastSaved = now }
            }
            require(text.isNotBlank()) { "The model returned no answer." }
            save("finished")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { save("failed", e.message ?: "Model request failed.") }
    }

    fun prefer(model: String) {
        val id = current.value?.id ?: return
        scope.launch { mutate(id) { task ->
            require(QuickTaskJson.answers(task.answersJson).any { it.model.id == model && it.status == "finished" })
            task.copy(preferredModelId = model)
        } }
    }

    fun analyze(model: TaskModel) {
        val selected = current.value ?: return
        if (busy.value) return
        busy.value = true; error.value = null
        job = scope.launch {
            try {
                val answers = QuickTaskJson.answers(selected.answersJson)
                require(answers.size == 2 && answers.all { it.status == "finished" }) { "Both answers must finish before analyzing differences." }
                val source = SharedInput(UUID.randomUUID().toString(), "Original task: ${selected.prompt}\n\n" +
                    answers.joinToString("\n\n") { "${it.model.name} (${it.model.id}):\n${it.text}" })
                val stream = prepare(model.id, QuickTaskPolicy.ANALYSIS, source)
                var analysis = ""
                stream.collect { if (it is StreamChunk.Content) {
                    require(analysis.length + it.text.length <= 64_000) { "Analysis is too long." }
                    analysis += it.text
                } }
                require(analysis.isNotBlank()) { "The model returned no analysis." }
                mutate(selected.id) { it.copy(analysis = analysis, analysisModel = model.name) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Could not analyze the answers." }
            finally { busy.value = false }
        }
    }

    fun delete(task: QuickTask) {
        if (busy.value) return
        scope.launch { dao.delete(task.id); if (current.value?.id == task.id) current.value = null }
    }

    private suspend fun mutate(id: String, update: (QuickTask) -> QuickTask) = writes.withLock {
        val latest = dao.get(id) ?: return@withLock
        val next = update(latest)
        dao.save(next)
        if (current.value?.id == id) current.value = next
    }
}
