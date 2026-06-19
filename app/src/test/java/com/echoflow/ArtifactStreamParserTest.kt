package com.echoflow

import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactStreamParser
import com.echoflow.data.StreamChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Drives the parser synchronously by feeding content in arbitrary chunk splits. */
class ArtifactStreamParserTest {

    private fun run(vararg chunks: String): List<StreamChunk> {
        val parser = ArtifactStreamParser()
        val out = mutableListOf<StreamChunk>()
        chunks.forEach { out += parser.onChunk(StreamChunk.Content(it)) }
        out += parser.onComplete()
        return out
    }

    private fun proseOf(chunks: List<StreamChunk>) =
        chunks.filterIsInstance<StreamChunk.Content>().joinToString("") { it.text }

    @Test
    fun `prose without an artifact passes straight through`() {
        val out = run("Hello ", "world")
        assertEquals("Hello world", proseOf(out))
        assertTrue(out.none { it is StreamChunk.ArtifactStarted })
    }

    @Test
    fun `extracts a single artifact and keeps surrounding prose`() {
        val out = run(
            "Here you go: ",
            "<echo:artifact type=\"html\" title=\"Page\">",
            "<h1>Hi</h1>",
            "</echo:artifact>",
            " done",
        )
        assertEquals("Here you go:  done", proseOf(out))
        val started = out.filterIsInstance<StreamChunk.ArtifactStarted>().single()
        assertEquals("Page", started.title)
        assertEquals(Artifact.TYPE_HTML, started.artifactType)
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("<h1>Hi</h1>", done.content)
        assertTrue(!done.truncated)
    }

    @Test
    fun `handles an open tag split across chunk boundaries`() {
        val out = run("intro <echo:art", "ifact type=\"markdown\" title=\"Doc\">body", "</echo:artifact>")
        assertEquals("intro ", proseOf(out))
        val started = out.filterIsInstance<StreamChunk.ArtifactStarted>().single()
        assertEquals(Artifact.TYPE_MARKDOWN, started.artifactType)
        assertEquals("body", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
    }

    @Test
    fun `handles a close tag split across chunk boundaries`() {
        val out = run("<echo:artifact type=\"latex\" title=\"R\">x=1</echo:art", "ifact>tail")
        assertEquals("tail", proseOf(out))
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("x=1", done.content)
        assertEquals(Artifact.TYPE_LATEX, done.artifactType)
    }

    @Test
    fun `unterminated artifact is surfaced as truncated`() {
        val out = run("<echo:artifact type=\"html\" title=\"P\">partial code never closed")
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("partial code never closed", done.content)
        assertTrue(done.truncated)
    }
}
