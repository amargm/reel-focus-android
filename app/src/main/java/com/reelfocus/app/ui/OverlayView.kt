package com.reelfocus.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.reelfocus.app.models.TextSize

class OverlayView(context: Context, private val textSizeConfig: TextSize = TextSize.MEDIUM) : LinearLayout(context) {

    private val sessionLabel: TextView
    private val timerLabel: TextView
    
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        
        // Create session label with configurable size
        sessionLabel = TextView(context).apply {
            textSize = 10f * textSizeConfig.scaleFactor
            setTextColor(Color.parseColor("#E6E1E5"))
            alpha = 0.8f
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }
        
        // Create timer label with configurable size
        timerLabel = TextView(context).apply {
            textSize = 18f * textSizeConfig.scaleFactor
            setTextColor(Color.parseColor("#E6E1E5"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }
        
        addView(sessionLabel)
        addView(timerLabel)
        
        updateBackground(false)
    }
    
    fun updateState(
        secondsElapsed: Int,
        limitValue: Int,
        currentSession: Int,
        maxSessions: Int
    ) {
        // Update session label
        sessionLabel.text = "SESSION $currentSession/$maxSessions"

        // Calculate remaining time and warning state
        val totalSecondsLimit = limitValue * 60
        val remaining = maxOf(0, totalSecondsLimit - secondsElapsed)
        val mins = remaining / 60
        val secs = remaining % 60
        val displayText = String.format("%d:%02d", mins, secs)
        val isWarning = remaining < 60

        timerLabel.text = displayText
        updateBackground(isWarning)
    }

    /** Show a break countdown — "BREAK" label + remaining M:SS in blue tones. */
    fun updateBreakState(remainingSeconds: Int) {
        sessionLabel.text = "BREAK"
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        timerLabel.text = String.format("%d:%02d", mins, secs)
        updateBreakBackground()
    }

    private fun updateBreakBackground() {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            setColor(Color.parseColor("#80003060"))   // Deep blue, 50% opacity
            setStroke(dpToPx(1), Color.parseColor("#7DD4F8"))
        }
        background = drawable
        elevation = dpToPx(8).toFloat()
        sessionLabel.setTextColor(Color.parseColor("#A8D8F8"))
        timerLabel.setTextColor(Color.parseColor("#E8F4FD"))
    }
    
    private fun updateBackground(isWarning: Boolean) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(16).toFloat()
            
            if (isWarning) {
                // Warning colors
                setColor(Color.parseColor("#E68C1D18")) // Red with 90% opacity
                setStroke(dpToPx(2), Color.parseColor("#F2B8B5"))
            } else {
                // Normal colors
                setColor(Color.parseColor("#801D1B20")) // Dark with 50% opacity
                setStroke(dpToPx(1), Color.parseColor("#49454F"))
            }
        }
        
        background = drawable
        elevation = dpToPx(8).toFloat()
        
        // Update text colors
        val textColor = if (isWarning) {
            Color.parseColor("#F9DEDC")
        } else {
            Color.parseColor("#E6E1E5")
        }
        
        sessionLabel.setTextColor(textColor)
        timerLabel.setTextColor(textColor)
    }
    
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
