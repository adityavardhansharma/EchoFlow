package com.echoflow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStreamPlannerTest {

    private fun chunks(plan: MarkdownStreamPlan) = plan.blocks.filterIsInstance<MarkdownChunk>()

    private fun visible(text: String) = text.filterNot { it.isWhitespace() }

    @Test
    fun frozenChunksNeverChangeWhileStreaming() {
        val planner = MarkdownStreamPlanner()
        val settled = HashMap<Int, String>()
        var prefix = ""
        for (token in streamTokens(STREAMING_SAMPLE)) {
            prefix += token
            val plan = planner.plan(prefix)
            plan.blocks.dropLast(1).forEach { block ->
                val source = (block as MarkdownChunk).source
                val before = settled.putIfAbsent(block.key, source)
                if (before != null) assertEquals("chunk at ${block.key} changed", before, source)
            }
            assertEquals(visible(prefix), visible(chunks(plan).joinToString("") { it.source }))
        }
        assertTrue("expected several chunks", settled.size > 5)
    }

    @Test
    fun aFinishedReplyPlansExactlyAsItStreamed() {
        val streamed = MarkdownStreamPlanner()
        var prefix = ""
        var last: MarkdownStreamPlan? = null
        for (token in streamTokens(STREAMING_SAMPLE)) {
            prefix += token
            last = streamed.plan(prefix)
        }
        assertEquals(MarkdownStreamPlanner().plan(STREAMING_SAMPLE).blocks, last!!.blocks)
    }

    @Test
    fun streamingOneCharacterAtATimeIsStable() {
        val text = STREAMING_SAMPLE.take(4_000)
        val planner = MarkdownStreamPlanner(chunkTarget = 1)
        val settled = HashMap<Int, String>()
        for (end in 1..text.length) {
            val plan = planner.plan(text.substring(0, end))
            plan.blocks.dropLast(1).forEach { block ->
                val source = (block as MarkdownChunk).source
                val before = settled.putIfAbsent(block.key, source)
                if (before != null) assertEquals(before, source)
            }
        }
        assertEquals(MarkdownStreamPlanner(chunkTarget = 1).plan(text).blocks, planner.plan(text).blocks)
    }

    @Test
    fun blankLinesInsideFencesAndMathAndListsDoNotSplit() {
        val text = "Intro\n\n```kotlin\nval a = 1\n\nval b = 2\n```\n\n\$\$\na = b\n\nc = d\n\$\$\n\n1. one\n\n2. two\n   more\n\n- three\n\nOutro"
        val sources = chunks(MarkdownStreamPlanner(chunkTarget = 1).plan(text)).map { it.source }
        assertEquals(
            listOf(
                "Intro",
                "```kotlin\nval a = 1\n\nval b = 2\n```",
                "\$\$\na = b\n\nc = d\n\$\$",
                "1. one\n\n2. two\n   more\n\n- three",
                "Outro",
            ),
            sources,
        )
    }

    @Test
    fun anUnfinishedLineThatMayBecomeAListItemKeepsTheListOpen() {
        val planner = MarkdownStreamPlanner(chunkTarget = 1)
        assertEquals(1, planner.plan("- a\n\n1").blocks.size)
        assertEquals(1, planner.plan("- a\n\n1.").blocks.size)
        assertEquals(1, planner.plan("- a\n\n1. b").blocks.size)
        assertEquals(2, MarkdownStreamPlanner(chunkTarget = 1).plan("- a\n\nText").blocks.size)
    }

    @Test
    fun aRegisteredFenceBecomesAnExtensionBlock() {
        val planner = MarkdownStreamPlanner(extensionKinds = setOf("json-render"))
        val open = planner.plan("Intro\n\n```json-render\n{\"op\":\"add\"}\n")
        val part = open.blocks.last() as MarkdownExtensionPart
        assertEquals("json-render", part.block.kind)
        assertEquals("{\"op\":\"add\"}", part.block.body)
        assertEquals(false, part.block.complete)
        assertEquals("Intro", (open.blocks.first() as MarkdownChunk).source)

        val done = planner.plan("Intro\n\n```json-render\n{\"op\":\"add\"}\n```\nAfter")
        val closed = done.blocks[1] as MarkdownExtensionPart
        assertEquals(true, closed.block.complete)
        assertEquals("{\"op\":\"add\"}", closed.block.body)
        assertEquals("After", (done.blocks.last() as MarkdownChunk).source)

        val other = MarkdownStreamPlanner(extensionKinds = setOf("json-render")).plan("```kotlin\nx\n```")
        assertTrue(other.blocks.single() is MarkdownChunk)
    }

    @Test
    fun aNewTextStartsOver() {
        val planner = MarkdownStreamPlanner(chunkTarget = 1)
        planner.plan("First\n\nSecond\n\nThird")
        assertEquals(listOf("Other", "Text"), chunks(planner.plan("Other\n\nText")).map { it.source })
    }

    @Test
    fun referenceDefinitionsReachEveryChunk() {
        val plan = MarkdownStreamPlanner(chunkTarget = 1).plan("See [docs][1].\n\nMore.\n\n[1]: https://x.dev")
        assertEquals("[1]: https://x.dev", plan.references)
        assertEquals("See [docs][1].\n\n[1]: https://x.dev", prepareMarkdownChunk("See [docs][1].", plan.references, streamingTail = false))
    }

    @Test
    fun definitionsInsideCodeAreNotShared() {
        assertEquals("", referenceDefinitions("Literal [sample].\n\n```md\n[sample]: https://example.com\n```"))
        assertEquals("", referenceDefinitions("    [sample]: https://example.com"))
        assertEquals("[a]: https://a.dev", referenceDefinitions("[a]: https://a.dev\n\n```\n[b]: https://b.dev"))
    }

    @Test
    fun sharedDefinitionsStayOutOfAnOpenFence() {
        assertEquals(
            "See [a].\n\n```kotlin\nval x = 1\n```\n\n[a]: https://a.dev",
            prepareMarkdownChunk("See [a].\n\n```kotlin\nval x = 1", "[a]: https://a.dev", streamingTail = true),
        )
    }

    @Test
    fun indentedBackticksAreNotAnOpenFence() {
        assertEquals(
            "See [a].\n\n    ```\n    literal\n\n[a]: https://a.dev",
            prepareMarkdownChunk("See [a].\n\n    ```\n    literal", "[a]: https://a.dev", streamingTail = false),
        )
    }

    @Test
    fun chunksAreNormalisedForTheNativeParser() {
        assertEquals(
            "Costs \\\$5, so **x**\n\n\$\$\nE = mc^2\n\$\$",
            prepareMarkdownChunk("Costs \$5, so <b>x</b>\n\\[E = mc^2\\]", references = "", streamingTail = false),
        )
        assertEquals("Video: https://x.dev/v.mp4", prepareMarkdownChunk("<video src=\"https://x.dev/v.mp4\"></video>", "", false))
    }
}
