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
    private lateinit var usageStatsStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var statusMessage: TextView
    private lateinit var appUsageMonitor: AppUsageMonitor

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_USAGE_STATS_PERMISSION = 1002
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
        usageStatsStatus = findViewById(R.id.usage_stats_status)
        overlayStatus = findViewById(R.id.overlay_status)
        statusMessage = findViewById(R.id.status_message)

        updateUI()

        // Permission container click handlers
        usageStatsContainer.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        overlayContainer.setOnClickListener {
            requestOverlayPermission()
        }
        
        // Start button
        startButton.setOnClickListener {
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
        }
        
        // Settings button
        settingsButton.setOnClickListener {
            android.util.Log.d("MainActivity", "Opening Settings")
            startActivity(Intent(this, SettingsActivity::class.java))
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
    
    private fun isOverlayServiceRunning(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (OverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
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
        android.util.Log.d("MainActivity", "=== startOverlayService called ===")
        android.util.Log.d("MainActivity", "Overlay permission: ${canDrawOverlays()}")
        android.util.Log.d("MainActivity", "Usage Stats permission: ${hasUsageStatsPermission()}")
        
        // Load config and check monitored apps
        val config = prefsHelper.loadConfig()
        android.util.Log.d("MainActivity", "Monitored apps count: ${config.monitoredApps.size}")
        config.monitoredApps.forEach {
            android.util.Log.d("MainActivity", "  - ${it.appName} (${it.packageName}) enabled=${it.isEnabled}")
        }
        
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        android.util.Log.d("MainActivity", "Service start intent sent")
        
        // Don't set isServiceRunning here - let updateUI() verify service actually started
        Toast.makeText(this, "Starting monitoring...", Toast.LENGTH_SHORT).show()
        
        // Give service time to start, then verify and update UI
        android.os.Handler(mainLooper).postDelayed({
            updateUI()
            android.util.Log.d("MainActivity", "After 500ms - isServiceRunning: $isServiceRunning")
            if (isServiceRunning) {
                Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to start monitoring", Toast.LENGTH_LONG).show()
            }
        }, 500)
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        
        // Don't set isServiceRunning here - let updateUI() verify service actually stopped
        Toast.makeText(this, "Stopping monitoring...", Toast.LENGTH_SHORT).show()
        
        // Give service time to stop, then verify and update UI
        android.os.Handler(mainLooper).postDelayed({
            updateUI()
            if (!isServiceRunning) {
                Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
            }
        }, 500)
    }

    private fun updateUI() {
        val hasOverlay = canDrawOverlays()
        val hasUsageStats = hasUsageStatsPermission()
        
        // Sync service running state with actual service status
        isServiceRunning = isOverlayServiceRunning()
        
        // Check if any monitored apps are configured
        val config = com.reelfocus.app.utils.PreferencesHelper(this).loadConfig()
        val hasMonitoredApps = config.monitoredApps.any { it.isEnabled }
        
        // Get M3 theme colors
        val colorSuccess = getColor(R.color.md_theme_success)
        val colorPrimary = getColor(R.color.md_theme_primary)
        val colorOnSurfaceVariant = getColor(R.color.md_theme_on_surface_variant)
        
        // Update permission status indicators with M3 colors
        usageStatsStatus.text = if (hasUsageStats) getString(R.string.granted) else getString(R.string.grant)
        usageStatsStatus.setTextColor(if (hasUsageStats) colorSuccess else colorPrimary)
        
        overlayStatus.text = if (hasOverlay) getString(R.string.granted) else getString(R.string.grant)
        overlayStatus.setTextColor(if (hasOverlay) colorSuccess else colorPrimary)
        
        // Keep all containers clickable - allow users to re-check or manage permissions
        usageStatsContainer.isClickable = true
        overlayContainer.isClickable = true
        
        // Update start button state - requires permissions AND monitored apps
        val canStart = hasOverlay && hasUsageStats && hasMonitoredApps
        startButton.isEnabled = canStart
        startButton.text = if (isServiceRunning) getString(R.string.stop_monitoring) else getString(R.string.start_monitoring)
        
        // Update button background using MaterialButton backgroundTint (not backgroundColor)
        if (isServiceRunning) {
            startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.md_theme_error))
        } else {
            startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.md_theme_primary))
        }
        
        // Update status message with M3 colors
        statusMessage.text = when {
            isServiceRunning -> getString(R.string.monitoring_active)
            !hasMonitoredApps -> "Configure apps in Settings first"
            !hasOverlay && !hasUsageStats -> "Grant both permissions to start"
            !hasOverlay -> "Overlay permission required"
            !hasUsageStats -> "Usage stats permission required"
            else -> getString(R.string.ready_to_start)
        }
        statusMessage.setTextColor(if (isServiceRunning) colorSuccess else colorOnSurfaceVariant)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("MainActivity", "onResume - refreshing UI")
        
        // Immediately check service state for instant feedback
        updateUI()
        
        // Also do a delayed check for permission changes from system settings
        android.os.Handler(mainLooper).postDelayed({
            android.util.Log.d("MainActivity", "onResume - delayed permission check")
            updateUI()
        }, 300)
    }
}
