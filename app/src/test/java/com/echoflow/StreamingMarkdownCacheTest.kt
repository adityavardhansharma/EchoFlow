package com.echoflow

import com.echoflow.ui.components.StreamingMarkdownCache
import com.echoflow.ui.components.parseMarkdownBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StreamingMarkdownCacheTest {
    @Test fun `every streamed prefix matches the full parser`() {
        val samples = listOf(
            "Paragraph\ncontinued\n\n## Heading\n\n- one\n- two\n\nTail",
            "Before\n\n```kotlin\nval n = 1\n\nprintln(n)\n```\n\nAfter",
            "Before\n\n$$\nx + y\n\nz\n$$\n\nAfter",
            "Before\n\n| A | B |\n| --- | --- |\n| a | b |\n\nAfter",
            "Before\n\n\\[\nx + y\n\nz\n\\]\n\nAfter",
            "Before\n\n~~~text\nunfinished\n\ncode",
        )
        for (text in samples) {
            val cache = StreamingMarkdownCache()
            for (end in 0..text.length) {
                val prefix = text.take(end)
                assertEquals("Prefix: $prefix", parseMarkdownBlocks(prefix), cache.parse(prefix))
            }
        }
    }

    @Test fun `completed blocks retain identity and replacements invalidate the cache`() {
        val cache = StreamingMarkdownCache()
        val before = cache.parse("First\n\nTail")
        val after = cache.parse("First\n\nTail grows")
        assertSame(before.first(), after.first())
        val replacement = "Different\n\nAnswer"
        assertEquals(parseMarkdownBlocks(replacement), cache.parse(replacement))
        assertEquals(parseMarkdownBlocks("Short"), cache.parse("Short"))
    }
}
