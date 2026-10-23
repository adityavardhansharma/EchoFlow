package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `the agent clears any card tool left by older versions`() {
        val body = VoiceAgentSpec.agentBody(config)
        assertEquals(emptyList<Any>(), prompt(body)["tools"])
        assertFalse("client_tool_call" in events(body))
        assertFalse("show_card" in (prompt(body)["prompt"] as String))
        assertFalse("show_card" in VoiceAgentSpec.sessionPrompt(emptyList()))
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
