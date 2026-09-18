package com.echoflow.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Per-reply acknowledgement of text actually revealed by a visible, started UI. The producer
 * waits only while the reply's viewport is visible; leaving the chat or backgrounding
 * the activity releases the wait. No Compose state or frame clock is owned by the ViewModel.
 */
class StreamRevealState {
    private data class Reader(val segmentIndex: Int, val shown: Int)
    private data class Presentation(
        val pendingViewports: Set<Any> = emptySet(),
        val viewports: Set<Any> = emptySet(),
        val readers: Map<Any, Reader> = emptyMap(),
        val trackingStarted: Boolean = false,
        val trackingEnded: Boolean = false,
    )
    private val presentation = MutableStateFlow(Presentation())

    /** Registers a started chat surface before LazyColumn has composed its streaming row. */
    fun beginViewport(viewport: Any) {
        presentation.update {
            it.copy(
                pendingViewports = it.pendingViewports + viewport,
                trackingStarted = true,
                trackingEnded = false,
            )
        }
    }

    fun attachViewport(viewport: Any) {
        presentation.update {
            it.copy(
                pendingViewports = it.pendingViewports - viewport,
                viewports = it.viewports + viewport,
            )
        }
    }

    fun detachViewport(viewport: Any) {
        presentation.update {
            // A false visibility sample before the first layout must not release the handoff.
            if (viewport in it.pendingViewports) it
            else it.copy(viewports = it.viewports - viewport)
        }
    }

    /** Abandons a pending or visible surface when its lifecycle owner leaves composition. */
    fun abandonViewport(viewport: Any) {
        presentation.update {
            it.copy(
                pendingViewports = it.pendingViewports - viewport,
                viewports = it.viewports - viewport,
                trackingEnded = it.pendingViewports - viewport == emptySet<Any>() &&
                    it.viewports - viewport == emptySet<Any>(),
            )
        }
    }

    fun report(reader: Any, segmentIndex: Int, shown: Int) {
        val progress = Reader(segmentIndex, shown)
        presentation.update {
            if (it.readers[reader] == progress) it else it.copy(readers = it.readers + (reader to progress))
        }
    }

    /** Marks a conditionally hidden segment as intentionally skipped by its visible reader. */
    fun reportSkipped(reader: Any, segmentIndex: Int) = report(reader, segmentIndex, Int.MAX_VALUE)

    fun detach(reader: Any) {
        presentation.update { it.copy(readers = it.readers - reader) }
    }

    suspend fun awaitRevealed(segments: List<StreamSegment>) {
        val tracking = withTimeoutOrNull(INITIAL_TRACKING_TIMEOUT_MS) {
            presentation.first { it.trackingStarted || it.trackingEnded }
        } ?: return
        if (tracking.trackingEnded) return
        presentation.first { mounted ->
            mounted.trackingEnded || when {
                mounted.pendingViewports.isNotEmpty() -> false
                mounted.viewports.isEmpty() -> true
                else -> segments.withIndex().all { (index, segment) ->
                    val readers = mounted.readers.values.filter { it.segmentIndex == index }
                    when (segment) {
                        // Include text not composed yet: the last network chunk may have just
                        // created a new answer segment after reasoning or a tool card.
                        is StreamSegment.Text -> readers.isNotEmpty() &&
                            readers.maxOf { it.shown } >= segment.text.length
                        is StreamSegment.Reasoning -> {
                            if (index != segments.lastIndex) {
                                true
                            } else {
                                readers.isNotEmpty() && readers.any { it.shown >= segment.text.length }
                            }
                        }
                        else -> true
                    }
                }
            }
        }
    }

    private companion object {
        const val INITIAL_TRACKING_TIMEOUT_MS = 1_500L
    }
}
