package com.echoflow.weave.core.catalog

/**
 * The rest of 04: every component and mark beyond the 1a slice, with the tier and phase 04
 * gives each. Signatures follow 04's text; record-shaped parameters (`{lat, lng}`) are typed
 * `rec` because the checker validates their fields at runtime.
 */
internal object CatalogExtras {

    private fun comp(
        name: String, sig: String, leads: Int, tier: Int, phase: Int, doc: String, group: String,
        children: ChildrenSpec = ChildrenSpec.None, aliases: Map<String, String> = emptyMap(),
        placeholder: PlaceholderKind = PlaceholderKind.BAR, block: Boolean = true,
    ): ComponentSpec {
        val all = Sig.params(sig)
        val ls = all.take(leads)
        val ps = all.drop(leads)
        return ComponentSpec(name, ls, ps, ps.map { it.name }, aliases, children, tier, phase, doc, placeholder, block, true, group)
    }

    private val INPUT = mapOf("bind" to "value")

    val components: List<ComponentSpec> = listOf(
        // ---- layout (04 §4)
        comp("Carousel", "itemW: num = 0.75, gap: space = s, snap: bool = true, peek: bool = true, pager: bool = false, h?: height", 0, 1, 1,
            "horizontally scrolling cards", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Split", "ratio: num = 0.5, breakpoint: medium|expanded = medium, gap: space = l", 0, 1, 1,
            "two panes side by side on wide screens, stacked on phones", "Layout", ChildrenSpec.Exactly(2), placeholder = PlaceholderKind.NONE),
        comp("Sticky", "h?: height", 0, 2, 2, "header that stays visible", "Layout", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),

        // ---- text and content (04 §5)
        comp("Rich", "", 0, 2, 2, "one paragraph built from inline parts", "Text", ChildrenSpec.Any, placeholder = PlaceholderKind.TEXT),
        comp("Avatar", "name?: str, src?: str, size: s|m|l = m, shape: shape = full, tone: tone = primary", 1, 1, 1, "initials or a photo", "Text", block = false),
        comp("Badge", "content: str | num, tone: tone = primary", 1, 1, 1, "small pill with a count", "Text", block = false),
        comp("Chip", "label: str, icon?: icon, selected?: bool, onTap?: action, trail?: icon", 1, 1, 1, "compact label or filter", "Text", block = false),
        comp("Link", "label: str, href: str", 2, 1, 1, "a link (opens after confirmation)", "Text", block = false),
        comp("Quote", "content: str, cite?: str", 1, 1, 1, "a quotation", "Text", placeholder = PlaceholderKind.TEXT),
        comp("Code", "source: str, lang?: str, run: bool = false, wrap: bool = false", 1, 1, 1, "code with highlighting and copy", "Text", placeholder = PlaceholderKind.TEXT),
        comp("Kbd", "keys: str | list", 1, 1, 2, "keyboard keys", "Text", block = false),
        comp("Empty", "title: str, body?: str, icon: icon = inbox, action?: node", 1, 1, 1, "empty-state message", "Text"),

        // ---- numbers and status (04 §6)
        comp("Progress", "value: num, kind: linear|circular|wavy = wavy, label?: str, showValue: bool = false, tone: tone = primary", 1, 1, 1, "progress bar or ring (value 0–1)", "Numbers"),
        comp("Gauge", "value: num, min: num = 0, max: num = 100, bands?: list, label?: str, format?: str, size: s|m|l = m", 1, 1, 2, "arc gauge", "Numbers", placeholder = PlaceholderKind.CHART),
        comp("Rating", "value: num, max: num = 5", 1, 1, 1, "stars, read-only", "Numbers", block = false),
        comp("Steps", "current: num, labels: list<str>", 2, 1, 2, "step indicator", "Numbers"),
        comp("Timer", "seconds: num, running: bool, onDone?: action, style: ring|digits = ring", 2, 2, 2, "countdown", "Numbers"),
        comp("Clock", "time?: datetime | time, style: digital|analog = digital, zone?: str", 0, 1, 2, "a clock", "Numbers"),

        // ---- inputs (04 §7)
        comp("Fab", "icon: icon, onTap: action, label?: str, expanded: bool = false, size: s|m|l = m, tone: tone = primary", 2, 2, 2, "floating action button at the block's corner", "Inputs", placeholder = PlaceholderKind.BUTTON),
        comp("SplitButton", "label: str, onTap: action, menu: list, variant: filled|tonal|outlined = filled", 2, 2, 2, "button with a menu of more actions", "Inputs", placeholder = PlaceholderKind.BUTTON),
        comp("ButtonGroup", "value?: place, options?: list, multi: bool = false", 0, 2, 2, "connected group of buttons or toggles", "Inputs", ChildrenSpec.Any, aliases = INPUT, placeholder = PlaceholderKind.BUTTON),
        comp("RangeSlider", "value: place, min: num = 0, max: num = 100, step?: num, label?: str, format?: str, prefix?: str, suffix?: str", 1, 2, 2, "pick a range [lo, hi]", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("DatePicker", "value: place, label: str, min?: date, max?: date", 1, 2, 1, "date field with a calendar", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("DateRange", "value: place, label: str", 1, 2, 2, "pick a start and end date", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("TimePicker", "value: place, label: str", 1, 2, 2, "time field with a clock", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("ColorPick", "value: place, options: list = [primary, secondary, tertiary, error, success, warning, neutral]", 1, 2, 2, "pick a theme colour", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("RatingInput", "value: place, max: num = 5, half: bool = false", 1, 2, 2, "tap stars to rate", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.INPUT),
        comp("Pad2D", "value: place, x: rec, y: rec, h: height = l", 1, 2, 3, "drag a point on a 2D pad", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.CHART),
        comp("Sketch", "value: place, h: height = l, tools: list = [pen, marker, eraser], tone: tone = neutral", 1, 2, 3, "drawing surface", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.CHART),
        comp("Swipe", "deck: place, render: fn(item) → node, onLeft?: fn(item) → action, onRight?: fn(item) → action, h: height = l", 1, 2, 3, "swipe cards left or right", "Inputs", placeholder = PlaceholderKind.CHART),
        comp("Rank", "value: place, render?: fn(item) → node", 1, 2, 3, "drag to reorder", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.TABLE),
        comp("Sort", "value: place, columns: list<str>, render?: fn(item) → node, colW: num = 0.8", 1, 2, 3, "kanban board", "Inputs", aliases = INPUT, placeholder = PlaceholderKind.TABLE),
        comp("Form", "onSubmit: action, submitLabel: str = \"Submit\", gap: space = m", 0, 2, 1, "fields with a submit button that checks their rules", "Inputs", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),

        // ---- data (04 §8)
        comp("Tree", "root: any, children: fn(node) → list, render?: fn(node) → node, open: num = 1", 1, 2, 2, "collapsible tree", "Data", placeholder = PlaceholderKind.TABLE),
        comp("Timeline", "items: list, render?: fn(item) → node, time?: fn(item) → any, dir: vertical|horizontal = vertical, tone: tone = primary", 1, 1, 2, "events along a line", "Data", placeholder = PlaceholderKind.TABLE),
        comp("Calendar", "events: list, month?: date, view: month|week|agenda = month, onDay?: fn(date) → action, selected?: place", 1, 2, 2, "month calendar with events", "Data", placeholder = PlaceholderKind.CHART),
        comp("Heatcal", "values: list, tone: tone = primary, weeks: num = 17", 1, 1, 2, "GitHub-style activity grid", "Data", placeholder = PlaceholderKind.CHART),
        comp("Diff", "before: str, after: str, mode: inline|split = inline", 2, 1, 2, "word-level diff", "Data", placeholder = PlaceholderKind.TEXT),
        comp("Json", "value: any, open: num = 1", 1, 1, 2, "collapsible JSON tree", "Data", placeholder = PlaceholderKind.TEXT),

        // ---- navigation (04 §9)
        comp("Pager", "index: place, dots: bool = true, h?: height, peek: bool = false", 1, 2, 1, "swipe between pages", "Navigation", ChildrenSpec.Any, placeholder = PlaceholderKind.CHART),
        comp("Wizard", "step: place, labels: list<str>, next?: list, onFinish: action, finishLabel: str = \"Done\"", 1, 2, 1, "step-by-step pages with Back/Next", "Navigation", ChildrenSpec.Any),
        comp("Accordion", "open?: place, single: bool = true", 0, 2, 2, "collapsible sections", "Navigation", ChildrenSpec.Only(setOf("Section"))),
        comp("Reveal", "label: str = \"Show more\", lines: num = 3", 0, 1, 1, "clamped content with Show more", "Navigation", ChildrenSpec.Any, placeholder = PlaceholderKind.TEXT),
        comp("Flip", "side?: place, back: node", 0, 2, 2, "card that flips on tap", "Navigation", ChildrenSpec.Any),

        // ---- media (04 §10)
        comp("Image", "src: str | image, alt: str, fit: cover|contain = cover, h?: height, shape: shape = m, zoom: bool = false", 2, 1, 1, "an image from an https URL", "Media", placeholder = PlaceholderKind.CHART),
        comp("Gallery", "srcs: list<str>, alts?: list<str>, cols: num = 3, viewer: bool = true", 1, 1, 2, "grid of images", "Media", placeholder = PlaceholderKind.CHART),
        comp("Video", "src: str, poster?: str, autoplay: bool = false, loop: bool = false, h?: height", 1, 2, 2, "video player", "Media", placeholder = PlaceholderKind.CHART),
        comp("Audio", "src: str, title?: str, waveform: bool = true", 1, 2, 2, "audio player", "Media"),
        comp("Map", "center?: rec | str, zoom: num = 12, markers?: list, route?: list, h: height = l, interactive: bool = false", 0, 3, 3, "a map", "Media", placeholder = PlaceholderKind.CHART),
        comp("Model3D", "src: str, autoRotate: bool = true, h: height = l", 1, 4, 3, "3D model viewer", "Media", placeholder = PlaceholderKind.CHART),
        comp("Lottie", "src: str, loop: bool = true, h: height = m", 1, 2, 3, "Lottie animation", "Media", placeholder = PlaceholderKind.CHART),
        comp("Qr", "content: str, size: s|m|l = m", 1, 1, 2, "QR code", "Media", placeholder = PlaceholderKind.CHART),

        // ---- overlays (04 §11): bindings opened with !open(name)
        comp("Sheet", "title?: str, full: bool = false", 0, 2, 1, "bottom sheet, opened with !open(name)", "Overlays", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Dialog", "title: str, confirm?: rec, dismiss?: rec", 1, 2, 1, "dialog, opened with !open(name)", "Overlays", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Popover", "anchor: name", 1, 2, 2, "small card below a node, opened with !open(name)", "Overlays", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Menu", "anchor: name, items: list", 2, 2, 2, "menu below a node, opened with !open(name)", "Overlays", placeholder = PlaceholderKind.NONE),

        // ---- charts (04 §12.4)
        comp("PieChart", "data: list<rec>, value: field, label: field, h: height = m", 1, 1, 1, "pie chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("DonutChart", "data: list<rec>, value: field, label: field, h: height = m", 1, 1, 1, "donut chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("ScatterChart", "data: list<rec>, x: field, y: field, size?: field, h: height = m", 1, 1, 1, "scatter plot", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Sparkline", "values: list<num>, tone: tone = primary, h: height = xs", 1, 1, 1, "tiny line without axes", "Charts", placeholder = PlaceholderKind.CHART, block = false),
        comp("Histogram", "values: list<num>, bins?: num, h: height = m", 1, 1, 1, "histogram of values", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Radar", "data: list<rec>, axes: list<str>, series?: field, h: height = m", 1, 2, 2, "radar chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Funnel", "data: list<rec>, value: field, label: field, h: height = m", 1, 1, 2, "funnel", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Treemap", "data: list<rec>, value: field, label: field, h: height = m", 1, 1, 2, "treemap", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Sankey", "data: list<rec>, from: field, to: field, value: field, h: height = l", 1, 2, 2, "flows between stages", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Candles", "data: list<rec>, x: field, open: field, high: field, low: field, close: field, h: height = m", 1, 2, 2, "candlestick chart", "Charts", placeholder = PlaceholderKind.CHART),
        comp("BoxPlot", "data: list<rec>, x: field, y: field, h: height = m", 1, 2, 2, "box plot per group", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Heatmap", "data: list<rec>, x: field, y: field, value: field, h: height = m", 1, 1, 2, "heatmap", "Charts", placeholder = PlaceholderKind.CHART),
        comp("Waterfall", "data: list<rec>, x: field, y: field, h: height = m", 1, 1, 2, "running-total bars", "Charts", placeholder = PlaceholderKind.CHART),

        // ---- canvas (04 §13)
        comp("Canvas", "view: list = [0, 0, 100, 60], h: height = l, fit: contain|cover = contain, onTap?: fn(p) → action, onDrag?: fn(p) → action, onDragEnd?: fn(p) → action, onPinch?: fn(s) → action, label: str = \"Drawing\"", 0, 2, 3,
            "free drawing with marks in view units", "Visual", ChildrenSpec.Marks("Canvas"), placeholder = PlaceholderKind.CHART),

        // ---- visual (04 §14)
        comp("Gradient", "tones: list = [primaryContainer, tertiaryContainer], angle: num = 135, h?: height, shape: shape = m", 0, 1, 2, "gradient background", "Visual", ChildrenSpec.Any, placeholder = PlaceholderKind.NONE),
        comp("Shader", "kind: aurora|mesh|grain|ripple|liquid, tones?: list, speed: num = 1, h: height = m, shape: shape = m", 1, 4, 3, "animated shader background", "Visual", ChildrenSpec.Any, placeholder = PlaceholderKind.CHART),
        comp("Shape", "kind: shape, size: s|m|l|xl = m, tone: tone = primary, emphasis: low|medium|high = medium, morphTo?: shape, rotate?: num, icon?: icon", 1, 1, 2, "an expressive shape", "Visual", block = false),
        comp("Blob", "seed: any, tone: tone = primary, h: height = m", 1, 1, 3, "organic blob", "Visual"),
        comp("Skeleton", "kind: text|card|chart|list = card", 0, 1, 1, "loading placeholder", "Visual"),

        // ---- learning and play (04 §15)
        comp("Quiz", "answers: place, questions: list, shuffle: bool = false, onDone?: fn(score) → action", 2, 2, 3, "multiple-choice quiz", "Play", placeholder = PlaceholderKind.TABLE),
        comp("Flashcards", "state: place, cards: list, spaced: bool = true", 2, 2, 3, "flip cards with spaced repetition", "Play"),
        comp("Blanks", "values: place, text: str", 2, 2, 3, "fill in the blanks", "Play", placeholder = PlaceholderKind.TEXT),
        comp("Match", "pairs: place, left: list<str>, right: list<str>", 3, 2, 3, "match left to right", "Play", placeholder = PlaceholderKind.TABLE),
        comp("Board", "cells: place, rows: num, cols: num, render: fn(cell, r, c) → node, onCell: fn(r, c) → action, gap: space = xxs", 3, 2, 3, "grid game board", "Play", placeholder = PlaceholderKind.CHART),
        comp("Dice", "values: place, count: num = 1, sides: num = 6, onRoll?: fn(values) → action", 1, 2, 3, "tap to roll dice", "Play", block = false),
        comp("Spinner", "choice: place, options: list<str>", 2, 2, 3, "spin the wheel", "Play", placeholder = PlaceholderKind.CHART),
        comp("Scratch", "cover: color = surfaceContainerHighest", 0, 2, 3, "scratch to reveal", "Play", ChildrenSpec.Any),
    )

    private fun mark(name: String, sig: String, doc: String, phase: Int, container: Set<String>): MarkSpec {
        val all = Sig.params(sig)
        val leads = all.takeWhile { it.required }
        return MarkSpec(name, container, leads, all.drop(leads.size), acceptsChildren = name == "group", tier = if ("Canvas" in container) 2 else 1, phase = phase, doc = doc)
    }

    private val CANVAS = setOf("Canvas")

    val marks: List<MarkSpec> = listOf(
        mark("range", "x: field, lo: field, hi: field, tone?: tone", "vertical range with caps", 2, setOf("Chart")),
        mark("cell", "x: field, y: field, value: field, tone?: tone", "heatmap cell", 2, setOf("Chart")),
        mark("link", "from: field, to: field, value: field", "sankey link", 2, setOf("Chart")),
        mark("circle", "x: num, y: num, r: num, fill?: color, stroke?: color, width?: num, onTap?: action, key?: any", "circle", 3, CANVAS),
        mark("rect", "x: num, y: num, w: num, h: num, fill?: color, stroke?: color, width?: num, radius: num = 0, onTap?: action, key?: any", "rectangle", 3, CANVAS),
        mark("line", "x1: num, y1: num, x2: num, y2: num, stroke: color = onSurface, width: any = s, dashed: bool = false, cap: round|butt = round, onTap?: action, key?: any", "line segment", 3, CANVAS),
        mark("path", "d: str, fill?: color, stroke?: color, width?: num, onTap?: action, key?: any", "SVG path", 3, CANVAS),
        mark("poly", "points: list, fill?: color, stroke?: color, width?: num, closed: bool = true, onTap?: action, key?: any", "polygon or polyline", 3, CANVAS),
        mark("label", "x: num, y: num, content: any, style: token<text> = labelMedium, tone?: tone, color?: color, anchor: start|middle|end = middle, onTap?: action, key?: any", "text", 3, CANVAS),
        mark("icon", "x: num, y: num, name: icon, size: s|m|l = m, tone?: tone, onTap?: action, key?: any", "icon", 3, CANVAS),
        mark("image", "x: num, y: num, w: num, h: num, src: str, onTap?: action, key?: any", "image", 3, CANVAS),
        mark("group", "translate?: list, rotate?: num, scale?: num, origin?: list, opacity?: num, onTap?: action, key?: any", "transformed group", 3, CANVAS),
    )
}
