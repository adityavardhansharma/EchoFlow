package com.echoflow.data

import kotlinx.coroutines.flow.Flow

/**
 * The tool loop shared by schedule conversations and unattended runs: stream a reply, execute
 * the tool calls it contains, hand the results back, repeat until the model answers in prose.
 *
 * The person only ever sees prose. Each round's visible text is streamed through [onText] as the
 * running total across rounds, so "Setting that up…" followed by "Done — weekdays at 8" reads as
 * one reply rather than two.
 */
class ScheduleAgent(
    private val complete: (history: List<ChatMessage>) -> Flow<String>,
    private val maxRounds: Int = 5,
) {
    data class Reply(val text: String, val toolCalls: Int)

    suspend fun run(
        history: List<ChatMessage>,
        execute: suspend (ScheduleToolProtocol.Call) -> String,
        onText: (String) -> Unit = {},
    ): Reply {
        val working = history.toMutableList()
        val shown = mutableListOf<String>()
        var toolCalls = 0
        for (round in 0 until maxRounds) {
            val raw = StringBuilder()
            complete(working).collect { delta ->
                raw.append(delta)
                onText(join(shown, ScheduleToolProtocol.visible(raw.toString(), streaming = true)))
            }
            val text = raw.toString()
            val calls = ScheduleToolProtocol.calls(text)
            if (round == maxRounds - 1) {
                if (calls.isNotEmpty()) {
                    // Discard prose attached to unexecuted calls, then request one answer-only turn.
                    onText(join(shown, ""))
                    working += ScheduleModelRunner.message("assistant", text)
                    working += ScheduleModelRunner.message("user",
                        "Tool limit reached. No more tools are available. Answer now with what you have. " +
                            "Do not claim that an unexecuted action succeeded.")
                    val final = StringBuilder()
                    complete(working).collect { delta ->
                        final.append(delta)
                        onText(join(shown, ScheduleToolProtocol.visible(final.toString(), streaming = true)))
                    }
                    val finalText = final.toString()
                    val visible = ScheduleToolProtocol.visible(finalText)
                    if (visible.isBlank()) error("The schedule assistant returned no answer after reaching its tool limit.")
                    shown += visible + if (ScheduleToolProtocol.calls(finalText).isNotEmpty())
                        "\n\nSome requested actions were not completed." else ""
                    break
                }
            }
            ScheduleToolProtocol.visible(text).takeIf { it.isNotBlank() }?.let(shown::add)
            if (calls.isEmpty()) break
            val results = calls.map { call -> toolCalls++; ScheduleToolProtocol.result(call.name, execute(call)) }
            working += ScheduleModelRunner.message("assistant", text)
            working += ScheduleModelRunner.message("user", ScheduleToolProtocol.resultsTurn(results))
        }
        val reply = join(shown, "")
        onText(reply)
        return Reply(reply, toolCalls)
    }

    private fun join(done: List<String>, current: String) =
        (done + current).filter { it.isNotBlank() }.joinToString("\n\n")
}
