package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Lightweight custom View for the locked floating circular Trigger overlay.
 *
 * Uses direct Canvas drawing (no Compose overhead inside WindowManager) for optimal RAM/CPU
 * efficiency on entry-level Android 11 hardware such as Nokia C30.
 *
 * Behavior:
 * - Light white semi-transparent appearance while idle.
 * - Immediately glows neon green (#00E676) on ACTION_DOWN while held.
 * - Immediately returns to white on ACTION_UP, ACTION_CANCEL, detachment, or forced stop.
 * - Bounded strictly to this circular window; outside areas pass touches directly to the game.
 */
@SuppressLint("ViewConstructor")
class TriggerCircleView(
    context: Context,
    private val onHoldStart: () -> Unit,
    private val onHoldStop: () -> Unit
) : View(context) {

    private var isTriggerHeld = false

    private val idleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(46, 255, 255, 255)
    }

    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(82, 0, 230, 118)
    }

    private val idleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
        color = Color.argb(205, 255, 255, 255)
    }

    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.8f)
        color = Color.rgb(0, 230, 118)
    }

    private val idleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(215, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val activeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(105, 240, 174)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(175, 255, 255, 255)
    }

    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 230, 118)
    }

    init {
        isClickable = false
        isFocusable = false
        contentDescription = "GLOW FIRST Floating Trigger"
    }

    fun syncHoldVisual(active: Boolean) {
        if (!active && isTriggerHeld) {
            setHeldState(false)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val borderPaint = if (isTriggerHeld) activeBorderPaint else idleBorderPaint
        val fillPaint = if (isTriggerHeld) activeFillPaint else idleFillPaint
        val dotPaint = if (isTriggerHeld) activeDotPaint else centerDotPaint
        val textPaint = if (isTriggerHeld) activeTextPaint else idleTextPaint
        val radius = (min(width, height) / 2f) - borderPaint.strokeWidth

        if (radius <= 0f) return

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        textPaint.textSize = (radius * 0.30f).coerceIn(spToPx(8f), spToPx(12f))
        val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawCircle(cx, cy - radius * 0.25f, dpToPx(2.8f), dotPaint)
        canvas.drawText("TRIGGER", cx, cy + radius * 0.25f - textOffset, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val cx = width / 2f
                val cy = height / 2f
                val radius = min(width, height) / 2f
                val dist = hypot((event.x - cx).toDouble(), (event.y - cy).toDouble()).toFloat()
                if (dist <= radius) {
                    setHeldState(true)
                    onHoldStart()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                return isTriggerHeld
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isTriggerHeld) {
                    setHeldState(false)
                    onHoldStop()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE && isTriggerHeld) {
            setHeldState(false)
            onHoldStop()
        }
    }

    override fun onDetachedFromWindow() {
        if (isTriggerHeld) {
            setHeldState(false)
            onHoldStop()
        }
        super.onDetachedFromWindow()
    }

    private fun setHeldState(held: Boolean) {
        if (isTriggerHeld != held) {
            isTriggerHeld = held
            invalidate()
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density

    @Suppress("DEPRECATION")
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
