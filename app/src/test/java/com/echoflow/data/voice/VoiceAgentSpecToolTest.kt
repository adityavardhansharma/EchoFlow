package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAgentSpecToolTest {

    private val config = VoiceModeConfig()

    @Suppress("UNCHECKED_CAST")
    private fun prompt(body: Map<String, Any?>) =
        ((body["conversation_config"] as Map<String, Any?>)["agent"] as Map<String, Any?>)["prompt"] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun events(body: Map<String, Any?>) =
        ((body["conversation_config"] as Map<String, Any?>)["conversation"] as Map<String, Any?>)["client_events"] as List<String>

    @Test
    fun `the agent carries the card tool and asks for its event`() {
        val body = VoiceAgentSpec.agentBody(config)
        val tools = prompt(body)["tools"] as List<*>
        assertEquals(VoicePlanTool.NAME, (tools.single() as Map<*, *>)["name"])
        assertTrue(VoiceAgentSpec.TOOL_EVENT in events(body))
    }

    @Test
    fun `without tools neither the tool nor its event is sent`() {
        val body = VoiceAgentSpec.agentBody(config, withTools = false)
        assertNull(prompt(body)["tools"])
        assertFalse(VoiceAgentSpec.TOOL_EVENT in events(body))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `a user already talking gets no greeting`() {
        fun firstMessage(greet: Boolean): Any? {
            val init = VoiceAgentSpec.initiation(config, emptyList(), greet = greet)
            val override = init["conversation_config_override"] as Map<String, Any?>
            return (override["agent"] as Map<String, Any?>)["first_message"]
        }
        assertEquals(VoiceAgentSpec.GREETING, firstMessage(greet = true))
        assertNull(firstMessage(greet = false))
    }
}
