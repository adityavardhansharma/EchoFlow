@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.max

/**
 * Material 3 Expressive shape utilities.
 *
 * These wrap the [MaterialShapes] polygon set (Cookie, Clover, Sunny, ...) as Compose [Shape]s so
 * brand surfaces — the AI avatar, the empty-state hero — can use real geometric shapes and morph
 * between them, which is one of the defining traits of the Expressive design language.
 */

private fun RoundedPolygon.bounds(): Rect =
    calculateBounds().let { Rect(it[0], it[1], it[2], it[3]) }

/** Clips content to a single [MaterialShapes] polygon, scaled to fill the layout bounds. */
class RoundedPolygonShape(private val polygon: RoundedPolygon) : Shape {
    private val matrix = Matrix()
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path: Path = polygon.toPath().asComposePath()
        matrix.reset()
        val b = polygon.bounds()
        val maxDim = max(b.width, b.height).takeIf { it > 0f } ?: 1f
        val scaleX = size.width / maxDim
        val scaleY = size.height / maxDim
        // Centre the (uniformly scaled) shape: a non-square polygon would otherwise sit flush to
        // the top-left, making centred content (e.g. an icon) look off-centre.
        matrix.translate((size.width - b.width * scaleX) / 2f, (size.height - b.height * scaleY) / 2f)
        matrix.scale(scaleX, scaleY)
        matrix.translate(-b.left, -b.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Like [RoundedPolygonShape] but stretches the polygon non-uniformly to fill the layout's full
 * width AND height — so a [MaterialShapes] polygon (e.g. ClamShell) can be used as the outline of a
 * wide button, giving an expressive faceted shape instead of a plain rounded rectangle.
 */
class StretchedPolygonShape(private val polygon: RoundedPolygon) : Shape {
    private val matrix = Matrix()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = polygon.toPath().asComposePath()
        matrix.reset()
        val b = polygon.bounds()
        val w = b.width.takeIf { it > 0f } ?: 1f
        val h = b.height.takeIf { it > 0f } ?: 1f
        matrix.scale(size.width / w, size.height / h)
        matrix.translate(-b.left, -b.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Clips content to a [Morph] between two [MaterialShapes] polygons at the given [progress]
 * (0f..1f). The transform is derived from the morphed path's own bounds so it fills the layout
 * correctly regardless of how the source polygons are normalized.
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    private val matrix = Matrix()
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path: Path = morph.toPath(progress = progress).asComposePath()
        val b = path.getBounds()
        val maxDim = max(b.width, b.height).takeIf { it > 0f } ?: 1f
        val scaleX = size.width / maxDim
        val scaleY = size.height / maxDim
        matrix.reset()
        // Centre the morphed shape so centred content (the icon) stays visually centred even when
        // the morph's bounds aren't square.
        matrix.translate((size.width - b.width * scaleX) / 2f, (size.height - b.height * scaleY) / 2f)
        matrix.scale(scaleX, scaleY)
        matrix.translate(-b.left, -b.top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** A gentle, looping morph progress driver for ambient "alive" shapes. */
@Composable
fun rememberMorphProgress(durationMillis: Int = 2600): State<Float> {
    val transition = rememberInfiniteTransition(label = "expressive-morph")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "expressive-morph-progress",
    )
}

/** Remembers a [Morph] between two [MaterialShapes] polygons. */
@Composable
fun rememberMorph(start: RoundedPolygon, end: RoundedPolygon): Morph =
    remember(start, end) { Morph(start, end) }

/** Curated brand shapes used across the app. */
object BrandShapes {
    val avatarStart: RoundedPolygon get() = MaterialShapes.Cookie9Sided
    val avatarEnd: RoundedPolygon get() = MaterialShapes.Clover4Leaf
    val heroStart: RoundedPolygon get() = MaterialShapes.Sunny
    val heroEnd: RoundedPolygon get() = MaterialShapes.Cookie12Sided
}

