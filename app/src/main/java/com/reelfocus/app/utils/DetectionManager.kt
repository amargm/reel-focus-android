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
    private val FRESH_ACCESSIBILITY_RESULT_MAX_AGE_MS = 2_000L
    private val ACCESSIBILITY_RESULT_MAX_AGE_MS = 10_000L
    
    /**
     * Get the currently active monitored app.
     * BUG-008 FIX: check AccessibilityService result first; only fall back to
     * UsageStats when the service result is unavailable or too old.
     */
    suspend fun getActiveReelApp(monitoredPackages: List<String>): DetectionResult? {
        // 1. Prefer the latest accessibility classification while UsageStats
        // confirms that the same app is still in the foreground. This preserves
        // fresh negative results instead of silently turning them into whole-app
        // positives through the fallback path.
        val accessibilityResult = ReelDetectionAccessibilityService.getLatestDetection()
        if (accessibilityResult != null &&
            monitoredPackages.contains(accessibilityResult.packageName)
        ) {
            val ageMs = System.currentTimeMillis() - accessibilityResult.timestamp
            val foregroundPackage = appMonitor.getForegroundPackage()
            val isFresh = ageMs >= 0L && ageMs <= FRESH_ACCESSIBILITY_RESULT_MAX_AGE_MS
            val isRecentForConfirmedForeground = foregroundPackage == accessibilityResult.packageName &&
                ageMs >= 0L && ageMs <= ACCESSIBILITY_RESULT_MAX_AGE_MS
            if (isFresh || isRecentForConfirmedForeground) {
                return if (accessibilityResult.isReelDetected) accessibilityResult else null
            }
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
