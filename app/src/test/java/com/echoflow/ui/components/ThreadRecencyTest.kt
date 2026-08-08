package com.echoflow.ui.components

import com.echoflow.data.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Recency buckets are calendar-based, not span-based. "Yesterday" has to mean the previous
 * calendar day — a conversation touched at 23:50 must not still read as "today" at 00:10 the
 * next morning just because 24 hours have not elapsed.
 */
class ThreadRecencyTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private val now = at(2026, Calendar.JULY, 27, hour = 10)

    @Test fun `earlier the same day is today, however early`() {
        assertEquals(ThreadRecency.TODAY, ThreadRecency.bucketOf(at(2026, Calendar.JULY, 27, 0, 5), now))
        assertEquals(ThreadRecency.TODAY, ThreadRecency.bucketOf(now, now))
    }

    @Test fun `last night is yesterday even though it is under 24 hours ago`() {
        val lastNight = at(2026, Calendar.JULY, 26, hour = 23, minute = 50)
        assertEquals(ThreadRecency.YESTERDAY, ThreadRecency.bucketOf(lastNight, now))
    }

    @Test fun `the rest of the week, then everything older`() {
        assertEquals(ThreadRecency.THIS_WEEK, ThreadRecency.bucketOf(at(2026, Calendar.JULY, 23), now))
        assertEquals(ThreadRecency.EARLIER, ThreadRecency.bucketOf(at(2026, Calendar.JULY, 1), now))
        assertEquals(ThreadRecency.EARLIER, ThreadRecency.bucketOf(at(2025, Calendar.DECEMBER, 25), now))
    }

    @Test fun `sections come back newest-first and skip buckets with nothing in them`() {
        val threads = listOf(
            thread("old", at(2026, Calendar.JANUARY, 2)),
            thread("today", at(2026, Calendar.JULY, 27, 9)),
            thread("week", at(2026, Calendar.JULY, 24)),
        )

        val grouped = ThreadRecency.group(threads, now)

        assertEquals(listOf(ThreadRecency.TODAY, ThreadRecency.THIS_WEEK, ThreadRecency.EARLIER), grouped.map { it.first })
        assertEquals(listOf("today"), grouped[0].second.map { it.id })
        assertEquals(listOf("week"), grouped[1].second.map { it.id })
    }

    @Test fun `an empty list produces no sections rather than four empty ones`() {
        assertEquals(emptyList<Pair<String, List<ChatThread>>>(), ThreadRecency.group(emptyList(), now))
    }

    private fun thread(id: String, updatedAt: Long) =
        ChatThread(id = id, title = id, createdAt = updatedAt, updatedAt = updatedAt)
}
