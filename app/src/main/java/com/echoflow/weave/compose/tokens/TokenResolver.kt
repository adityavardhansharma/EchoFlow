package com.echoflow.weave.compose.tokens

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.weave.core.catalog.Tokens
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import java.util.concurrent.ConcurrentHashMap

/** A word from a token slot: symbols and strings both count (04 §1). */
fun WValue?.word(): String? = when (this) {
    is WSym -> name
    is WStr -> v
    else -> null
}

/** Token tables from 04 §1 resolved against the current Material theme. */
object TokenResolver {

    /** `token<space>` (04 §1.1); numbers are dp. Words resolve through the app's [Spacing] scale. */
    fun space(v: WValue?, default: Dp): Dp = when (v) {
        is WNum -> v.v.toFloat().coerceIn(0f, 96f).dp
        else -> v.word()?.let { spaceToken(it) } ?: default
    }

    /** 04 §1.1: Weave `l` is `Spacing.base`, not `Spacing.l`. */
    fun spaceToken(word: String): Dp? = when (word) {
        "none" -> 0.dp
        "xxs" -> 2.dp
        "xs" -> com.echoflow.ui.theme.Spacing.xs
        "s" -> com.echoflow.ui.theme.Spacing.s
        "m" -> com.echoflow.ui.theme.Spacing.m
        "l" -> com.echoflow.ui.theme.Spacing.base
        "xl" -> com.echoflow.ui.theme.Spacing.xl
        "xxl" -> com.echoflow.ui.theme.Spacing.xxl
        else -> null
    }

    /** `token<height>` (04 §1.2). Null = wrap content. [screen] is the window height. */
    fun height(v: WValue?, screen: Dp): Dp? = when (v) {
        is WNum -> v.v.toFloat().coerceIn(0f, 2000f).dp
        else -> when (val w = v.word()) {
            null, "wrap" -> null
            "screen" -> screen * 0.8f
            else -> Tokens.HEIGHT[w]?.dp
        }
    }

    /** Height with a component default token. */
    fun heightOr(v: WValue?, default: String, screen: Dp): Dp = height(v, screen) ?: Tokens.HEIGHT[default]?.dp ?: 168.dp

    /**
     * `token<shape>` (04 §1.5), including the expressive shapes. The corner scale resolves through
     * `MaterialTheme.shapes`, so a theme change reaches blocks (T-414).
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    fun shape(v: WValue?, default: Shape = androidx.compose.material3.MaterialTheme.shapes.small): Shape {
        if (v is WNum) return RoundedCornerShape(v.v.toFloat().coerceIn(0f, 64f).dp)
        val w = v.word() ?: return default
        val shapes = androidx.compose.material3.MaterialTheme.shapes
        when (w) {
            "none" -> return RectangleShape
            "xs" -> return shapes.extraSmall
            "s" -> return shapes.small
            "m" -> return shapes.medium
            "l" -> return shapes.large
            "xl" -> return shapes.extraLarge
        }
        return when (w) {
            "full" -> CircleShape
            "circle" -> RoundedPolygonShape(MaterialShapes.Circle)
            "square" -> RoundedPolygonShape(MaterialShapes.Square)
            "slanted" -> RoundedPolygonShape(MaterialShapes.Slanted)
            "arch" -> RoundedPolygonShape(MaterialShapes.Arch)
            "fan" -> RoundedPolygonShape(MaterialShapes.Fan)
            "oval" -> RoundedPolygonShape(MaterialShapes.Oval)
            "pill" -> RoundedPolygonShape(MaterialShapes.Pill)
            "triangle" -> RoundedPolygonShape(MaterialShapes.Triangle)
            "diamond" -> RoundedPolygonShape(MaterialShapes.Diamond)
            "pentagon" -> RoundedPolygonShape(MaterialShapes.Pentagon)
            "gem" -> RoundedPolygonShape(MaterialShapes.Gem)
            "sunny" -> RoundedPolygonShape(MaterialShapes.Sunny)
            "verySunny" -> RoundedPolygonShape(MaterialShapes.VerySunny)
            "cookie" -> RoundedPolygonShape(MaterialShapes.Cookie9Sided)
            "cookie4" -> RoundedPolygonShape(MaterialShapes.Cookie4Sided)
            "cookie12" -> RoundedPolygonShape(MaterialShapes.Cookie12Sided)
            "clover" -> RoundedPolygonShape(MaterialShapes.Clover4Leaf)
            "clover8" -> RoundedPolygonShape(MaterialShapes.Clover8Leaf)
            "burst" -> RoundedPolygonShape(MaterialShapes.Burst)
            "softBurst" -> RoundedPolygonShape(MaterialShapes.SoftBurst)
            "boom" -> RoundedPolygonShape(MaterialShapes.Boom)
            "flower" -> RoundedPolygonShape(MaterialShapes.Flower)
            "puffy" -> RoundedPolygonShape(MaterialShapes.Puffy)
            "ghost" -> RoundedPolygonShape(MaterialShapes.Ghostish)
            "bun" -> RoundedPolygonShape(MaterialShapes.Bun)
            "heart" -> RoundedPolygonShape(MaterialShapes.Heart)
            else -> default
        }
    }

    /** `token<text>` (04 §1.6), aliases included; `mono` uses a monospace family. */
    fun text(v: WValue?, t: Typography, default: String = "bodyLarge"): TextStyle {
        val raw = v.word() ?: default
        val name = Tokens.TEXT_ALIASES[raw] ?: raw
        val style = when (name) {
            "displayLarge" -> t.displayLarge; "displayMedium" -> t.displayMedium; "displaySmall" -> t.displaySmall
            "headlineLarge" -> t.headlineLarge; "headlineMedium" -> t.headlineMedium; "headlineSmall" -> t.headlineSmall
            "titleLarge" -> t.titleLarge; "titleMedium" -> t.titleMedium; "titleSmall" -> t.titleSmall
            "bodyLarge" -> t.bodyLarge; "bodyMedium" -> t.bodyMedium; "bodySmall" -> t.bodySmall
            "labelLarge" -> t.labelLarge; "labelMedium" -> t.labelMedium; "labelSmall" -> t.labelSmall
            else -> t.bodyLarge
        }
        return if (raw == "mono") style.copy(fontFamily = FontFamily.Monospace) else style
    }

    /** One colour for a single colour slot (`color`, `bg`, mark fill) — 04 §1.4 left table. */
    fun color(v: WValue?, s: ColorScheme, dark: Boolean, fill: Boolean = false): Color? {
        val w = v.word() ?: return null
        return when (w) {
            "primary" -> s.primary; "onPrimary" -> s.onPrimary; "primaryContainer" -> s.primaryContainer; "onPrimaryContainer" -> s.onPrimaryContainer
            "secondary" -> s.secondary; "onSecondary" -> s.onSecondary; "secondaryContainer" -> s.secondaryContainer; "onSecondaryContainer" -> s.onSecondaryContainer
            "tertiary" -> s.tertiary; "onTertiary" -> s.onTertiary; "tertiaryContainer" -> s.tertiaryContainer; "onTertiaryContainer" -> s.onTertiaryContainer
            "error", "negative" -> s.error; "onError" -> s.onError; "errorContainer" -> s.errorContainer; "onErrorContainer" -> s.onErrorContainer
            "surface" -> s.surface; "onSurface" -> s.onSurface; "surfaceVariant" -> s.surfaceVariant; "onSurfaceVariant" -> s.onSurfaceVariant
            "surfaceContainerLowest" -> s.surfaceContainerLowest; "surfaceContainerLow" -> s.surfaceContainerLow
            "surfaceContainer" -> s.surfaceContainer; "surfaceContainerHigh" -> s.surfaceContainerHigh; "surfaceContainerHighest" -> s.surfaceContainerHighest
            "outline" -> s.outline; "outlineVariant" -> s.outlineVariant; "inverseSurface" -> s.inverseSurface
            "inverseOnSurface" -> s.inverseOnSurface; "inversePrimary" -> s.inversePrimary
            "neutral" -> if (fill) s.surfaceContainerHighest else s.onSurface
            "muted" -> s.onSurfaceVariant
            "info" -> s.primary
            "success", "positive" -> semantic(SUCCESS_HUE, s, dark).solid
            "warning" -> semantic(WARNING_HUE, s, dark).solid
            else -> null
        }
    }

    /** A container/content pair for a tone at an emphasis (04 §1.4 tone table). */
    data class Pair2(val container: Color, val content: Color)

    fun tone(tone: String?, emphasis: String?, s: ColorScheme, dark: Boolean): Pair2? {
        val t = tone ?: return null
        val e = emphasis ?: "medium"
        fun pick(high: Pair2, medium: Pair2, low: Color) = when (e) {
            "high" -> high; "low" -> Pair2(Color.Transparent, low); else -> medium
        }
        return when (t) {
            "primary", "info" -> pick(Pair2(s.primary, s.onPrimary), Pair2(s.primaryContainer, s.onPrimaryContainer), s.primary)
            "secondary" -> pick(Pair2(s.secondary, s.onSecondary), Pair2(s.secondaryContainer, s.onSecondaryContainer), s.secondary)
            "tertiary" -> pick(Pair2(s.tertiary, s.onTertiary), Pair2(s.tertiaryContainer, s.onTertiaryContainer), s.tertiary)
            "error", "negative" -> pick(Pair2(s.error, s.onError), Pair2(s.errorContainer, s.onErrorContainer), s.error)
            "neutral" -> pick(Pair2(s.inverseSurface, s.inverseOnSurface), Pair2(s.surfaceContainerHighest, s.onSurface), s.onSurface)
            "muted" -> pick(Pair2(s.surfaceContainerHighest, s.onSurface), Pair2(s.surfaceContainer, s.onSurfaceVariant), s.onSurfaceVariant)
            "success", "positive" -> semantic(SUCCESS_HUE, s, dark).let { pick(Pair2(it.solid, it.onSolid), Pair2(it.container, it.onContainer), it.solid) }
            "warning" -> semantic(WARNING_HUE, s, dark).let { pick(Pair2(it.solid, it.onSolid), Pair2(it.container, it.onContainer), it.solid) }
            else -> null
        }
    }

    /** Content colour of a tone at low emphasis: the text colour a `Metric` value uses. */
    fun toneText(tone: String?, s: ColorScheme, dark: Boolean): Color? = tone(tone, "low", s, dark)?.content

    // ---- harmonised semantic colours (04 §1.4.2) ------------------------------------------------

    data class Semantic(val solid: Color, val onSolid: Color, val container: Color, val onContainer: Color)

    private const val SUCCESS_HUE = 145f
    private const val WARNING_HUE = 75f
    private val semanticCache = ConcurrentHashMap<String, Semantic>()

    /**
     * Success/warning tonal pairs that fit the palette: the source hue with the primary's chroma
     * (at least a floor), at the 04 §1.4.2 tones. Uses HSL lightness as the tone axis (a close
     * stand-in for HCT that needs no extra library).
     */
    fun semantic(hue: Float, s: ColorScheme, dark: Boolean): Semantic {
        val key = "$hue:${s.primary.toArgb()}:$dark"
        return semanticCache.getOrPut(key) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(s.primary.toArgb(), hsl)
            val sat = maxOf(0.45f, hsl[1]).coerceAtMost(0.85f)
            fun tone(t: Int) = Color(ColorUtils.HSLToColor(floatArrayOf(hue, sat, t / 100f)))
            if (dark) Semantic(tone(80), tone(20), tone(30), tone(90))
            else Semantic(tone(40), tone(100), tone(90), tone(10))
        }
    }

    /** Chart series palette (04 §1.4.1). */
    fun series(i: Int, s: ColorScheme): Color {
        val base = listOf(s.primary, s.tertiary, s.secondary, s.error)
        return when {
            i < 4 -> base[i]
            i < 8 -> base[i - 4].copy(alpha = 0.6f)
            else -> s.onSurfaceVariant
        }
    }

    // ---- icons (04 §1.9) ------------------------------------------------------------------------

    private val iconCache = ConcurrentHashMap<String, ImageVector>()

    /** A Material Symbols name (`open_in_full`) → the extended icon set, or `widgets`. */
    fun icon(name: String?): ImageVector {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return Icons.Filled.Widgets
        return iconCache.getOrPut(n) { loadIcon(n) ?: Icons.Filled.Widgets }
    }

    fun iconOrNull(name: String?): ImageVector? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return iconCache[n] ?: loadIcon(n)?.also { iconCache[n] = it }
    }

    private fun loadIcon(name: String): ImageVector? {
        val pascal = name.split('_', '-').joinToString("") { p -> p.replaceFirstChar { it.uppercaseChar() } }
            .let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }
        for (style in listOf("filled", "outlined", "rounded")) {
            val holder = when (style) { "filled" -> Icons.Filled; "outlined" -> Icons.Outlined; else -> Icons.Rounded }
            val v = runCatching {
                Class.forName("androidx.compose.material.icons.$style.${pascal}Kt")
                    .getMethod("get$pascal", holder.javaClass)
                    .invoke(null, holder) as? ImageVector
            }.getOrNull()
            if (v != null) return v
        }
        return null
    }
}
