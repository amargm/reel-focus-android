package com.reelfocus.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
        setContentView(R.layout.activity_daily_block)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "App"
        val currentSession = intent.getIntExtra(EXTRA_CURRENT_SESSION, 1)
        val maxSessions = intent.getIntExtra(EXTRA_MAX_SESSIONS, 5)

        val titleText = findViewById<TextView>(R.id.block_title)
        val messageText = findViewById<TextView>(R.id.block_message)
        val sessionInfo = findViewById<TextView>(R.id.block_session_info)
        val homeButton = findViewById<Button>(R.id.home_button)
        val settingsButton = findViewById<Button>(R.id.open_settings_button)

        titleText.text = "Daily Limit Reached"
        
        messageText.text = "You've completed all $maxSessions sessions for today on $appName.\n\n" +
                "Your limit will reset at midnight.\n\n" +
                "Great job being mindful of your screen time! 🎯"
        
        sessionInfo.text = "Sessions today: $currentSession of $maxSessions"

        homeButton.setOnClickListener {
            // Return to home screen
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        settingsButton.setOnClickListener {
            // Open settings to adjust limits
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Prevent back button - user must go home
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
