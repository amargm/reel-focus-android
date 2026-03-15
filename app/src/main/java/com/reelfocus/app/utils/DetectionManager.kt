package com.reelfocus.app.utils

import android.content.Context
import com.reelfocus.app.ReelDetectionAccessibilityService
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult

/**
 * Detection manager - prefers AccessibilityService result (reel-specific),
 * falls back to UsageStats when the service is unavailable or result is stale.
 */
class DetectionManager(private val context: Context) {
    
    private val appMonitor = AppUsageMonitor(context)

    // Maximum age of an accessibility result before we treat it as stale
    private val ACCESSIBILITY_RESULT_MAX_AGE_MS = 2000L
    
    /**
     * Get the currently active monitored app.
     * BUG-008 FIX: check AccessibilityService result first; only fall back to
     * UsageStats when the service result is unavailable or too old.
     */
    suspend fun getActiveReelApp(monitoredPackages: List<String>): DetectionResult? {
        // 1. Try the accessibility-based reel detector first (more precise)
        val accessibilityResult = ReelDetectionAccessibilityService.getLatestDetection()
        if (accessibilityResult != null &&
            accessibilityResult.isReelDetected &&
            monitoredPackages.contains(accessibilityResult.packageName) &&
            (System.currentTimeMillis() - accessibilityResult.timestamp) <= ACCESSIBILITY_RESULT_MAX_AGE_MS
        ) {
            return accessibilityResult
        }

        // 2. Fall back to UsageStats-level detection (whole-app, not reel-specific)
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
