package com.echoflow.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.echoflow.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Queue incoming shares without overwriting an open draft; retain grants until copying finishes. */
class ShareIntakeViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = SharedInputStore(application)
    val input = MutableStateFlow<SharedInput?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null
    init {
        val id = saved.get<String>("inputId")
        if (id == null) drain() else {
            busy.value = true
            job = viewModelScope.launch {
                try { input.value = store.load(id) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { saved["inputId"] = null; error.value = "The shared draft could not be restored. Please share it again." }
                finally { busy.value = false }
            }
        }
    }
    fun receive(intent: Intent, initial: Boolean = false) {
        if (!SharedInputStore.accepts(intent)) return
        if (initial && saved.get<Boolean>("initialHandled") == true) return
        saved["initialHandled"] = true
        val queue = ArrayList(saved.get<ArrayList<Intent>>("queue").orEmpty())
        if (queue.size >= 5) { error.value = "Finish the pending shares before sharing more."; return }
        val clean = try { SharedInputStore.sanitize(intent) } catch (e: Exception) { error.value = e.message ?: "Invalid share."; return }
        val totalText = (queue + clean).sumOf { (it.getCharSequenceExtra(Intent.EXTRA_TEXT) ?: it.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))?.length ?: 0 }
        if (totalText > 100_000) { error.value = "Finish the pending shares before adding more text."; return }
        queue.add(clean)
        saved["queue"] = queue
        drain()
    }
    private fun drain() {
        if (job?.isActive == true || saved.get<String>("inputId") != null) return
        val intent = saved.get<ArrayList<Intent>>("queue")?.firstOrNull() ?: return
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            busy.value = true
            try {
                val imported = store.import(intent)
                store.persist(imported)
                saved["inputId"] = imported.id
                input.value = imported
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Could not read the shared content." }
            finally { busy.value = false }
            saved["queue"] = ArrayList(saved.get<ArrayList<Intent>>("queue").orEmpty().drop(1))
        }.also { task -> task.invokeOnCompletion { if (!task.isCancelled && saved.get<String>("inputId") == null) drain() }; task.start() }
    }
    fun consumed(discard: Boolean = false) {
        val previous = input.value
        saved["inputId"] = null
        input.value = null
        if (discard && previous != null) viewModelScope.launch { store.discard(previous) }
        drain()
    }
}
