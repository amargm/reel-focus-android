package com.reelfocus.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.reelfocus.app.models.BootAwareTime
import com.reelfocus.app.utils.PreferencesHelper
import java.util.Locale

/**
 * Blocking surface shown whenever a monitored app is reopened during an
 * active break. The break itself remains persisted by OverlayService.
 */
class BreakActivity : AppCompatActivity() {
    private lateinit var preferences: PreferencesHelper
    private lateinit var countdown: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refreshCountdown()
            if (!isFinishing) handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_break)
        applyLegacySystemInsets()
        com.reelfocus.app.ui.CalmMotion.revealScreen(findViewById(android.R.id.content))
        com.reelfocus.app.ui.CalmMotion.floatOnce(findViewById(R.id.break_orb))

        preferences = PreferencesHelper(this)
        countdown = findViewById(R.id.break_countdown)

        onBackPressedDispatcher.addCallback(this) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Your break is still in progress",
                Snackbar.LENGTH_SHORT
            ).show()
        }

        findViewById<Button>(R.id.break_home_button).setOnClickListener {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        refreshCountdown()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    private fun refreshCountdown() {
        val config = preferences.loadConfig()
        val state = preferences.loadSessionState(config)
        if (!state.isOnBreak) {
            finish()
            return
        }

        val remainingMs = BootAwareTime.remainingDurationMs(
            deadlineWallTimeMs = state.breakEndWallTime,
            nowWallTimeMs = System.currentTimeMillis(),
            originalDurationMs = 10 * 60 * 1_000L,
            deadlineElapsedRealtimeMs = state.breakEndElapsedRealtime,
            deadlineBootCount = state.breakBootCount,
            nowElapsedRealtimeMs = android.os.SystemClock.elapsedRealtime(),
            currentBootCount = Settings.Global.getInt(
                contentResolver,
                Settings.Global.BOOT_COUNT,
                0
            )
        )

        if (remainingMs <= 0L) {
            state.isOnBreak = false
            state.breakStartTime = 0L
            state.breakEndWallTime = 0L
            state.breakEndElapsedRealtime = 0L
            state.breakBootCount = -1
            preferences.saveSessionState(state)
            finish()
            return
        }

        val totalSeconds = (remainingMs + 999L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        countdown.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
