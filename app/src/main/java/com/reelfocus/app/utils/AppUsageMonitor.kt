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
    }
    
    /**
     * Get the currently active monitored app from the provided list
     */
    fun getActiveMonitoredApp(monitoredPackages: List<String>): String? {
        val foregroundApp = getForegroundApp()
        return if (foregroundApp != null && monitoredPackages.contains(foregroundApp)) {
            foregroundApp
        } else {
            null
        }
    }
    
    /**
     * Get the currently foreground app package name.
     * BUG-009 FIX: use UsageEvents (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND) instead of
     * querying lastTimeUsed from UsageStats, which reflects when an app was last
     * backgrounded — not whether it is currently in the foreground.
     */
    private fun getForegroundApp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 300_000L // events window: last 5 minutes
                                                // SHORT WINDOWS (e.g. 10s) caused the overlay to
                                                // disappear — if the user stayed in the app longer
                                                // than the window, no MOVE_TO_FOREGROUND was found.

            val usageEvents = usageStatsManager?.queryEvents(beginTime, endTime)
                ?: return null

            // Walk events forward; track the last app that moved to foreground
            // and hasn't yet moved to background.
            var lastForegroundPackage: String? = null
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastForegroundPackage = event.packageName
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (event.packageName == lastForegroundPackage) {
                            lastForegroundPackage = null
                        }
                    }
                }
            }
            return lastForegroundPackage

        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            return null
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
