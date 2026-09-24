package com.echoflow.data

import org.json.JSONObject

/**
 * The tool-call wire format for schedule conversations and runs.
 *
 * Schedules run on every model EchoFlow can talk to — OpenRouter, the direct providers, custom
 * OpenAI-compatible endpoints and on-device models — and most of those paths have no native
 * function calling (or implement it differently). So tools travel in the text itself:
 *
 * ```
 * <tool name="update_schedule">{"time": "08:00", "days": ["mon", "fri"]}</tool>
 * ```
 *
 * One format, parsed by one tolerant parser, means a local 3B model and a frontier model are held
 * to the same contract and the conversation never depends on which provider happens to answer.
 * Results go back as `<tool_result>` blocks in a user-role turn that is clearly marked as coming
 * from EchoFlow, not the person.
 */
object ScheduleToolProtocol {
    data class Call(val name: String, val arguments: JSONObject, val raw: String)

    private val block = Regex(
        """<tool\s+name\s*=\s*["']([A-Za-z_]+)["']\s*(?:/>|>(.*?)</tool\s*>)""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val echoedResult = Regex("""<tool_result\b.*?</tool_result\s*>""", RegexOption.DOT_MATCHES_ALL)
    private val fence = Regex("""^```[a-zA-Z]*\s*|\s*```$""")

    /** Every complete call in [text], in order. Arguments that are not a JSON object become `{}`. */
    fun calls(text: String): List<Call> = block.findAll(text).map { match ->
        val body = match.groupValues.getOrNull(2).orEmpty().trim().replace(fence, "").trim()
        val args = if (body.isEmpty()) JSONObject() else runCatching { JSONObject(body) }.getOrElse {
            // A model that wraps the object in prose still usually emits one clean object.
            runCatching { JSONObject(body.substring(body.indexOf('{'), body.lastIndexOf('}') + 1)) }
                .getOrDefault(JSONObject().put("__invalid", body.take(200)))
        }
        Call(match.groupValues[1].lowercase(), args, match.value)
    }.toList()

    /**
     * What the person should see: [text] without tool blocks. While [streaming], a trailing
     * fragment that could still become a tool tag is held back, so markup never flashes on screen.
     */
    fun visible(text: String, streaming: Boolean = false): String {
        var out = block.replace(text, "").replace(echoedResult, "")
        if (streaming) {
            val open = out.lastIndexOf("<tool", ignoreCase = true)
            if (open >= 0) out = out.substring(0, open)
            else {
                val lt = out.lastIndexOf('<')
                if (lt >= 0 && "<tool".startsWith(out.substring(lt).lowercase())) out = out.substring(0, lt)
            }
        } else {
            // A call the model never closed is still markup, not prose.
            val open = out.indexOf("<tool", ignoreCase = true)
            if (open >= 0) out = out.substring(0, open)
        }
        return out.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun result(name: String, payload: String): String = "<tool_result name=\"$name\">$payload</tool_result>"

    /** The user-role turn that carries results back, worded so no model mistakes it for the person. */
    fun resultsTurn(results: List<String>): String =
        "[EchoFlow tool results — automatic, not written by the user]\n" + results.joinToString("\n") +
            "\nContinue: call more tools if needed, otherwise reply to the user in plain language."
}
