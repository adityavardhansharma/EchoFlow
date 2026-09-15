
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import android.os.Build
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/** The unified "+" menu: add context (image) or turn on a capability (search / research). */
@Composable
internal fun PlusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    showImage: Boolean,
    showFiles: Boolean,
    webSearchOn: Boolean,
    deepResearchOn: Boolean,
    dataAgentOn: Boolean,
    dataAgentAvailable: Boolean,
    echoAdviserOn: Boolean,
    echoFusionOn: Boolean,
    echoAgentOn: Boolean,
    echoAdviserAvailable: Boolean,
    echoFusionAvailable: Boolean,
    echoAgentAvailable: Boolean,
    browserFlowOn: Boolean,
    browserFlowAvailable: Boolean,
    artifactOn: Boolean,
    onImage: () -> Unit,
    onFiles: () -> Unit,
    onToggleWebSearch: () -> Unit,
    onToggleDeepResearch: () -> Unit,
    onToggleDataAgent: () -> Unit,
    onToggleEchoAdviser: () -> Unit,
    onToggleEchoFusion: () -> Unit,
    onToggleEchoAgent: () -> Unit,
    onToggleBrowserFlow: () -> Unit,
    onToggleArtifact: () -> Unit,
    memoryAvailable: Boolean = false,
    memoryOn: Boolean = false,
    onToggleMemory: () -> Unit = {},
) {
    // Bespoke popup anchored above the "+". One surface that blooms out of the anchor corner: a
    // fast scale + fade, no slide. The drop shadow is drawn by the *same* graphics layer as the
    // animation (see below) so it stays rounded and composites cleanly with the fade.
    //
    // Placement (above vs below the "+") is only known after the first popup measure pass, so motion
    // is gated on that: the popup mounts invisibly, [PlusMenuPositionProvider] resolves flip, then
    // the enter animation starts with the transform origin on the correct (anchor) corner.
    var flippedDown by remember { mutableStateOf<Boolean?>(null) }
    var pivotFractionX by remember { mutableStateOf<Float?>(null) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    // Shadow is drawn *outside* the card. Pad the popup by that bloom so the window
    // (and the screen edge) don't clip the left/top, which was leaving a hard band on
    // the right and bottom. The position provider then aligns the *card* to the "+".
    val shadowBlur = 20.dp
    val shadowOffsetY = 2.dp
    val shadowPad = 22.dp
    val shadowPadPx = with(LocalDensity.current) { shadowPad.roundToPx() }
    val positionProvider = remember(shadowPadPx) {
        PlusMenuPositionProvider(
            gapPx = 8,
            shadowPadPx = shadowPadPx,
            onFlipDown = { flippedDown = it },
            onPivotFractionX = { pivotFractionX = it },
        )
    }
    val motion = remember { MutableTransitionState(false) }
    if (expanded && flippedDown != null) {
        motion.targetState = true
    } else if (!expanded) {
        motion.targetState = false
    }
    LaunchedEffect(motion.isIdle, motion.currentState, expanded) {
        if (motion.isIdle && !motion.currentState && !expanded) {
            flippedDown = null
            pivotFractionX = null
        }
    }
    val keepPopup = expanded || motion.currentState || motion.targetState || !motion.isIdle
    if (keepPopup) {
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            val transition = updateTransition(motion, label = "plus-menu")
            val reducedMotion = rememberReducedMotion()
            // Near-instant: a snappy pop in, a quicker fade out. Simple, the way a "+" menu should be.
            val enterMs = if (reducedMotion) 0 else 115
            val exitMs = if (reducedMotion) 0 else 80

            // Grow out of the "+": scale + fade with the transform origin on the button. X is the
            // anchor's resolved position inside the popup (from the position provider, so on-screen
            // clamping can't pull the origin off the button); Y is the edge nearest the anchor —
            // bottom when the menu sits above, top when it flips below.
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val padFracX = if (popupSize.width > 0) shadowPadPx.toFloat() / popupSize.width else 0f
            val padFracY = if (popupSize.height > 0) shadowPadPx.toFloat() / popupSize.height else 0f
            val transformOrigin = TransformOrigin(
                pivotFractionX = pivotFractionX ?: if (isRtl) 1f - padFracX else padFracX,
                pivotFractionY = if (flippedDown == true) padFracY else 1f - padFracY,
            )
            val scale by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) tween(enterMs, easing = FastOutSlowInEasing)
                    else tween(exitMs, easing = FastOutLinearInEasing)
                },
                label = "scale",
            ) { if (it) 1f else 0.9f }
            val alpha by transition.animateFloat(
                transitionSpec = {
                    if (false isTransitioningTo true) tween(enterMs, easing = FastOutSlowInEasing)
                    else tween(exitMs, easing = FastOutLinearInEasing)
                },
                label = "alpha",
            ) { if (it) 1f else 0f }

            // Never taller than the window (compact/landscape/split-screen): cap the height and let
            // the rows scroll, so lower items like Echo Labs can't be pushed off-screen.
            val maxMenuHeight = (LocalConfiguration.current.screenHeightDp - 24).dp
            val menuShape = RoundedCornerShape(22.dp)
            // Even, soft drop shadow. Android's native elevation *spot* shadow is directional — it
            // casts to one side depending on where the popup sits on screen, which looked lopsided
            // and harsh (a hard band down one edge). Instead draw a symmetric Gaussian shadow
            // (setShadowLayer) that blooms evenly around the rounded rect with a tiny downward
            // bias. The popup is padded by [shadowPad] so that bloom is part of the window and
            // isn't clipped into a hard band on the right/bottom. Alpha is baked into the colour
            // so it fades with the menu; the shared layer is driven by scale only (not alpha).
            val shadowColor = Color.Black.copy(alpha = 0.16f * alpha)
            Box(
                Modifier
                    .onSizeChanged { popupSize = it }
                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        this.transformOrigin = transformOrigin
                        clip = false
                    }
                    .padding(shadowPad)
                    .softDropShadow(shadowColor, cornerRadius = 22.dp, blurRadius = shadowBlur, offsetY = shadowOffsetY),
            ) {
                Surface(
                    shape = menuShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .testTag("plus_menu_surface")
                        .graphicsLayer { this.alpha = alpha },
                ) {
                    Column(
                        Modifier
                            .heightIn(max = maxMenuHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(6.dp),
                    ) {
                    if (showImage || showFiles) {
                        MenuSectionLabel("Attach")
                        if (showImage) {
                            PlusMenuRow(Icons.Outlined.AddPhotoAlternate, "Image", on = null, onClick = onImage)
                        }
                        if (showFiles) {
                            PlusMenuRow(Icons.Default.PictureAsPdf, "Files", on = null, onClick = onFiles)
                        }
                        PlusMenuDivider()
                    }
                    MenuSectionLabel("Capabilities")
                    if (memoryAvailable) PlusMenuRow(Icons.Default.AutoAwesome, "Recall for next reply", on = memoryOn, onClick = onToggleMemory)
                    PlusMenuRow(Icons.Default.TravelExplore, "Web search", on = webSearchOn, onClick = onToggleWebSearch)
                    PlusMenuRow(Icons.Default.Science, "Deep Research", on = deepResearchOn, onClick = onToggleDeepResearch)
                    PlusMenuRow(Icons.Default.AutoAwesome, "Artifact", on = artifactOn, onClick = onToggleArtifact)
                    if (dataAgentAvailable) {
                        PlusMenuRow(Icons.Default.Dataset, "Data Agent", on = dataAgentOn, onClick = onToggleDataAgent)
                    }
                    if (browserFlowAvailable) {
                        PlusMenuRow(Icons.Default.Language, "Browser Flow", on = browserFlowOn, onClick = onToggleBrowserFlow)
                    }
                    if (echoAdviserAvailable || echoFusionAvailable || echoAgentAvailable) {
                        PlusMenuDivider()
                        MenuSectionLabel("Echo Labs")
                        if (echoAdviserAvailable) {
                            PlusMenuRow(Icons.Default.Psychology, "Echo Adviser", on = echoAdviserOn, onClick = onToggleEchoAdviser)
                        }
                        if (echoFusionAvailable) {
                            PlusMenuRow(Icons.Default.AccountTree, "Echo Fusion", on = echoFusionOn, onClick = onToggleEchoFusion)
                        }
                        if (echoAgentAvailable) {
                            PlusMenuRow(Icons.Default.Hub, "Echo Agents", on = echoAgentOn, onClick = onToggleEchoAgent)
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * One row of the "+" popup. [on] = null marks a one-shot action (Attach); a non-null value marks a
 * capability toggle, whose leading chip lifts to the accent and gains a trailing check when active.
 */
@Composable
private fun PlusMenuRow(
    icon: ImageVector,
    label: String,
    on: Boolean?,
    onClick: () -> Unit,
) {
    val active = on == true

    // Attach chips are a neutral tonal; an active capability lifts to the accent container.
    val chipColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer
        on == null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val chipContent = when {
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        on == null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp))
                .background(chipColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(17.dp), tint = chipContent)
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (active) {
            Icon(Icons.Default.Check, "On", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Even, symmetric soft shadow around a rounded rect, drawn with a Gaussian [setShadowLayer] rather
 * than Android's directional elevation spot shadow. [offsetY] gives a tiny downward bias; the
 * horizontal spread stays symmetric so the menu doesn't look lit from one side.
 *
 * The caller must pad the popup by at least [blurRadius] + |[offsetY]| so the bloom is inside
 * the window. On API 24–27, [setShadowLayer] is ignored for non-text on a hardware canvas, so
 * we fall back to a soft expanded rounded rect.
 */
private fun Modifier.softDropShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetY: Dp,
): Modifier = drawBehind {
    if (color.alpha <= 0f || blurRadius <= 0.dp) return@drawBehind
    val r = cornerRadius.toPx()
    val blurPx = blurRadius.toPx()
    val dy = offsetY.toPx()
    if (Build.VERSION.SDK_INT >= 28) {
        drawIntoCanvas { canvas ->
            val frameworkPaint = Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = android.graphics.Color.TRANSPARENT
                setShadowLayer(blurPx, 0f, dy, color.toArgb())
            }
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, frameworkPaint)
        }
    } else {
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.4f),
            topLeft = Offset(-blurPx * 0.4f, -blurPx * 0.4f + dy),
            size = Size(size.width + blurPx * 0.8f, size.height + blurPx * 0.8f),
            cornerRadius = CornerRadius(r + blurPx * 0.2f),
        )
    }
}

/** Hairline divider between "+" popup sections. */
@Composable
private fun PlusMenuDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Positions the "+" popup just above the anchor (the "+" button), left edges aligned, and flips it
 * below only if there isn't room above — which at the foot of the screen there almost always is.
 *
 * [shadowPadPx] is the bloom reserved around the card. The *card* (not the padded window) aligns
 * to the "+", so the shadow can sit on every side without being clipped by the popup or the
 * screen edge.
 */
internal class PlusMenuPositionProvider(
    private val gapPx: Int,
    private val shadowPadPx: Int = 0,
    private val onFlipDown: (Boolean) -> Unit,
    private val onPivotFractionX: (Float) -> Unit = {},
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // When the popup is padded for its shadow, that pad *is* the margin — don't add [gapPx]
        // on top or the card drifts away from the "+". Without a pad, keep [gapPx] from the edge.
        val edge = (gapPx - shadowPadPx).coerceAtLeast(0)
        val maxX = (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge)
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            (anchorBounds.left - shadowPadPx).coerceIn(edge, maxX)
        } else {
            (anchorBounds.right - popupContentSize.width + shadowPadPx).coerceIn(edge, maxX)
        }
        // Where the "+" sits inside the *resolved* (post-clamp) popup box, as a 0..1 fraction. The
        // enter/exit scale grows from this point so it always blooms out of the button — including
        // when horizontal clamping shifts the popup off the anchor's leading edge.
        val anchorLeadingX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right
        val pivotFractionX = if (popupContentSize.width <= 0) {
            if (layoutDirection == LayoutDirection.Ltr) 0f else 1f
        } else {
            ((anchorLeadingX - x).toFloat() / popupContentSize.width).coerceIn(0f, 1f)
        }
        onPivotFractionX(pivotFractionX)
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val flipDown = above < edge
        onFlipDown(flipDown)
        val y = if (flipDown) {
            (anchorBounds.bottom + gapPx)
                .coerceIn(edge, (windowSize.height - popupContentSize.height - edge).coerceAtLeast(edge))
        } else {
            above
        }
        return IntOffset(x, y)
    }
}

/** A quiet header inside the "+" menu, so its two halves read as different kinds of choice. */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.m, top = Spacing.s, bottom = Spacing.xs),
    )
}
