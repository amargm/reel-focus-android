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
import com.reelfocus.app.utils.PreferencesHelper
import com.reelfocus.app.utils.HistoryManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class OverlayService : LifecycleService() {

    private var overlayView: com.reelfocus.app.ui.OverlayView? = null
    private var windowManager: WindowManager? = null
    private lateinit var sessionState: SessionState
    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var appMonitor: AppUsageMonitor
    private lateinit var historyManager: HistoryManager
    private var monitorJob: Job? = null
    private var isOverlayVisible = false
    private var limitReached = false
    private var lastActiveApp: String? = null
    private var inactiveCounter = 0
    private var lastTimerUpdateTime = 0L  // Debounce counter

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
            
            while (true) {
                delay(1000) // Check every second
                
                val activeApp = appMonitor.getActiveMonitoredApp(monitoredPackages)
                
                if (activeApp != null) {
                    // Monitored app is in foreground
                    inactiveCounter = 0  // Reset counter
                    lastActiveApp = activeApp
                    handleMonitoredAppActive(activeApp, config)
                } else {
                    // No monitored app detected
                    inactiveCounter++
                    
                    // Smart debouncing strategy:
                    // - If session is active and recently had activity: Use 5 second grace period
                    //   (handles brief detection gaps during video watching)
                    // - If overlay visible but no recent activity: Hide immediately
                    //   (user clearly switched apps)
                    
                    if (sessionState.isActive && lastActiveApp != null && inactiveCounter < 5) {
                        // Grace period: Continue session for video watching scenarios
                        handleMonitoredAppActive(lastActiveApp!!, config)
                    } else {
                        // User has left the app - hide overlay and pause session
                        handleMonitoredAppInactive(config)
                    }
                }
            }
        }
    }
    
    private fun handleMonitoredAppActive(packageName: String, config: com.reelfocus.app.models.AppConfig) {
        val currentTime = System.currentTimeMillis()
        
        // M-05: Check if daily limit already reached BEFORE processing
        if (sessionState.currentSession > sessionState.maxSessions) {
            showDailyBlockScreen(packageName, config)
            return
        }
        
        // Check if this is a completely new session (first time or after reset)
        if (sessionState.sessionStartTime == 0L) {
            // First session of the day
            startNewSession(packageName, config)
            lastTimerUpdateTime = currentTime
        } else if (!sessionState.isActive) {
            // Session was paused - check if gap exceeded
            val gapMinutes = (currentTime - sessionState.lastActivityTime) / (60 * 1000)
            if (gapMinutes >= config.sessionResetGapMinutes) {
                // Gap exceeded - start completely new session
                // If previous session was completed, increment counter
                if (sessionState.sessionCompleted) {
                    sessionState.currentSession++
                    sessionState.sessionCompleted = false
                }
                startNewSession(packageName, config)
                lastTimerUpdateTime = currentTime
            } else {
                // Resume current session (keep timer continuing)
                sessionState.isActive = true
                sessionState.activeAppPackage = packageName
                lastTimerUpdateTime = currentTime
            }
        } else if (sessionState.activeAppPackage != packageName) {
            // Switching between monitored apps - just update package, keep timer running
            sessionState.activeAppPackage = packageName
        }
        
        // Only increment once per second (prevents double counting)
        if (sessionState.isActive && (currentTime - lastTimerUpdateTime) >= 1000) {
            sessionState.secondsElapsed++
            sessionState.lastActivityTime = currentTime
            lastTimerUpdateTime = currentTime
            
            // Save state every 5 seconds to prevent data loss
            if (sessionState.secondsElapsed % 5 == 0) {
                prefsHelper.saveSessionState(sessionState)
            }
        }
        
        // Show overlay only if not already visible
        if (!isOverlayVisible) {
            showOverlay(config)
        } else {
            // Just update the existing overlay
            updateOverlay()
        }
        
        // M-04: Check if limit reached
        checkLimitReached(config)
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
            
            // Mark session as completed but don't increment counter yet
            // Counter increments when starting next session after gap
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

        overlayView = com.reelfocus.app.ui.OverlayView(this, config.overlayTextSize).apply {
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
            .setSmallIcon(R.drawable.ic_notification)
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
