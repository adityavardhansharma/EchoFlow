package com.echoflow.weave.core.catalog

import com.echoflow.weave.core.check.TokenSet

/** Token tables from 04 §1. dp values are the ones auto-fix S4 snaps with (02 §8.2). */
object Tokens {
    val SPACE = linkedMapOf("none" to 0, "xxs" to 2, "xs" to 4, "s" to 8, "m" to 12, "l" to 16, "xl" to 24, "xxl" to 32)
    val HEIGHT = linkedMapOf("xs" to 48, "s" to 96, "m" to 168, "l" to 240, "xl" to 320)
    val HEIGHT_WORDS = listOf("screen", "wrap")
    /** EchoFlow default `Shapes` (04 §1.5); S4 uses these, never the runtime theme. */
    val SHAPE = linkedMapOf("none" to 0, "xs" to 8, "s" to 14, "m" to 20, "l" to 28, "xl" to 36)
    val SHAPE_WORDS = listOf("full")
    val EXPRESSIVE_SHAPES = listOf(
        "circle", "square", "slanted", "arch", "fan", "oval", "pill", "triangle", "diamond", "pentagon",
        "gem", "sunny", "verySunny", "cookie", "cookie4", "cookie12", "clover", "clover8", "burst",
        "softBurst", "boom", "flower", "puffy", "ghost", "bun", "heart",
    )
    /** The ten expressive shapes the prompt advertises (06 §3.2). */
    val PROMPT_EXPRESSIVE = listOf("cookie", "clover", "sunny", "gem", "pill", "arch", "burst", "flower", "puffy", "heart")
    val WIDTH_WORDS = listOf("fill", "wrap")
    val TONES = listOf("primary", "secondary", "tertiary", "error", "neutral", "success", "warning", "info", "muted", "positive", "negative")
    val EMPHASIS = listOf("low", "medium", "high")
    val COLOR_ROLES = listOf(
        "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
        "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
        "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
        "error", "onError", "errorContainer", "onErrorContainer",
        "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
        "outline", "outlineVariant", "inverseSurface", "inverseOnSurface", "inversePrimary",
        "neutral", "muted", "success", "warning", "info", "positive", "negative",
    )
    val TEXT_STYLES = listOf(
        "displayLarge", "displayMedium", "displaySmall", "headlineLarge", "headlineMedium", "headlineSmall",
        "titleLarge", "titleMedium", "titleSmall", "bodyLarge", "bodyMedium", "bodySmall",
        "labelLarge", "labelMedium", "labelSmall",
    )
    val TEXT_ALIASES = mapOf(
        "hero" to "displaySmall", "h1" to "headlineSmall", "h2" to "titleLarge", "h3" to "titleMedium",
        "body" to "bodyLarge", "caption" to "bodySmall", "label" to "labelLarge", "overline" to "labelSmall", "mono" to "bodyMedium",
    )
    val HAPTIC = listOf("tick", "click", "confirm", "reject", "heavy")
    val ALIGN = listOf("start", "center", "end", "stretch")
    val VALIGN = listOf("top", "center", "bottom", "baseline")
    val JUSTIFY = listOf("start", "center", "end", "between", "around", "evenly")
    val ENTER = listOf("fade", "rise", "scale", "slide", "pop", "none")
    val EXPORT = listOf("png", "pdf", "csv", "json", "weave")
    private val ICON = Regex("[a-z0-9_]+")

    /** Words valid in a token set (numbers handled separately). */
    fun members(set: TokenSet): List<String> = when (set) {
        TokenSet.SPACE -> SPACE.keys.toList()
        TokenSet.HEIGHT -> HEIGHT.keys.toList() + HEIGHT_WORDS
        TokenSet.WIDTH -> WIDTH_WORDS
        TokenSet.TONE -> TONES
        TokenSet.EMPHASIS -> EMPHASIS
        TokenSet.COLOR -> COLOR_ROLES
        TokenSet.TEXT -> TEXT_STYLES + TEXT_ALIASES.keys
        TokenSet.SHAPE -> SHAPE.keys.toList() + SHAPE_WORDS + EXPRESSIVE_SHAPES
        TokenSet.ELEV -> listOf("0", "1", "2", "3")
        TokenSet.ICON -> emptyList()
        TokenSet.HAPTIC -> HAPTIC
        TokenSet.ENUM -> emptyList()
    }

    fun isMember(set: TokenSet, word: String): Boolean = when (set) {
        TokenSet.ICON -> ICON.matches(word)
        else -> word in members(set)
    }

    /** dp table used by S4 for numeric snapping, or null when the set doesn't snap. */
    fun dpTable(set: TokenSet): Map<String, Int>? = when (set) {
        TokenSet.SPACE -> SPACE
        TokenSet.HEIGHT -> HEIGHT
        TokenSet.SHAPE -> SHAPE
        else -> null
    }

    /** Nearest token by dp; ties go to the smaller token (02 §8.2 S4). */
    fun snap(set: TokenSet, dp: Double): String? {
        val table = dpTable(set) ?: return null
        return table.entries.minWithOrNull(compareBy<Map.Entry<String, Int>>({ Math.abs(it.value - dp) }, { it.value }))?.key
    }

    /** All formats in 03 §11, for E202 on unknown interpolation formats. */
    val FORMATS = setOf(
        "auto", "int", "num", "dec", "compact", "percent", "pct", "currency", "inr", "usd", "eur", "gbp", "jpy", "money",
        "inrCompact", "moneyCompact", "signed", "ordinal", "bytes", "upper", "lower", "title", "trim", "truncate", "plural",
        "date", "weekday", "monthYear", "time", "datetime", "relative", "duration", "clock",
    )
}
