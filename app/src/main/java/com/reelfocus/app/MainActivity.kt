package com.reelfocus.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var permissionButton: Button

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleButton = findViewById(R.id.toggle_button)
        permissionButton = findViewById(R.id.permission_button)

        updateUI()

        permissionButton.setOnClickListener {
            requestOverlayPermission()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
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
    }

    private fun toggleService() {
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
        val hasPermission = canDrawOverlays()
        
        permissionButton.isEnabled = !hasPermission
        permissionButton.text = if (hasPermission) {
            "✓ Permission Granted"
        } else {
            "Grant Overlay Permission"
        }
        
        toggleButton.isEnabled = hasPermission
        toggleButton.text = if (isServiceRunning) {
            "Stop Overlay"
        } else {
            "Start Overlay"
        }
        
        statusText.text = when {
            !hasPermission -> "⚠️ Overlay permission required"
            isServiceRunning -> "✓ Overlay Active"
            else -> "Ready to start"
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
