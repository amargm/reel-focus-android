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
import com.reelfocus.app.models.SessionHistory
import com.reelfocus.app.utils.AppUsageMonitor
import com.reelfocus.app.utils.DetectionManager
import com.reelfocus.app.utils.PreferencesHelper
import com.reelfocus.app.utils.HistoryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SESSION & TIMER MANAGEMENT LOGIC
 * =================================
 * 
 * TIMER BEHAVIOR (Simple):
 * - Monitored app in foreground → Timer runs (secondsElapsed++)
 * - App in background → Timer pauses
 * 
 * SESSION CONCEPT:
 * - A "session" = one period of usage from start until limit reached
 * - Timer (secondsElapsed) is CUMULATIVE across all monitored apps
 * - When timer hits limit → Session completes → Show interrupt screen
 * 
 * GLOBAL vs PER-APP:
 * - Sessions (currentSession/maxSessions): GLOBAL across all apps
 *   Example: If maxSessions=5, you get 5 sessions total for the day, 
 *   regardless of which apps you use
 * 
 * - Timer Limit (limitValue): PER-APP (can override global default)
 *   Example: Instagram=20min, YouTube=30min
 *   When you switch apps, limit updates but timer keeps counting
 * 
 * SESSION COMPLETION FLOW:
 * 1. User watches Instagram for 20min → Timer reaches 20min limit
 * 2. limitReached = true → Show interrupt screen
 * 3. sessionCompleted = true (but currentSession stays same)
 * 4. User clicks "Stop" → Service stops, timer pauses
 * 5. User returns after 30min gap → New session starts
 * 6. currentSession++ (now on session 2)
 * 7. Timer resets to 0, starts counting again
 * 
 * APP SWITCHING BEHAVIOR:
 * - Scenario: Instagram (20min limit), then switch to YouTube (30min limit)
 * - Timer continues: If you had 15min on Instagram, YouTube starts at 15min
 * - Limit updates: Now checking against 30min limit for YouTube
 * - limitReached resets: Fresh check for YouTube's limit
 * 
 * EXAMPLE DAY (maxSessions=3, Instagram=20min, YouTube=30min):
 * Session 1: Instagram 20min → Limit reached → Stop
 * [30min break]
 * Session 2: Instagram 10min, YouTube 20min (total 30min) → Limit reached → Stop
 * [40min break]
 * Session 3: YouTube 30min → Limit reached → Stop
 * [Try again] → Daily limit reached, blocked
 */
class OverlayService : LifecycleService() {

    private var overlayView: com.reelfocus.app.ui.OverlayView? = null
    private var windowManager: WindowManager? = null
    private lateinit var sessionState: SessionState
    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var appMonitor: AppUsageMonitor
    private lateinit var detectionManager: DetectionManager
    private lateinit var historyManager: HistoryManager
    private var monitorJob: Job? = null
    private var isOverlayVisible = false
    private var limitReached = false
    private var lastTimerUpdateTime = 0L  // Prevent double-counting per second
    private var currentMonitoredApp: String? = null  // Track current app to prevent flickering

    companion object {
        const val ACTION_START = "com.reelfocus.app.ACTION_START"
        const val ACTION_STOP = "com.reelfocus.app.ACTION_STOP"
        const val ACTION_EXTEND = "com.reelfocus.app.ACTION_EXTEND"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "reel_focus_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefsHelper = PreferencesHelper(this)
        appMonitor = AppUsageMonitor(this)
        detectionManager = DetectionManager(this)
        historyManager = HistoryManager(this)
        
        // Check if new day and reset session
        prefsHelper.checkAndResetIfNewDay()
        
        // Load configuration and session state
        val config = prefsHelper.loadConfig()
        sessionState = prefsHelper.loadSessionState(config)
        
        // Initialize timer to prevent immediate increment on restart
        if (sessionState.isActive) {
            lastTimerUpdateTime = System.currentTimeMillis()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startMonitoring()
            }
            ACTION_EXTEND -> {
                // UX-003: Extend by 5 minutes
                handleExtend()
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
            
            if (monitoredPackages.isEmpty()) {
                android.util.Log.e("OverlayService", "No monitored apps configured")
                stopSelf()
                return@launch
            }
            
            while (true) {
                try {
                    delay(1000) // Check every second
                    
                    val detectionResult = detectionManager.getActiveReelApp(monitoredPackages)
                    val activeApp = detectionResult?.packageName
                    
                    // Only update if app state changed to prevent flickering
                    if (activeApp != currentMonitoredApp) {
                        currentMonitoredApp = activeApp
                        
                        if (activeApp != null) {
                            handleMonitoredAppActive(activeApp, config)
                        } else {
                            handleMonitoredAppInactive(config)
                        }
                    } else if (activeApp != null) {
                        // Same app still active - just update timer
                        updateTimerOnly(config)
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("OverlayService", "Error in monitoring loop", e)
                }
            }
        }
    }
    
    private fun updateTimerOnly(config: com.reelfocus.app.models.AppConfig) {
        if (!sessionState.isActive) return
        
        val currentTime = System.currentTimeMillis()
        
        // Increment timer (once per second)
        if ((currentTime - lastTimerUpdateTime) >= 1000) {
            sessionState.secondsElapsed++
            sessionState.lastActivityTime = currentTime
            lastTimerUpdateTime = currentTime
            
            // Auto-save every 5 seconds
            if (sessionState.secondsElapsed % 5 == 0) {
                prefsHelper.saveSessionState(sessionState)
            }
            
            // Update overlay display
            updateOverlay()
            
            // Check if limit reached
            checkLimitReached(config)
        }
    }
    
    private fun handleMonitoredAppActive(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        val currentTime = System.currentTimeMillis()
        
        // Check if daily session limit already reached
        if (sessionState.currentSession > sessionState.maxSessions) {
            showDailyBlockScreen(packageName, config)
            return
        }
        
        // Initialize session on first run
        if (sessionState.sessionStartTime == 0L) {
            startNewSession(packageName, config)
            lastTimerUpdateTime = currentTime
        }
        
        // Resume session if paused
        if (!sessionState.isActive) {
            val gapMinutes = (currentTime - sessionState.lastActivityTime) / (60 * 1000)
            if (gapMinutes >= config.sessionResetGapMinutes) {
                if (sessionState.sessionCompleted) {
                    sessionState.currentSession++
                    sessionState.sessionCompleted = false
                }
                startNewSession(packageName, config)
            } else {
                sessionState.isActive = true
                sessionState.activeAppPackage = packageName
            }
            lastTimerUpdateTime = currentTime
        }
        
        // Update limit when switching apps
        if (sessionState.activeAppPackage != packageName) {
            sessionState.activeAppPackage = packageName
            val monitoredApp = config.monitoredApps.find { it.packageName == packageName }
            val newLimitValue = monitoredApp?.customLimitValue ?: config.defaultLimitValue
            sessionState.limitValue = newLimitValue
            sessionState.limitType = config.defaultLimitType
            limitReached = false
        }
        
        // Show overlay once when app becomes active
        if (!isOverlayVisible) {
            showOverlay(config)
        }
    }
    
    private fun checkLimitReached(config: com.reelfocus.app.models.AppConfig) {
        if (limitReached) return // Already showing interrupt screen
        
        val limitExceeded = if (sessionState.limitType == LimitType.TIME) {
            // Time-based limit (in seconds)
            val totalSecondsLimit = sessionState.limitValue * 60
            android.util.Log.d("ReelFocus", "Time check: ${sessionState.secondsElapsed}s / ${totalSecondsLimit}s")
            sessionState.secondsElapsed >= totalSecondsLimit
        } else {
            // Count-based limit (estimate: 15 seconds per reel)
            val estimatedReelsViewed = sessionState.secondsElapsed / 15
            android.util.Log.d("ReelFocus", "Count check: $estimatedReelsViewed / ${sessionState.limitValue}")
            estimatedReelsViewed >= sessionState.limitValue
        }
        
        if (limitExceeded) {
            android.util.Log.d("ReelFocus", "LIMIT REACHED! Session ${sessionState.currentSession} complete")
            limitReached = true
            
            // Mark session as completed
            // Session counter will increment when user returns after gap period
            sessionState.sessionCompleted = true
            prefsHelper.saveSessionState(sessionState)
            
            // Record completed session to history
            recordSessionToHistory(completed = true)
            
            showInterruptScreen(config)
        }
    }
    
    private fun showInterruptScreen(config: com.reelfocus.app.models.AppConfig) {
        // Get app name
        val appName = config.monitoredApps
            .find { it.packageName == sessionState.activeAppPackage }
            ?.appName ?: "App"
        
        // Check if daily limit reached (M-05)
        val dailyLimitReached = sessionState.currentSession >= sessionState.maxSessions
        
        // Launch InterruptActivity
        val intent = Intent(this, InterruptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(InterruptActivity.EXTRA_APP_NAME, appName)
            putExtra(InterruptActivity.EXTRA_LIMIT_TYPE, sessionState.limitType)
            putExtra(InterruptActivity.EXTRA_LIMIT_VALUE, sessionState.limitValue)
            putExtra(InterruptActivity.EXTRA_CURRENT_SESSION, sessionState.currentSession)
            putExtra(InterruptActivity.EXTRA_MAX_SESSIONS, sessionState.maxSessions)
            putExtra(InterruptActivity.EXTRA_DAILY_LIMIT_REACHED, dailyLimitReached)
        }
        startActivity(intent)
        
        // Hide overlay and pause monitoring while interrupt screen is shown
        hideOverlay()
        sessionState.isActive = false
        prefsHelper.saveSessionState(sessionState)
    }
    
    private fun showDailyBlockScreen(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        // Get app name
        val appName = config.monitoredApps
            .find { it.packageName == packageName }
            ?.appName ?: "App"
        
        // Launch DailyBlockActivity
        val intent = Intent(this, DailyBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(DailyBlockActivity.EXTRA_APP_NAME, appName)
            putExtra(DailyBlockActivity.EXTRA_CURRENT_SESSION, sessionState.currentSession)
            putExtra(DailyBlockActivity.EXTRA_MAX_SESSIONS, sessionState.maxSessions)
        }
        startActivity(intent)
        
        // Hide overlay
        hideOverlay()
        sessionState.isActive = false
        prefsHelper.saveSessionState(sessionState)
    }
    
    private fun handleExtend() {
        // UX-003: Add 5 minutes to the limit
        sessionState.limitValue += 5
        sessionState.extensionCount++
        sessionState.sessionCompleted = false  // Extending means session not yet complete
        limitReached = false
        sessionState.isActive = true
        lastTimerUpdateTime = System.currentTimeMillis()
        prefsHelper.saveSessionState(sessionState)
        
        // Show overlay again with updated limit
        val config = prefsHelper.loadConfig()
        showOverlay(config)
    }
    
    private fun handleMonitoredAppInactive(config: com.reelfocus.app.models.AppConfig) {
        // App not in foreground → PAUSE timer
        if (sessionState.isActive) {
            sessionState.isActive = false
            sessionState.lastActivityTime = System.currentTimeMillis()
            prefsHelper.saveSessionState(sessionState)
        }
        
        // Hide overlay
        if (isOverlayVisible) {
            hideOverlay()
        }
    }
    
    private fun startNewSession(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        // Get app-specific timer limit (if set) or use global default
        val monitoredApp = config.monitoredApps.find { it.packageName == packageName }
        
        sessionState.apply {
            activeAppPackage = packageName
            isActive = true
            secondsElapsed = 0
            sessionStartTime = System.currentTimeMillis()
            lastActivityTime = System.currentTimeMillis()
            limitType = config.defaultLimitType  // ALWAYS use global limit type
            limitValue = monitoredApp?.customLimitValue ?: config.defaultLimitValue  // Can override per-app
            maxSessions = config.maxSessionsDaily  // ALWAYS use global max sessions
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
        android.util.Log.d("OverlayService", "showOverlay called - isOverlayVisible=$isOverlayVisible")
        
        if (isOverlayVisible) {
            android.util.Log.d("OverlayService", "Overlay already visible, skipping")
            return
        }
        
        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.e("OverlayService", "Cannot draw overlays - permission not granted!")
                return
            }
        }
        android.util.Log.d("OverlayService", "Overlay permission granted, creating overlay view")

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
            OverlayPosition.CENTER_RIGHT -> android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
            OverlayPosition.BOTTOM_LEFT -> android.view.Gravity.BOTTOM or android.view.Gravity.START
            OverlayPosition.BOTTOM_RIGHT -> android.view.Gravity.BOTTOM or android.view.Gravity.END
        }

        layoutParams.x = 40
        layoutParams.y = 120

        overlayView = com.reelfocus.app.ui.OverlayView(this, config.overlayTextSize).apply {
            updateState(
                sessionState.secondsElapsed,
                sessionState.limitValue,
                sessionState.limitType,
                sessionState.currentSession,
                sessionState.maxSessions
            )
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
            isOverlayVisible = true
            android.util.Log.d("OverlayService", "Overlay view added successfully to WindowManager")
        } catch (e: android.view.WindowManager.BadTokenException) {
            android.util.Log.e("OverlayService", "Invalid window token - overlay permission may be revoked", e)
            isOverlayVisible = false
        } catch (e: IllegalStateException) {
            android.util.Log.e("OverlayService", "Illegal state when adding overlay view", e)
            isOverlayVisible = false
        } catch (e: SecurityException) {
            android.util.Log.e("OverlayService", "Security exception - missing overlay permission", e)
            isOverlayVisible = false
        }
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return
        
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "Error removing overlay view", e)
            }
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun showNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)  // Use different ID than foreground
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
    
    private fun recordSessionToHistory(completed: Boolean) {
        // Only record if session has meaningful duration (at least 10 seconds)
        if (sessionState.secondsElapsed < 10 || sessionState.sessionStartTime == 0L) return
        
        val config = prefsHelper.loadConfig()
        val monitoredApp = config.monitoredApps.find { it.packageName == sessionState.activeAppPackage }
        
        val session = SessionHistory(
            id = UUID.randomUUID().toString(),
            appName = monitoredApp?.appName ?: "Unknown App",
            appPackage = sessionState.activeAppPackage ?: "",
            startTime = sessionState.sessionStartTime,
            endTime = System.currentTimeMillis(),
            durationSeconds = sessionState.secondsElapsed,
            limitType = sessionState.limitType,
            limitValue = sessionState.limitValue,
            extensionsUsed = sessionState.extensionCount,
            completed = completed,
            date = historyManager.getTodayDate()
        )
        
        historyManager.recordSession(session)
    }

    override fun onDestroy() {
        // Record session if service is destroyed while active
        if (sessionState.isActive && sessionState.secondsElapsed >= 10) {
            recordSessionToHistory(completed = false)
        }
        stopMonitoring()
        hideOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
