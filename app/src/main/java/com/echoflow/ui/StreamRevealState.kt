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

    /**
     * True once a surface showing this reply has left (the user switched chats, or the row
     * scrolled out of the list). Text that arrived meanwhile isn't replayed on return.
     */
    @Volatile private var returning = false

    /** Segments some reader has already drawn part of; kept after that reader leaves. */
    private val drawn: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

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
        returning = true
        presentation.update {
            it.copy(
                pendingViewports = it.pendingViewports - viewport,
                viewports = it.viewports - viewport,
                trackingEnded = it.pendingViewports - viewport == emptySet<Any>() &&
                    it.viewports - viewport == emptySet<Any>(),
            )
        }
    }

    /**
     * Where a newly composed reader of [segmentIndex] should start revealing [length] characters
     * from: 0 the first time the reply is on screen, or all of it when the user comes back to a
     * reply they already had open, or when this segment was already drawn and its row is coming
     * back into view. Without this a remounted row would type the whole answer out again.
     */
    fun resumePoint(segmentIndex: Int, length: Int): Int {
        return if (returning || segmentIndex in drawn) length else 0
    }

    fun report(reader: Any, segmentIndex: Int, shown: Int) {
        if (shown > 0) drawn += segmentIndex
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
