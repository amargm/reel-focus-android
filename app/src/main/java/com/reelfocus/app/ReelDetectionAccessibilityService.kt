package com.reelfocus.app

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult

/**
 * AccessibilityService for detecting when users are actually watching reels
 * vs browsing other sections of the app (profile, settings, etc.)
 * 
 * Uses UI pattern recognition to identify reel-watching state:
 * - Full-screen video views
 * - Vertical scroll containers
 * - 9:16 aspect ratio (portrait video)
 */
class ReelDetectionAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "ReelDetectionService"
        
        // Shared state for detection results
        @Volatile
        private var latestDetection: DetectionResult? = null
        
        /**
         * Get the latest detection result (called by DetectionManager)
         */
        @JvmStatic
        fun getLatestDetection(): DetectionResult? = latestDetection
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only process window state changes and content changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }
        
        val packageName = event.packageName?.toString() ?: return
        
        // Only monitor specific apps (Instagram, TikTok, YouTube)
        if (!isMonitoredApp(packageName)) {
            return
        }
        
        // Analyze the UI hierarchy to detect reel-watching state
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val isReelDetected = analyzeUIForReelPatterns(rootNode, packageName)
                val confidence = if (isReelDetected) 0.9f else 0.3f
                
                latestDetection = DetectionResult(
                    packageName = packageName,
                    appName = getAppName(packageName),
                    isReelDetected = isReelDetected,
                    detectionMethod = DetectionMethod.ACCESSIBILITY_SERVICE,
                    confidence = confidence,
                    timestamp = System.currentTimeMillis()
                )
                
                Log.d(TAG, "Detection: $packageName - Reel: $isReelDetected (confidence: $confidence)")
                
                rootNode.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing UI: ${e.message}")
        }
    }
    
    /**
     * Analyze the UI hierarchy for reel-watching patterns
     * Phase 1: Basic implementation returning true (assume reel watching)
     * Phase 2: Will add actual pattern recognition logic
     */
    private fun analyzeUIForReelPatterns(rootNode: AccessibilityNodeInfo, packageName: String): Boolean {
        // Phase 1: Simplified detection - assume true if monitored app is active
        // This makes the service testable before implementing complex pattern matching
        
        // TODO Phase 2: Implement pattern recognition
        // - Check for full-screen video views (height > 80% of screen)
        // - Look for vertical scroll containers (RecyclerView/ViewPager)
        // - Verify 9:16 aspect ratio
        // - Detect app-specific UI signatures (reels_viewer, shorts, etc.)
        
        return true  // Placeholder for Phase 1
    }
    
    /**
     * Check if package is one of the monitored apps
     */
    private fun isMonitoredApp(packageName: String): Boolean {
        return packageName in listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube"
        )
    }
    
    /**
     * Get human-readable app name
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.instagram.android" -> "Instagram"
            "com.zhiliaoapp.musically" -> "TikTok"
            "com.google.android.youtube" -> "YouTube"
            else -> packageName
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected - Reel detection active")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        latestDetection = null
        Log.d(TAG, "Service destroyed")
    }
}
