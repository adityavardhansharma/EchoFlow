package com.echoflow.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.echoflow.ui.screens.chat.MemoryActivityLine
import com.echoflow.ui.screens.settings.MemoryPage
import com.echoflow.ui.screens.settings.MemoryViewModel
import com.echoflow.ui.screens.settings.MyMemoriesPage
import com.echoflow.data.memory.MemorySettings
import com.echoflow.data.memory.SupermemoryClient
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class MemoryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun connection_and_coming_soon() {
        val vm = MemoryViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { EchoFlowTheme { MemoryPage({}, {}, vm) } }
        compose.onNodeWithText("Connect").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_connect.png")
        compose.onNodeWithText("EchoBrain").performClick()
        compose.onNodeWithText("Coming soon").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_echobrain.png")
    }
    @Test fun quiet_chat_receipts() {
        compose.setContent { EchoFlowTheme { Surface { Column(Modifier.fillMaxWidth().padding(24.dp)) {
            MemoryActivityLine("Recalled 2 memories", false)
            MemoryActivityLine("Memory saved", false)
            MemoryActivityLine("No relevant memories", false)
            MemoryActivityLine("Memory unavailable · chat can continue", false)
        } } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_chat_receipts.png")
    }
    private fun connectedViewModel(): MemoryViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settings = MemorySettings(app.getSharedPreferences("memory-screenshot", 0)).apply {
            connect("fixture-key", "echoflow-personal")
        }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val json = when (chain.request().url.encodedPath) {
                "/v3/auth/billing" -> """{"plan":"pro","resetDate":"2026-10-01"}"""
                "/v3/auth/billing/usage" -> """{"features":[{"id":"usd_credits","used":4.25,"limit":20}]}"""
                "/v4/profile" -> """{"profile":{"static":["You enjoy cricket and prefer concise answers."],"dynamic":["You're building EchoFlow, an Android AI chat app."]}}"""
                "/v4/memories/list" -> """{"memoryEntries":[{"id":"m1","memory":"Prefers short, clear answers over long explanations.","updatedAt":"2026-09-15"},{"id":"m2","memory":"EchoFlow uses Kotlin and Jetpack Compose.","updatedAt":"2026-09-15"}],"pagination":{"totalPages":1}}"""
                else -> """{"memories":[]}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture").body(json.toResponseBody()).build()
        }.build()
        return MemoryViewModel(app, settings) { key, space -> SupermemoryClient(key, space, http) }
    }
    @Test fun connected_account() {
        val vm = connectedViewModel()
        compose.setContent { EchoFlowTheme { MemoryPage({}, {}, vm) } }
        compose.waitUntil(10_000) { vm.billing != null && !vm.busy }
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_account.png")
        compose.onNodeWithText("My Memories →").performScrollTo().assertIsEnabled()
    }
    @Test fun memory_library_dark() {
        val vm = connectedViewModel()
        compose.setContent { EchoFlowTheme(darkTheme = true) { MyMemoriesPage({}, vm) } }
        compose.waitUntil(10_000) { vm.memories.isNotEmpty() && !vm.busy }
        compose.onNodeWithText("About you").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_library_dark.png")
    }
}
