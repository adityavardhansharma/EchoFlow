package com.echoflow.ui.components

import com.echoflow.data.ResearchStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchBatchTest {

    private fun step(id: String, state: String = ResearchStep.STATE_DONE) = ResearchStep(
        id = id,
        label = "Step $id",
        state = state,
    )

    @Test
    fun `empty timeline yields no batches`() {
        assertTrue(chunkResearchSteps(emptyList()).isEmpty())
    }

    @Test
    fun `fewer than five steps stay in a single batch`() {
        val batches = chunkResearchSteps(listOf(step("1"), step("2"), step("3")))
        assertEquals(1, batches.size)
        assertEquals(3, batches[0].steps.size)
        assertEquals(1, batches[0].index)
    }

    @Test
    fun `exactly five steps fill one batch`() {
        val steps = (1..5).map { step(it.toString()) }
        val batches = chunkResearchSteps(steps)
        assertEquals(1, batches.size)
        assertEquals(5, batches[0].steps.size)
    }

    @Test
    fun `sixth step opens a second batch`() {
        val steps = (1..6).map { step(it.toString()) }
        val batches = chunkResearchSteps(steps)
        assertEquals(2, batches.size)
        assertEquals(5, batches[0].steps.size)
        assertEquals(1, batches[1].steps.size)
        assertEquals(2, batches[1].index)
    }

    @Test
    fun `seventy three steps become fifteen batches with a short tail`() {
        val steps = (1..73).map { step(it.toString()) }
        val batches = chunkResearchSteps(steps)
        assertEquals(15, batches.size)
        assertTrue(batches.dropLast(1).all { it.steps.size == RESEARCH_BATCH_SIZE })
        assertEquals(3, batches.last().steps.size)
    }

    @Test
    fun `header prefers the active step label`() {
        val batch = ResearchBatch(
            index = 1,
            steps = listOf(
                step("1", ResearchStep.STATE_DONE),
                ResearchStep(id = "2", label = "Scanning suppliers", state = ResearchStep.STATE_ACTIVE),
                step("3", ResearchStep.STATE_PENDING),
            ),
        )
        assertEquals("Scanning suppliers", batch.headerText("Researching…"))
        assertTrue(batch.hasActive)
        assertFalse(batch.allTerminal)
    }

    @Test
    fun `settled batch reports thought duration when timestamps exist`() {
        val batch = ResearchBatch(
            index = 1,
            steps = listOf(
                ResearchStep(id = "1", label = "A", state = ResearchStep.STATE_DONE, startedAt = 1_000L, endedAt = 3_000L),
                ResearchStep(id = "2", label = "B", state = ResearchStep.STATE_DONE, startedAt = 3_000L, endedAt = 5_500L),
            ),
        )
        assertEquals("Thought for 4s", batch.headerText(null))
    }

    @Test
    fun `content key ignores state transitions on the same steps`() {
        val active = ResearchBatch(
            index = 1,
            steps = listOf(
                ResearchStep(id = "search-0", label = "Who supplies cones?", state = ResearchStep.STATE_ACTIVE),
            ),
        )
        val done = ResearchBatch(
            index = 1,
            steps = listOf(
                ResearchStep(id = "search-0", label = "Who supplies cones?", state = ResearchStep.STATE_DONE),
            ),
        )
        assertEquals(active.contentKey, done.contentKey)
    }

    @Test
    fun `replan with same positional ids gets a new content key`() {
        // Engines key search rows by position (search-0, …). A replan reuses those ids with new
        // questions — contentKey must change so expand state does not follow the old plan.
        val before = ResearchBatch(
            index = 2,
            steps = listOf(
                ResearchStep(id = "search-0", label = "Old question A", state = ResearchStep.STATE_PENDING),
                ResearchStep(id = "search-1", label = "Old question B", state = ResearchStep.STATE_PENDING),
            ),
        )
        val after = ResearchBatch(
            index = 2,
            steps = listOf(
                ResearchStep(id = "search-0", label = "New question A", state = ResearchStep.STATE_PENDING),
                ResearchStep(id = "search-1", label = "New question B", state = ResearchStep.STATE_PENDING),
            ),
        )
        assertTrue(before.contentKey != after.contentKey)
        val manual = mapOf(before.contentKey to true)
        assertFalse(resolveBatchOpen(after, manual, defaultOpen = false))
        assertTrue(resolveBatchOpen(before, manual, defaultOpen = false))
    }

    @Test
    fun `manual open survives batch growth via content prefix`() {
        val small = ResearchBatch(
            index = 1,
            steps = listOf(
                step("1"),
                step("2"),
            ),
        )
        val grown = ResearchBatch(
            index = 1,
            steps = listOf(
                step("1"),
                step("2"),
                step("3"),
            ),
        )
        val manual = mapOf(small.contentKey to false)
        // User collapsed while the batch still had two steps; third step landing must not reopen it.
        assertFalse(resolveBatchOpen(grown, manual, defaultOpen = true))
        assertTrue(grown.contentKey.startsWith(small.contentKey + "\u001f"))
    }

    @Test
    fun `toggle replaces prefix keys so a replan cannot resurrect them`() {
        val small = ResearchBatch(index = 1, steps = listOf(step("1"), step("2")))
        val grown = ResearchBatch(index = 1, steps = listOf(step("1"), step("2"), step("3")))
        val afterCollapse = manualOpenAfterToggle(small, emptyMap(), expanded = false)
        val afterGrowToggle = manualOpenAfterToggle(grown, afterCollapse, expanded = true)
        // Only the grown key remains — the short key must not linger for a later replan match.
        assertEquals(setOf(grown.contentKey), afterGrowToggle.keys)
        assertTrue(afterGrowToggle.getValue(grown.contentKey))

        val replanned = ResearchBatch(
            index = 1,
            steps = listOf(
                ResearchStep(id = "1", label = "Relabelled", state = ResearchStep.STATE_DONE),
                step("2"),
                step("3"),
            ),
        )
        assertTrue(resolveBatchOpen(replanned, afterGrowToggle, defaultOpen = false) == false)
    }
}
