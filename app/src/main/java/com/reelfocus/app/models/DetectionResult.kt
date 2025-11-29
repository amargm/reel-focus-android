package com.reelfocus.app.models

/**
 * Result from the detection system indicating which app is active
 * and whether the user is actually watching reels (vs browsing other sections)
 */
data class DetectionResult(
    val packageName: String,
    val appName: String,
    val isReelDetected: Boolean,
    val detectionMethod: DetectionMethod,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DetectionMethod {
    ACCESSIBILITY_SERVICE,  // Pattern recognition via AccessibilityService
    USAGE_STATS_FALLBACK,   // App-level detection via UsageStatsManager
    UNKNOWN
}
