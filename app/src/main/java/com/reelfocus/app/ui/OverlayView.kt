package com.reelfocus.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.reelfocus.app.models.LimitType
import com.reelfocus.app.models.TextSize

class OverlayView(context: Context, private val textSize: TextSize = TextSize.MEDIUM) : LinearLayout(context) {

    private val sessionLabel: TextView
    private val timerLabel: TextView
    
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        
        // Create session label with configurable size
        sessionLabel = TextView(context).apply {
            textSize = 10f * textSize.scaleFactor
            setTextColor(Color.parseColor("#E6E1E5"))
            alpha = 0.8f
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }
        
        // Create timer label with configurable size
        timerLabel = TextView(context).apply {
            textSize = 18f * textSize.scaleFactor
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
        limitType: LimitType,
        currentSession: Int,
        maxSessions: Int
    ) {
        // Update session label
        sessionLabel.text = "SESSION $currentSession/$maxSessions"
        
        // Calculate display value and warning state
        val isWarning: Boolean
        val displayText: String
        
        if (limitType == LimitType.TIME) {
            val totalSecondsLimit = limitValue * 60
            val remaining = maxOf(0, totalSecondsLimit - secondsElapsed)
            val mins = remaining / 60
            val secs = remaining % 60
            displayText = String.format("%d:%02d", mins, secs)
            isWarning = remaining < 60
        } else {
            // Count mode: estimate 15s per reel
            val estimatedCountUsed = secondsElapsed / 15
            val remaining = maxOf(0, limitValue - estimatedCountUsed)
            displayText = "$remaining Left"
            isWarning = remaining < 3
        }
        
        timerLabel.text = displayText
        updateBackground(isWarning)
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
