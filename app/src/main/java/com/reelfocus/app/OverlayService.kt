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
import com.reelfocus.app.utils.AppUsageMonitor
import com.reelfocus.app.utils.PreferencesHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : LifecycleService() {

    private var overlayView: com.reelfocus.app.ui.OverlayView? = null
    private var windowManager: WindowManager? = null
    private lateinit var sessionState: SessionState
    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var appMonitor: AppUsageMonitor
    private var monitorJob: Job? = null
    private var isOverlayVisible = false

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
        prefsHelper = PreferencesHelper(this)
        appMonitor = AppUsageMonitor(this)
        
        // Check if new day and reset session
        prefsHelper.checkAndResetIfNewDay()
        
        // Load configuration and session state
        val config = prefsHelper.loadConfig()
        sessionState = prefsHelper.loadSessionState(config)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startMonitoring()
            }
            ACTION_STOP -> {
                stopMonitoring()
                hideOverlay()
                stopSelf()
            }
        }

        return START_STICKY
    }
    
    // M-01 & M-02: Monitor foreground app and track session
    private fun startMonitoring() {
        monitorJob = lifecycleScope.launch {
            val config = prefsHelper.loadConfig()
            val monitoredPackages = config.monitoredApps
                .filter { it.isEnabled }
                .map { it.packageName }
            
            while (true) {
                delay(1000) // Check every second
                
                val activeApp = appMonitor.getActiveMonitoredApp(monitoredPackages)
                
                if (activeApp != null) {
                    // Monitored app is in foreground
                    handleMonitoredAppActive(activeApp, config)
                } else {
                    // No monitored app in foreground
                    handleMonitoredAppInactive(config)
                }
            }
        }
    }
    
    private fun handleMonitoredAppActive(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        val currentTime = System.currentTimeMillis()
        
        // Check if this is a new session (different app or gap exceeded)
        if (sessionState.activeAppPackage != packageName) {
            // Different app - start new session for this app
            startNewSession(packageName, config)
        } else if (!sessionState.isActive) {
            // Same app but session was paused
            val gapMinutes = (currentTime - sessionState.lastActivityTime) / (60 * 1000)
            if (gapMinutes >= config.sessionResetGapMinutes) {
                // Gap exceeded - start new session
                sessionState.currentSession++
                prefsHelper.saveSessionState(sessionState)
                startNewSession(packageName, config)
            } else {
                // Resume current session
                sessionState.isActive = true
            }
        }
        
        // Update session time
        sessionState.secondsElapsed++
        sessionState.lastActivityTime = currentTime
        
        // Show overlay if not visible
        if (!isOverlayVisible) {
            showOverlay(config)
        }
        
        // Update overlay
        updateOverlay()
    }
    
    private fun handleMonitoredAppInactive(config: com.reelfocus.app.models.AppConfig) {
        if (sessionState.isActive) {
            // User left the monitored app
            sessionState.isActive = false
            sessionState.lastActivityTime = System.currentTimeMillis()
            prefsHelper.saveSessionState(sessionState)
        }
        
        // Hide overlay when no monitored app is active
        if (isOverlayVisible) {
            hideOverlay()
        }
    }
    
    private fun startNewSession(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        // Get app-specific limits or use defaults
        val monitoredApp = config.monitoredApps.find { it.packageName == packageName }
        
        sessionState.apply {
            activeAppPackage = packageName
            isActive = true
            secondsElapsed = 0
            sessionStartTime = System.currentTimeMillis()
            lastActivityTime = System.currentTimeMillis()
            limitType = monitoredApp?.customLimitType ?: config.defaultLimitType
            limitValue = monitoredApp?.customLimitValue ?: config.defaultLimitValue
            maxSessions = config.maxSessionsDaily
            extensionCount = 0
        }
        
        prefsHelper.saveSessionState(sessionState)
    }
    
    private fun stopMonitoring() {
        monitorJob?.cancel()
        sessionState.isActive = false
        prefsHelper.saveSessionState(sessionState)
    }

    private fun showOverlay(config: com.reelfocus.app.models.AppConfig) {
        if (isOverlayVisible) return

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

        // Position overlay from config
        layoutParams.gravity = when (config.overlayPosition) {
            OverlayPosition.TOP_LEFT -> android.view.Gravity.TOP or android.view.Gravity.START
            OverlayPosition.TOP_RIGHT -> android.view.Gravity.TOP or android.view.Gravity.END
            OverlayPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            OverlayPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
        }

        layoutParams.x = 40
        layoutParams.y = 120

        overlayView = com.reelfocus.app.ui.OverlayView(this).apply {
            updateState(
                sessionState.secondsElapsed,
                sessionState.limitValue,
                sessionState.limitType,
                sessionState.currentSession,
                sessionState.maxSessions
            )
        }

        windowManager?.addView(overlayView, layoutParams)
        isOverlayVisible = true
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return
        
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        isOverlayVisible = false
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
        stopMonitoring()
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
