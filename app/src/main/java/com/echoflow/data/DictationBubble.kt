package com.echoflow.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.sin

internal enum class DictationPhase { Idle, Recording, Transcribing }

/** Exactly one 48dp non-focusable window. WindowManager never gets a fullscreen touch surface. */
internal class DictationBubble(
    context: Context,
    private val settings: SettingsRepository,
    private val onTap: () -> Unit,
    private val onFailure: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val size = (48 * density).toInt()
    private val edgeGap = (8 * density).toInt()
    private var keyboardTop: Int? = null
    private var fullMaxY = 0
    private var attached = false
    private var dockRight = settings.getDictationBubbleRight()
    private var yFraction = settings.getDictationBubbleY()
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    @Suppress("RtlHardcoded") // Persist physical LEFT/RIGHT edges, independent of text direction.
    private val params = WindowManager.LayoutParams(size, size,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }
    private var minY = 0
    private var maxY = 0
    private var leftX = 0
    private var rightX = 0
    private val button = object : View(context) {
        var phase = DictationPhase.Idle
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val accent = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.colorAccent, it, true)
        }.data
        override fun onDraw(canvas: Canvas) {
            val seconds = (android.os.SystemClock.uptimeMillis() % 10_000L) / 1000f
            val unit = width / 48f
            canvas.save()
            canvas.scale(unit, unit)
            paint.color = accent
            paint.style = Paint.Style.FILL
            paint.alpha = when (phase) {
                DictationPhase.Idle -> if (isPressed) 255 else 225
                DictationPhase.Recording -> (215 + 40 * sin(seconds * 5)).toInt()
                DictationPhase.Transcribing -> 240
            }
            canvas.drawRoundRect(1f, 1f, 47f, 47f, 12f, 12f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.alpha = 255
            if (phase == DictationPhase.Recording) {
                canvas.drawRoundRect(17f, 17f, 31f, 31f, 3f, 3f, paint)
            } else {
                // Symmetric microphone geometry stays centered without bitmap padding.
                canvas.drawRoundRect(20f, 12f, 28f, 27f, 4f, 4f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(16f, 18f, 32f, 32f, 0f, 180f, false, paint)
                canvas.drawLine(24f, 32f, 24f, 36f, paint)
                canvas.drawLine(20f, 36f, 28f, 36f, paint)
            }
            if (phase == DictationPhase.Transcribing) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawArc(5f, 5f, 43f, 43f, seconds * 270 % 360, 250f, false, paint)
            }
            canvas.restore()
            if (phase != DictationPhase.Idle) postInvalidateOnAnimation()
        }
        override fun performClick(): Boolean { super.performClick(); onTap(); return true }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    dragging = false; isPressed = true; invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        params.x = (startX + dx.toInt()).coerceIn(leftX, rightX)
                        params.y = (startY + dy.toInt()).coerceIn(minY, maxY)
                        update()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false; invalidate()
                    if (dragging) {
                        dockRight = params.x > (leftX + rightX) / 2
                        yFraction = if (fullMaxY > minY) (params.y - minY).toFloat() / (fullMaxY - minY) else 0.5f
                        settings.saveDictationBubblePosition(dockRight, yFraction)
                        position(); update()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                    dragging = false
                }
            }
            return true
        }
    }.apply { contentDescription = "Dictate"; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }

    fun setKeyboardTop(top: Int?) { keyboardTop = top }

    private fun position() {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            leftX = insets.left; rightX = (metrics.bounds.width() - insets.right - size).coerceAtLeast(leftX)
            minY = insets.top; maxY = (metrics.bounds.height() - insets.bottom - size).coerceAtLeast(minY)
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") manager.defaultDisplay.getRealMetrics(metrics)
            @Suppress("DEPRECATION")
            val insets = button.rootWindowInsets
            fun systemDimension(name: String): Int {
                val id = button.resources.getIdentifier(name, "dimen", "android")
                return if (id != 0) button.resources.getDimensionPixelSize(id) else 0
            }
            @Suppress("DEPRECATION")
            val left = insets?.stableInsetLeft ?: 0
            @Suppress("DEPRECATION")
            val right = insets?.stableInsetRight ?: if (metrics.widthPixels > metrics.heightPixels) systemDimension("navigation_bar_width") else 0
            @Suppress("DEPRECATION")
            val bottom = insets?.stableInsetBottom ?: systemDimension("navigation_bar_height")
            @Suppress("DEPRECATION")
            val top = insets?.stableInsetTop ?: systemDimension("status_bar_height")
            leftX = left; rightX = (metrics.widthPixels - right - size).coerceAtLeast(leftX)
            minY = top; maxY = (metrics.heightPixels - size - bottom).coerceAtLeast(minY)
        }
        leftX += edgeGap
        rightX = (rightX - edgeGap).coerceAtLeast(leftX)
        minY += edgeGap
        fullMaxY = (maxY - edgeGap).coerceAtLeast(minY)
        maxY = keyboardTop?.let { (it - size - edgeGap).coerceIn(minY, fullMaxY) } ?: fullMaxY
        params.x = if (dockRight) rightX else leftX
        // Keyboard avoidance is temporary; restore the saved height when it closes.
        params.y = (minY + ((fullMaxY - minY) * yFraction).toInt()).coerceIn(minY, maxY)
    }
    fun show(phase: DictationPhase) {
        button.phase = phase
        button.contentDescription = when (phase) {
            DictationPhase.Idle -> "Dictate"
            DictationPhase.Recording -> "Stop dictation"
            DictationPhase.Transcribing -> "Transcribing"
        }
        button.invalidate()
        if (!dragging) position()
        try {
            if (!attached) { manager.addView(button, params); attached = true }
            else if (!dragging) manager.updateViewLayout(button, params)
        } catch (_: Exception) { hide(); onFailure() }
    }
    private fun update() {
        try { if (attached) manager.updateViewLayout(button, params) }
        catch (_: Exception) { hide(); onFailure() }
    }
    fun hide() {
        if (attached) runCatching { manager.removeViewImmediate(button) }
        attached = false
        dragging = false
    }
}
