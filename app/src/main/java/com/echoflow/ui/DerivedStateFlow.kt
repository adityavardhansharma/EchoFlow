package com.echoflow.ui

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * A read-only view of one part of a [StateFlow]. Unlike `map { }.stateIn(...)` its [value] is
 * always in step with the source, with no scope and no lag, so a state machine held in one
 * flow can still expose the separate flows screens already read.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> {
    val source = this
    return object : StateFlow<R> {
        override val value: R get() = transform(source.value)
        override val replayCache: List<R> get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<R>): Nothing {
            source.map(transform).distinctUntilChanged().collect(collector)
            awaitCancellation()
        }
    }
}
