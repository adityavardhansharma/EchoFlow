package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultChatModelsTest {
    @Test fun `ships Luna as the primary default and Echo Lumen as the free router`() {
        assertEquals("openai/gpt-5.6-luna", DefaultChatModels.DEFAULT_MODEL_ID)
        assertEquals("GPT 5.6 Luna", DefaultChatModels.DEFAULT_MODEL_NAME)
        assertEquals("openrouter/free", DefaultChatModels.ECHO_LUMEN_MODEL_ID)
        assertEquals("Echo Lumen", DefaultChatModels.ECHO_LUMEN_MODEL_NAME)
    }

    @Test fun `built in picker entries match shipped models`() {
        assertEquals(DefaultChatModels.SHIPPED, DefaultChatModels.BUILT_IN)
        assertTrue(DefaultChatModels.BUILT_IN_IDS.contains(DefaultChatModels.DEFAULT_MODEL_ID))
        assertTrue(DefaultChatModels.BUILT_IN_IDS.contains(DefaultChatModels.ECHO_LUMEN_MODEL_ID))
    }

    @Test fun `displayName resolves shipped and legacy ids`() {
        assertEquals("GPT 5.6 Luna", DefaultChatModels.displayName(DefaultChatModels.DEFAULT_MODEL_ID))
        assertEquals("Echo Lumen", DefaultChatModels.displayName(DefaultChatModels.ECHO_LUMEN_MODEL_ID))
        assertEquals("Gemini 2.0 Flash", DefaultChatModels.displayName(DefaultChatModels.LEGACY_DEFAULT_MODEL_ID))
        assertNull(DefaultChatModels.displayName("anthropic/claude-sonnet-4.5"))
    }

    @Test fun `pickerDisplayName adds a free badge only for Echo Lumen`() {
        assertEquals(
            "(Free) Echo Lumen",
            DefaultChatModels.pickerDisplayName(
                DefaultChatModels.ECHO_LUMEN_MODEL_ID,
                DefaultChatModels.ECHO_LUMEN_MODEL_NAME,
            ),
        )
        assertEquals(
            "GPT 5.6 Luna",
            DefaultChatModels.pickerDisplayName(
                DefaultChatModels.DEFAULT_MODEL_ID,
                DefaultChatModels.DEFAULT_MODEL_NAME,
            ),
        )
        assertEquals("Echo Lumen", DefaultChatModels.displayName(DefaultChatModels.ECHO_LUMEN_MODEL_ID))
    }
}
