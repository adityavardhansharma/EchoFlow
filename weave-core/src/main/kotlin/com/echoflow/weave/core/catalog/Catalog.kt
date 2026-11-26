package com.echoflow.weave.core.catalog

import com.echoflow.weave.core.check.TokenSet

/** 05 §3.3. */
interface Catalog {
    fun component(name: String): ComponentSpec?
    fun mark(container: String, name: String): MarkSpec?
    fun action(name: String): ActionSpec?
    fun function(name: String): FunctionSpec?
    fun trigger(name: String): TriggerSpec?
    fun tokens(set: TokenSet): List<String> = Tokens.members(set)
    fun restrictedTo(tier: Int, phase: Int): Catalog

    /**
     * Further restricts components to those the renderer registry can draw (05 §3.3). A component
     * outside the set is never advertised and is checked as E209.
     */
    fun registeredOnly(names: Set<String>): Catalog

    val components: List<ComponentSpec>
    val marks: List<MarkSpec>
    val actions: List<ActionSpec>
    val functions: List<FunctionSpec>
    val triggers: List<TriggerSpec>

    /** The unrestricted catalog this one was filtered from (to tell E209 from "unknown"). */
    val full: Catalog

    /** Tier this catalog is restricted to (4 = unrestricted). */
    val tier: Int
}

/** A filtered view over [BuiltinCatalog]. */
class FilteredCatalog(
    override val full: Catalog,
    override val tier: Int,
    private val phase: Int,
    private val registry: Set<String>?,
) : Catalog {
    override val components = full.components.filter { it.tier <= tier && it.phase <= phase && (registry == null || it.name in registry) }
    override val marks = full.marks.filter { it.tier <= tier && it.phase <= phase }
    override val actions = full.actions.filter { it.tier <= tier && it.phase <= phase }
    override val functions = full.functions.filter { it.tier <= tier && it.phase <= phase }
    override val triggers = full.triggers.filter { it.tier <= tier && it.phase <= phase }
    private val byName = components.associateBy { it.name }
    private val actionsByName = actions.associateBy { it.name }
    private val fnByName = functions.associateBy { it.name }
    private val trigByName = triggers.associateBy { it.name }

    override fun component(name: String) = byName[name]
    override fun mark(container: String, name: String) = marks.firstOrNull { it.name == name && container in it.container }
    override fun action(name: String) = actionsByName[name]
    override fun function(name: String) = fnByName[name]
    override fun trigger(name: String) = trigByName[name]
    override fun restrictedTo(tier: Int, phase: Int): Catalog = FilteredCatalog(full, minOf(tier, this.tier), minOf(phase, this.phase), registry)
    override fun registeredOnly(names: Set<String>): Catalog = FilteredCatalog(full, tier, phase, registry?.intersect(names) ?: names)
}

/**
 * The components the phase 1a renderer draws (01 §29). Until `weave-compose`'s `Registry.kt`
 * exists, this is the registry the checker, prompt generator and eval harness use.
 */
val PHASE_1A_COMPONENTS: Set<String> = setOf(
    "Column", "Row", "Card", "Title", "Text", "Metric", "Stat", "Callout", "Button", "Slider", "Segmented",
    "TextField", "Select", "Table", "List", "Item", "Tabs", "Chart", "LineChart", "AreaChart", "BarChart",
)
