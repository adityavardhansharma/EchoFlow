package com.echoflow.data.memory

import com.echoflow.data.jev.JevClient
import org.junit.Assert.*
import org.junit.Test

class MemoryPrivacyRegressionTest {
    @Test fun `basic prose and ordinary references to tokens remain intact`() {
        val text = "Explain basic knowledge and token counting."
        assertEquals(text, MemoryPrivacy.redact(text))
    }
    @Test fun `common credential forms never reach either router text field`() {
        val credentials = listOf(
            "password: secret", "password: \"my secret\"", "api-key=x", "access_token: 'a b'",
            "client-secret: short", "Authorization: Basic dXNlcjpwYXNz", "Basic dXNlcjpwYXNz",
            """{"token": "short", "authToken": "with spaces"}""",
            """{"password": "with \"escaped\" quotes"}""",
        )
        credentials.forEach { credential ->
            val redacted = MemoryPrivacy.redact(credential)
            assertNotEquals(credential, redacted)
            val state = JevClient().buildState(credential, listOf("user" to credential), true, true)
            assertEquals(redacted, state.getString("message"))
            assertEquals(redacted, state.getJSONArray("recent_turns").getJSONObject(0).getString("text"))
            listOf("secret", "short", "with spaces", "escaped", "dXNlcjpwYXNz").forEach {
                assertFalse("Leaked $it", redacted.contains(it))
            }
        }
    }
}
