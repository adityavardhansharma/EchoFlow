package com.echoflow

import com.echoflow.data.ArtifactRef
import com.echoflow.data.PersistedSegment
import com.echoflow.data.ToolEventJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card-chrome quarantine depends on pre-redesign JSON (no `uiVersion` field) deserializing
 * to [ArtifactRef.UI_VERSION_LEGACY]. If that default ever flips, every old chat would suddenly
 * render the redesigned card — the exact failure the split exists to prevent.
 */
class ArtifactRefUiVersionTest {

    @Test
    fun preRedesignJsonWithoutUiVersionIsLegacy() {
        // Shape written by the app before the redesign: no uiVersion key at all.
        val json = """
            [{"type":"artifact","artifact":{"artifactId":"a1","title":"Page","type":"html","version":2}}]
        """.trimIndent()

        val segment = ToolEventJson.segmentsFromJson(json).single()
        val ref = segment.artifact!!

        assertEquals(ArtifactRef.UI_VERSION_LEGACY, ref.uiVersion)
        assertTrue(ref.usesLegacyUi)
        assertEquals("a1", ref.artifactId)
        assertEquals(2, ref.version)
    }

    @Test
    fun currentStampSurvivesRoundTrip() {
        val original = PersistedSegment(
            type = "artifact",
            artifact = ArtifactRef(
                artifactId = "a2",
                title = "Doc",
                type = "markdown",
                version = 1,
                uiVersion = ArtifactRef.UI_VERSION_CURRENT,
            ),
        )
        val reloaded = ToolEventJson.segmentsFromJson(ToolEventJson.segmentsToJson(listOf(original)))
            .single().artifact!!

        assertEquals(ArtifactRef.UI_VERSION_CURRENT, reloaded.uiVersion)
        assertFalse(reloaded.usesLegacyUi)
    }
}
