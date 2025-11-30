package com.reelfocus.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.reelfocus.app.models.LimitType
import com.reelfocus.app.models.SessionHistory
import com.reelfocus.app.models.SessionState
import com.reelfocus.app.utils.HistoryManager
import com.reelfocus.app.utils.PreferencesHelper
import java.util.UUID

// M-04: Limit Interrupter - UX-001, UX-002, UX-003
class InterruptActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_LIMIT_TYPE = "limit_type"
        const val EXTRA_LIMIT_VALUE = "limit_value"
        const val EXTRA_CURRENT_SESSION = "current_session"
        const val EXTRA_MAX_SESSIONS = "max_sessions"
        const val EXTRA_DAILY_LIMIT_REACHED = "daily_limit_reached"
        const val EXTRA_IS_EXTENSION_COMPLETED = "is_extension_completed"
        const val EXTRA_EXTENSION_USED = "extension_used"
        
        const val RESULT_STOP = 1
        const val RESULT_EXTEND = 2
        const val RESULT_NEXT_SESSION = 3
        const val RESULT_TAKE_BREAK = 4
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
        val isExtensionCompleted = intent.getBooleanExtra(EXTRA_IS_EXTENSION_COMPLETED, false)
        val extensionUsed = intent.getBooleanExtra(EXTRA_EXTENSION_USED, false)

        val titleText = findViewById<TextView>(R.id.interrupt_title)
        val messageText = findViewById<TextView>(R.id.interrupt_message)
        val sessionInfo = findViewById<TextView>(R.id.session_info)
        val stopButton = findViewById<Button>(R.id.stop_button)
        val extendButton = findViewById<Button>(R.id.extend_button)
        
        val limitDescription = if (limitType == LimitType.TIME) {
            "$limitValue minutes"
        } else {
            "$limitValue reels"
        }
        
        when {
            dailyLimitReached -> {
                // All sessions used for today
                titleText.text = "Daily Limit Reached"
                messageText.text = "You've completed all $maxSessions sessions for today.\n\n" +
                        "Great job being mindful of your screen time!"
                sessionInfo.text = "All sessions completed"
                
                stopButton.text = "Done"
                stopButton.setOnClickListener {
                    stopAndGoHome()
                }
                
                extendButton.isEnabled = false
                extendButton.alpha = 0.5f
                extendButton.text = "No more sessions"
            }
            
            isExtensionCompleted -> {
                // Extension completed - offer next session or break
                titleText.text = "Extension Complete"
                messageText.text = "You've used your extension time.\n\n" +
                        "Start the next session or take a 10-minute break."
                sessionInfo.text = "Session $currentSession of $maxSessions - Extension used"
                
                stopButton.text = "Start Next Session"
                stopButton.setOnClickListener {
                    startNextSession()
                }
                
                extendButton.text = "Take 10-Min Break"
                extendButton.setOnClickListener {
                    takeBreak()
                }
            }
            
            else -> {
                // Main session completed - offer next session or extension
                titleText.text = "Session Limit Reached"
                messageText.text = "You've reached your $limitDescription limit for $appName.\n\n" +
                        "Start the next session or extend this one by 5 minutes."
                sessionInfo.text = "Session $currentSession of $maxSessions"
                
                stopButton.text = "Start Next Session"
                stopButton.setOnClickListener {
                    startNextSession()
                }
                
                if (extensionUsed) {
                    extendButton.isEnabled = false
                    extendButton.alpha = 0.5f
                    extendButton.text = "Extension Used"
                } else {
                    extendButton.text = "Extend by 5 Min"
                    extendButton.setOnClickListener {
                        extendSession()
                    }
                }
            }
        }
    }
    
    private fun stopAndGoHome() {
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(stopIntent)
        
        setResult(RESULT_STOP)
        finish()
        
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }
    
    private fun startNextSession() {
        val nextSessionIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_NEXT_SESSION
        }
        startService(nextSessionIntent)
        
        setResult(RESULT_NEXT_SESSION)
        finish()
    }
    
    private fun extendSession() {
        val extendIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_EXTEND
        }
        startService(extendIntent)
        
        setResult(RESULT_EXTEND)
        finish()
    }
    
    private fun takeBreak() {
        val breakIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_TAKE_BREAK
        }
        startService(breakIntent)
        
        setResult(RESULT_TAKE_BREAK)
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button - user must make a choice
        // Do nothing
    }
}
