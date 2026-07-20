package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLlmPromptingTest {
    @Test
    fun runtimeSelectionFollowsModelFileFormat() {
        assertEquals(LocalLlmRuntime.GGUF, LocalLlmPrompting.runtimeFor(model("weights.gguf")))
        assertEquals(LocalLlmRuntime.LITERT, LocalLlmPrompting.runtimeFor(model("bundle.litertlm")))
        assertEquals(LocalLlmRuntime.MEDIAPIPE, LocalLlmPrompting.runtimeFor(model("model.task")))
    }

    @Test
    fun transcriptKeepsRolesAndOrder() {
        val turns = listOf(
            message("user", "Hello"),
            message("assistant", "Hi"),
            message("system", "Status"),
        )

        assertEquals(
            "Human: Hello\nEchoFlow: Hi\nEchoFlow: Status",
            LocalLlmPrompting.transcriptOf(turns),
        )
    }

    @Test
    fun userTurnPromptPreservesProtocol() {
        assertEquals(
            "Human message:\nQuestion\n\nEchoFlow reply:",
            LocalLlmPrompting.userTurnPrompt("Question"),
        )
    }

    @Test
    fun severeRepetitionDetectorIgnoresOrdinaryRepeatedWords() {
        val detector = SevereRepetitionDetector()

        assertEquals(false, detector.append("very very very very useful"))
    }

    @Test
    fun severeRepetitionDetectorFindsSustainedTranscriptLoopAcrossChunks() {
        val detector = SevereRepetitionDetector()
        val loop = "Human: hi EchoFlow: hello "

        assertEquals(false, detector.append(loop))
        assertEquals(false, detector.append(loop))
        assertEquals(false, detector.append(loop))
        assertEquals(true, detector.append(loop))
    }

    private fun model(fileName: String) =
        LocalModel("local/test", "Test", fileName, 1L, "imported", 0L)

    private fun message(role: String, content: String) =
        ChatMessage("id-$role", "chat", role, content, 0L)
}
