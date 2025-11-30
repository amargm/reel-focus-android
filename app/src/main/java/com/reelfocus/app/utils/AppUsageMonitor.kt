package com.reelfocus.app.utils

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

// M-01: App Monitor - Detects foreground app using Usage Stats API with fallbacks
class AppUsageMonitor(private val context: Context) {
    
    private val usageStatsManager: UsageStatsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    } else {
        null
    }
    
    private val activityManager: ActivityManager = 
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Cache for reducing repeated queries
    private var lastForegroundApp: String? = null
    private var lastQueryTime: Long = 0
    private val cacheValidityMs = 800L // Cache valid for 800ms - longer to reduce query frequency
    
    companion object {
        private const val TAG = "AppUsageMonitor"
        private const val QUERY_INTERVAL_MS = 10000L // Query last 10 seconds (increased to reduce permission cycling)
    }
    
    /**
     * Get the currently active monitored app from the provided list
     * Returns the monitored app if it's currently in foreground, null otherwise
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
     * Uses caching and multiple detection methods for reliability
     * Returns null if unable to determine or no permission
     */
    fun getForegroundApp(): String? {
        val currentTime = System.currentTimeMillis()
        
        // Return cached result if still valid
        if (currentTime - lastQueryTime < cacheValidityMs && lastForegroundApp != null) {
            return lastForegroundApp
        }
        
        // Try primary method: UsageStats API (most reliable on modern Android)
        val foregroundApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            getForegroundAppViaUsageStats()
        } else {
            // Fallback for older Android versions
            getForegroundAppViaActivityManager()
        }
        
        // Update cache
        if (foregroundApp != null) {
            lastForegroundApp = foregroundApp
            lastQueryTime = currentTime
        }
        
        return foregroundApp
    }
    
    /**
     * Primary detection method using UsageStats API (Android 5.1+)
     * Most reliable but requires PACKAGE_USAGE_STATS permission
     */
    private fun getForegroundAppViaUsageStats(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return null
        }
        
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - QUERY_INTERVAL_MS
            
            val usageStatsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                beginTime,
                endTime
            )
            
            if (usageStatsList.isNullOrEmpty()) {
                Log.w(TAG, "UsageStats returned empty - permission may be denied")
                return null
            }
            
            // Find the most recently used app within the query window
            var recentApp: UsageStats? = null
            var mostRecentTime = 0L
            
            usageStatsList.forEach { usageStats ->
                // Consider both lastTimeUsed and lastTimeVisible (Android 9+)
                val lastTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    maxOf(usageStats.lastTimeUsed, usageStats.lastTimeVisible)
                } else {
                    usageStats.lastTimeUsed
                }
                
                if (lastTime > mostRecentTime) {
                    mostRecentTime = lastTime
                    recentApp = usageStats
                }
            }
            
            val packageName = recentApp?.packageName
            
            // CRITICAL: Check if the detected app is actually CURRENTLY in foreground
            // lastTimeUsed can be up to several seconds old when app goes to background
            val timeSinceLastUse = endTime - mostRecentTime
            
            // Only consider it "foreground" if it was used within last 2 seconds
            // This prevents false positives when app just went to background
            if (timeSinceLastUse > 2000) {
                Log.d(TAG, "App $packageName was used ${timeSinceLastUse}ms ago - not currently in foreground")
                return null
            }
            
            if (packageName != null) {
                Log.d(TAG, "Detected foreground app: $packageName (${timeSinceLastUse}ms ago)")
            }
            
            return packageName
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - missing PACKAGE_USAGE_STATS permission", e)
            return null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid arguments for UsageStats query", e)
            return null
        } catch (e: RuntimeException) {
            Log.e(TAG, "Runtime error querying UsageStats", e)
            return null
        }
    }
    
    /**
     * Fallback detection method using ActivityManager (deprecated but works on old devices)
     * Less reliable on modern Android due to privacy restrictions
     */
    @Suppress("DEPRECATION")
    private fun getForegroundAppViaActivityManager(): String? {
        try {
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                val topActivity = runningTasks[0].topActivity
                val packageName = topActivity?.packageName
                if (packageName != null) {
                    Log.d(TAG, "Detected foreground app via ActivityManager: $packageName")
                }
                return packageName
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception querying ActivityManager", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Runtime error querying ActivityManager", e)
        }
        return null
    }
    
    /**
     * Check if a specific app is currently in the foreground
     */
    fun isAppInForeground(packageName: String): Boolean {
        val foregroundApp = getForegroundApp()
        return foregroundApp == packageName
    }
    
    /**
     * Clear the cache to force fresh detection on next query
     */
    fun clearCache() {
        lastForegroundApp = null
        lastQueryTime = 0
    }
    
    /**
     * Check if UsageStats permission is granted using AppOpsManager
     * This is the proper way to check the permission on Android 5.0+
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
            
            val hasPermission = mode == AppOpsManager.MODE_ALLOWED
            
            if (!hasPermission) {
                Log.w(TAG, "UsageStats permission not granted (mode=$mode)")
            } else {
                Log.d(TAG, "UsageStats permission verified (mode=MODE_ALLOWED)")
            }
            
            return hasPermission
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking UsageStats permission", e)
            return false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid arguments for permission check", e)
            return false
        }
    }
}
