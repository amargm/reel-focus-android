package com.reelfocus.app

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.reelfocus.app.models.DetectionMethod
import com.reelfocus.app.models.DetectionResult
import com.reelfocus.app.models.MonitoredPackagePolicy
import com.reelfocus.app.utils.PreferencesHelper
import com.reelfocus.app.utils.ReelPatternMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var lastContentAnalysisAtMs = 0L
    private var lastPackageRefreshAtMs = 0L
    private var monitoredPackages: Set<String> = emptySet()
    private var hasLoadedMonitoredPackages = false
    
    companion object {
        private const val TAG = "ReelDetectionService"
        
        // Shared state for detection results
        @Volatile
        private var latestDetection: DetectionResult? = null

        @Volatile
        var isConnected: Boolean = false
            private set

        private val _connectionState = MutableStateFlow(false)
        val connectionState = _connectionState.asStateFlow()

        /** Wall-clock time of the most recent event from a selected app. */
        @Volatile
        var lastSelectedAppEventAtMs: Long = 0L
            private set
        
        /**
         * Get the latest detection result (called by DetectionManager)
         */
        @JvmStatic
        fun getLatestDetection(): DetectionResult? = latestDetection
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val now = System.currentTimeMillis()
        refreshMonitoredPackagesIfNeeded(now)
        val packageName = event.packageName?.toString()
        if (packageName == null || !MonitoredPackagePolicy.contains(monitoredPackages, packageName)) {
            // Do not let a positive result from one selected app leak into
            // another app after the user switches away.
            latestDetection = null
            return
        }

        lastSelectedAppEventAtMs = now
        // Content-change events can fire dozens of times per second. A window
        // transition is always inspected; repeated content updates are sampled.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            now - lastContentAnalysisAtMs < 700L) return
        lastContentAnalysisAtMs = now

        val rootNode = rootInActiveWindow ?: run {
            latestDetection = null
            return
        }
        try {
            val confidence = analyzeUIForReelPatterns(rootNode, packageName)
            latestDetection = DetectionResult(
                packageName = packageName,
                appName = getAppName(packageName),
                isReelDetected = confidence >= 0.7f,
                detectionMethod = DetectionMethod.ACCESSIBILITY_SERVICE,
                confidence = confidence,
                timestamp = now
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect active app UI", e)
            latestDetection = null
        } finally {
            rootNode.recycle()
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
     * Read the current user selection periodically. This keeps accessibility
     * detection in sync with Choose Apps without restarting the service.
     */
    private fun refreshMonitoredPackagesIfNeeded(now: Long = System.currentTimeMillis()) {
        if (hasLoadedMonitoredPackages && now - lastPackageRefreshAtMs < 5_000L) return
        monitoredPackages = runCatching {
            MonitoredPackagePolicy.enabledPackages(PreferencesHelper(this).loadConfig().monitoredApps)
        }.onFailure {
            Log.w(TAG, "Unable to refresh selected apps; retaining last known selection", it)
        }.getOrDefault(monitoredPackages)
        lastPackageRefreshAtMs = now
        hasLoadedMonitoredPackages = true
    }
    
    /**
     * Get human-readable app name
     */
    private fun getAppName(packageName: String): String {
        return runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName.substringAfterLast("."))
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        _connectionState.value = true
        monitoredPackages = emptySet()
        hasLoadedMonitoredPackages = false
        lastPackageRefreshAtMs = 0L
        refreshMonitoredPackagesIfNeeded()

        // Initialize pattern matcher with screen dimensions
        val displayMetrics = Resources.getSystem().displayMetrics
        patternMatcher = ReelPatternMatcher(
            screenHeight = displayMetrics.heightPixels,
            screenWidth = displayMetrics.widthPixels
        )
        
        Log.d(TAG, "Service connected - Reel detection active (${displayMetrics.widthPixels}x${displayMetrics.heightPixels})")
    }
    
    override fun onDestroy() {
        isConnected = false
        _connectionState.value = false
        // Preserve the last event timestamp for diagnostics after disconnection.
        super.onDestroy()
        latestDetection = null
        Log.d(TAG, "Service destroyed")
    }
}
