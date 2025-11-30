package com.reelfocus.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reelfocus.app.models.AppConfig
import com.reelfocus.app.models.LimitType
import com.reelfocus.app.models.MonitoredApp
import com.reelfocus.app.utils.PreferencesHelper
import kotlinx.coroutines.*

// C-002: Target App Selection with installed apps
class AppSelectionActivity : AppCompatActivity() {

    private lateinit var prefsHelper: PreferencesHelper
    private lateinit var config: AppConfig
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var saveButton: Button
    private lateinit var adapter: AppListAdapter
    private val selectedApps = mutableListOf<InstalledAppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        prefsHelper = PreferencesHelper(this)

        recyclerView = findViewById(R.id.apps_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        saveButton = findViewById(R.id.save_apps_button)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        saveButton.setOnClickListener {
            saveSelectedApps()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload config every time screen is shown
        config = prefsHelper.loadConfig()
        loadInstalledApps()
    }

    /**
     * SIMPLIFIED filtering logic - filter by app display name.
     * Much simpler and more reliable than complex package-based logic.
     * 
     * Shows apps with names containing popular keywords OR apps with launcher.
     */
    private fun isMonitorableApp(appInfo: ApplicationInfo, packageManager: PackageManager): Boolean {
        val packageName = appInfo.packageName
        
        // Exclude our own app
        if (packageName == this.packageName) return false
        
        // Exclude providers (no launcher, just data providers)
        if (packageName.contains("provider") || packageName.contains(".auto_generated_rro")) {
            return false
        }
        
        // Get app display name
        val appName = try {
            appInfo.loadLabel(packageManager).toString().lowercase()
        } catch (e: Exception) {
            packageName.lowercase()
        }
        
        // Simple keyword matching on app name
        val socialKeywords = listOf(
            "youtube", "instagram", "facebook", "twitter", "tiktok", 
            "snapchat", "whatsapp", "telegram", "discord", "reddit",
            "netflix", "twitch", "spotify", "chrome", "firefox",
            "messenger", "linkedin", "pinterest", "tumblr",
            "roblox", "pubg", "fortnite", "minecraft",
            "gmail", "maps", "photos", "calendar", "clock",
            "camera", "gallery", "music", "video", "game"
        )
        
        // Check if app name contains any social keyword
        if (socialKeywords.any { appName.contains(it) }) {
            android.util.Log.d("AppSelection", "✓ Matched by name: $appName ($packageName)")
            return true
        }
        
        // Also show any app with launcher activity (user-facing apps)
        val hasLauncherActivity = packageManager.getLaunchIntentForPackage(packageName) != null
        if (hasLauncherActivity) {
            // Exclude system utilities by name
            val excludedNames = listOf("settings", "setup", "launcher", "keyboard", "wallpaper")
            if (excludedNames.any { appName.contains(it) }) {
                return false
            }
            
            android.util.Log.d("AppSelection", "✓ Has launcher: $appName ($packageName)")
            return true
        }
        
        return false
    }

    private fun loadInstalledApps() {
        android.util.Log.d("AppSelection", "loadInstalledApps called")
        android.util.Log.d("AppSelection", "Current config has ${config.monitoredApps.size} monitored apps")
        config.monitoredApps.forEach {
            android.util.Log.d("AppSelection", "  Monitored: ${it.appName} (${it.packageName}) enabled=${it.isEnabled}")
        }
        
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val packageManager = packageManager
            // OPTIMIZATION: Filter and collect app info WITHOUT loading icons first
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence() // Use sequence for lazy evaluation
                .filter { appInfo ->
                    isMonitorableApp(appInfo, packageManager)
                }
                .map { appInfo ->
                    val pkgName = appInfo.packageName
                    val isCurrentlySelected = config.monitoredApps.any { 
                        it.packageName == pkgName && it.isEnabled 
                    }
                    android.util.Log.d("AppSelection", "App found: ${appInfo.loadLabel(packageManager)} ($pkgName) - isSelected=$isCurrentlySelected")
                    InstalledAppInfo(
                        packageName = pkgName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        icon = null, // Load icon lazily in adapter
                        isSelected = isCurrentlySelected
                    )
                }
                .sortedBy { it.appName }
                .toList()
            
            android.util.Log.d("AppSelection", "Total filtered apps: ${installedApps.size}")

            withContext(Dispatchers.Main) {
                selectedApps.clear()
                selectedApps.addAll(installedApps.filter { it.isSelected })
                
                adapter = AppListAdapter(installedApps) { app, isChecked ->
                    if (isChecked) {
                        if (!selectedApps.contains(app)) {
                            selectedApps.add(app)
                        }
                    } else {
                        selectedApps.remove(app)
                    }
                    updateSaveButton()
                }
                
                recyclerView.adapter = adapter
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                updateSaveButton()
            }
        }
    }

    private fun updateSaveButton() {
        saveButton.text = "Save ${selectedApps.size} Apps"
        saveButton.isEnabled = selectedApps.isNotEmpty()
    }

    private fun saveSelectedApps() {
        android.util.Log.d("AppSelection", "saveSelectedApps called - selectedApps.size=${selectedApps.size}")
        selectedApps.forEach {
            android.util.Log.d("AppSelection", "  - ${it.appName} (${it.packageName})")
        }
        
        // Convert to MonitoredApp list
        val monitoredApps = selectedApps.map { app ->
            // Check if app already has custom timer setting
            val existing = config.monitoredApps.find { it.packageName == app.packageName }
            MonitoredApp(
                packageName = app.packageName,
                appName = app.appName,
                isEnabled = true,
                customLimitValue = existing?.customLimitValue  // Preserve custom timer if set
            )
        }

        val updatedConfig = config.copy(monitoredApps = monitoredApps)
        prefsHelper.saveConfig(updatedConfig)
        
        android.util.Log.d("AppSelection", "Saved ${monitoredApps.size} monitored apps to config")

        Toast.makeText(this, "${selectedApps.size} apps configured", Toast.LENGTH_SHORT).show()
        finish()
    }

    // Data class for installed apps
    data class InstalledAppInfo(
        val packageName: String,
        val appName: String,
        var icon: Drawable?, // Nullable for lazy loading
        var isSelected: Boolean
    )

    // RecyclerView Adapter
    class AppListAdapter(
        private val apps: List<InstalledAppInfo>,
        private val onAppChecked: (InstalledAppInfo, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

        class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_name)
            val packageName: TextView = view.findViewById(R.id.app_package)
            val checkbox: CheckBox = view.findViewById(R.id.app_checkbox)
            val configButton: ImageButton = view.findViewById(R.id.app_config_button)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_selection, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            
            // OPTIMIZATION: Lazy load icon on bind
            if (app.icon == null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val packageManager = holder.itemView.context.packageManager
                        val appIcon = packageManager.getApplicationIcon(app.packageName)
                        withContext(Dispatchers.Main) {
                            app.icon = appIcon
                            holder.icon.setImageDrawable(appIcon)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AppSelection", "Failed to load icon for ${app.packageName}", e)
                        withContext(Dispatchers.Main) {
                            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
                        }
                    }
                }
            } else {
                holder.icon.setImageDrawable(app.icon)
            }
            
            holder.name.text = app.appName
            holder.packageName.text = app.packageName
            
            // CRITICAL: Remove listener before setting checked state to prevent false triggers
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected

            // Set listener after state is restored
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onAppChecked(app, isChecked)
            }

            holder.itemView.setOnClickListener {
                // Toggle checkbox programmatically
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }

            // C-005: App-specific overrides (placeholder for future)
            holder.configButton.setOnClickListener {
                showAppConfigDialog(holder.itemView.context, app)
            }
        }

        override fun getItemCount() = apps.size

        private fun showAppConfigDialog(context: android.content.Context, app: InstalledAppInfo) {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_config, null)
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .create()

            // Get current monitored app config if exists
            val prefsHelper = PreferencesHelper(context)
            val config = prefsHelper.loadConfig()
            val existingApp = config.monitoredApps.find { it.packageName == app.packageName }

            // UI elements
            val appNameText = dialogView.findViewById<TextView>(R.id.dialog_app_name)
            val useDefaultCheckbox = dialogView.findViewById<CheckBox>(R.id.use_default_checkbox)
            val customSection = dialogView.findViewById<LinearLayout>(R.id.custom_limit_section)
            val limitTypeSpinner = dialogView.findViewById<Spinner>(R.id.limit_type_spinner)
            val limitValueLabel = dialogView.findViewById<TextView>(R.id.limit_value_label)
            val limitValueSeekbar = dialogView.findViewById<SeekBar>(R.id.limit_value_seekbar)
            val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
            val saveButton = dialogView.findViewById<Button>(R.id.save_button)

            appNameText.text = app.appName

            // Initialize values
            var currentLimitValue = existingApp?.customLimitValue ?: config.defaultLimitValue
            val useDefault = existingApp?.customLimitValue == null

            useDefaultCheckbox.isChecked = useDefault
            customSection.visibility = if (useDefault) View.GONE else View.VISIBLE
            
            // Note: Limit type is ALWAYS global (from settings), only timer value can be customized per-app
            limitTypeSpinner.visibility = View.GONE  // Hide limit type - always use global

            // Update seekbar based on global limit type
            fun updateSeekbar() {
                if (config.defaultLimitType == LimitType.TIME) {
                    limitValueSeekbar.max = 55 // 5 to 60 minutes
                    limitValueSeekbar.progress = (currentLimitValue - 5).coerceIn(0, 55)
                    limitValueLabel.text = "$currentLimitValue minutes"
                } else {
                    limitValueSeekbar.max = 95 // 5 to 100 reels
                    limitValueSeekbar.progress = (currentLimitValue - 5).coerceIn(0, 95)
                    limitValueLabel.text = "$currentLimitValue reels"
                }
            }

            updateSeekbar()

            // Checkbox listener
            useDefaultCheckbox.setOnCheckedChangeListener { _, isChecked ->
                customSection.visibility = if (isChecked) View.GONE else View.VISIBLE
            }

            // Seekbar listener
            limitValueSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentLimitValue = progress + 5
                    limitValueLabel.text = if (config.defaultLimitType == LimitType.TIME) {
                        "$currentLimitValue minutes"
                    } else {
                        "$currentLimitValue reels"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Cancel button
            cancelButton.setOnClickListener {
                dialog.dismiss()
            }

            // Save button
            saveButton.setOnClickListener {
                // Update monitored apps with custom timer config (if any)
                val updatedApps = config.monitoredApps.toMutableList()
                val existingIndex = updatedApps.indexOfFirst { it.packageName == app.packageName }

                val updatedApp = if (useDefaultCheckbox.isChecked) {
                    // Use default timer - set custom value to null
                    MonitoredApp(
                        packageName = app.packageName,
                        appName = app.appName,
                        isEnabled = app.isSelected,
                        customLimitValue = null
                    )
                } else {
                    // Use custom timer value (type is always global)
                    MonitoredApp(
                        packageName = app.packageName,
                        appName = app.appName,
                        isEnabled = app.isSelected,
                        customLimitValue = currentLimitValue
                    )
                }

                if (existingIndex >= 0) {
                    updatedApps[existingIndex] = updatedApp
                } else if (app.isSelected) {
                    updatedApps.add(updatedApp)
                }

                // Save updated config
                val updatedConfig = config.copy(monitoredApps = updatedApps)
                prefsHelper.saveConfig(updatedConfig)

                Toast.makeText(
                    context,
                    "Custom limit saved for ${app.appName}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
