package com.reelfocus.app

import androidx.activity.enableEdgeToEdge

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

// M-05: Daily Blocking Manager - Shows when max daily sessions exceeded
class DailyBlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_CURRENT_SESSION = "current_session"
        const val EXTRA_MAX_SESSIONS = "max_sessions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_daily_block)
        applyLegacySystemInsets()
        com.reelfocus.app.ui.CalmMotion.revealScreen(findViewById(android.R.id.content))
        com.reelfocus.app.ui.CalmMotion.floatOnce(findViewById(R.id.block_orb))

        // BUG-015 FIX: use OnBackPressedDispatcher instead of deprecated onBackPressed()
        onBackPressedDispatcher.addCallback(this) {
            startActivity(Intent(this@DailyBlockActivity, WellnessActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val currentSession = intent.getIntExtra(EXTRA_CURRENT_SESSION, 1)
        val maxSessions = intent.getIntExtra(EXTRA_MAX_SESSIONS, 5)

        val titleText = findViewById<TextView>(R.id.block_title)
        val messageText = findViewById<TextView>(R.id.block_message)
        val sessionInfo = findViewById<TextView>(R.id.block_session_info)
        val homeButton = findViewById<Button>(R.id.home_button)
        val settingsButton = findViewById<Button>(R.id.open_settings_button)

        titleText.text = "Your focus time is complete"
        
        messageText.text = "You've reached the sessions you planned for $appName today.\n\n" +
                "Your allowance gently resets at midnight. Take the rest of today at your own pace."
        
        sessionInfo.text = "Sessions today: $currentSession of $maxSessions"

        homeButton.setOnClickListener {
            startActivity(Intent(this, WellnessActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }

        settingsButton.setOnClickListener {
            // Return to the current app navigation; settings now lives inside Today.
            val settingsIntent = Intent(this, WellnessActivity::class.java)
            startActivity(settingsIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        @Suppress("DEPRECATION")
        super.onBackPressed() // handled by dispatcher callback above
    }
}
