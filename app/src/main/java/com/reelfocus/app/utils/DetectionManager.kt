package com.reelfocus.app.utils

import android.content.Context
import android.provider.Settings
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult
import kotlinx.coroutines.*

/**
 * Central orchestrator for all detection methods.
 * Manages tiered detection: AccessibilityService (preferred) → UsageStats (fallback)
 */
class DetectionManager(private val context: Context) {
    
    private val appMonitor = AppUsageMonitor(context)
    private var lastAccessibilityResult: DetectionResult? = null
    private var lastAccessibilityCheck: Long = 0
    
    // Configuration
    private val accessibilityTimeoutMs = 3000L  // Wait 3s for accessibility before fallback
    private val accessibilityCheckIntervalMs = 1000L  // Check accessibility result every 1s
    
    /**
     * Get the currently active reel app from monitored packages.
     * Uses tiered detection with automatic fallback.
     * 
     * @param monitoredPackages List of package names to monitor
     * @return DetectionResult if reel is detected, null otherwise
     */
    suspend fun getActiveReelApp(monitoredPackages: List<String>): DetectionResult? {
        return withContext(Dispatchers.IO) {
            // Check if AccessibilityService is enabled
            if (isAccessibilityServiceEnabled()) {
                // Try Tier 1: AccessibilityService with timeout
                val accessibilityResult = tryAccessibilityDetection(monitoredPackages)
                if (accessibilityResult != null) {
                    return@withContext accessibilityResult
                }
            }
            
            // Tier 2: UsageStats fallback
            return@withContext tryUsageStatsDetection(monitoredPackages)
        }
    }
    
    /**
     * Tier 1: Try AccessibilityService pattern recognition
     * Returns result from ReelDetectionAccessibilityService if available
     */
    private suspend fun tryAccessibilityDetection(monitoredPackages: List<String>): DetectionResult? {
        // Get the latest result from AccessibilityService
        val currentTime = System.currentTimeMillis()
        val result = com.reelfocus.app.ReelDetectionAccessibilityService.getLatestDetection()
        
        // Check if result is recent and matches monitored packages
        if (result != null && 
            (currentTime - result.timestamp) < accessibilityCheckIntervalMs &&
            monitoredPackages.contains(result.packageName) &&
            result.confidence >= 0.7f) {
            return result
        }
        
        return null
    }
    
    /**
     * Tier 2: UsageStats-based detection (fallback)
     * Assumes if app is in foreground, user is watching reels
     */
    private fun tryUsageStatsDetection(monitoredPackages: List<String>): DetectionResult? {
        val foregroundApp = appMonitor.getActiveMonitoredApp(monitoredPackages)
        
        return if (foregroundApp != null) {
            DetectionResult(
                packageName = foregroundApp,
                appName = getAppName(foregroundApp),
                isReelDetected = true,  // Assume true in fallback mode
                detectionMethod = DetectionMethod.USAGE_STATS_FALLBACK,
                confidence = 0.5f,  // Lower confidence for assumption-based detection
                timestamp = System.currentTimeMillis()
            )
        } else {
            null
        }
    }
    
    /**
     * Check if our AccessibilityService is enabled
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val expectedServiceName = "${context.packageName}/com.reelfocus.app.ReelDetectionAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return enabledServices.contains(expectedServiceName)
    }
    
    /**
     * Get human-readable app name from package name
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            else -> packageName.substringAfterLast(".")
        }
    }
    
    /**
     * Invalidate cached detection results
     */
    fun invalidateCache() {
        lastAccessibilityResult = null
        lastAccessibilityCheck = 0
    }
}
