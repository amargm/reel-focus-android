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
            if (canDrawOverlays() && hasUsageStatsPermission()) {
                toggleService()
            } else {
                Toast.makeText(
                    this,
                    "Please grant all required permissions first",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
            if (hasUsageStats) 0xFF10B981.toInt() else 0xFF3B82F6.toInt()
        )
        
        overlayStatus.text = if (hasOverlay) "✓ Granted" else "Grant"
        overlayStatus.setTextColor(
            if (hasOverlay) 0xFF10B981.toInt() else 0xFF3B82F6.toInt()
        )
        
        accessibilityStatus.text = if (hasAccessibility) "✓ Enabled" else "Enable"
        accessibilityStatus.setTextColor(
            if (hasAccessibility) 0xFF10B981.toInt() else 0xFF3B82F6.toInt()
        )
        
        // Enable/disable containers based on permission status
        usageStatsContainer.isClickable = !hasUsageStats
        overlayContainer.isClickable = !hasOverlay
        // Accessibility is always clickable (optional)
        
        // Update start button
        val canStart = hasOverlay && hasUsageStats
        startButton.isEnabled = canStart
        startButton.text = if (isServiceRunning) "Stop Monitoring" else "Start Monitoring"
        startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (canStart && !isServiceRunning) 0xFF3B82F6.toInt() else 0xFF9CA3AF.toInt()
        )
        if (isServiceRunning) {
            startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFEF4444.toInt())
        }
    }

    override fun onResume() {
        super.onResume()
        // Force recheck permissions when returning to activity
        android.os.Handler(mainLooper).postDelayed({
            updateUI()
        }, 100) // Small delay to ensure permission changes are registered
    }
}
