package com.echoflow.weave.core.prompt

import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.catalog.CatalogExamples
import com.echoflow.weave.core.catalog.ChildrenSpec
import com.echoflow.weave.core.json.TolerantJson

/**
 * The on-demand docs tool (06 §2.1, §8; 01 §21.2). Tool-capable models at tier ≥ 3 get the
 * common set's full lines and an index of the long tail; `weave_docs(names)` returns the
 * signature and one example for each name they ask about.
 *
 * Everything here works on the catalog the request was built from, so a name above the
 * request's tier or phase, or one the renderer doesn't draw, is `unknown` (06 §1).
 */
object WeaveDocs {

    const val TOOL_NAME = "weave_docs"
    const val MAX_NAMES = 30

    /** 06 §8.2. */
    const val DESCRIPTION =
        "Get exact signatures and one example for Weave components, marks, or the groups \"functions\", \"actions\", \"effects\"."

    /** JSON Schema for the tool's arguments (06 §8.2). */
    val PARAMETERS: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "names" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "maxItems" to MAX_NAMES),
        ),
        "required" to listOf("names"),
    )

    /** The common set that keeps its full lines in a trimmed prompt: the phase 1a slice (06 §2.1). */
    val COMMON_SET: Set<String> = linkedSetOf(
        "Column", "Row", "Card", "Title", "Text", "Metric", "Stat", "Callout", "Button", "Slider",
        "Segmented", "TextField", "Select", "Chart", "LineChart", "AreaChart", "BarChart", "Table", "Tabs", "List", "Item",
    )

    private val GROUP_NAMES = listOf("functions", "actions", "effects")

    /**
     * Whether a request gets the trimmed prompt and the tool (06 §2.1): phase 3 or later, tier 3
     * or more, and a model that can call tools on this request.
     */
    fun usesIndex(tier: Int, phase: Int, toolUse: Boolean): Boolean = toolUse && tier >= 3 && phase >= 3

    private const val INDEX_INTRO = """### Reference index
The components above are all you need for most blocks. These others also exist; call the weave_docs tool with their names to get exact signatures before using any of them."""

    /** 06 §8.1: one line per group, names only, excluding the common set. "" when nothing is left. */
    fun index(c: Catalog): String {
        val lines = EchoUiPrompt.GROUPS.mapNotNull { g ->
            val names = c.components.filter { it.group == g && it.name !in COMMON_SET }.map { it.name }
            if (names.isEmpty()) null else "$g: " + names.joinToString(" ")
        }
        return if (lines.isEmpty()) "" else INDEX_INTRO + "\n" + lines.joinToString("\n")
    }

    /**
     * Reads the tool's `{"names": [...]}` arguments. A bare string or a list of strings is
     * accepted too, since models vary. Returns null when nothing usable is there.
     */
    fun parseNames(arguments: String): List<String>? {
        val j = TolerantJson.parse(arguments.trim()) ?: return arguments.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }?.let { listOf(it) }
        val raw = when (j) {
            is TolerantJson.JObj -> j.fields["names"] ?: j.fields["name"]
            else -> j
        }
        val names = when (raw) {
            is TolerantJson.JArr -> raw.items.mapNotNull { (it as? TolerantJson.JStr)?.v }
            is TolerantJson.JStr -> raw.v.split(',')
            else -> return null
        }.map { it.trim() }.filter { it.isNotEmpty() }
        return names.takeIf { it.isNotEmpty() }
    }

    /**
     * The tool result (06 §8.2): for each name, its §3.3 line and one example; the §3.4–3.6 text
     * for a group name; `unknown: <name>` otherwise. At most [MAX_NAMES] names, duplicates once.
     */
    fun answer(names: List<String>, tier: Int, c: Catalog): String {
        val asked = names.map { it.trim().removePrefix("!").removeSuffix("()") }.filter { it.isNotEmpty() }.distinct()
        if (asked.isEmpty()) return "Call weave_docs with {\"names\": [\"Quiz\", \"Canvas\"]}: component or mark names, or \"functions\", \"actions\", \"effects\"."
        val out = ArrayList<String>()
        for (name in asked.take(MAX_NAMES)) out += entry(name, tier, c)
        if (asked.size > MAX_NAMES) out += "Only the first $MAX_NAMES names were answered."
        return out.joinToString("\n\n")
    }

    private fun entry(name: String, tier: Int, c: Catalog): String {
        val group = GROUP_NAMES.firstOrNull { it.equals(name, ignoreCase = true) }
        if (group != null) {
            val text = when (group) {
                "functions" -> EchoUiPrompt.functions(tier, c)
                "actions" -> EchoUiPrompt.actions(tier, c)
                else -> EchoUiPrompt.effects(tier, c)
            }
            return text.ifEmpty { "unknown: $name" }
        }
        // Components are capitalised; accept a model's lowercase spelling when it isn't a mark.
        val comp = c.component(name) ?: c.components.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.takeIf { c.marks.none { m -> m.name == name } }
        if (comp != null) {
            val sb = StringBuilder(EchoUiPrompt.componentLine(comp))
            val marks = (comp.children as? ChildrenSpec.Marks)?.container?.let { container ->
                c.marks.filter { container in it.container && c.mark(container, it.name) != null }
            }.orEmpty()
            for (m in marks) sb.append("\n  ").append(EchoUiPrompt.markLine(m))
            CatalogExamples.component(comp.name)?.let { sb.append("\nExample:\n").append(it) }
            return sb.toString()
        }
        // A mark is documented once per container this request can draw (`line` is in Chart and Canvas).
        val marks = c.marks.filter { it.name == name }.flatMap { m ->
            m.container.filter { c.component(it) != null && c.mark(it, m.name) != null }.map { it to m }
        }
        if (marks.isNotEmpty()) {
            return marks.joinToString("\n\n") { (container, m) ->
                val sb = StringBuilder(EchoUiPrompt.markLine(m)).append(" (a mark inside ").append(container).append(")")
                CatalogExamples.mark(container, m.name)?.let { sb.append("\nExample: ").append(it) }
                sb.toString()
            }
        }
        return "unknown: $name"
    }
}
