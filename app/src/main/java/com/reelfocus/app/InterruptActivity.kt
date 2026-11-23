package com.reelfocus.app

import androidx.activity.enableEdgeToEdge

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.addCallback
import com.reelfocus.app.models.SessionHistory
import com.reelfocus.app.models.SessionState
import com.reelfocus.app.utils.HistoryManager
import com.reelfocus.app.utils.PreferencesHelper
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID

// M-04: Limit Interrupter - UX-001, UX-002, UX-003
class InterruptActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
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
        enableEdgeToEdge()
        setContentView(R.layout.activity_interrupt)
        applyLegacySystemInsets()
        com.reelfocus.app.ui.CalmMotion.revealScreen(findViewById(android.R.id.content))
        com.reelfocus.app.ui.CalmMotion.floatOnce(findViewById(R.id.interrupt_orb))

        // BUG-015 FIX: use OnBackPressedDispatcher instead of deprecated onBackPressed()
        onBackPressedDispatcher.addCallback(this) {
            Snackbar.make(findViewById(android.R.id.content), "Please make a choice to continue", Snackbar.LENGTH_SHORT).show()
        }

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val limitValue = intent.getIntExtra(EXTRA_LIMIT_VALUE, 20)
        val currentSession = intent.getIntExtra(EXTRA_CURRENT_SESSION, 1)
        val maxSessions = intent.getIntExtra(EXTRA_MAX_SESSIONS, 5)
        val dailyLimitReached = intent.getBooleanExtra(EXTRA_DAILY_LIMIT_REACHED, false)
        val isExtensionCompleted = intent.getBooleanExtra(EXTRA_IS_EXTENSION_COMPLETED, false)

        val titleText = findViewById<TextView>(R.id.interrupt_title)
        val messageText = findViewById<TextView>(R.id.interrupt_message)
        val sessionInfo = findViewById<TextView>(R.id.session_info)
        val stopButton = findViewById<Button>(R.id.stop_button)
        val extendButton = findViewById<Button>(R.id.extend_button)
        val breakButton = findViewById<Button>(R.id.break_button)
        val hintText = findViewById<TextView>(R.id.hint_text)
        val preferences = PreferencesHelper(this)
        val gapMinutes = preferences.loadConfig().sessionResetGapMinutes
        val dailyExtensionCount = preferences.getDailyExtensionCount()

        val limitDescription = "$limitValue minutes"
        
        when {
            dailyLimitReached -> {
                // All sessions used for today
                titleText.text = "A pause for today"
                messageText.text = "You've reached the sessions you set for today.\n\nLet the rest of the day open up for something else."
                sessionInfo.text = "All sessions completed"
                hintText.text = "Sessions reset at midnight"

                stopButton.text = "Close for today"
                stopButton.setOnClickListener {
                    stopAndGoHome()
                }

                extendButton.isEnabled = false
                extendButton.alpha = 0.5f
                extendButton.text = "Resets at midnight"
                breakButton.visibility = android.view.View.GONE
            }
            
            isExtensionCompleted -> {
                // Extension completed - offer next session or break
                titleText.text = "Your extra time is complete"
                messageText.text = "Your extra minutes are over.\n\nYou can begin another session or take a little time away."
                sessionInfo.text = "Session $currentSession of $maxSessions · Extensions used today: $dailyExtensionCount"
                hintText.text = "Begin another session, take a break, or use another 5 minutes if today's allowance remains."

                stopButton.text = "Begin next session"
                stopButton.setOnClickListener {
                    startNextSession()
                }

                if (dailyExtensionCount >= com.reelfocus.app.models.DailyExtensionPolicy.MAX_PER_LOCAL_DAY) {
                    extendButton.isEnabled = false
                    extendButton.alpha = 0.5f
                    extendButton.text = "Daily extensions used"
                } else {
                    extendButton.text = "5 more minutes"
                    extendButton.setOnClickListener {
                        showExtensionFrictionDialog(extendButton)
                    }
                }
                breakButton.visibility = android.view.View.VISIBLE
                breakButton.text = "Take a 10-minute break"
            }
            
            else -> {
                // Main session completed - offer next session or extension
                titleText.text = "A moment to pause"
                messageText.text = "You've reached the time you set for $appName.\n\nTake a breath, then choose what feels right next."
                sessionInfo.text = "Session $currentSession of $maxSessions"
                hintText.text = "Next session available after a $gapMinutes-min gap. Daily extensions used: $dailyExtensionCount of ${com.reelfocus.app.models.DailyExtensionPolicy.MAX_PER_LOCAL_DAY}."

                stopButton.text = "Begin next session"
                stopButton.setOnClickListener {
                    startNextSession()
                }

                if (dailyExtensionCount >= com.reelfocus.app.models.DailyExtensionPolicy.MAX_PER_LOCAL_DAY) {
                    extendButton.isEnabled = false
                    extendButton.alpha = 0.5f
                    extendButton.text = "Daily extensions used"
                } else {
                    extendButton.text = "5 more minutes"
                    extendButton.setOnClickListener {
                        showExtensionFrictionDialog(extendButton)
                    }
                }
            }
        }
        breakButton.setOnClickListener { takeBreak() }
    }
    
    private fun showExtensionFrictionDialog(button: Button) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Take 5 more minutes?")
            .setMessage("Wait five seconds, then confirm. You can use at most two extensions each local day.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Wait 5s", null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            var secondsRemaining = 5
            val handler = Handler(Looper.getMainLooper())
            val countdown = object : Runnable {
                override fun run() {
                    if (!dialog.isShowing) return
                    if (secondsRemaining > 0) {
                        positive.text = "Wait ${secondsRemaining}s"
                        secondsRemaining--
                        handler.postDelayed(this, 1_000L)
                    } else {
                        positive.text = "Use 5 minutes"
                        positive.isEnabled = true
                    }
                }
            }
            handler.post(countdown)
            dialog.setOnDismissListener { handler.removeCallbacks(countdown) }
            positive.setOnClickListener {
                if (!positive.isEnabled) return@setOnClickListener
                val reservationToken = UUID.randomUUID().toString()
                if (PreferencesHelper(this).reserveDailyExtension(reservationToken)) {
                    dialog.dismiss()
                    extendSession(reservationToken)
                } else {
                    dialog.dismiss()
                    button.isEnabled = false
                    button.alpha = 0.5f
                    button.text = "Daily extensions used"
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "You've used both extensions for today",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
        dialog.show()
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
    
    private fun extendSession(reservationToken: String) {
        val extendIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_EXTEND
            putExtra(OverlayService.EXTRA_EXTENSION_RESERVATION_TOKEN, reservationToken)
        }
        try {
            startService(extendIntent)
            setResult(RESULT_EXTEND)
            finish()
        } catch (error: RuntimeException) {
            PreferencesHelper(this).refundDailyExtension(reservationToken)
            Snackbar.make(
                findViewById(android.R.id.content),
                "Couldn't start the extension. Your daily allowance was restored.",
                Snackbar.LENGTH_LONG
            ).show()
        }
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
        @Suppress("DEPRECATION")
        super.onBackPressed() // consumed by dispatcher callback above
    }
}
