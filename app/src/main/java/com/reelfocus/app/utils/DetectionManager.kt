package com.reelfocus.app.utils

import android.content.Context
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult

/**
 * Simple detection manager - just uses UsageStats
 */
class DetectionManager(private val context: Context) {
    
    private val appMonitor = AppUsageMonitor(context)
    
    /**
     * Get the currently active monitored app
     */
    suspend fun getActiveReelApp(monitoredPackages: List<String>): DetectionResult? {
        val foregroundApp = appMonitor.getActiveMonitoredApp(monitoredPackages)
        
        return if (foregroundApp != null) {
            DetectionResult(
                packageName = foregroundApp,
                appName = getAppName(foregroundApp),
                isReelDetected = true,
                detectionMethod = DetectionMethod.USAGE_STATS_FALLBACK,
                confidence = 1.0f,
                timestamp = System.currentTimeMillis()
            )
        } else {
            null
        }
    }
    
    /**
     * Get app name from package
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            else -> packageName.substringAfterLast(".")
        }
    }
}
