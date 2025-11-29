package com.reelfocus.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.reelfocus.app.utils.AppUsageMonitor

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var usageStatsContainer: android.view.View
    private lateinit var overlayContainer: android.view.View
    private lateinit var accessibilityContainer: android.view.View
    private lateinit var usageStatsStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var statusMessage: TextView
    private lateinit var appUsageMonitor: AppUsageMonitor

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_USAGE_STATS_PERMISSION = 1002
        private const val REQUEST_ACCESSIBILITY_PERMISSION = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appUsageMonitor = AppUsageMonitor(this)
        
        // Initialize views
        startButton = findViewById(R.id.start_button)
        settingsButton = findViewById(R.id.settings_button)
        usageStatsContainer = findViewById(R.id.usage_stats_container)
        overlayContainer = findViewById(R.id.overlay_permission_container)
        accessibilityContainer = findViewById(R.id.accessibility_container)
        usageStatsStatus = findViewById(R.id.usage_stats_status)
        overlayStatus = findViewById(R.id.overlay_status)
        accessibilityStatus = findViewById(R.id.accessibility_status)
        statusMessage = findViewById(R.id.status_message)

        updateUI()

        // Permission container click handlers
        usageStatsContainer.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        overlayContainer.setOnClickListener {
            requestOverlayPermission()
        }
        
        accessibilityContainer.setOnClickListener {
            requestAccessibilityPermission()
        }
        
        // Start button
        startButton.setOnClickListener {
            // Force refresh UI to check latest permission state
            updateUI()
            android.os.Handler(mainLooper).postDelayed({
                if (canDrawOverlays() && hasUsageStatsPermission()) {
                    toggleService()
                } else {
                    val missing = mutableListOf<String>()
                    if (!canDrawOverlays()) missing.add("Overlay")
                    if (!hasUsageStatsPermission()) missing.add("Usage Stats")
                    Toast.makeText(
                        this,
                        "Missing permissions: ${missing.joinToString(", ")}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }, 50) // Small delay to ensure UI refresh completes
        }
        
        // Settings button
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        // Use the improved AppUsageMonitor's built-in check
        return appUsageMonitor.hasUsageStatsPermission()
    }
    
    private fun hasAccessibilityPermission(): Boolean {
        val expectedServiceName = "$packageName/com.reelfocus.app.ReelDetectionAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return enabledServices.contains(expectedServiceName)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivityForResult(intent, REQUEST_USAGE_STATS_PERMISSION)
        } else {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestAccessibilityPermission() {
        if (!hasAccessibilityPermission()) {
            Toast.makeText(
                this,
                "Enable 'Reel Focus' in the accessibility services list for better detection",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION)
        } else {
            Toast.makeText(this, "Accessibility service already enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Delay to ensure system registers permission changes
        android.os.Handler(mainLooper).postDelayed({
            when (requestCode) {
                REQUEST_OVERLAY_PERMISSION -> {
                    updateUI()
                    if (canDrawOverlays()) {
                        Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Overlay permission is required for this app to work",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                REQUEST_USAGE_STATS_PERMISSION -> {
                    updateUI()
                    if (hasUsageStatsPermission()) {
                        Toast.makeText(this, "Usage stats permission granted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Usage stats permission is required to monitor apps",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                REQUEST_ACCESSIBILITY_PERMISSION -> {
                    updateUI()
                    if (hasAccessibilityPermission()) {
                        Toast.makeText(this, "Accessibility service enabled! Enhanced detection active.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Accessibility service is optional but recommended for accurate detection",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, 200) // Increased delay for better permission state sync
    }

    private fun toggleService() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please grant usage stats permission first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isServiceRunning) {
            stopOverlayService()
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasOverlay = canDrawOverlays()
        val hasUsageStats = hasUsageStatsPermission()
        val hasAccessibility = hasAccessibilityPermission()
        
        // Update permission status indicators
        usageStatsStatus.text = if (hasUsageStats) "✓ Granted" else "Grant"
        usageStatsStatus.setTextColor(
            if (hasUsageStats) 0xFF10B981.toInt() else 0xFF007AFF.toInt()
        )
        
        overlayStatus.text = if (hasOverlay) "✓ Granted" else "Grant"
        overlayStatus.setTextColor(
            if (hasOverlay) 0xFF10B981.toInt() else 0xFF007AFF.toInt()
        )
        
        accessibilityStatus.text = if (hasAccessibility) "✓ Enabled" else "Enable"
        accessibilityStatus.setTextColor(
            if (hasAccessibility) 0xFF10B981.toInt() else 0xFF6C757D.toInt()
        )
        
        // Enable/disable containers based on permission status
        usageStatsContainer.isClickable = !hasUsageStats
        overlayContainer.isClickable = !hasOverlay
        // Accessibility is always clickable (optional)
        
        // Update start button and status message
        val canStart = hasOverlay && hasUsageStats
        startButton.isEnabled = canStart
        startButton.isActivated = isServiceRunning
        startButton.text = if (isServiceRunning) "Stop Monitoring" else "Start Monitoring"
        
        // Update status message
        statusMessage.text = when {
            isServiceRunning && hasAccessibility -> "✓ Monitoring active with enhanced detection"
            isServiceRunning -> "✓ Monitoring active"
            !hasOverlay && !hasUsageStats -> "Grant both permissions to start"
            !hasOverlay -> "Overlay permission required"
            !hasUsageStats -> "Usage stats permission required"
            !hasAccessibility -> "Ready to start • Enable accessibility for better detection"
            else -> "✓ All permissions granted • Ready to start"
        }
        statusMessage.setTextColor(
            if (isServiceRunning) 0xFF10B981.toInt() else 0xFF6C757D.toInt()
        )
    }

    override fun onResume() {
        super.onResume()
        // Force recheck permissions when returning to activity
        android.os.Handler(mainLooper).postDelayed({
            updateUI()
        }, 200) // Delay to ensure permission changes are registered
    }
}
