package com.reelfocus.app.utils

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

class AppUsageMonitor(private val context: Context) {
    
    private val usageStatsManager: UsageStatsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    } else {
        null
    }
    
    companion object {
        private const val TAG = "AppUsageMonitor"
        private const val INITIAL_LOOKBACK_MS = 5 * 60 * 1000L
        private const val EVENT_OVERLAP_MS = 2_000L
    }

    // Keep the last known foreground package and only query events since the
    // previous poll. Re-reading a five-minute event window every second wastes
    // CPU and battery on long-running sessions.
    private var lastEventQueryTime = 0L
    private var cachedForegroundPackage: String? = null
    
    /**
     * Get the currently active monitored app from the provided list
     */
    fun getActiveMonitoredApp(monitoredPackages: List<String>): String? {
        val foregroundApp = getForegroundPackage()
        return if (foregroundApp != null && monitoredPackages.contains(foregroundApp)) {
            foregroundApp
        } else {
            null
        }
    }
    
    /** Current foreground package, cached from incremental UsageEvents. */
    fun getForegroundPackage(): String? = getForegroundApp()

    /**
     * Get the currently foreground app package name.
     * BUG-009 FIX: use UsageEvents (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND) instead of
     * querying lastTimeUsed from UsageStats, which reflects when an app was last
     * backgrounded — not whether it is currently in the foreground.
     */
    private fun getForegroundApp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return null

        try {
            val endTime = System.currentTimeMillis()
            val beginTime = if (lastEventQueryTime == 0L) {
                (endTime - INITIAL_LOOKBACK_MS).coerceAtLeast(0L)
            } else {
                (lastEventQueryTime - EVENT_OVERLAP_MS).coerceAtLeast(0L)
            }

            val usageEvents = usageStatsManager?.queryEvents(beginTime, endTime)
                ?: return cachedForegroundPackage

            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val packageName = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        cachedForegroundPackage = packageName
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (cachedForegroundPackage == packageName) {
                            cachedForegroundPackage = null
                        }
                    }
                    // Android 10+ uses activity lifecycle event types on many devices.
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        cachedForegroundPackage = packageName
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (cachedForegroundPackage == packageName) {
                            cachedForegroundPackage = null
                        }
                    }
                }
            }
            lastEventQueryTime = endTime
            return cachedForegroundPackage
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            return cachedForegroundPackage
        }
    }

    /**
     * Check if UsageStats permission is granted
     */
    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false
        }
        
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            
            return mode == AppOpsManager.MODE_ALLOWED
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
            return false
        }
    }
}
