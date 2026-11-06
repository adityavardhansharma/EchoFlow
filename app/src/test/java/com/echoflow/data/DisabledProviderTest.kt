package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisabledProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun config() = SettingsRepository(context).getCustomProviderConfigDirect().copy(
        cloudApisEnabled = true, openAiEnabled = true, openAiApiKey = "sk-test",
        claudeEnabled = true, ollamaEnabled = true,
    )

    @Test fun `a turned-off provider or cloud switch blocks the send`() {
        val on = config()
        assertNull(CustomProviderRouting.disabledMessage("openai", on))
        assertNull(CustomProviderRouting.disabledMessage("claude", on))
        assertNull(CustomProviderRouting.disabledMessage(null, on))

        val openAiOff = CustomProviderRouting.disabledMessage("openai", on.copy(openAiEnabled = false))
        assertEquals("OpenAI is turned off. Turn it on in Settings → Custom or choose another model.", openAiOff)
        assertNotNull(CustomProviderRouting.disabledMessage("openai", on.copy(cloudApisEnabled = false)))
        assertNotNull(CustomProviderRouting.disabledMessage("claude", on.copy(cloudApisEnabled = false)))
        assertNotNull(CustomProviderRouting.disabledMessage("groq", on))

        // Local endpoints don't sit behind the cloud switch.
        assertNull(CustomProviderRouting.disabledMessage("ollama", on.copy(cloudApisEnabled = false)))
        assertTrue(CustomProviderRouting.disabledMessage("ollama", on.copy(ollamaEnabled = false))!!
            .contains("Echo Labs"))

        // Sarvam keeps its own message, which also covers a missing key.
        assertEquals(on.copy(sarvamEnabled = true).sarvamConfigurationError(),
            CustomProviderRouting.disabledMessage("sarvam", on.copy(sarvamEnabled = true)))
    }

    @Test fun `the router never calls a turned-off provider`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { error("no request should be made") }.build()
        val router = CustomProviderFlowRouter(CustomProviderService(client = client))
        val off = config().copy(openAiEnabled = false)
        val failure = runCatching {
            router.stream("openai", off, "gpt-5", emptyList(), "", InferenceLimits.CLOUD_DEFAULTS).toList()
        }.exceptionOrNull()
        assertEquals("OpenAI is turned off. Turn it on in Settings → Custom or choose another model.", failure?.message)
        val toolFailure = runCatching {
            router.streamWithTools("openai", off, "gpt-5", emptyList(), "", InferenceLimits.CLOUD_DEFAULTS) { emptyList() }.toList()
        }.exceptionOrNull()
        assertEquals(failure?.message, toolFailure?.message)
    }
}
