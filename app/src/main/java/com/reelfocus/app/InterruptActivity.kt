package com.reelfocus.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.reelfocus.app.models.LimitType

// M-04: Limit Interrupter - UX-001, UX-002, UX-003
class InterruptActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_LIMIT_TYPE = "limit_type"
        const val EXTRA_LIMIT_VALUE = "limit_value"
        const val EXTRA_CURRENT_SESSION = "current_session"
        const val EXTRA_MAX_SESSIONS = "max_sessions"
        const val EXTRA_DAILY_LIMIT_REACHED = "daily_limit_reached"
        
        const val RESULT_STOP = 1
        const val RESULT_EXTEND = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interrupt)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val limitType = intent.getSerializableExtra(EXTRA_LIMIT_TYPE) as? LimitType ?: LimitType.TIME
        val limitValue = intent.getIntExtra(EXTRA_LIMIT_VALUE, 20)
        val currentSession = intent.getIntExtra(EXTRA_CURRENT_SESSION, 1)
        val maxSessions = intent.getIntExtra(EXTRA_MAX_SESSIONS, 5)
        val dailyLimitReached = intent.getBooleanExtra(EXTRA_DAILY_LIMIT_REACHED, false)

        // UX-001: Clear contextual messaging
        val titleText = findViewById<TextView>(R.id.interrupt_title)
        val messageText = findViewById<TextView>(R.id.interrupt_message)
        val sessionInfo = findViewById<TextView>(R.id.session_info)
        
        titleText.text = "Session Limit Reached"
        
        val limitDescription = if (limitType == LimitType.TIME) {
            "$limitValue minutes"
        } else {
            "$limitValue reels"
        }
        
        // Supportive, non-judgmental language
        messageText.text = if (dailyLimitReached) {
            "You've completed all $maxSessions sessions for today.\n\n" +
                    "Great job being mindful of your screen time!"
        } else {
            "You've reached your $limitDescription limit for $appName.\n\n" +
                    "Take a moment to reflect on how you'd like to spend your time."
        }
        
        sessionInfo.text = "Session ${currentSession - 1} of $maxSessions today"

        // UX-002: Primary action - Stop
        val stopButton = findViewById<Button>(R.id.stop_button)
        stopButton.setOnClickListener {
            // Stop the service
            val stopIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_STOP
            }
            startService(stopIntent)
            
            setResult(RESULT_STOP)
            finish()
            
            // Return to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }

        // UX-003: Secondary action - Extend by 5 minutes (disabled if daily limit reached)
        val extendButton = findViewById<Button>(R.id.extend_button)
        if (dailyLimitReached) {
            extendButton.isEnabled = false
            extendButton.alpha = 0.5f
            extendButton.text = "Daily Limit Reached"
        } else {
            extendButton.setOnClickListener {
                // Tell service to extend
                val extendIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_EXTEND
                }
                startService(extendIntent)
                
                setResult(RESULT_EXTEND)
                finish()
            }
        }
    }

    override fun onBackPressed() {
        // Prevent back button - user must make a choice
        // Do nothing
    }
}
