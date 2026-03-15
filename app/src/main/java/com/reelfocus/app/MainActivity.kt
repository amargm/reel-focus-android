package com.reelfocus.app

import android.app.ActivityManager
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
import com.reelfocus.app.utils.PreferencesHelper

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private lateinit var startButton: Button
    private lateinit var settingsButton: Button
    private lateinit var usageStatsContainer: android.view.View
    private lateinit var overlayContainer: android.view.View
    private lateinit var usageStatsStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var statusMessage: TextView
    private lateinit var selectedAppsSummary: TextView
    private lateinit var selectAppsCard: android.view.View
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
        selectedAppsSummary = findViewById(R.id.selected_apps_summary)
        selectAppsCard = findViewById(R.id.select_apps_card)

        updateUI()

        // Permission container click handlers
        usageStatsContainer.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        overlayContainer.setOnClickListener {
            requestOverlayPermission()
        }

        // Step 2: Tap card to go to AppSelectionActivity
        selectAppsCard.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
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
        
        // Toggle based on current state
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
        
        // Set state immediately
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        
        // Set state immediately
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasOverlay = canDrawOverlays()
        val hasUsageStats = hasUsageStatsPermission()
        
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
        
        // Update selected-apps summary in Step 2 card
        val enabledApps = config.monitoredApps.filter { it.isEnabled }
        selectedAppsSummary.text = if (enabledApps.isEmpty()) {
            "No apps selected — tap to choose"
        } else {
            "${enabledApps.size} app${if (enabledApps.size == 1) "" else "s"} selected: " +
                    enabledApps.take(3).joinToString(", ") { it.appName } +
                    if (enabledApps.size > 3) " +${enabledApps.size - 3} more" else ""
        }

        // Update start button state - requires permissions AND monitored apps to START,
        // but the button must always be enabled if the service is already running so the
        // user can STOP it even if permissions/apps have since changed (BUG-F03 FIX).
        val canStart = hasOverlay && hasUsageStats && hasMonitoredApps
        startButton.isEnabled = canStart || isServiceRunning
        startButton.text = if (isServiceRunning) getString(R.string.stop_monitoring) else getString(R.string.start_monitoring)
        
        // Update button background using MaterialButton backgroundTint (not backgroundColor)
        if (isServiceRunning) {
            startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.md_theme_error))
        } else {
            startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.md_theme_primary))
        }
        
        // Update status message with M3 colors
        statusMessage.text = when {
            isServiceRunning -> "✓ Monitoring active"
            !hasOverlay && !hasUsageStats -> "Complete Step 1 — grant both permissions"
            !hasOverlay -> "Complete Step 1 — grant Overlay permission"
            !hasUsageStats -> "Complete Step 1 — grant Usage Stats permission"
            !hasMonitoredApps -> "Complete Step 2 — select at least one app"
            else -> "Ready — tap Start Monitoring"
        }
        statusMessage.setTextColor(if (isServiceRunning) colorSuccess else colorOnSurfaceVariant)
    }

    override fun onResume() {
        super.onResume()
        // BUG-004 FIX: sync isServiceRunning from actual system state on every resume
        // so button label and duplicate-start protection are always correct after
        // rotation, back-stack return, or returning from Settings.
        isServiceRunning = isOverlayServiceRunning()
        updateUI()
    }

    @Suppress("DEPRECATION")
    private fun isOverlayServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == OverlayService::class.java.name
        }
    }
}
