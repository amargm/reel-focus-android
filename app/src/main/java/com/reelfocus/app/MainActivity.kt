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
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var permissionButton: Button
    private lateinit var usageStatsButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var settingsButton: Button
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
        
        statusText = findViewById(R.id.status_text)
        toggleButton = findViewById(R.id.toggle_button)
        permissionButton = findViewById(R.id.permission_button)
        usageStatsButton = findViewById(R.id.usage_stats_button)
        accessibilityButton = findViewById(R.id.accessibility_button)
        settingsButton = findViewById(R.id.settings_button)

        updateUI()

        permissionButton.setOnClickListener {
            requestOverlayPermission()
        }
        
        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        usageStatsButton.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        accessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }

        toggleButton.setOnClickListener {
            if (canDrawOverlays()) {
                toggleService()
            } else {
                Toast.makeText(
                    this,
                    "Please grant overlay permission first",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasOverlay = canDrawOverlays()
        val hasUsageStats = hasUsageStatsPermission()
        val hasAccessibility = hasAccessibilityPermission()
        
        permissionButton.isEnabled = !hasOverlay
        permissionButton.text = if (hasOverlay) {
            "✓ Overlay Permission"
        } else {
            "Grant Overlay Permission"
        }
        
        usageStatsButton.isEnabled = !hasUsageStats
        usageStatsButton.text = if (hasUsageStats) {
            "✓ Usage Stats Permission"
        } else {
            "Grant Usage Stats Permission"
        }
        
        // Accessibility is optional but recommended
        accessibilityButton.isEnabled = !hasAccessibility
        accessibilityButton.text = if (hasAccessibility) {
            "✓ Accessibility (Enhanced)"
        } else {
            "Enable Accessibility (Recommended)"
        }
        
        // Only require overlay and usage stats - accessibility is optional
        val canStart = hasOverlay && hasUsageStats
        toggleButton.isEnabled = canStart
        toggleButton.text = if (isServiceRunning) {
            "Stop Overlay"
        } else {
            "Start Overlay"
        }
        
        // Status priority: service running > missing permissions > ready
        statusText.text = when {
            isServiceRunning && hasAccessibility -> "✓ Overlay Active (Enhanced Detection)"
            isServiceRunning -> "✓ Overlay Active (Basic Detection)"
            !hasOverlay -> "⚠️ Overlay permission required"
            !hasUsageStats -> "⚠️ Usage stats permission required"
            !hasAccessibility -> "💡 Ready to start (Enable accessibility for better detection)"
            else -> "✓ All permissions granted - Ready to start!"
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
