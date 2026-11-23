package com.reelfocus.app

import android.accessibilityservice.AccessibilityService
import android.content.res.Resources
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat

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
    private var selectionReceiverRegistered = false
    private val selectionChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            hasLoadedMonitoredPackages = false
            lastPackageRefreshAtMs = 0L
            refreshMonitoredPackagesIfNeeded()
        }
    }
    
    companion object {
        private const val TAG = "ReelDetectionService"
        const val ACTION_SELECTED_APPS_CHANGED = "com.reelfocus.app.ACTION_SELECTED_APPS_CHANGED"
        
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
        val refreshedPackages = runCatching {
            MonitoredPackagePolicy.enabledPackages(PreferencesHelper(this).loadConfig().monitoredApps)
        }.onFailure {
            Log.w(TAG, "Unable to refresh selected apps; retaining last known selection", it)
        }.getOrDefault(monitoredPackages)
        val selectionChanged = !hasLoadedMonitoredPackages || refreshedPackages != monitoredPackages
        monitoredPackages = refreshedPackages
        if (selectionChanged && isConnected) {
            applySelectedPackageFilter()
        }
        lastPackageRefreshAtMs = now
        hasLoadedMonitoredPackages = true
    }

    /**
     * Let Android deliver accessibility events only for the selected packages.
     * The XML intentionally has no static package filter; this is refreshed
     * from the user's current selection.
     */
    private fun applySelectedPackageFilter() {
        runCatching {
            val info = serviceInfo
            info.packageNames = monitoredPackages.toTypedArray()
            serviceInfo = info
        }.onFailure {
            Log.w(TAG, "Unable to update Accessibility package filter", it)
        }
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
        // Require a new selected-app event before Full detection is trusted again.
        latestDetection = null
        monitoredPackages = emptySet()
        hasLoadedMonitoredPackages = false
        lastPackageRefreshAtMs = 0L
        refreshMonitoredPackagesIfNeeded()
        if (!selectionReceiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                selectionChangedReceiver,
                IntentFilter(ACTION_SELECTED_APPS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            selectionReceiverRegistered = true
        }

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
        if (selectionReceiverRegistered) {
            runCatching { unregisterReceiver(selectionChangedReceiver) }
            selectionReceiverRegistered = false
        }
        // Preserve the last event timestamp for diagnostics after disconnection.
        super.onDestroy()
        latestDetection = null
        Log.d(TAG, "Service destroyed")
    }
}
