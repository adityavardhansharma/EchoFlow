package com.echoflow.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoflow.data.memory.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialises account operations without silently dropping work requested during navigation. */
class MemoryViewModel internal constructor(
    application: Application,
    val settings: MemorySettings,
    private val clientFactory: (String, String) -> SupermemoryClient,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, MemorySettings(application), { key, space -> SupermemoryClient(key, space) })
    var connected by mutableStateOf(settings.connected); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var billing by mutableStateOf<MemoryBilling?>(null); private set
    var billingNote by mutableStateOf<String?>(null); private set
    var profileNote by mutableStateOf<String?>(null); private set
    var suggestionsNote by mutableStateOf<String?>(null); private set
    var profile by mutableStateOf(MemoryProfile(emptyList(), emptyList())); private set
    var memories by mutableStateOf<List<RemoteMemory>>(emptyList()); private set
    var suggestions by mutableStateOf<List<RemoteMemory>>(emptyList()); private set
    var hasMore by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    var activeQuery by mutableStateOf(""); private set
    var recall by mutableStateOf(settings.recall); private set
    var learn by mutableStateOf(settings.learn); private set
    var local by mutableStateOf(settings.allowLocal); private set
    var learningNote by mutableStateOf(settings.learningNote); private set
    var learningStatus by mutableStateOf(MemoryLearningStatus()); private set
    var cleanupPlan by mutableStateOf<MemoryPolicy.CleanupPlan?>(null); private set
    private var page = 1
    private var pendingActions = 0
    private var learningRefreshJob: Job? = null
    private val actions = Mutex()

    init {
        viewModelScope.launch {
            settings.changes().collect {
                connected = it.connected; recall = it.recall; learn = it.learn; local = it.allowLocal
                learningNote = it.learningNote
            }
        }
        viewModelScope.launch { refreshLearningStatusDirect() }
    }

    private fun client(): SupermemoryClient {
        check(settings.connected) { "Connect Supermemory in Memory settings first." }
        return clientFactory(settings.key, settings.space)
    }
    private fun action(block: suspend () -> Unit) {
        pendingActions++; busy = true
        viewModelScope.launch {
            try {
                actions.withLock {
                    error = null; notice = null
                    try { block() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        error = if (e is MemoryApiException || e is IllegalArgumentException || e is IllegalStateException) e.message
                            else "Couldn't reach Supermemory. Check your connection and try again."
                    }
                }
            } finally { pendingActions--; busy = pendingActions > 0 }
        }
    }
    fun clearFeedback() { error = null; notice = null }
    fun connect(key: String, space: String) = action {
        require(space.matches(Regex("[a-zA-Z0-9_:-]{1,100}"))) { "Choose a valid memory space (letters, numbers, hyphens, underscores or colons)." }
        require(key.isNotBlank()) { "Enter an API key." }
        clientFactory(key.trim(), space).profile()
        settings.connect(key, space); connected = true; learn = false
        resetLibrary()
        MemoryLearning.pruneObsolete(getApplication())
        loadBilling()
    }
    private suspend fun loadBilling() {
        try { billing = client().billing(); settings.plan = billing!!.plan; billingNote = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            billing = null
            billingNote = if (e is MemoryApiException && e.code == 403)
                "This key can't read billing. View usage in the Supermemory dashboard."
            else "Usage is temporarily unavailable. Your memory settings are unchanged."
        }
    }
    private suspend fun loadList(query: String = activeQuery) {
        val result = if (query.isBlank()) client().list() else com.echoflow.data.memory.MemoryPage(client().search(query), false)
        memories = result.entries; hasMore = result.hasMore; page = 1; activeQuery = query; loaded = true
    }
    private suspend fun loadProfile() {
        try { profile = client().profile(); settings.cacheProfile(profile); profileNote = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { profileNote = "Couldn't refresh your profile. Any details below are from the last successful refresh." }
    }
    private suspend fun loadSuggestions() {
        try { suggestions = client().reviewQueue(); suggestionsNote = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            suggestionsNote = if (e is MemoryApiException && e.code == 403)
                "Suggestions aren't available to this API key. Your saved memories still work."
            else "Couldn't refresh suggestions. Try again shortly."
        }
    }
    fun refreshBilling() = action { loadBilling() }
    fun refresh() = action { loadList(); loadProfile(); loadSuggestions(); refreshLearningStatusDirect() }
    fun more() = action {
        if (!hasMore || activeQuery.isNotBlank()) return@action
        val result = client().list(page + 1)
        memories = (memories + result.entries).distinctBy { it.id }; hasMore = result.hasMore; page++
    }
    fun search(query: String) = action { loadList(query.trim()) }
    private suspend fun refreshAfterWrite() {
        settings.invalidateProfile()
        try { loadList() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { notice = "Your change was saved. Refresh to update this list." }
        loadProfile()
    }
    fun save(id: String?, text: String, onSaved: () -> Unit = {}) = action {
        val content = text.trim()
        require(content.isNotBlank() && content.length <= 4000) { "Use between 1 and 4,000 characters." }
        if (id == null) client().add(content) else client().edit(id, content)
        // Close only after the write succeeds; a subsequent read failure must not invite duplicate writes.
        onSaved(); notice = if (id == null) "Memory added." else "Memory updated."
        refreshAfterWrite()
    }
    fun forget(memory: RemoteMemory, onForgotten: () -> Unit = {}) = action {
        client().forget(memory.id)
        settings.invalidateProfile()
        memories = memories.filterNot { it.id == memory.id }
        onForgotten(); notice = "Memory forgotten."
        loadProfile()
    }
    fun review(memory: RemoteMemory, approve: Boolean) = action {
        client().review(memory.id, approve)
        suggestions = suggestions.filterNot { it.id == memory.id }
        notice = if (approve) "Suggestion kept." else "Suggestion dismissed."
        if (approve) refreshAfterWrite()
    }
    private fun resetLibrary() {
        profile = MemoryProfile(emptyList(), emptyList()); memories = emptyList(); suggestions = emptyList()
        profileNote = null; suggestionsNote = null; activeQuery = ""; loaded = false; hasMore = false; page = 1
        cleanupPlan = null; learningStatus = MemoryLearningStatus()
    }
    fun disconnect() = action {
        settings.disconnect(); connected = false; learn = false; billing = null; billingNote = null
        resetLibrary()
        // Cancel also prunes only older generations, serialized with ledger inserts.
        MemoryLearning.cancel(getApplication())
    }
    fun setLearning(enabled: Boolean) = action {
        settings.learn = enabled; learn = settings.learn
        if (enabled) MemoryLearning.schedule(getApplication()) else MemoryLearning.cancel(getApplication())
        refreshLearningStatusDirect()
    }
    fun updateRecall(enabled: Boolean) = action { settings.recall = enabled; recall = settings.recall }
    fun updateLocal(enabled: Boolean) = action { settings.allowLocal = enabled; local = settings.allowLocal }
    fun retryLearning() = action { MemoryLearning.retry(getApplication()); refreshLearningStatusDirect() }
    /** Starts immediate learning for eligible completed conversations. */
    fun learnNow() = action {
        val status = MemoryLearning.flushNow(getApplication())
        learningStatus = status
        notice = MemoryLearning.manualFlushNotice(status)
        watchLearningProgress()
    }

    /** Refreshes the ledger while the forced WorkManager job transitions to completion. */
    private fun watchLearningProgress() {
        learningRefreshJob?.cancel()
        learningRefreshJob = viewModelScope.launch {
            repeat(20) {
                delay(1_500)
                refreshLearningStatusDirect()
                if (learningStatus.queued == 0 && learningStatus.processing == 0) return@launch
            }
        }
    }

    fun refreshLearningStatus() = action { refreshLearningStatusDirect() }
    private suspend fun refreshLearningStatusDirect() {
        learningStatus = if (settings.connected) MemoryLearning.status(getApplication()) else MemoryLearningStatus()
    }

    /** Reads the complete library first; deletion happens only after a separate confirmation. */
    fun scanCleanup() = action {
        val all = mutableListOf<RemoteMemory>()
        var nextPage = 1
        var more: Boolean
        do {
            val result = client().list(nextPage++)
            all += result.entries
            more = result.hasMore
        } while (more)
        cleanupPlan = MemoryPolicy.cleanupPlan(all.distinctBy { it.id })
        if (cleanupPlan?.isEmpty == true) notice = "No duplicate or assistant-meta memories found."
    }

    fun dismissCleanup() { cleanupPlan = null }
    fun applyCleanup() = action {
        val plan = cleanupPlan ?: return@action
        var removed = 0
        var failed = 0
        plan.discard.forEach { memory ->
            try { client().forget(memory.id); removed++ }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed++ }
        }
        settings.invalidateProfile()
        cleanupPlan = null
        loadList()
        loadProfile()
        notice = when {
            failed == 0 -> "Cleaned up $removed low-quality ${if (removed == 1) "memory" else "memories"}."
            removed == 0 -> "Cleanup couldn't remove any memories. Try again."
            else -> "Cleaned up $removed memories; $failed couldn't be removed. Try again."
        }
    }
}
