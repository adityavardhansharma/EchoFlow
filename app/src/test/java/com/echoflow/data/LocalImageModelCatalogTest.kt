package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalImageModelCatalogTest {
    @Test
    fun `catalog exposes exactly four pinned artifacts`() {
        val entries = LocalImageModelCatalog.entries
        assertEquals(4, entries.size)
        assertEquals(4, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id.startsWith("local-image/") })
        assertTrue(entries.all { it.artifactAvailable })
        assertEquals(4, entries.mapNotNull { it.artifactUrl }.distinct().size)
        entries.forEach {
            assertTrue(LocalImageCatalogEntry.SHA256_REGEX.matches(it.artifactSha256.orEmpty()))
            assertTrue((it.downloadBytes ?: 0L) > 2_000_000_000L || !it.experimental)
        }
    }

    @Test
    fun `only recommended MediaPipe model downloads before experimental opt in`() {
        val defaultEntries = LocalImageModelCatalog.entries.filter { it.canDownload(false) }
        assertEquals(listOf("local-image/stable-diffusion-1.5"), defaultEntries.map { it.id })
        assertTrue(LocalImageModelCatalog.entries.all { it.canDownload(true) })

        val recommended = defaultEntries.single()
        assertEquals(LocalImageRuntime.MEDIAPIPE, recommended.runtime)
        assertEquals(LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP, recommended.artifactFormat)
        assertNull(recommended.modelFileName)
        assertEquals(1_906_219_565L, recommended.downloadBytes)
        assertEquals(
            "0e17f95821b6a247d01807b20920206052fc32483af2af42f03ff61954397758",
            recommended.artifactSha256,
        )
    }

    @Test
    fun `three experimental models route to stable diffusion cpp with correct files`() {
        val experimental = LocalImageModelCatalog.entries.filter { it.experimental }
        assertEquals(3, experimental.size)
        assertTrue(experimental.all { it.runtime == LocalImageRuntime.STABLE_DIFFUSION_CPP })
        assertEquals(
            setOf(
                "dreamshaper_8.safetensors",
                "analog-diffusion-1.0.safetensors",
                "openjourney-v4.ckpt",
            ),
            experimental.mapNotNull { it.modelFileName }.toSet(),
        )
        assertEquals(LocalImageArtifactFormat.CHECKPOINT, experimental.single { it.id.endsWith("openjourney-v4") }.artifactFormat)
    }

    @Test
    fun `DreamShaper carries Civitai terms while Hugging Face artifacts carry OpenRAIL`() {
        val dreamShaper = LocalImageModelCatalog.entryById("local-image/dreamshaper-8")!!
        assertEquals(LocalImageModelCatalog.LICENSE_CIVITAI_MODEL_TERMS, dreamShaper.licenseId)
        assertEquals(LocalImageModelCatalog.DREAMSHAPER_TERMS_URL, dreamShaper.licenseUrl)
        LocalImageModelCatalog.entries.filter { it !== dreamShaper }.forEach {
            assertEquals(LocalImageModelCatalog.LICENSE_OPENRAIL_M, it.licenseId)
        }
    }

    @Test
    fun `analog diffusion activation phrase remains catalog metadata`() {
        assertEquals(
            "analog style",
            LocalImageModelCatalog.entryById("local-image/analog-diffusion")?.activationPhrase,
        )
        assertFalse(LocalImageModelCatalog.entries.filterNot { it.id.endsWith("analog-diffusion") }
            .any { it.activationPhrase != null })
    }
}
