package com.echoflow.data.voice

/** One line of a [VoicePlan]: "Day 1" and what happens on it. [edited] marks a line the user just changed. */
data class VoicePlanRow(val label: String, val text: String, val edited: Boolean = false)

/**
 * The card the agent builds while it talks: a trip, a recipe's steps, a short list of options.
 * The agent fills it through the [VoicePlanTool] client tool and sends it again, whole, whenever
 * the user changes something, so the screen can show exactly which lines moved.
 */
data class VoicePlan(val title: String, val rows: List<VoicePlanRow>)

/** The `show_card` client tool: its definition on the agent, and how a call's parameters become a card. */
internal object VoicePlanTool {
    const val NAME = "show_card"
    const val MAX_ROWS = 8
    private const val MAX_LABEL = 24
    private const val MAX_TEXT = 120

    /** The inline client-tool definition carried in the agent's prompt config. */
    fun definition(): Map<String, Any?> = linkedMapOf(
        "type" to "client",
        "name" to NAME,
        "description" to "Shows a small card on the user's screen while you talk: a plan, steps, " +
            "a short list of options or a schedule. Send the whole card every time; to change " +
            "a line, send the card again with that line updated.",
        "expects_response" to false,
        "parameters" to linkedMapOf(
            "type" to "object",
            "properties" to linkedMapOf(
                "title" to linkedMapOf(
                    "type" to "string",
                    "description" to "A few words naming the card, such as 'Lisbon · 3 days · March'.",
                ),
                "items" to linkedMapOf(
                    "type" to "array",
                    "description" to "One to eight short lines, in order.",
                    "items" to linkedMapOf(
                        "type" to "string",
                        "description" to "One line as 'Label: text', such as 'Day 1: Alfama and Baixa, the old town'.",
                    ),
                ),
            ),
            "required" to listOf("title", "items"),
        ),
    )

    /**
     * Turns a tool call's [params] into a card, or null when there is nothing to show. When the
     * card is an update of [previous] (same title), lines whose text changed are marked edited.
     */
    fun parse(params: Map<*, *>?, previous: VoicePlan?): VoicePlan? {
        val title = (params?.get("title") as? String)?.trim().orEmpty().take(MAX_TEXT)
        val items: List<String> = when (val raw = params?.get("items")) {
            is List<*> -> raw.mapNotNull { (it as? String) ?: (it as? Map<*, *>)?.let(::itemFromMap) }
            // Some models send the list as one string, a line per item.
            is String -> raw.lines()
            else -> emptyList()
        }
        val rows = items.map { it.trim().trimStart('-', '•', '*').trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_ROWS)
            .map(::row)
        if (title.isEmpty() && rows.isEmpty()) return null
        val update = previous != null && previous.title.equals(title, ignoreCase = true)
        val marked = if (!update) rows else rows.mapIndexed { i, row ->
            val before = previous!!.rows.getOrNull(i)
            row.copy(
                edited = before != null &&
                    (!before.text.equals(row.text, ignoreCase = true) || !before.label.equals(row.label, ignoreCase = true)),
            )
        }
        return VoicePlan(title = title, rows = marked)
    }

    private fun itemFromMap(map: Map<*, *>): String? {
        val text = (map["text"] as? String)?.trim().orEmpty()
        val label = (map["label"] as? String)?.trim().orEmpty()
        return when {
            text.isEmpty() -> label.ifEmpty { null }
            label.isEmpty() -> text
            else -> "$label: $text"
        }
    }

    private fun row(item: String): VoicePlanRow {
        val colon = item.indexOf(':')
        return if (colon in 1..MAX_LABEL && colon < item.length - 1) {
            VoicePlanRow(item.substring(0, colon).trim(), item.substring(colon + 1).trim().take(MAX_TEXT))
        } else {
            VoicePlanRow("", item.take(MAX_TEXT))
        }
    }
}
