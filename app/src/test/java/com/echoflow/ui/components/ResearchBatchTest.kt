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
}
