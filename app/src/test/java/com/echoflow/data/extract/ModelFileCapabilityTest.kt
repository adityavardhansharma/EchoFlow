package com.echoflow.data.extract

import com.echoflow.data.DefaultChatModels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileCapabilityTest {
    @Test fun `openrouter models can take files`() {
        assertTrue(ModelFileCapability.readsFiles(DefaultChatModels.DEFAULT_MODEL_ID))
        assertTrue(ModelFileCapability.readsFiles("google/gemini-2.0-flash"))
        assertTrue(ModelFileCapability.readsFiles("anthropic/claude-sonnet-4.6"))
    }

    @Test fun `local and custom models cannot take files`() {
        assertFalse(ModelFileCapability.readsFiles("local/gemma-3-1b"))
        assertFalse(ModelFileCapability.readsFiles("custom/openai/gpt-4o"))
        assertFalse(ModelFileCapability.readsFiles("custom/ollama/llama3"))
        assertFalse(ModelFileCapability.readsFiles(""))
    }
}
