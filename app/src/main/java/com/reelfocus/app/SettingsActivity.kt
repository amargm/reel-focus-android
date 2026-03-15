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
    private lateinit var limitValueSeekBar: Slider
    private lateinit var limitValueText: TextView
    private lateinit var overlayPositionSpinner: Spinner
    private lateinit var positionTopLeftButton: Button
    private lateinit var positionTopButton: Button
    private lateinit var positionCenterButton: Button
    private lateinit var positionBottomLeftButton: Button
    private lateinit var positionBottomButton: Button
    private lateinit var textSizeSpinner: Spinner
    private lateinit var sizeSmallButton: Button
    private lateinit var sizeMediumButton: Button
    private lateinit var sizeLargeButton: Button
    private lateinit var manageAppsButton: LinearLayout
    private lateinit var styleTextButton: Button
    private lateinit var styleDonutButton: Button
    private lateinit var styleCircleButton: Button
    private lateinit var overlayPreviewContainer: android.widget.FrameLayout
    private lateinit var hapticSwitch: com.google.android.material.switchmaterial.SwitchMaterial

    private var selectedOverlayPosition = OverlayPosition.TOP_RIGHT
    private var selectedTextSize = TextSize.MEDIUM
    private var selectedOverlayStyle = com.reelfocus.app.models.OverlayStyle.TEXT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SettingsActivity", "onCreate started")
        
        try {
            setContentView(R.layout.activity_settings)

            prefsHelper = PreferencesHelper(this)
            config = prefsHelper.loadConfig()

            initializeViews()
            setupListeners()
            loadCurrentSettings()
            
            android.util.Log.d("SettingsActivity", "onCreate completed successfully")
        } catch (e: android.content.res.Resources.NotFoundException) {
            android.util.Log.e("SettingsActivity", "Layout or resource not found", e)
            Toast.makeText(this, "Error: Missing resources", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: ClassCastException) {
            android.util.Log.e("SettingsActivity", "View type mismatch", e)
            Toast.makeText(this, "Error: View configuration issue", Toast.LENGTH_LONG).show()
            finish()
        } catch (e: RuntimeException) {
            android.util.Log.e("SettingsActivity", "Runtime error in onCreate", e)
            Toast.makeText(this, "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeViews() {
        // Toolbar with back navigation
        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { 
            android.util.Log.d("SettingsActivity", "Back button clicked")
            finish() 
        }
        
        // C-001: Max Sessions Daily
        maxSessionsSeekBar = findViewById(R.id.max_sessions_seekbar)
        maxSessionsValue = findViewById(R.id.max_sessions_value)
        
        // C-003: Session Reset Gap
        sessionGapSeekBar = findViewById(R.id.session_gap_seekbar)
        sessionGapValue = findViewById(R.id.session_gap_value)
        
        // C-004: Limit Value (always Time in minutes)
        limitValueSeekBar = findViewById(R.id.limit_value_seekbar)
        limitValueText = findViewById(R.id.limit_value_text)
        
        // C-006: Overlay Position
        overlayPositionSpinner = findViewById(R.id.overlay_position_spinner)
        positionTopLeftButton = findViewById(R.id.position_top_left_button)
        positionTopButton = findViewById(R.id.position_top_button)
        positionCenterButton = findViewById(R.id.position_center_button)
        positionBottomLeftButton = findViewById(R.id.position_bottom_left_button)
        positionBottomButton = findViewById(R.id.position_bottom_button)
        
        // C-007: Text Size
        textSizeSpinner = findViewById(R.id.text_size_spinner)
        sizeSmallButton = findViewById(R.id.size_small_button)
        sizeMediumButton = findViewById(R.id.size_medium_button)
        sizeLargeButton = findViewById(R.id.size_large_button)
        
        // C-008: Overlay Style
        styleTextButton   = findViewById(R.id.style_text_button)
        styleDonutButton  = findViewById(R.id.style_donut_button)
        styleCircleButton = findViewById(R.id.style_circle_button)
        overlayPreviewContainer = findViewById(R.id.overlay_preview_container)
        hapticSwitch = findViewById(R.id.haptic_switch)

        // Navigation buttons
        manageAppsButton = findViewById(R.id.manage_apps_button)
        val viewHistoryButton = findViewById<LinearLayout>(R.id.view_history_button)

        // Setup spinners
        setupOverlayPositionSpinner()
        setupTextSizeSpinner()
        // History button is registered in setupListeners() — no duplicate here
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

        // Privacy Policy
        findViewById<LinearLayout>(R.id.privacy_policy_button).setOnClickListener {
            val url = "https://amargm.github.io/reel-focus-android/privacy"
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, "No browser found to open the privacy policy", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Overlay Position Buttons
        positionTopLeftButton.setOnClickListener {
            selectedOverlayPosition = OverlayPosition.TOP_LEFT
            updatePositionButtons()
            autoSave()
        }
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
        positionBottomLeftButton.setOnClickListener {
            selectedOverlayPosition = OverlayPosition.BOTTOM_LEFT
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

        // Overlay Style Buttons
        styleTextButton.setOnClickListener {
            selectedOverlayStyle = com.reelfocus.app.models.OverlayStyle.TEXT
            updateStyleButtons()
            refreshOverlayPreview()
            autoSave()
        }
        styleDonutButton.setOnClickListener {
            selectedOverlayStyle = com.reelfocus.app.models.OverlayStyle.DONUT
            updateStyleButtons()
            refreshOverlayPreview()
            autoSave()
        }
        styleCircleButton.setOnClickListener {
            selectedOverlayStyle = com.reelfocus.app.models.OverlayStyle.SHRINKING_CIRCLE
            updateStyleButtons()
            refreshOverlayPreview()
            autoSave()
        }

        hapticSwitch.setOnCheckedChangeListener { _, _ -> autoSave() }
    }
    
    private fun updatePositionButtons() {
        val p = selectedOverlayPosition
        positionTopLeftButton.alpha    = if (p == OverlayPosition.TOP_LEFT)     1.0f else 0.5f
        positionTopButton.alpha        = if (p == OverlayPosition.TOP_RIGHT)    1.0f else 0.5f
        positionCenterButton.alpha     = if (p == OverlayPosition.CENTER_RIGHT) 1.0f else 0.5f
        positionBottomLeftButton.alpha = if (p == OverlayPosition.BOTTOM_LEFT)  1.0f else 0.5f
        positionBottomButton.alpha     = if (p == OverlayPosition.BOTTOM_RIGHT) 1.0f else 0.5f
    }
    
    private fun updateTextSizeButtons() {
        sizeSmallButton.alpha = if (selectedTextSize == TextSize.SMALL) 1.0f else 0.5f
        sizeMediumButton.alpha = if (selectedTextSize == TextSize.MEDIUM) 1.0f else 0.5f
        sizeLargeButton.alpha = if (selectedTextSize == TextSize.LARGE) 1.0f else 0.5f
    }

    private fun updateStyleButtons() {
        val s = selectedOverlayStyle
        styleTextButton.alpha   = if (s == com.reelfocus.app.models.OverlayStyle.TEXT)             1.0f else 0.5f
        styleDonutButton.alpha  = if (s == com.reelfocus.app.models.OverlayStyle.DONUT)            1.0f else 0.5f
        styleCircleButton.alpha = if (s == com.reelfocus.app.models.OverlayStyle.SHRINKING_CIRCLE) 1.0f else 0.5f
    }

    /** Rebuilds the embedded OverlayView preview whenever style or text-size changes. */
    private fun refreshOverlayPreview() {
        overlayPreviewContainer.removeAllViews()
        val preview = com.reelfocus.app.ui.OverlayView(
            this,
            selectedTextSize,
            selectedOverlayStyle
        )
        // Seed with a representative 60% progress: 12 min of 20 elapsed, session 1 of 5
        preview.updateState(
            secondsElapsed  = 720,
            limitValue      = 20,
            currentSession  = 1,
            maxSessions     = 5
        )
        val lp = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        )
        overlayPreviewContainer.addView(preview, lp)
    }

    private fun updateLimitValueSeekBar() {
        // TIME: 5-60 minutes (step by 5)
        limitValueSeekBar.valueFrom = 5f
        limitValueSeekBar.valueTo = 60f
        limitValueSeekBar.stepSize = 5f
        updateLimitValueText()
    }

    private fun updateLimitValueText() {
        val value = limitValueSeekBar.value.toInt()
        limitValueText.text = "$value min"
    }

    private fun loadCurrentSettings() {
        // C-001: Max Sessions
        maxSessionsSeekBar.value = config.maxSessionsDaily.toFloat()
        maxSessionsValue.text = "${config.maxSessionsDaily}"

        // C-003: Session Gap
        sessionGapSeekBar.value = config.sessionResetGapMinutes.toFloat()
        sessionGapValue.text = "${config.sessionResetGapMinutes} min"

        // C-004: Limit Value (always Time in minutes)
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

        // C-008: Overlay Style
        selectedOverlayStyle = config.overlayStyle
        updateStyleButtons()
        refreshOverlayPreview()

        // Haptic
        hapticSwitch.isChecked = config.hapticEnabled
    }

    private fun updateLimitValueDisplay() {
        updateLimitValueSeekBar()
    }
    
    private fun autoSave() {
        // Build updated config
        val updatedConfig = config.copy(
            maxSessionsDaily = maxSessionsSeekBar.value.toInt(),
            sessionResetGapMinutes = sessionGapSeekBar.value.toInt(),
            defaultLimitValue = limitValueSeekBar.value.toInt(),
            overlayPosition = selectedOverlayPosition,
            overlayTextSize = selectedTextSize,
            overlayStyle = selectedOverlayStyle,
            hapticEnabled = hapticSwitch.isChecked
        )

        prefsHelper.saveConfig(updatedConfig)
        config = updatedConfig // Update local copy
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("SettingsActivity", "onResume - reloading config")
        // Reload config in case apps were updated from AppSelectionActivity
        config = prefsHelper.loadConfig()
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("SettingsActivity", "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("SettingsActivity", "onDestroy")
    }
}
