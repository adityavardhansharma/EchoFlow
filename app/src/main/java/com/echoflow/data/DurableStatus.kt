package com.echoflow.data

/**
 * The one transition rule browser sessions and research runs share: once a job has ended it
 * stays ended.
 *
 * Video jobs don't use it. Their "completed" is OpenRouter's word for "the render is done",
 * after which we still move the row to "downloading", so for them completed isn't the end.
 *
 * Statuses stay the plain strings Room already stores, so older sideloaded builds can still
 * read them. What this adds is a check at the write: a coroutine that wakes up late with a
 * stale copy of the row (a poll after a cancel, a step after a stop) can no longer turn a
 * finished job back into a running one.
 */
object DurableStatus {
    /**
     * Whether a row stored with status [stored] may be written with status [next]. A missing
     * row ([stored] null) is a new job. A finished job may only be rewritten with its own status,
     * which still lets its phase text or timestamps change.
     */
    fun canMove(stored: String?, next: String, terminal: Set<String>): Boolean =
        stored == null || stored !in terminal || stored == next
}
