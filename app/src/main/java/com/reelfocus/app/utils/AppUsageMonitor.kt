package com.reelfocus.app.utils

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

// M-01: App Monitor - Detects foreground app using Usage Stats API
class AppUsageMonitor(private val context: Context) {
    
    private val usageStatsManager: UsageStatsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    } else {
        null
    }
    
    /**
     * Get the currently foreground app package name
     * Returns null if unable to determine or no permission
     */
    fun getForegroundApp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 1000 * 2 // Last 2 seconds
            
            val usageStatsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                beginTime,
                endTime
            ) ?: return null
            
            // Find the most recently used app
            var recentApp: UsageStats? = null
            usageStatsList.forEach { usageStats ->
                if (recentApp == null || usageStats.lastTimeUsed > recentApp!!.lastTimeUsed) {
                    recentApp = usageStats
                }
            }
            
            return recentApp?.packageName
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Check if a specific app is currently in the foreground
     */
    fun isAppInForeground(packageName: String): Boolean {
        val foregroundApp = getForegroundApp()
        return foregroundApp == packageName
    }
    
    /**
     * Check if any of the monitored apps is in foreground
     * Returns the package name if found, null otherwise
     */
    fun getActiveMonitoredApp(monitoredPackages: List<String>): String? {
        val foregroundApp = getForegroundApp() ?: return null
        return if (monitoredPackages.contains(foregroundApp)) {
            foregroundApp
        } else {
            null
        }
    }
}
