package com.reelfocus.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.slider.Slider
import com.reelfocus.app.models.*
import com.reelfocus.app.utils.PreferencesHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var config: AppConfig

    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var maxSessionsSeekBar: Slider
    private lateinit var maxSessionsValue: TextView
    private lateinit var sessionGapSeekBar: Slider
    private lateinit var sessionGapValue: TextView
    private lateinit var limitTypeSpinner: Spinner
    private lateinit var limitTypeText: TextView
    private lateinit var limitValueSeekBar: Slider
    private lateinit var limitValueText: TextView
    private lateinit var overlayPositionSpinner: Spinner
    private lateinit var positionTopButton: Button
    private lateinit var positionCenterButton: Button
    private lateinit var positionBottomButton: Button
    private lateinit var textSizeSpinner: Spinner
    private lateinit var sizeSmallButton: Button
    private lateinit var sizeMediumButton: Button
    private lateinit var sizeLargeButton: Button
    private lateinit var manageAppsButton: LinearLayout
    
    private var selectedOverlayPosition = OverlayPosition.TOP_RIGHT
    private var selectedTextSize = TextSize.MEDIUM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsHelper = PreferencesHelper(this)
        config = prefsHelper.loadConfig()

        initializeViews()
        setupListeners()
        loadCurrentSettings()
    }

    private fun initializeViews() {
        // Toolbar with back navigation
        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        
        // C-001: Max Sessions Daily
        maxSessionsSeekBar = findViewById(R.id.max_sessions_seekbar)
        maxSessionsValue = findViewById(R.id.max_sessions_value)
        
        // C-003: Session Reset Gap
        sessionGapSeekBar = findViewById(R.id.session_gap_seekbar)
        sessionGapValue = findViewById(R.id.session_gap_value)
        
        // C-004: Default Limit Type
        limitTypeSpinner = findViewById(R.id.limit_type_spinner)
        limitTypeText = findViewById(R.id.limit_type_text)
        
        // C-004: Default Limit Value
        limitValueSeekBar = findViewById(R.id.limit_value_seekbar)
        limitValueText = findViewById(R.id.limit_value_text)
        
        // C-006: Overlay Position
        overlayPositionSpinner = findViewById(R.id.overlay_position_spinner)
        positionTopButton = findViewById(R.id.position_top_button)
        positionCenterButton = findViewById(R.id.position_center_button)
        positionBottomButton = findViewById(R.id.position_bottom_button)
        
        // C-007: Text Size
        textSizeSpinner = findViewById(R.id.text_size_spinner)
        sizeSmallButton = findViewById(R.id.size_small_button)
        sizeMediumButton = findViewById(R.id.size_medium_button)
        sizeLargeButton = findViewById(R.id.size_large_button)
        
        // Navigation buttons
        manageAppsButton = findViewById(R.id.manage_apps_button)
        val viewHistoryButton = findViewById<LinearLayout>(R.id.view_history_button)
        val limitTypeContainer = findViewById<LinearLayout>(R.id.limit_type_container)
        
        // Setup spinners
        setupLimitTypeSpinner()
        setupOverlayPositionSpinner()
        setupTextSizeSpinner()
        
        // Limit type click handler (toggle between Time/Count)
        limitTypeContainer.setOnClickListener {
            val newType = if (config.defaultLimitType == LimitType.TIME) LimitType.COUNT else LimitType.TIME
            config = config.copy(defaultLimitType = newType)
            limitTypeText.text = if (newType == LimitType.TIME) "Time" else "Count"
            updateLimitValueDisplay()
            autoSave()
        }
        
        // History button click
        viewHistoryButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun setupLimitTypeSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Time (Minutes)", "Count (Reels)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        limitTypeSpinner.adapter = adapter
    }

    private fun setupOverlayPositionSpinner() {
        val positions = OverlayPosition.values().map { it.displayName }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            positions
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        overlayPositionSpinner.adapter = adapter
    }

    private fun setupTextSizeSpinner() {
        val sizes = arrayOf("Small", "Medium", "Large")
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            sizes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        textSizeSpinner.adapter = adapter
    }

    private fun setupListeners() {
        // Max Sessions Slider (1-20)
        maxSessionsSeekBar.addOnChangeListener { slider, value, fromUser ->
            maxSessionsValue.text = value.toInt().toString()
        }
        maxSessionsSeekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                autoSave()
            }
        })

        // Session Gap Slider (5-120 minutes, step by 5)
        sessionGapSeekBar.addOnChangeListener { slider, value, fromUser ->
            val minutes = value.toInt()
            sessionGapValue.text = "$minutes min"
        }
        sessionGapSeekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                autoSave()
            }
        })

        // Limit Value Slider
        limitValueSeekBar.addOnChangeListener { slider, value, fromUser ->
            updateLimitValueText()
        }
        limitValueSeekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                autoSave()
            }
        })

        // Navigate to App Selection
        manageAppsButton.setOnClickListener {
            val intent = Intent(this, AppSelectionActivity::class.java)
            startActivity(intent)
        }
        
        // Navigate to History
        val viewHistoryButton = findViewById<LinearLayout>(R.id.view_history_button)
        viewHistoryButton.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
        
        // Overlay Position Buttons
        positionTopButton.setOnClickListener {
            selectedOverlayPosition = OverlayPosition.TOP_RIGHT
            updatePositionButtons()
            autoSave()
        }
        positionCenterButton.setOnClickListener {
            selectedOverlayPosition = OverlayPosition.CENTER_RIGHT
            updatePositionButtons()
            autoSave()
        }
        positionBottomButton.setOnClickListener {
            selectedOverlayPosition = OverlayPosition.BOTTOM_RIGHT
            updatePositionButtons()
            autoSave()
        }
        
        // Text Size Buttons
        sizeSmallButton.setOnClickListener {
            selectedTextSize = TextSize.SMALL
            updateTextSizeButtons()
            autoSave()
        }
        sizeMediumButton.setOnClickListener {
            selectedTextSize = TextSize.MEDIUM
            updateTextSizeButtons()
            autoSave()
        }
        sizeLargeButton.setOnClickListener {
            selectedTextSize = TextSize.LARGE
            updateTextSizeButtons()
            autoSave()
        }
    }
    
    private fun updatePositionButtons() {
        positionTopButton.alpha = if (selectedOverlayPosition == OverlayPosition.TOP_RIGHT) 1.0f else 0.5f
        positionCenterButton.alpha = if (selectedOverlayPosition == OverlayPosition.CENTER_RIGHT) 1.0f else 0.5f
        positionBottomButton.alpha = if (selectedOverlayPosition == OverlayPosition.BOTTOM_RIGHT) 1.0f else 0.5f
    }
    
    private fun updateTextSizeButtons() {
        sizeSmallButton.alpha = if (selectedTextSize == TextSize.SMALL) 1.0f else 0.5f
        sizeMediumButton.alpha = if (selectedTextSize == TextSize.MEDIUM) 1.0f else 0.5f
        sizeLargeButton.alpha = if (selectedTextSize == TextSize.LARGE) 1.0f else 0.5f
    }

    private fun updateLimitValueSeekBar(isTimeMode: Boolean) {
        if (isTimeMode) {
            // TIME: 5-60 minutes (step by 5)
            limitValueSeekBar.valueFrom = 5f
            limitValueSeekBar.valueTo = 60f
            limitValueSeekBar.stepSize = 5f
        } else {
            // COUNT: 5-100 reels (step by 5)
            limitValueSeekBar.valueFrom = 5f
            limitValueSeekBar.valueTo = 100f
            limitValueSeekBar.stepSize = 5f
        }
        updateLimitValueText()
    }

    private fun updateLimitValueText() {
        val isTimeMode = config.defaultLimitType == LimitType.TIME
        val value = limitValueSeekBar.value.toInt()
        limitValueText.text = if (isTimeMode) {
            "$value min"
        } else {
            "$value"
        }
    }

    private fun loadCurrentSettings() {
        // C-001: Max Sessions
        maxSessionsSeekBar.value = config.maxSessionsDaily.toFloat()
        maxSessionsValue.text = "${config.maxSessionsDaily}"

        // C-003: Session Gap
        sessionGapSeekBar.value = config.sessionResetGapMinutes.toFloat()
        sessionGapValue.text = "${config.sessionResetGapMinutes} min"

        // C-004: Limit Type
        limitTypeSpinner.setSelection(if (config.defaultLimitType == LimitType.TIME) 0 else 1)
        limitTypeText.text = if (config.defaultLimitType == LimitType.TIME) "Time" else "Count"

        // C-004: Limit Value
        limitValueSeekBar.value = config.defaultLimitValue.toFloat()
        updateLimitValueDisplay()

        // C-006: Overlay Position
        selectedOverlayPosition = config.overlayPosition
        overlayPositionSpinner.setSelection(config.overlayPosition.ordinal)
        updatePositionButtons()

        // C-007: Text Size
        selectedTextSize = config.overlayTextSize
        textSizeSpinner.setSelection(config.overlayTextSize.ordinal)
        updateTextSizeButtons()
    }

    private fun saveSettings() {
        // Build updated config
        val updatedConfig = config.copy(
            maxSessionsDaily = maxSessionsSeekBar.value.toInt(),
            sessionResetGapMinutes = sessionGapSeekBar.value.toInt(),
            defaultLimitType = if (limitTypeSpinner.selectedItemPosition == 0) LimitType.TIME else LimitType.COUNT,
            defaultLimitValue = limitValueSeekBar.value.toInt(),
            overlayPosition = selectedOverlayPosition,
            overlayTextSize = selectedTextSize
        )

        prefsHelper.saveConfig(updatedConfig)
        
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        
        // Restart service to apply changes
        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(stopIntent)
        
        android.os.Handler(mainLooper).postDelayed({
            val startIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
        }, 500)
        
        finish()
    }
    
    private fun updateLimitValueDisplay() {
        val isTimeMode = config.defaultLimitType == LimitType.TIME
        updateLimitValueSeekBar(isTimeMode)
    }
    
    private fun autoSave() {
        // Build updated config
        val updatedConfig = config.copy(
            maxSessionsDaily = maxSessionsSeekBar.value.toInt(),
            sessionResetGapMinutes = sessionGapSeekBar.value.toInt(),
            defaultLimitType = config.defaultLimitType,
            defaultLimitValue = limitValueSeekBar.value.toInt(),
            overlayPosition = selectedOverlayPosition,
            overlayTextSize = selectedTextSize
        )

        prefsHelper.saveConfig(updatedConfig)
        config = updatedConfig // Update local copy
    }

    override fun onResume() {
        super.onResume()
        // Reload config in case apps were updated
        config = prefsHelper.loadConfig()
    }
}
