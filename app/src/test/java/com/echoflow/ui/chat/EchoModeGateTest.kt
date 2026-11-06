package com.echoflow.ui.chat

import com.echoflow.data.ChatMode
import org.junit.Assert.*
import org.junit.Test

class EchoModeGateTest {
    @Test fun `messages match what each mode always said`() {
        assertEquals("Echo Agents needs a cloud main model. Pick one from the model selector.",
            EchoModeGate.modelError(ChatMode.EchoAgent, isLocal = true, customProviderActive = false))
        assertEquals("Echo Adviser needs a cloud model. Pick one from the model selector.",
            EchoModeGate.modelError(ChatMode.EchoAdviser, isLocal = true, customProviderActive = false))
        assertEquals("Echo Fusion uses OpenRouter server tools. Pick an OpenRouter Cloud model first.",
            EchoModeGate.modelError(ChatMode.EchoFusion, isLocal = false, customProviderActive = true))
        assertEquals("Echo Agents uses OpenRouter server tools. Pick an OpenRouter Cloud model first.",
            EchoModeGate.modelError(ChatMode.EchoAgent, isLocal = false, customProviderActive = true))
    }

    @Test fun `on-device is checked before direct providers`() {
        assertTrue(EchoModeGate.modelError(ChatMode.EchoAdviser, isLocal = true, customProviderActive = true)!!.contains("needs"))
    }

    @Test fun `fusion brings its own panel so a local main model is fine`() {
        assertNull(EchoModeGate.modelError(ChatMode.EchoFusion, isLocal = true, customProviderActive = false))
    }

    @Test fun `other modes have no rule here`() {
        assertNull(EchoModeGate.modelError(ChatMode.Normal, isLocal = true, customProviderActive = true))
        assertNull(EchoModeGate.modelError(ChatMode.EchoAgent, isLocal = false, customProviderActive = false))
    }
}
