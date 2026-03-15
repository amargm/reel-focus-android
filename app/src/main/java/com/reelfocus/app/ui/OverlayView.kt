package com.reelfocus.app.ui

import android.content.Context
import android.graphics.*
import android.util.TypedValue
import android.view.View
import com.reelfocus.app.models.OverlayStyle
import com.reelfocus.app.models.TextSize

/**
 * Floating overlay view that supports three visual styles:
 *  - TEXT:             Rounded-rect with session label + countdown clock
 *  - DONUT:            Circular progress ring that drains as time passes
 *  - SHRINKING_CIRCLE: Filled circle whose radius shrinks to zero
 */
class OverlayView(
    context: Context,
    private val textSizeConfig: TextSize = TextSize.MEDIUM,
    private val overlayStyle: OverlayStyle = OverlayStyle.TEXT
) : View(context) {

    // ── State ─────────────────────────────────────────────────────────────────
    private var progress    = 1.0f   // 1.0 = full time remaining, 0.0 = expired
    private var sessionText = "S 1/5"
    private var timeText    = "20:00"
    private var isBreak     = false

    // ── Dimensions ────────────────────────────────────────────────────────────
    private val vpW: Int by lazy {
        if (overlayStyle == OverlayStyle.TEXT) dpToPx(100) else dpToPx(84)
    }
    private val vpH: Int by lazy {
        if (overlayStyle == OverlayStyle.TEXT) dpToPx(56) else dpToPx(84)
    }

    // ── Paints ────────────────────────────────────────────────────────────────
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2D333B")
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ── Color helper ──────────────────────────────────────────────────────────
    private fun progressColor(): Int = when {
        isBreak          -> Color.parseColor("#00BFA5")
        progress > 0.20f -> Color.parseColor("#00BFA5")
        progress > 0.10f -> Color.parseColor("#FFB300")
        else             -> Color.parseColor("#CF6679")
    }

    // ── Measure ───────────────────────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(vpW, vpH)
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        when (overlayStyle) {
            OverlayStyle.TEXT             -> drawTextMode(canvas)
            OverlayStyle.DONUT            -> drawDonutMode(canvas)
            OverlayStyle.SHRINKING_CIRCLE -> drawShrinkingCircleMode(canvas)
        }
    }

    // ── TEXT mode ─────────────────────────────────────────────────────────────
    private fun drawTextMode(canvas: Canvas) {
        val w = vpW.toFloat()
        val h = vpH.toFloat()
        val r = dpToPx(14).toFloat()

        // Background
        if (isBreak) {
            bgPaint.color = Color.argb(180, 0, 48, 96)
        } else {
            bgPaint.color = Color.argb(200, 13, 17, 23)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, bgPaint)

        // Border
        val borderColor = progressColor()
        borderPaint.color = borderColor
        borderPaint.strokeWidth = dpToPx(if (progress < 0.10f) 2 else 1).toFloat()
        canvas.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), r - 1f, r - 1f, borderPaint)

        val sp = resources.displayMetrics.scaledDensity
        val scale = textSizeConfig.scaleFactor

        // Session / Break label
        labelPaint.textSize = 9f * scale * sp
        labelPaint.color = Color.parseColor("#8B949E")
        canvas.drawText(if (isBreak) "BREAK" else sessionText, w / 2f, h * 0.40f, labelPaint)

        // Timer
        timePaint.textSize = 17f * scale * sp
        timePaint.color = if (isBreak) Color.parseColor("#A7F3E8") else Color.parseColor("#E6EDF3")
        canvas.drawText(timeText, w / 2f, h * 0.78f, timePaint)
    }

    // ── DONUT mode ────────────────────────────────────────────────────────────
    private fun drawDonutMode(canvas: Canvas) {
        val cx = vpW / 2f
        val cy = vpH / 2f
        val strokeW = dpToPx(9).toFloat()
        val radius = minOf(vpW, vpH) / 2f - strokeW / 2f - dpToPx(4)
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Track (background ring)
        trackPaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Progress arc — clockwise from top
        arcPaint.color = progressColor()
        arcPaint.strokeWidth = strokeW
        canvas.drawArc(oval, -90f, progress * 360f, false, arcPaint)

        val sp = resources.displayMetrics.scaledDensity
        val scale = textSizeConfig.scaleFactor

        // Timer in center
        timePaint.textSize = 14f * scale * sp
        timePaint.color = Color.parseColor("#E6EDF3")
        val descent = (timePaint.descent() - timePaint.ascent()) / 2f - timePaint.descent()
        canvas.drawText(timeText, cx, cy + descent, timePaint)

        // Session / break label below timer
        labelPaint.textSize = 8f * scale * sp
        labelPaint.color = Color.parseColor("#8B949E")
        canvas.drawText(
            if (isBreak) "BREAK" else sessionText,
            cx,
            cy + descent + (timePaint.textSize * 0.9f) + dpToPx(2),
            labelPaint
        )
    }

    // ── SHRINKING_CIRCLE mode ─────────────────────────────────────────────────
    private fun drawShrinkingCircleMode(canvas: Canvas) {
        val cx = vpW / 2f
        val cy = vpH / 2f
        val maxR = minOf(vpW, vpH) / 2f - dpToPx(6).toFloat()

        // Faint outer reference ring
        trackPaint.strokeWidth = dpToPx(1).toFloat()
        canvas.drawCircle(cx, cy, maxR, trackPaint)

        // Filled shrinking circle
        val curR = maxR * progress
        if (curR > 0.5f) {
            fillPaint.color = progressColor()
            canvas.drawCircle(cx, cy, curR, fillPaint)
        }

        val sp = resources.displayMetrics.scaledDensity
        val scale = textSizeConfig.scaleFactor

        // Timer — dark text when there's enough circle to read against
        timePaint.textSize = 13f * scale * sp
        timePaint.color = if (progress > 0.28f && !isBreak)
            Color.parseColor("#0D1117") else Color.parseColor("#E6EDF3")
        val descent = (timePaint.descent() - timePaint.ascent()) / 2f - timePaint.descent()
        canvas.drawText(timeText, cx, cy + descent, timePaint)

        // Session / break label
        labelPaint.textSize = 8f * scale * sp
        labelPaint.color = if (progress > 0.35f && !isBreak)
            Color.parseColor("#1E3A33") else Color.parseColor("#8B949E")
        canvas.drawText(
            if (isBreak) "BREAK" else sessionText,
            cx,
            cy + descent + (timePaint.textSize * 0.9f) + dpToPx(2),
            labelPaint
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun updateState(secondsElapsed: Int, limitValue: Int, currentSession: Int, maxSessions: Int) {
        isBreak = false
        val totalSeconds = limitValue * 60
        val remaining = maxOf(0, totalSeconds - secondsElapsed)
        progress = if (totalSeconds > 0) remaining.toFloat() / totalSeconds else 0f
        timeText = String.format("%d:%02d", remaining / 60, remaining % 60)
        sessionText = "S $currentSession/$maxSessions"
        invalidate()
    }

    fun updateBreakState(remainingSeconds: Int) {
        isBreak = true
        progress = remainingSeconds / (10f * 60f)
        timeText = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60)
        sessionText = "BREAK"
        invalidate()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
    ).toInt()
}
