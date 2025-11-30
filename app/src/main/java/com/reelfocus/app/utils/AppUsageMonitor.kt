package com.reelfocus.app.utils

import android.app.AppOpsManager
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
     * Get the currently foreground app package name
     */
    private fun getForegroundApp(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 60000 // Look back 60 seconds
            
            val usageStatsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                beginTime,
                endTime
            )
            
            if (usageStatsList.isNullOrEmpty()) {
                return null
            }
            
            // Find the most recently used app
            var mostRecentTime = 0L
            var foregroundPackage: String? = null
            
            for (usageStats in usageStatsList) {
                if (usageStats.lastTimeUsed > mostRecentTime) {
                    mostRecentTime = usageStats.lastTimeUsed
                    foregroundPackage = usageStats.packageName
                }
            }
            
            return foregroundPackage
            
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
