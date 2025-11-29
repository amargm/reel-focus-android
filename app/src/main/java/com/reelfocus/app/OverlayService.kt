package com.reelfocus.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.reelfocus.app.models.LimitType
import com.reelfocus.app.models.OverlayPosition
import com.reelfocus.app.models.SessionState
import com.reelfocus.app.models.TextSize
import com.reelfocus.app.ui.OverlayView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : LifecycleService() {

    private var overlayView: OverlayView? = null
    private var windowManager: WindowManager? = null
    private val sessionState = SessionState()
    private var timerJob: Job? = null

    companion object {
        const val ACTION_START = "com.reelfocus.app.ACTION_START"
        const val ACTION_STOP = "com.reelfocus.app.ACTION_STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "reel_focus_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                showOverlay()
                startTimer()
            }
            ACTION_STOP -> {
                stopTimer()
                hideOverlay()
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        // Position overlay (default TOP_RIGHT)
        val position = OverlayPosition.TOP_RIGHT
        layoutParams.gravity = when (position) {
            OverlayPosition.TOP_LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            OverlayPosition.TOP_RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
            OverlayPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            OverlayPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
        }

        layoutParams.x = 40
        layoutParams.y = 120

        overlayView = OverlayView(this).apply {
            updateState(
                sessionState.secondsElapsed,
                sessionState.limitValue,
                sessionState.limitType,
                sessionState.currentSession,
                sessionState.maxSessions
            )
        }

        windowManager?.addView(overlayView, layoutParams)
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private fun startTimer() {
        sessionState.isActive = true
        timerJob = lifecycleScope.launch {
            while (sessionState.isActive) {
                delay(1000)
                sessionState.secondsElapsed++
                updateOverlay()
            }
        }
    }

    private fun stopTimer() {
        sessionState.isActive = false
        timerJob?.cancel()
    }

    private fun updateOverlay() {
        overlayView?.updateState(
            sessionState.secondsElapsed,
            sessionState.limitValue,
            sessionState.limitType,
            sessionState.currentSession,
            sessionState.maxSessions
        )
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reel Focus Active")
            .setContentText("Session timer is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reel Focus Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Reel Focus overlay is active"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopTimer()
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
