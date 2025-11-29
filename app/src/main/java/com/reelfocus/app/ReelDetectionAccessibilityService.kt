package com.reelfocus.app

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult
import com.reelfocus.app.utils.ReelPatternMatcher

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
    
    private lateinit var patternMatcher: ReelPatternMatcher
    
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
                val confidence = analyzeUIForReelPatterns(rootNode, packageName)
                val isReelDetected = confidence >= 0.7f  // 70% confidence threshold
                
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
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state analyzing UI: ${e.message}", e)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Null pointer in UI analysis: ${e.message}", e)
        }
    }
    
    /**
     * Analyze the UI hierarchy for reel-watching patterns
     * Returns confidence score (0.0-1.0) from pattern matching
     */
    private fun analyzeUIForReelPatterns(rootNode: AccessibilityNodeInfo, packageName: String): Float {
        return if (::patternMatcher.isInitialized) {
            patternMatcher.analyzeForReelPatterns(rootNode, packageName)
        } else {
            // Fallback if matcher not initialized yet
            0.5f
        }
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
        
        // Initialize pattern matcher with screen dimensions
        val displayMetrics = Resources.getSystem().displayMetrics
        patternMatcher = ReelPatternMatcher(
            screenHeight = displayMetrics.heightPixels,
            screenWidth = displayMetrics.widthPixels
        )
        
        Log.d(TAG, "Service connected - Reel detection active (${displayMetrics.widthPixels}x${displayMetrics.heightPixels})")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        latestDetection = null
        Log.d(TAG, "Service destroyed")
    }
}
