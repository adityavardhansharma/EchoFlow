package com.echoflow.data

import org.junit.Assert.assertTrue
import org.junit.Test

class StableDiffusionRequestValidatorTest {
    @Test
    fun `accepts the bounded request used by the app`() {
        StableDiffusionRequestValidator.validate(
            prompt = "a lighthouse at dusk",
            negativePrompt = "distorted",
            width = 512,
            height = 512,
            steps = 20,
            cfgScale = 7f,
        )
    }

    @Test
    fun `rejects unsafe native boundary values`() {
        val invalidCalls = listOf<() -> Unit>(
            { StableDiffusionRequestValidator.validate("prompt", "", 513, 512, 20, 7f) },
            { StableDiffusionRequestValidator.validate("prompt", "", 512, 512, 0, 7f) },
            { StableDiffusionRequestValidator.validate("prompt", "", 512, 512, 20, Float.NaN) },
            { StableDiffusionRequestValidator.validate("", "", 512, 512, 20, 7f) },
            { StableDiffusionRequestValidator.validate("a".repeat(8_193), "", 512, 512, 20, 7f) },
        )

        invalidCalls.forEach { call ->
            val failed = runCatching(call).exceptionOrNull()
            assertTrue(failed is IllegalArgumentException)
        }
    }
}
