package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelProgressCollapserTest {

    @Test
    fun `parses Objective and Query prefixes`() {
        assertEquals(
            "Find cone suppliers",
            ParallelProgressCollapser.parseObjective("Objective: Find cone suppliers"),
        )
        assertEquals(
            "best waffle cone manufacturer",
            ParallelProgressCollapser.parseQuery("Query: best waffle cone manufacturer"),
        )
        assertEquals(null, ParallelProgressCollapser.parseObjective("Query: not an objective"))
    }

    @Test
    fun `many progress messages collapse to a handful of chapters`() {
        val c = ParallelProgressCollapser()
        // Simulate a noisy Parallel stream: plan spam + 2 objectives × several queries + tools.
        val events = listOf(
            "plan" to "Break the question into supplier and quality angles",
            "plan" to "Also check seasonal demand",
            "search" to "Objective: Identify major waffle cone manufacturers",
            "search" to "Query: waffle cone suppliers USA",
            "search" to "Query: commercial ice cream cone manufacturers",
            "search" to "Query: Joy Cone company profile",
            "tool" to "Reading joycone.com",
            "result" to "Found product catalog",
            "search" to "Objective: Compare quality and pricing signals",
            "search" to "Query: waffle cone wholesale price",
            "search" to "Query: food service cone reviews",
            "tool" to "Extracting pricing tables",
            "result" to "Three vendors stand out",
        )

        var maxOpenChapters = 0
        val seenIds = linkedSetOf<String>()
        events.forEach { (subtype, message) ->
            val steps = c.onProgressMessage(subtype, message)
            steps.forEach { seenIds += it.id }
            // Active chapters that are not DONE in this emit batch
            val open = steps.count { it.state == ResearchStep.STATE_ACTIVE }
            maxOpenChapters = maxOf(maxOpenChapters, open)
        }
        val terminal = c.onTerminal(failed = false)
        assertTrue(terminal.single().isTerminal)

        // One plan + two objectives (+ maybe work only if tools arrived first — they didn't).
        assertEquals(
            setOf(ParallelProgressCollapser.PLAN_ID, "parallel-obj-0", "parallel-obj-1"),
            seenIds,
        )
        // Never more than one ACTIVE row emitted at a time from a single event.
        assertEquals(1, maxOpenChapters)
        // Far fewer chapters than the 13 raw messages.
        assertTrue(seenIds.size <= 4)
        assertTrue(seenIds.size < events.size / 2)
    }

    @Test
    fun `queries fold into the active objective detail`() {
        val c = ParallelProgressCollapser()
        c.onProgressMessage("search", "Objective: Map suppliers")
        val afterQueries = c.onProgressMessage("search", "Query: alpha")
            .last()
            .let { active ->
                c.onProgressMessage("search", "Query: beta")
                c.onProgressMessage("search", "Query: gamma").last()
            }
        assertEquals("Map suppliers", afterQueries.label)
        assertEquals(ResearchStep.KIND_SEARCH, afterQueries.kind)
        assertEquals("3 queries", afterQueries.detail)
        assertEquals(ResearchStep.STATE_ACTIVE, afterQueries.state)
    }

    @Test
    fun `stats refresh active chapter meta without a new id`() {
        val c = ParallelProgressCollapser()
        val opened = c.onProgressMessage("search", "Objective: Read sources").single()
        val withStats = c.onStats(sourcesRead = 12, sourcesConsidered = 40).single()
        assertEquals(opened.id, withStats.id)
        assertTrue(withStats.detail!!.contains("12 of 40 read"))
    }

    @Test
    fun `replan-style second objective closes the first`() {
        val c = ParallelProgressCollapser()
        c.onProgressMessage("search", "Objective: First")
        val secondBatch = c.onProgressMessage("search", "Objective: Second")
        assertEquals(2, secondBatch.size)
        assertEquals(ResearchStep.STATE_DONE, secondBatch[0].state)
        assertEquals("First", secondBatch[0].label)
        assertEquals(ResearchStep.STATE_ACTIVE, secondBatch[1].state)
        assertEquals("Second", secondBatch[1].label)
        assertFalse(secondBatch[0].id == secondBatch[1].id)
    }

    @Test
    fun `tool events alone open one work chapter not one per tool`() {
        val c = ParallelProgressCollapser()
        val ids = buildSet {
            c.onProgressMessage("tool", "call A").forEach { add(it.id) }
            c.onProgressMessage("tool", "call B").forEach { add(it.id) }
            c.onProgressMessage("result", "finding").forEach { add(it.id) }
        }
        assertEquals(setOf("parallel-work-0"), ids)
    }
}
