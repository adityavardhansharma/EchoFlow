package com.echoflow

import com.echoflow.data.ArtifactVersion
import com.echoflow.data.lineDelta
import com.echoflow.data.versionDeltas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactDiffTest {

    @Test
    fun `no predecessor counts every line as added`() {
        val delta = lineDelta(null, "a\nb\nc")
        assertEquals(3, delta.added)
        assertEquals(0, delta.removed)
    }

    @Test
    fun `blank lines are ignored on both sides`() {
        val delta = lineDelta("a\n\n\nb", "a\nb\n\n")
        assertTrue(delta.isEmpty)
    }

    @Test
    fun `identical content is an empty delta`() {
        assertTrue(lineDelta("x\ny\nz", "x\ny\nz").isEmpty)
    }

    @Test
    fun `an inserted line counts as one addition, not a rewrite`() {
        // LCS keeps the shared a/b/c run, so only the inserted line is new.
        val delta = lineDelta("a\nb\nc", "a\nNEW\nb\nc")
        assertEquals(1, delta.added)
        assertEquals(0, delta.removed)
    }

    @Test
    fun `a replaced line is one add and one remove`() {
        val delta = lineDelta("a\nb\nc", "a\nB\nc")
        assertEquals(1, delta.added)
        assertEquals(1, delta.removed)
    }

    @Test
    fun `a removed line counts as a removal`() {
        val delta = lineDelta("a\nb\nc\nd", "a\nc\nd")
        assertEquals(0, delta.added)
        assertEquals(1, delta.removed)
    }

    @Test
    fun `version deltas measure each version against the previous`() {
        val versions = listOf(
            version(2, "a\nB\nc"),   // out of order on purpose
            version(1, "a\nb\nc"),
            version(3, "a\nB\nc\nd"),
        )
        val deltas = versionDeltas(versions)

        assertEquals(listOf(1, 2, 3), deltas.map { it.version })
        // v1 is the origin: a size, not a +/-.
        assertTrue(deltas[0].isFirst)
        assertEquals(3, deltas[0].lineCount)
        // v2 replaced one line.
        assertFalse(deltas[1].isFirst)
        assertEquals(1, deltas[1].delta.added)
        assertEquals(1, deltas[1].delta.removed)
        // v3 appended one line.
        assertEquals(1, deltas[2].delta.added)
        assertEquals(0, deltas[2].delta.removed)
    }

    private fun version(number: Int, content: String) = ArtifactVersion(
        id = "v$number",
        artifactId = "art",
        versionNumber = number,
        content = content,
        sourcePrompt = "",
        createdAt = number.toLong(),
    )
}
