package com.echoflow.weave.runtime

import com.echoflow.data.ChatMessage
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.runtime.storage.WeaveBlockRow

/**
 * History for the model (05 §9.4): each block fence in an assistant message is sent as its
 * current canonical source plus the 06 §2.4 state line; edit fences are dropped (their effect is
 * in the source); blocks more than 10 assistant turns back keep only their header and state
 * line; blocks shown as plain text are sent as that text.
 */
object WeaveHistory {
    /**
     * A top-level `weave` / `weave-json` fence as the renderer reads them: up to three leading
     * spaces, three or more markers, closed by a fence of the same marker at least as long.
     * Groups: 1 = opening run, 2 = `-json` or empty, 3 = rest of the info string, 4 = body.
     */
    private val FENCE = Regex("""(?m)^ {0,3}(`{3,}|~{3,})weave(-json)?([^\n]*)\n([\s\S]*?)\n?^ {0,3}\1[`~]*[ \t]*$""")
    private const val FULL_SOURCE_TURNS = 10

    fun rewrite(history: List<ChatMessage>, rows: List<WeaveBlockRow>): List<ChatMessage> {
        if (rows.isEmpty() && history.none { it.role == "assistant" && it.content.contains("weave") && FENCE.containsMatchIn(it.content) }) return history
        val byMessage = rows.groupBy { it.messageId }.mapValues { (_, r) -> r.sortedBy { it.blockKey } }
        val assistantIdx = history.withIndex().filter { it.value.role == "assistant" }.map { it.index }
        return history.mapIndexed { i, m ->
            if (m.role != "assistant" || !m.content.contains("weave")) return@mapIndexed m
            val turnsBack = assistantIdx.size - 1 - assistantIdx.indexOf(i)
            val blocks = byMessage[m.id].orEmpty()
            var ordinal = 0
            val out = FENCE.replace(m.content) { match ->
                val json = match.groupValues[2].isNotEmpty()
                val info = match.groupValues[3].trim()
                val fenceBody = match.groupValues[4]
                // edits are dropped: their effect is in the target's stored source
                if (info.startsWith("edit")) return@replace ""
                if (json && com.echoflow.weave.core.json.JsonLowering.lower(fenceBody)?.editTarget != null) return@replace ""
                val row = blocks.getOrNull(ordinal++) ?: return@replace match.value
                "```weave\n" + body(row, turnsBack > FULL_SOURCE_TURNS) + "\n```"
            }
            if (out == m.content) m else m.copy(content = out.replace(Regex("\n{3,}"), "\n\n"))
        }
    }

    /** The fence body for one stored block. */
    fun body(row: WeaveBlockRow, headerOnly: Boolean): String {
        val program = Compiler.compile(row.source, BuiltinCatalog)
        val current = StateCodec.decodeState(program, row.stateJson)
        val initial = runCatching { Session(program).state.toMap() }.getOrDefault(emptyMap())
        val line = runCatching { StateCodec.stateLine(program, initial + current, initial) }.getOrNull()
        if (WeaveRuntime.hasFlag(row.uiStateJson, "plain")) {
            val text = runCatching {
                val s = Session(program); s.restore(current); s.root()?.let { s.plainText(it) }
            }.getOrNull().orEmpty()
            return "# shown to the user as plain text:\n" + text.lines().joinToString("\n") { "# $it" }
        }
        val src = if (headerOnly) row.source.lineSequence().firstOrNull { it.startsWith("@weave") } ?: "@weave 1 id: ${row.blockId}" else row.source
        return if (line == null) src else "$src\n# state $line"
    }
}
