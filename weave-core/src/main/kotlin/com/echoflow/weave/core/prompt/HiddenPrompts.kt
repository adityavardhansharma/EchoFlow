package com.echoflow.weave.core.prompt

/**
 * User turns for the hidden requests (06 §4). The system prompt for each is
 * [EchoUiPrompt.editorSection] for the block's tier.
 */
object HiddenPrompts {
    private const val F = "```"

    private fun head(blockId: String, title: String?, source: String, stateJson: String) =
        "Block \"$blockId\" (${title ?: blockId}):\n${F}weave\n$source\n$F\nCurrent state: $stateJson"

    /** 06 §4.1. [askLog] = recent `prompt -> names` pairs, newest last. */
    fun ask(blockId: String, title: String?, source: String, stateJson: String, withJson: String, askLog: List<Pair<String, List<String>>>, prompt: String, update: List<String>): String {
        val log = if (askLog.isEmpty()) "none" else askLog.joinToString("; ") { (p, n) -> "\"$p\" -> ${n.joinToString(", ")}" }
        return head(blockId, title, source, stateJson) +
            "\nValues sent with this request: $withJson\nRecent updates: $log\n\nTask: $prompt\n\n" +
            "Reply with one fence: ${F}weave edit $blockId$F containing new definitions for only these parts: ${update.joinToString(", ")}. " +
            "You may add new helper parts with new names. Do not change anything else. Do not declare state."
    }

    /** 06 §4.2. */
    fun remix(blockId: String, title: String?, source: String, stateJson: String, userText: String): String =
        head(blockId, title, source, stateJson) + "\n\nThe user wants this change: $userText\n\n" +
            "Reply with one fence: ${F}weave edit $blockId$F containing only the statements to add or replace, and `-name` lines for anything to remove. " +
            "Keep state names the user already set unless the change requires otherwise; add ` reset` after a state declaration only if its value must start over. " +
            "An `on` effect you write replaces every effect on that same trigger, so if you change one, restate the others on that trigger that should stay."

    /** 06 §4.3. */
    fun lazy(blockId: String, title: String?, source: String, stateJson: String, bindingName: String, prompt: String, contextJson: String): String =
        head(blockId, title, source, stateJson) + "\n\nGenerate the part \"$bindingName\": $prompt\nContext: $contextJson\n\n" +
            "Reply with one fence: ${F}weave$F containing a `root` (the content to show in that place) and any helper parts. " +
            "Do not declare state and do not write a header. You may read the block's state and parts by name."

    /** 06 §4.4. */
    fun regen(source: String, stateJson: String): String =
        "Rewrite this Weave block from scratch with the same purpose, fixing anything that does not work well. Keep the same id and state names.\n" +
            "${F}weave\n$source\n$F\nCurrent state: $stateJson\nReply with one fence: ${F}weave$F containing the complete new block."
}
