package com.echoflow.ui.screens.schedules

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echoflow.data.AppDatabase
import com.echoflow.data.ChatMessage
import com.echoflow.data.ChatThread
import com.echoflow.data.ScheduleAgent
import com.echoflow.data.ScheduleDraft
import com.echoflow.data.ScheduleEvent
import com.echoflow.data.ScheduleManager
import com.echoflow.data.ScheduleModelRunner
import com.echoflow.data.SchedulePrompts
import com.echoflow.data.ScheduleRun
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleText
import com.echoflow.data.ScheduleTime
import com.echoflow.data.ScheduleTools
import com.echoflow.data.ScheduleWorkspace
import com.echoflow.data.toDraft
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Unsaved schedule drafts survive leaving the screen. Small and per-schedule, so preferences are
 * enough; the conversation itself lives in Room like every other chat.
 */
internal class ScheduleDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("schedule_drafts", Context.MODE_PRIVATE)

    fun load(scheduleId: String, fallbackModel: String): Pair<ScheduleDraft?, String?> {
        val raw = prefs.getString(scheduleId, null) ?: return null to null
        return runCatching {
            val json = JSONObject(raw)
            json.optJSONObject("draft")?.let { ScheduleDraft.fromJson(it, fallbackModel) } to json.optString("threadId").ifBlank { null }
        }.getOrDefault(null to null)
    }

    fun save(scheduleId: String, draft: ScheduleDraft?, threadId: String?) {
        prefs.edit().putString(scheduleId, JSONObject().apply {
            draft?.let { put("draft", it.toJson()) }
            threadId?.let { put("threadId", it) }
        }.toString()).apply()
    }

    fun clear(scheduleId: String) = prefs.edit().remove(scheduleId).apply()
}

/**
 * One schedule conversation. It owns the live draft and implements [ScheduleWorkspace], so the
 * model's tools and the person's taps on the card edit exactly the same state — nothing to sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleChatViewModel(
    application: Application,
    val scheduleId: String,
    openedThreadId: String?,
    defaultModel: String,
) : AndroidViewModel(application), ScheduleWorkspace {
    private val manager = ScheduleManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val runner = ScheduleModelRunner(application)
    private val store = ScheduleDraftStore(application)
    private val use24h = android.text.format.DateFormat.is24HourFormat(application)

    private val stored = store.load(scheduleId, defaultModel)
    private val _threadId = MutableStateFlow(openedThreadId ?: stored.second)
    val threadId: StateFlow<String?> = _threadId.asStateFlow()

    private val _draft = MutableStateFlow(stored.first)
    val draftState: StateFlow<ScheduleDraft?> = _draft.asStateFlow()

    private var latestSaved: ScheduleTask? = null
    val savedState: StateFlow<ScheduleTask?> = manager.task(scheduleId).map { task ->
        latestSaved = task
        if (task?.threadId != null && _threadId.value == null) _threadId.value = task.threadId
        task
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _model = MutableStateFlow(stored.first?.modelId ?: defaultModel)
    val model: StateFlow<String> = _model.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = _threadId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else database.messageDao().getMessagesForChat(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val running: StateFlow<Boolean> = manager.runs(scheduleId).map { runs -> runs.any { it.status == ScheduleRun.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _streaming = MutableStateFlow<String?>(null)
    /** The reply being written right now; "" while the model has not produced prose yet. */
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var replyJob: Job? = null

    init {
        viewModelScope.launch {
            // Adopt the saved model once the task loads, unless a draft already chose one.
            manager.taskNow(scheduleId)?.let { task ->
                latestSaved = task
                if (_draft.value == null) _model.value = task.modelId
                if (_threadId.value == null) _threadId.value = task.threadId
            }
        }
    }

    // ScheduleWorkspace ---------------------------------------------------------------------

    override val draft: ScheduleDraft? get() = _draft.value
    override val saved: ScheduleTask? get() = latestSaved
    override val modelId: String get() = _model.value

    override fun updateDraft(draft: ScheduleDraft) {
        val next = draft.copy(modelId = _model.value)
        // Editing back to exactly what is saved is not a change.
        _draft.value = next.takeUnless { it.sameAs(latestSaved?.toDraft()) }
        persistDraft()
    }

    override suspend fun save(): ScheduleTask {
        val working = _draft.value ?: latestSaved?.toDraft() ?: throw IllegalArgumentException("Describe the schedule first.")
        val thread = ensureThread(working.title)
        val resolved = working.copy(modelId = _model.value).resolve(latestSaved, id = scheduleId)
        val task = manager.save(resolved.copy(threadId = thread))
        latestSaved = task
        _draft.value = null
        store.clear(scheduleId)
        return task
    }

    override fun discard() {
        _draft.value = null
        latestSaved?.let { _model.value = it.modelId }
        persistDraft()
    }

    override suspend fun setStatus(status: String): ScheduleTask =
        manager.setStatus(scheduleId, status)?.also { latestSaved = it } ?: throw IllegalArgumentException("Save the schedule first.")

    override suspend fun runNow() = manager.runNow(scheduleId)

    // Actions from the screen --------------------------------------------------------------

    /** Hand edits from the card: a patch over whatever the person is looking at. */
    fun edit(transform: (ScheduleDraft) -> ScheduleDraft) {
        val base = _draft.value ?: latestSaved?.toDraft() ?: ScheduleDraft(modelId = _model.value)
        updateDraft(transform(base))
    }

    fun startManualDraft(seed: String) {
        if (_draft.value == null && latestSaved == null) {
            updateDraft(ScheduleDraft(title = "", instruction = seed.trim(), modelId = _model.value))
        }
    }

    fun selectModel(id: String) {
        _model.value = id
        if (_draft.value != null || latestSaved?.let { it.modelId != id } == true) edit { it.copy(modelId = id) }
    }

    /** The Save button: same path as the tool, plus a line in the chat recording it. */
    fun saveFromCard() = launchAction {
        val created = latestSaved == null
        val task = save()
        val cadence = ScheduleText.cadence(task, use24h) + (ScheduleText.until(task.toDraft().endDate)?.let { " · $it" } ?: "")
        val first = task.nextRunAt?.let { " · next run ${ScheduleText.occurrence(it, task.zoneId, use24h)}" }.orEmpty()
        manager.post(task, (if (created) "Created" else "Saved") + " · $cadence$first", ScheduleEvent(if (created) ScheduleEvent.CREATED else ScheduleEvent.SAVED))
    }

    fun discardFromCard() = discard()
    fun setStatusFromMenu(status: String) = launchAction { setStatus(status) }
    fun runNowFromMenu() = launchAction { runNow() }
    fun stopRun() = launchAction { manager.stopCurrentRun(scheduleId) }
    fun delete(onDone: () -> Unit) = launchAction {
        manager.delete(scheduleId)
        store.clear(scheduleId)
        onDone()
    }

    fun clearError() { _error.value = null }

    fun stopReply() { replyJob?.cancel() }

    fun send(text: String, modelLabel: String) {
        val content = text.trim()
        if (content.isEmpty() || replyJob?.isActive == true) return
        replyJob = viewModelScope.launch {
            _error.value = null
            val threadId = ensureThread(_draft.value?.title?.takeIf { it.isNotBlank() } ?: latestSaved?.title ?: "New schedule")
            val now = System.currentTimeMillis()
            database.messageDao().insertMessage(ChatMessage(UUID.randomUUID().toString(), threadId, "user", content, now))
            database.chatDao().touchUpdatedAt(threadId, now)
            _streaming.value = ""
            val edits = mutableListOf<String>()
            try {
                val history = database.messageDao().getMessagesForChatSync(threadId).takeLast(HISTORY).map(::forModel)
                val prompt = SchedulePrompts.conversation(SchedulePrompts.ConversationContext(
                    now = now, zoneId = latestSaved?.zoneId ?: TimeZone.getDefault().id, use24h = use24h,
                    modelLabel = modelLabel, saved = latestSaved, draft = _draft.value,
                    web = runner.webAccess(_model.value),
                ))
                val tools = ScheduleTools(this@ScheduleChatViewModel, use24h)
                val agent = ScheduleAgent(complete = { h -> runner.stream(_model.value, h, prompt) })
                val reply = agent.run(history, execute = { call ->
                    if (!tools.handles(call.name)) """{"ok":false,"error":"Unknown tool ${call.name}."}"""
                    else tools.execute(call).also { edits += it.edits }.result
                }, onText = { _streaming.value = it })
                val body = reply.text.ifBlank { if (edits.isNotEmpty()) "Done." else "" }
                if (body.isNotBlank()) {
                    database.messageDao().insertMessage(ChatMessage(
                        UUID.randomUUID().toString(), threadId, "assistant", body, System.currentTimeMillis(),
                        scheduleEvent = edits.takeIf { it.isNotEmpty() }?.let { ScheduleEvent(ScheduleEvent.EDITS, it.distinct()).toJson() },
                    ))
                }
                syncThreadTitle(threadId)
            } catch (e: CancellationException) {
                val partial = _streaming.value.orEmpty()
                if (partial.isNotBlank() || edits.isNotEmpty()) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    database.messageDao().insertMessage(ChatMessage(UUID.randomUUID().toString(), threadId, "assistant",
                        partial.ifBlank { "Stopped." }, System.currentTimeMillis(),
                        scheduleEvent = edits.takeIf { it.isNotEmpty() }?.let { ScheduleEvent(ScheduleEvent.EDITS, it.distinct()).toJson() }))
                }
                throw e
            } catch (e: Exception) {
                val partial = _streaming.value.orEmpty()
                if (partial.isNotBlank() || edits.isNotEmpty()) {
                    database.messageDao().insertMessage(ChatMessage(
                        UUID.randomUUID().toString(), threadId, "assistant",
                        partial.ifBlank { "Changes were applied, but I couldn't finish the reply." },
                        System.currentTimeMillis(),
                        scheduleEvent = edits.takeIf { it.isNotEmpty() }
                            ?.let { ScheduleEvent(ScheduleEvent.EDITS, it.distinct()).toJson() },
                    ))
                    syncThreadTitle(threadId)
                }
                _error.value = e.message ?: "The model couldn't reply. You can still edit the schedule on the card."
            } finally {
                _streaming.value = null
            }
        }
    }

    /** Next runs for the card's one-line preview, or the reason the draft can't be scheduled. */
    fun preview(draft: ScheduleDraft): Result<List<Long>> = runCatching {
        val task = draft.resolve(latestSaved, id = scheduleId)
        ScheduleTime.preview(task, 2)
    }

    // --------------------------------------------------------------------------------------

    private suspend fun ensureThread(title: String): String {
        _threadId.value?.takeIf { database.chatDao().getThreadById(it) != null }?.let { return it }
        latestSaved?.let { task -> manager.ensureThread(task.id)?.let { _threadId.value = it; return it } }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.chatDao().insertThread(ChatThread(id, title.ifBlank { "New schedule" }, now, now, scheduleId = scheduleId))
        _threadId.value = id
        persistDraft()
        return id
    }

    private suspend fun syncThreadTitle(threadId: String) {
        val title = latestSaved?.title ?: _draft.value?.title?.takeIf { it.isNotBlank() } ?: return
        database.chatDao().setTitle(threadId, title)
    }

    private fun persistDraft() {
        if (latestSaved != null && _draft.value == null) store.clear(scheduleId)
        else store.save(scheduleId, _draft.value, _threadId.value)
    }

    /** Run answers are labelled for the model so it can tell them apart from its own replies. */
    private fun forModel(message: ChatMessage): ChatMessage {
        val event = ScheduleEvent.parse(message.scheduleEvent) ?: return message
        val task = latestSaved
        val label = when (event.type) {
            ScheduleEvent.RUN -> "[Scheduled run answer" + (event.scheduledAt?.let { at ->
                " · " + ScheduleText.occurrence(at, task?.zoneId ?: TimeZone.getDefault().id, use24h)
            } ?: "") + "]\n"
            ScheduleEvent.EDITS -> ""
            else -> "[Schedule event] "
        }
        return message.copy(content = label + message.content)
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = e.message ?: "Something went wrong." }
        }
    }

    companion object {
        private const val HISTORY = 40

        fun factory(application: Application, scheduleId: String, threadId: String?, defaultModel: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { ScheduleChatViewModel(application, scheduleId, threadId, defaultModel) } }
    }
}
