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

    private fun runWithTurn(turn: StreamChunk.TurnEnded, vararg chunks: String): List<StreamChunk> {
        val parser = ArtifactStreamParser()
        val out = mutableListOf<StreamChunk>()
        chunks.forEach { out += parser.onChunk(StreamChunk.Content(it)) }
        out += parser.onChunk(turn)
        out += parser.onComplete()
        return out
    }

    private fun proseOf(chunks: List<StreamChunk>) =
        chunks.filterIsInstance<StreamChunk.Content>().joinToString("") { it.text }

    @Test
    fun `prose without an artifact passes through with a note`() {
        val out = run("Hello ", "world")
        assertTrue(proseOf(out).startsWith("Hello world\n\n_No artifact came back"))
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

    @Test
    fun `fenced html block becomes the artifact when the sentinel is missing`() {
        val out = run("Here's your page.\n``", "`html\n<html><body>Hi</body></html>\n`", "``\nEnjoy")
        assertEquals("Here's your page.\n\nEnjoy", proseOf(out))
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("<html><body>Hi</body></html>", done.content)
        assertEquals(Artifact.TYPE_HTML, done.artifactType)
    }

    @Test
    fun `sentinel inside a fence is unwrapped`() {
        val out = run("```html\n<echo:artifact type=\"html\" title=\"Sun\">\n<p>x</p>\n</echo:artifact>\n```")
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("Sun", done.title)
        assertEquals("<p>x</p>", done.content)
    }

    @Test
    fun `ordinary code fences pass through untouched`() {
        val out = run("Try:\n```kotlin\nval a = 1\n```\nok")
        assertTrue(proseOf(out).startsWith("Try:\n```kotlin\nval a = 1\n```\nok\n\n_No artifact"))
        assertTrue(out.none { it is StreamChunk.ArtifactStarted })
    }

    @Test
    fun `fences after the artifact stay in the prose`() {
        val out = run("<echo:artifact type=\"html\" title=\"P\">a</echo:artifact>\n```html\n<b>b</b>\n```")
        assertEquals(1, out.filterIsInstance<StreamChunk.ArtifactCompleted>().size)
        assertTrue(proseOf(out).contains("<b>b</b>"))
    }

    @Test
    fun `artifact sent as a tool call is recovered`() {
        val args = """{"type":"html","title":"Solar","content":"<!DOCTYPE html><html></html>"}"""
        val turn = StreamChunk.TurnEnded("tool_calls", listOf(StreamChunk.ToolCallSummary("echo:artifact", args)))
        val out = runWithTurn(turn, "Here's your simulator.")
        assertEquals("Here's your simulator.", proseOf(out))
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("Solar", done.title)
        assertEquals("<!DOCTYPE html><html></html>", done.content)
        assertEquals(1, out.filterIsInstance<StreamChunk.ArtifactStarted>().size)
    }

    @Test
    fun `missing artifact explains why`() {
        val out = runWithTurn(StreamChunk.TurnEnded("length", emptyList()), "Here's your simulator.")
        assertTrue(out.none { it is StreamChunk.ArtifactCompleted })
        assertTrue(proseOf(out).contains("output token limit"))
    }

    @Test
    fun `search tool calls are not mistaken for the artifact`() {
        val turn = StreamChunk.TurnEnded("stop", listOf(StreamChunk.ToolCallSummary("web_search", """{"query":"<html> tags"}""")))
        val out = runWithTurn(turn, "No page here.")
        assertTrue(out.none { it is StreamChunk.ArtifactCompleted })
        assertTrue(proseOf(out).contains("without writing the artifact block"))
    }

    @Test
    fun `no note when the artifact arrived`() {
        val out = runWithTurn(StreamChunk.TurnEnded("stop", emptyList()), "<echo:artifact type=\"html\" title=\"P\">a</echo:artifact>")
        assertEquals("", proseOf(out))
    }

    @Test
    fun `longer fence keeps a nested code block inside the artifact`() {
        val out = run("````markdown\n# Doc\n```kotlin\nval a = 1\n```\nend\n````\nbye")
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("# Doc\n```kotlin\nval a = 1\n```\nend", done.content)
        assertEquals(Artifact.TYPE_MARKDOWN, done.artifactType)
        assertEquals("\nbye", proseOf(out))
    }

    @Test
    fun `tilde fences are recognised`() {
        val out = run("~~~html\n<p>t</p>\n~~~")
        assertEquals("<p>t</p>", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
    }

    @Test
    fun `declared tool call content is recovered without page markers`() {
        val args = """{"type":"markdown","title":"Notes","content":"# Notes\nHello"}"""
        val turn = StreamChunk.TurnEnded("tool_calls", listOf(StreamChunk.ToolCallSummary("create_artifact", args)))
        val done = runWithTurn(turn, "Here.").filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("# Notes\nHello", done.content)
        assertEquals(Artifact.TYPE_MARKDOWN, done.artifactType)
    }

    @Test
    fun `sentinel inside an unlabelled fence still becomes the artifact`() {
        val out = run("Here.\n```\n<echo:artifact type=\"html\" title=\"P\">page</echo:artifact>\n```\nbye")
        assertEquals("page", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
        assertEquals("Here.\n\nbye", proseOf(out))
    }

    @Test
    fun `backticks mid-line across a chunk split do not close a code block`() {
        val out = run("```kotlin\n// ", "```\nval x = 1\n```\n<echo:artifact type=\"html\">page</echo:artifact>")
        assertEquals("page", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
        assertEquals("```kotlin\n// ```\nval x = 1\n```\n", proseOf(out))
    }

    @Test
    fun `indented closing fence closes the artifact`() {
        val out = run("  ```html\n<p>x</p>\n  ```\nEnjoy")
        val done = out.filterIsInstance<StreamChunk.ArtifactCompleted>().single()
        assertEquals("<p>x</p>", done.content)
        assertTrue(!done.truncated)
        assertEquals("  \nEnjoy", proseOf(out))
    }

    @Test
    fun `a fence in the middle of a sentence is prose`() {
        val out = run("Wrap it between ```html and ``` markers.\n<echo:artifact type=\"html\">page</echo:artifact>")
        assertEquals("page", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
        assertEquals("Wrap it between ```html and ``` markers.\n", proseOf(out))
    }

    @Test
    fun `a long closing fence split across chunks is dropped whole`() {
        val out = run("```\n<echo:artifact type=\"html\">page</echo:artifact>\n````", "``\nbye")
        assertEquals("page", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
        assertEquals("\nbye", proseOf(out))
    }

    @Test
    fun `indent split from its fence still opens the artifact`() {
        val out = run("Here.\n  ", "```html\n<p>x</p>\n  ```\nEnjoy")
        assertEquals("<p>x</p>", out.filterIsInstance<StreamChunk.ArtifactCompleted>().single().content)
        assertEquals("Here.\n  \nEnjoy", proseOf(out))
    }
}
