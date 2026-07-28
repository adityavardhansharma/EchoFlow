package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The pools are content, so the things worth asserting are the ones that quietly rot: a
 * duplicate pasted in during an edit, a pool that drifts below the size that makes repeats
 * rare, or a stray blank entry that would render as an empty suggestion.
 */
class ImaginePromptsTest {

    @Test fun `each medium offers a hundred prompts and ten headlines`() {
        ImagineMedia.entries.forEach { media ->
            assertEquals("$media prompts", 100, ImaginePrompts.promptsFor(media).size)
            assertEquals("$media headlines", 10, ImaginePrompts.headlinesFor(media).size)
        }
    }

    @Test fun `no prompt or headline is repeated within its pool`() {
        ImagineMedia.entries.forEach { media ->
            val prompts = ImaginePrompts.promptsFor(media)
            assertEquals("$media prompts", prompts.size, prompts.toSet().size)
            val headlines = ImaginePrompts.headlinesFor(media)
            assertEquals("$media headlines", headlines.size, headlines.toSet().size)
        }
    }

    @Test fun `nothing in a pool is blank or padded`() {
        ImagineMedia.entries.forEach { media ->
            (ImaginePrompts.promptsFor(media) + ImaginePrompts.headlinesFor(media)).forEach {
                assertEquals("“$it” has stray whitespace", it.trim(), it)
                assertTrue("a pool entry is blank", it.isNotBlank())
            }
        }
    }

    @Test fun `image and video suggest different things`() {
        // The two pools describe different jobs — a subject versus a shot — so any overlap is
        // a copy-paste, not a coincidence.
        val shared = ImaginePrompts.promptsFor(ImagineMedia.Image)
            .intersect(ImaginePrompts.promptsFor(ImagineMedia.Video).toSet())
        assertTrue("shared prompts: $shared", shared.isEmpty())
    }

    @Test fun `sampling is drawn from the medium's own pool`() {
        repeat(200) { seed ->
            ImagineMedia.entries.forEach { media ->
                val random = Random(seed)
                assertTrue(ImaginePrompts.prompt(media, random) in ImaginePrompts.promptsFor(media))
                assertTrue(ImaginePrompts.headline(media, random) in ImaginePrompts.headlinesFor(media))
            }
        }
    }

    @Test fun `sampling spreads across the pool rather than favouring one entry`() {
        val seen = (0 until 400).map { ImaginePrompts.prompt(ImagineMedia.Image, Random(it)) }.toSet()
        assertTrue("only ${seen.size} distinct prompts in 400 draws", seen.size > 60)
    }
}
