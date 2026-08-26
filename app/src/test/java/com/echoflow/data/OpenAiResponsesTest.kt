package com.echoflow.data

import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponsesTest {
    private val params = InferenceParams(temperature = 0.4f, topK = 0, topP = 0.9f, maxTokens = 512)

    @Test fun `request uses responses fields not chat completions`() {
        val payload = OpenAiResponses.request(
            model = "gpt-5.4",
            input = listOf(mapOf("role" to "user", "content" to "hi")),
            instructions = "be brief",
            params = params,
            tools = listOf(OpenAiResponses.webSearchTool),
        )
        assertEquals("gpt-5.4", payload["model"])
        assertEquals(true, payload["stream"])
        assertEquals("be brief", payload["instructions"])
        assertEquals(512, payload["max_output_tokens"])
        assertFalse(payload.containsKey("messages"))
        assertFalse(payload.containsKey("max_tokens"))
        val tool = (payload["tools"] as List<*>).single() as Map<*, *>
        assertEquals("function", tool["type"])
        assertEquals("web_search", tool["name"])
        assertFalse(tool.containsKey("function"))
    }

    @Test fun `omits optional fields when unset`() {
        val payload = OpenAiResponses.request(
            model = "gpt-4.1-mini",
            input = emptyList(),
            instructions = "  ",
            params = InferenceParams(temperature = 0.7f, topK = 0, topP = 1f, maxTokens = 0),
        )
        assertFalse(payload.containsKey("instructions"))
        assertFalse(payload.containsKey("max_output_tokens"))
        assertFalse(payload.containsKey("tools"))
        assertFalse(payload.containsKey("previous_response_id"))
    }

    @Test fun `carries previous_response_id for the tool follow-up turn`() {
        val payload = OpenAiResponses.request(
            model = "gpt-5.4",
            input = listOf(OpenAiResponses.functionCallOutput("call_1", "results")),
            instructions = "stay on topic",
            params = params,
            previousResponseId = "resp_abc",
        )
        assertEquals("resp_abc", payload["previous_response_id"])
        assertEquals("function_call_output", (payload["input"] as List<*>).let { (it.single() as Map<*, *>)["type"] })
    }

    @Test fun `builds scalar text input and image parts`() {
        val history = listOf(
            message(role = "user", content = "hello"),
            message(role = "assistant", content = "hi"),
            message(role = "user", content = "what is this", uri = "content://photo", mime = "image/png"),
        )
        val items = OpenAiResponses.inputItems(history) { if (it == null) null else "cGhvdG8=" }
        assertEquals("hello", items[0]["content"])
        assertEquals("hi", items[1]["content"])
        val parts = items[2]["content"] as List<*>
        assertEquals("input_text", (parts[0] as Map<*, *>)["type"])
        assertEquals("input_image", (parts[1] as Map<*, *>)["type"])
        assertEquals("data:image/png;base64,cGhvdG8=", (parts[1] as Map<*, *>)["image_url"])
    }

    @Test fun `skips PDFs and attaches extra images`() {
        val history = listOf(message(role = "user", content = "use these"))
        history.single().extraAttachments = listOf(
            LocalFileAttachment("file:///tmp/scan.pdf", "application/pdf", "scan.pdf"),
            LocalFileAttachment("file:///tmp/photo.png", "image/png", "photo.png"),
        )
        val items = OpenAiResponses.inputItems(history) { uri ->
            when {
                uri == null -> null
                uri.endsWith(".pdf") -> "cGRm"
                else -> "cG5n"
            }
        }
        val parts = items.single()["content"] as List<*>
        assertEquals(2, parts.size)
        assertEquals("input_image", (parts[1] as Map<*, *>)["type"])
        assertEquals("data:image/png;base64,cG5n", (parts[1] as Map<*, *>)["image_url"])
    }

    @Test fun `parses output text and reasoning deltas`() {
        assertEquals(
            OpenAiResponses.Event.Content("Hello"),
            OpenAiResponses.parseData("""{"type":"response.output_text.delta","delta":"Hello"}"""),
        )
        assertEquals(
            OpenAiResponses.Event.Reasoning("think"),
            OpenAiResponses.parseData("""{"type":"response.reasoning_summary_text.delta","delta":"think"}"""),
        )
        assertNull(OpenAiResponses.parseData("[DONE]"))
        assertNull(OpenAiResponses.parseData("""{"type":"response.output_text.done"}"""))
    }

    @Test fun `parses function-call stream events and failures`() {
        val added = OpenAiResponses.parseData(
            """{"type":"response.output_item.added","item":{"id":"fc_1","type":"function_call","call_id":"call_9","name":"web_search"}}""",
        )
        assertEquals(OpenAiResponses.Event.FunctionCallMeta("fc_1", "call_9", "web_search"), added)

        val delta = OpenAiResponses.parseData(
            """{"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{\"q"}""",
        )
        assertEquals(OpenAiResponses.Event.FunctionCallArgsDelta("fc_1", "{\"q"), delta)

        val done = OpenAiResponses.parseData(
            """{"type":"response.function_call_arguments.done","item_id":"fc_1","call_id":"call_9","name":"web_search","arguments":"{\"query\":\"news\"}"}""",
        )
        assertEquals(
            OpenAiResponses.Event.FunctionCallArgsDone("fc_1", "call_9", "web_search", """{"query":"news"}"""),
            done,
        )

        val failed = OpenAiResponses.parseData(
            """{"type":"response.failed","response":{"error":{"message":"model_not_found"}}}""",
        )
        assertEquals(OpenAiResponses.Event.Failed("model_not_found"), failed)
    }

    @Test fun `consumes a named SSE transcript into content`() {
        val wire = """
            event: response.created
            data: {"type":"response.created","response":{"id":"resp_1"}}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":"Hi"}

            event: response.output_text.delta
            data: {"type":"response.output_text.delta","delta":" there"}

            event: response.completed
            data: {"type":"response.completed","response":{"id":"resp_1"}}

        """.trimIndent()
        val source = Buffer().writeUtf8(wire)
        val chunks = mutableListOf<String>()
        var responseId: String? = null
        runBlocking {
            OpenAiResponses.consumeStream(source) { event ->
                when (event) {
                    is OpenAiResponses.Event.Content -> chunks.add(event.text)
                    is OpenAiResponses.Event.ResponseId -> responseId = event.id
                    else -> Unit
                }
            }
        }
        assertEquals(listOf("Hi", " there"), chunks)
        assertEquals("resp_1", responseId)
    }

    @Test fun `joinApiUrl points official OpenAI at v1 responses`() {
        assertEquals(
            "https://api.openai.com/v1/responses",
            ProviderHttpSupport.joinApiUrl(OpenAiResponses.DEFAULT_BASE_URL, OpenAiResponses.PATH),
        )
    }

    private fun message(
        role: String,
        content: String,
        uri: String? = null,
        mime: String? = null,
    ) = ChatMessage(
        id = "message-$role-$content",
        chatId = "chat",
        role = role,
        content = content,
        createdAt = 1L,
        localAttachmentUri = uri,
        localAttachmentMimeType = mime,
        localAttachmentName = mime,
    )
}
