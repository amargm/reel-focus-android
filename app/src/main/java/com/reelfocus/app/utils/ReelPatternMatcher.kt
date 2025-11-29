package com.reelfocus.app.utils

import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pattern recognition engine for detecting reel-watching UI patterns
 * Uses app-agnostic characteristics that work across updates:
 * - Full-screen video views (height > 80% of screen)
 * - Vertical scroll containers
 * - Portrait video aspect ratio (~9:16)
 */
class ReelPatternMatcher(private val screenHeight: Int, private val screenWidth: Int) {
    
    companion object {
        private const val TAG = "ReelPatternMatcher"
        
        // Pattern detection thresholds
        private const val FULL_SCREEN_THRESHOLD = 0.75f  // 75% of screen height
        private const val PORTRAIT_RATIO_MIN = 1.3f      // Height/Width > 1.3 (portrait)
        private const val PORTRAIT_RATIO_MAX = 2.5f      // Height/Width < 2.5 (valid video)
    }
    
    /**
     * Analyze UI hierarchy to detect reel-watching patterns
     * Returns confidence score 0.0-1.0
     */
    fun analyzeForReelPatterns(rootNode: AccessibilityNodeInfo, packageName: String): Float {
        return when (packageName) {
            "com.instagram.android" -> detectInstagramReels(rootNode)
            "com.zhiliaoapp.musically" -> detectTikTokReels(rootNode)
            "com.google.android.youtube" -> detectYouTubeShorts(rootNode)
            else -> 0.5f  // Unknown app, assume reel watching (fallback behavior)
        }
    }
    
    /**
     * Instagram Reels detection
     * Patterns:
     * - Large vertical content container (RecyclerView/ViewPager)
     * - Full-screen media views
     * - Portrait orientation
     */
    private fun detectInstagramReels(rootNode: AccessibilityNodeInfo): Float {
        var confidence = 0.0f
        val detectedPatterns = mutableListOf<String>()
        
        // Pattern 1: Full-screen scrollable container
        if (hasFullScreenScrollable(rootNode)) {
            confidence += 0.4f
            detectedPatterns.add("full-screen-scroll")
        }
        
        // Pattern 2: Large portrait video view
        if (hasLargePortraitView(rootNode)) {
            confidence += 0.4f
            detectedPatterns.add("portrait-video")
        }
        
        // Pattern 3: ViewPager or vertical container hints
        if (hasVerticalSwipeContainer(rootNode)) {
            confidence += 0.2f
            detectedPatterns.add("vertical-swipe")
        }
        
        if (confidence > 0.3f) {
            Log.d(TAG, "Instagram Reels detected: $confidence (${detectedPatterns.joinToString()})")
        }
        
        return confidence
    }
    
    /**
     * TikTok For You page detection
     * Patterns similar to Instagram but different class names
     */
    private fun detectTikTokReels(rootNode: AccessibilityNodeInfo): Float {
        var confidence = 0.0f
        val detectedPatterns = mutableListOf<String>()
        
        // Pattern 1: Full-screen content
        if (hasFullScreenScrollable(rootNode)) {
            confidence += 0.4f
            detectedPatterns.add("full-screen")
        }
        
        // Pattern 2: Portrait video container
        if (hasLargePortraitView(rootNode)) {
            confidence += 0.4f
            detectedPatterns.add("portrait-video")
        }
        
        // Pattern 3: ViewPager pattern (TikTok uses vertical paging)
        if (hasVerticalSwipeContainer(rootNode)) {
            confidence += 0.2f
            detectedPatterns.add("vertical-pager")
        }
        
        if (confidence > 0.3f) {
            Log.d(TAG, "TikTok detected: $confidence (${detectedPatterns.joinToString()})")
        }
        
        return confidence
    }
    
    /**
     * YouTube Shorts detection
     * Patterns:
     * - Similar full-screen vertical video
     * - "shorts" in view hierarchy or activity name
     */
    private fun detectYouTubeShorts(rootNode: AccessibilityNodeInfo): Float {
        var confidence = 0.0f
        val detectedPatterns = mutableListOf<String>()
        
        // Pattern 1: Check for "shorts" keyword in UI hierarchy
        if (containsShortsKeyword(rootNode)) {
            confidence += 0.5f
            detectedPatterns.add("shorts-keyword")
        }
        
        // Pattern 2: Full-screen video player
        if (hasFullScreenScrollable(rootNode)) {
            confidence += 0.3f
            detectedPatterns.add("full-screen")
        }
        
        // Pattern 3: Portrait video view
        if (hasLargePortraitView(rootNode)) {
            confidence += 0.2f
            detectedPatterns.add("portrait-video")
        }
        
        if (confidence > 0.3f) {
            Log.d(TAG, "YouTube Shorts detected: $confidence (${detectedPatterns.joinToString()})")
        }
        
        return confidence
    }
    
    /**
     * Check if UI has a full-screen scrollable container
     */
    private fun hasFullScreenScrollable(node: AccessibilityNodeInfo): Boolean {
        return searchNodeRecursive(node) { n ->
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            
            val height = bounds.height()
            val isScrollable = n.isScrollable
            val isFullScreen = height >= (screenHeight * FULL_SCREEN_THRESHOLD)
            
            isScrollable && isFullScreen
        }
    }
    
    /**
     * Check if UI has a large portrait-oriented view (likely video)
     */
    private fun hasLargePortraitView(node: AccessibilityNodeInfo): Boolean {
        return searchNodeRecursive(node) { n ->
            val bounds = android.graphics.Rect()
            n.getBoundsInScreen(bounds)
            
            val width = bounds.width()
            val height = bounds.height()
            
            // Check if it's tall enough and portrait ratio
            if (height >= (screenHeight * FULL_SCREEN_THRESHOLD) && width > 0) {
                val ratio = height.toFloat() / width.toFloat()
                ratio in PORTRAIT_RATIO_MIN..PORTRAIT_RATIO_MAX
            } else {
                false
            }
        }
    }
    
    /**
     * Check for vertical swipe container (ViewPager, RecyclerView with vertical layout)
     */
    private fun hasVerticalSwipeContainer(node: AccessibilityNodeInfo): Boolean {
        return searchNodeRecursive(node) { n ->
            val className = n.className?.toString() ?: ""
            val isScrollable = n.isScrollable
            
            // Look for ViewPager2, RecyclerView, or custom vertical containers
            (className.contains("ViewPager", ignoreCase = true) ||
             className.contains("RecyclerView", ignoreCase = true)) && isScrollable
        }
    }
    
    /**
     * Check if "shorts" keyword appears in view hierarchy (YouTube specific)
     */
    private fun containsShortsKeyword(node: AccessibilityNodeInfo): Boolean {
        return searchNodeRecursive(node) { n ->
            val viewId = n.viewIdResourceName?.lowercase() ?: ""
            val contentDesc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            
            viewId.contains("shorts") || 
            contentDesc.contains("shorts") || 
            text.contains("shorts")
        }
    }
    
    /**
     * Recursively search node tree for matching pattern
     */
    private fun searchNodeRecursive(
        node: AccessibilityNodeInfo,
        matcher: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        try {
            // Check current node
            if (matcher(node)) {
                return true
            }
            
            // Search children (limit depth to prevent performance issues)
            val childCount = node.childCount
            for (i in 0 until childCount.coerceAtMost(20)) {
                val child = node.getChild(i)
                if (child != null) {
                    if (searchNodeRecursive(child, matcher)) {
                        child.recycle()
                        return true
                    }
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching node: ${e.message}")
        }
        
        return false
    }
}
