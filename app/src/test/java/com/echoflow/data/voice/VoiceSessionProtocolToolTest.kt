package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionProtocolToolTest {

    @Test
    fun `a client tool call is parsed with its parameters`() {
        val event = VoiceSessionProtocol.parse(
            """{"type":"client_tool_call","client_tool_call":{"tool_name":"show_card","tool_call_id":"call_1",""" +
                """"parameters":{"title":"Lisbon","items":["Day 1: Alfama"]}}}""",
        ) { ByteArray(0) }
        assertTrue(event is VoiceServerEvent.ClientToolCall)
        event as VoiceServerEvent.ClientToolCall
        assertEquals("show_card", event.name)
        assertEquals("call_1", event.callId)
        assertEquals("Lisbon", event.parameters?.get("title"))
    }

    @Test
    fun `a tool call without an id is ignored`() {
        val event = VoiceSessionProtocol.parse(
            """{"type":"client_tool_call","client_tool_call":{"tool_name":"show_card"}}""",
        ) { ByteArray(0) }
        assertEquals(VoiceServerEvent.Ignored, event)
    }

    @Test
    fun `the tool result echoes the call id`() {
        val json = VoiceApiJson.read(VoiceSessionProtocol.clientToolResult("call_1", "Shown"))!!
        assertEquals("client_tool_result", json["type"])
        assertEquals("call_1", json["tool_call_id"])
        assertEquals("Shown", json["result"])
        assertEquals(false, json["is_error"])
    }
}
