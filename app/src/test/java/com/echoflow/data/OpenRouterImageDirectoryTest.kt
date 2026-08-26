package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterImageDirectoryTest {

    @Test
    fun `unfiltered chat catalog omits dedicated image-only models`() {
        val models = OpenRouterModelDirectory.parseDirectory(CHAT_CATALOG)
        assertNull(models.firstOrNull { it.id == "meta/muse-image" })
        val gemini = models.first { it.id == "google/gemini-2.5-flash-image" }
        assertTrue(gemini.outputsImage)
        assertTrue(gemini.outputsText)
        assertFalse(gemini.usesDedicatedImageApi)
    }

    @Test
    fun `image listing includes muse and does not treat it as free`() {
        val models = OpenRouterModelDirectory.parseDirectory(IMAGE_LISTING)
            .filter { it.outputsImage && !OpenRouterModelDirectory.isRouterAuto(it.id) }
        val muse = models.first { it.id == "meta/muse-image" }
        assertEquals("Meta: Muse Image", muse.name)
        assertTrue(muse.outputsImage)
        assertFalse(muse.outputsText)
        assertTrue(muse.usesDedicatedImageApi)
        assertTrue(muse.hasImageOutputPrice)
        assertFalse(muse.isFree)
        assertNotNull(models.firstOrNull { it.id == "google/gemini-2.5-flash-image" })
    }

    @Test
    fun `fallback routing sends muse to the image api and gemini through chat`() {
        assertTrue(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("meta/muse-image"))
        assertTrue(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("black-forest-labs/flux.2-pro"))
        assertTrue(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("openai/gpt-image-1"))
        assertFalse(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("google/gemini-2.5-flash-image"))
        assertFalse(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("openai/gpt-5-image"))
        assertFalse(OpenRouterModelDirectory.fallbackUsesDedicatedImageApi("openrouter/auto"))
    }

    @Test
    fun `image api response unwraps the first base64 image`() {
        val url = OpenRouterService.firstImageDataUrl(
            mapOf(
                "data" to listOf(
                    mapOf("b64_json" to "QUJD", "media_type" to "image/jpeg"),
                ),
            ),
        )
        assertEquals("data:image/jpeg;base64,QUJD", url)
        assertNull(OpenRouterService.firstImageDataUrl(mapOf("data" to emptyList<Any>())))
    }

    companion object {
        private const val CHAT_CATALOG = """
        {"data":[
          {"id":"google/gemini-2.5-flash-image","name":"Google: Gemini 2.5 Flash Image",
           "architecture":{"output_modalities":["image","text"]},
           "pricing":{"prompt":"0.0000003","completion":"0.0000025","image_output":"0.00003"}},
          {"id":"meta/muse-spark-1.2","name":"Meta: Muse Spark 1.2",
           "architecture":{"output_modalities":["text"]},
           "pricing":{"prompt":"0.1","completion":"0.1"}}
        ]}
        """

        private const val IMAGE_LISTING = """
        {"data":[
          {"id":"meta/muse-image","name":"Meta: Muse Image",
           "architecture":{"output_modalities":["image"]},
           "pricing":{"prompt":"0","completion":"0","image_output":"0.000002395"}},
          {"id":"google/gemini-2.5-flash-image","name":"Google: Gemini 2.5 Flash Image",
           "architecture":{"output_modalities":["image","text"]},
           "pricing":{"prompt":"0.0000003","completion":"0.0000025","image_output":"0.00003"}},
          {"id":"openrouter/auto","name":"Auto",
           "architecture":{"output_modalities":["text","image"]},
           "pricing":{"prompt":"0","completion":"0"}}
        ]}
        """
    }
}
