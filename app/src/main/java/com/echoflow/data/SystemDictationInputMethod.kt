package com.echoflow.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi

/** Stable identity for one editor connection. Restarts keep it; a different editor gets a new id. */
internal data class DictationEditorSession(
    val generation: Long,
    val packageName: String,
    val inputType: Int,
)

internal class DictationEditorTracker {
    private var nextGeneration = 0L
    var current: DictationEditorSession? = null
        private set

    fun start(packageName: String, inputType: Int, restarting: Boolean): DictationEditorSession {
        val generation = current?.generation?.takeIf { restarting } ?: ++nextGeneration
        return DictationEditorSession(generation, packageName, inputType).also { current = it }
    }

    fun finish() {
        current = null
    }
}

/** Android 13+ bridge to the same InputConnection used by the user's keyboard. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SystemDictationInputMethod(
    service: AccessibilityService,
    private val started: (EditorInfo, Boolean) -> Unit,
    private val finished: () -> Unit,
) : InputMethod(service) {
    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        started(attribute, restarting)
    }

    override fun onFinishInput() {
        finished()
        super.onFinishInput()
    }

    fun hasActiveConnection(): Boolean = currentInputStarted && currentInputConnection != null

    /** Dispatches insertion at the caret, replacing only the active selection. */
    fun commit(text: CharSequence): Boolean {
        if (!currentInputStarted) return false
        val connection = currentInputConnection ?: return false
        connection.commitText(text, 1, null)
        return true
    }
}
