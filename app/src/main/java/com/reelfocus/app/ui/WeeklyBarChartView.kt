package com.reelfocus.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/**
 * A lightweight Canvas-drawn 7-day bar chart.
 * No external charting library required.
 * Each bar represents total usage minutes for one day.
 * Colour: primary teal (#00BFA5), track: dark surface (#2D333B).
 */
class WeeklyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class BarEntry(val label: String, val minutes: Int)

    private var entries: List<BarEntry> = emptyList()

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00BFA5")
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2D333B")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#8B949E")
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#E6EDF3")
        isFakeBoldText = true
    }

    fun setData(data: List<BarEntry>) {
        entries = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val sp = resources.displayMetrics.scaledDensity

        labelPaint.textSize = 10f * sp
        valuePaint.textSize = 9f * sp

        val labelHeight = 20f    // space reserved at the bottom for day labels
        val topPad = 20f          // space reserved at the top for minute labels
        val chartH = h - labelHeight - topPad
        val n = entries.size
        if (n == 0) return

        val slotW = w / n
        val barW = slotW * 0.55f

        val maxMinutes = entries.maxOf { it.minutes }.coerceAtLeast(1)

        entries.forEachIndexed { i, entry ->
            val cx = slotW * i + slotW / 2f
            val barH = (entry.minutes.toFloat() / maxMinutes) * chartH
            val left = cx - barW / 2f
            val right = cx + barW / 2f
            val bottom = topPad + chartH
            val top = bottom - barH

            // Full-height track
            canvas.drawRoundRect(RectF(left, topPad, right, bottom), 6f, 6f, trackPaint)

            // Value bar (skipped when 0 to keep track-only appearance)
            if (barH >= 1f) {
                canvas.drawRoundRect(RectF(left, top, right, bottom), 6f, 6f, barPaint)
            }

            // Minute label above bar
            if (entry.minutes > 0) {
                canvas.drawText("${entry.minutes}m", cx, top - 5f, valuePaint)
            }

            // Day label below chart area
            canvas.drawText(entry.label, cx, h - 3f, labelPaint)
        }
    }
}
