package com.echoflow.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.echoflow.R
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
    private val size = (48 * context.resources.displayMetrics.density).toInt()
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
    private val circle = object : View(context) {
        var phase = DictationPhase.Idle
        private val logo = ContextCompat.getDrawable(context, R.drawable.logo)!!
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val circlePath = Path()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            circlePath.reset()
            circlePath.addCircle(w / 2f, h / 2f, w / 2f, Path.Direction.CW)
        }
        private val accent = android.util.TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.colorAccent, it, true)
        }.data
        override fun onDraw(canvas: Canvas) {
            val radius = width / 2f
            canvas.save()
            canvas.clipPath(circlePath)
            val seconds = (android.os.SystemClock.uptimeMillis() % 10_000L) / 1000f
            paint.color = accent
            paint.style = Paint.Style.FILL
            paint.alpha = when (phase) {
                DictationPhase.Idle -> if (isPressed) 210 else 110
                DictationPhase.Recording -> (190 + 60 * sin(seconds * 5)).toInt()
                DictationPhase.Transcribing -> 230
            }
            canvas.drawCircle(radius, radius, radius, paint)
            logo.alpha = if (phase == DictationPhase.Idle && !isPressed) 110 else 255
            val inset = (width * 0.23f).toInt()
            logo.setBounds(inset, inset, width - inset, height - inset)
            logo.draw(canvas)
            if (phase == DictationPhase.Transcribing) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = width * 0.055f
                paint.color = context.getColor(android.R.color.white)
                paint.alpha = 255
                canvas.drawArc(4f, 4f, width - 4f, height - 4f, seconds * 270 % 360, 250f, false, paint)
            }
            canvas.restore()
            if (phase != DictationPhase.Idle) postInvalidateOnAnimation()
        }
        override fun performClick(): Boolean { super.performClick(); onTap(); return true }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (hypot(event.x - width / 2f, event.y - height / 2f) > width / 2f) return false
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
                        yFraction = if (maxY > minY) (params.y - minY).toFloat() / (maxY - minY) else 0.5f
                        settings.saveDictationBubblePosition(dockRight, yFraction)
                        position(); update()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                    dragging = false
                }
            }
            return true
        }
    }.apply { contentDescription = "Dictate"; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }

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
            val insets = circle.rootWindowInsets
            fun systemDimension(name: String): Int {
                val id = circle.resources.getIdentifier(name, "dimen", "android")
                return if (id != 0) circle.resources.getDimensionPixelSize(id) else 0
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
        params.x = if (dockRight) rightX else leftX
        params.y = minY + ((maxY - minY) * yFraction).toInt()
    }
    fun show(phase: DictationPhase) {
        circle.phase = phase
        circle.contentDescription = when (phase) {
            DictationPhase.Idle -> "Dictate"
            DictationPhase.Recording -> "Stop dictation"
            DictationPhase.Transcribing -> "Transcribing"
        }
        circle.invalidate()
        if (!dragging) position()
        try {
            if (!attached) { manager.addView(circle, params); attached = true }
            else if (!dragging) manager.updateViewLayout(circle, params)
        } catch (_: Exception) { hide(); onFailure() }
    }
    private fun update() {
        try { if (attached) manager.updateViewLayout(circle, params) }
        catch (_: Exception) { hide(); onFailure() }
    }
    fun hide() {
        if (attached) runCatching { manager.removeViewImmediate(circle) }
        attached = false
        dragging = false
    }
}
