package com.echoflow.data.jev

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Last Jev classification, kept in memory for the Echo Labs > Jev page.
 * Full per-turn history lives on the assistant message rows ([jevJson]).
 */
object JevStore {
    private val _last = MutableStateFlow<JevDecision?>(null)
    val last: StateFlow<JevDecision?> = _last.asStateFlow()

    fun record(decision: JevDecision) {
        _last.value = decision
    }

    fun clear() {
        _last.value = null
    }
}
