package com.reelfocus.app.utils

import android.app.ActivityManager
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
        private const val QUERY_INTERVAL_MS = 3000L // Query last 3 seconds for better detection
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
            if (packageName != null) {
                Log.d(TAG, "Detected foreground app via UsageStats: $packageName")
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
     * Check if any of the monitored apps is in foreground
     * Returns the package name if found, null otherwise
     */
    fun getActiveMonitoredApp(monitoredPackages: List<String>): String? {
        if (monitoredPackages.isEmpty()) return null
        
        val foregroundApp = getForegroundApp() ?: return null
        
        return if (monitoredPackages.contains(foregroundApp)) {
            foregroundApp
        } else {
            null
        }
    }
    
    /**
     * Clear the cache to force fresh detection on next query
     */
    fun clearCache() {
        lastForegroundApp = null
        lastQueryTime = 0
    }
    
    /**
     * Check if UsageStats permission is granted
     * Helps with debugging permission issues
     */
    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return false
        }
        
        try {
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 60000 // Check last 60 seconds instead of 1 second
            
            val usageStatsList = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST, // Use INTERVAL_BEST instead of INTERVAL_DAILY
                beginTime,
                endTime
            )
            
            // Permission is granted if we can query and get a non-null list
            // Even empty list means permission is granted (no apps used in time window)
            val hasPermission = usageStatsList != null
            
            if (!hasPermission) {
                Log.w(TAG, "UsageStats permission check failed - permission likely not granted")
            } else {
                Log.d(TAG, "UsageStats permission verified - found ${usageStatsList?.size ?: 0} entries")
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
