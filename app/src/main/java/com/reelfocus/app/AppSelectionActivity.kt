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
import androidx.lifecycle.lifecycleScope
import com.reelfocus.app.models.AppConfig
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
    // Selected-apps section
    private lateinit var selectedSection: android.view.View
    private lateinit var selectedAppsRecycler: RecyclerView
    private lateinit var selectedCountBadge: android.widget.TextView
    private lateinit var selectedAdapter: SelectedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        prefsHelper = PreferencesHelper(this)

        recyclerView = findViewById(R.id.apps_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        saveButton = findViewById(R.id.save_apps_button)
        selectedSection = findViewById(R.id.selected_section)
        selectedAppsRecycler = findViewById(R.id.selected_apps_recycler)
        selectedCountBadge = findViewById(R.id.selected_count_badge)

        recyclerView.layoutManager = LinearLayoutManager(this)

        selectedAdapter = SelectedAppsAdapter(selectedApps) { removedApp ->
            // Deselect in main list and sync state
            if (::adapter.isInitialized) adapter.deselectApp(removedApp.packageName)
            selectedApps.remove(removedApp)
            selectedAdapter.notifyDataSetChanged()
            updateSaveButton()
        }
        selectedAppsRecycler.layoutManager = LinearLayoutManager(this)
        selectedAppsRecycler.adapter = selectedAdapter

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
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        // BUG-007 FIX: use lifecycleScope so the coroutine is cancelled when the activity is destroyed
        lifecycleScope.launch(Dispatchers.IO) {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { appInfo -> isMonitorableApp(appInfo, packageManager) }
                .map { appInfo ->
                    val pkgName = appInfo.packageName
                    val isCurrentlySelected = config.monitoredApps.any { 
                        it.packageName == pkgName && it.isEnabled 
                    }
                    InstalledAppInfo(
                        packageName = pkgName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        icon = null,
                        isSelected = isCurrentlySelected
                    )
                }
                .sortedBy { it.appName }
                .toMutableList()

            withContext(Dispatchers.Main) {
                selectedApps.clear()
                selectedApps.addAll(installedApps.filter { it.isSelected })
                
                adapter = AppListAdapter(installedApps, this@AppSelectionActivity.lifecycleScope) { app, isChecked ->
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
        // Refresh selected-apps section at top
        selectedCountBadge.text = selectedApps.size.toString()
        selectedSection.visibility = if (selectedApps.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        selectedAdapter.notifyDataSetChanged()
    }

    private fun saveSelectedApps() {
        val monitoredApps = selectedApps.map { app ->
            val existing = config.monitoredApps.find { it.packageName == app.packageName }
            MonitoredApp(
                packageName = app.packageName,
                appName = app.appName,
                isEnabled = true,
                customLimitValue = existing?.customLimitValue
            )
        }

        val updatedConfig = config.copy(monitoredApps = monitoredApps)
        prefsHelper.saveConfig(updatedConfig)

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
        private val apps: MutableList<InstalledAppInfo>,
        private val scope: kotlinx.coroutines.CoroutineScope,
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
            
            holder.name.text = app.appName
            holder.packageName.text = app.packageName
            
            // Show default icon immediately
            holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            
            // Load actual icon in background without blocking
            if (app.icon == null) {
                // BUG-007 FIX: use the lifecycle-bound scope passed from the activity
                scope.launch(Dispatchers.IO) {
                    try {
                        val packageManager = holder.itemView.context.packageManager
                        val appIcon = packageManager.getApplicationIcon(app.packageName)
                        app.icon = appIcon
                        withContext(Dispatchers.Main) {
                            // BUG-011 FIX: guard by package name, not display name
                            // (multiple apps can share the same display name)
                            val boundPosition = holder.bindingAdapterPosition
                            if (boundPosition != RecyclerView.NO_POSITION &&
                                apps.getOrNull(boundPosition)?.packageName == app.packageName
                            ) {
                                holder.icon.setImageDrawable(appIcon)
                            }
                        }
                    } catch (e: Exception) {
                        // Keep default icon on error
                    }
                }
            } else {
                holder.icon.setImageDrawable(app.icon)
            }
            
            // Remove listener before setting state
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = app.isSelected

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onAppChecked(app, isChecked)
            }

            holder.itemView.setOnClickListener {
                holder.checkbox.isChecked = !holder.checkbox.isChecked
            }

            holder.configButton.setOnClickListener {
                showAppConfigDialog(holder.itemView.context, app)
            }
        }

        override fun getItemCount() = apps.size

        /** Uncheck an app by package name (called when removed from the selected section). */
        fun deselectApp(packageName: String) {
            val index = apps.indexOfFirst { it.packageName == packageName }
            if (index != -1) {
                apps[index].isSelected = false
                notifyItemChanged(index)
            }
        }

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
            val limitValueLabel = dialogView.findViewById<TextView>(R.id.limit_value_label)
            val limitValueSeekbar = dialogView.findViewById<SeekBar>(R.id.limit_value_seekbar)
            val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
            val saveButton = dialogView.findViewById<Button>(R.id.save_button)

            appNameText.text = app.appName

            // PREMIUM GATE: per-app time limits are a future premium feature (isPerAppLimitEnabled == false by default)
            val premiumLockNotice = dialogView.findViewById<LinearLayout>(R.id.premium_lock_notice)
            if (!config.isPerAppLimitEnabled) {
                premiumLockNotice.visibility = View.VISIBLE
                useDefaultCheckbox.visibility = View.GONE
                customSection.visibility = View.GONE
                cancelButton.visibility = View.GONE
                saveButton.text = "Close"
                saveButton.setOnClickListener { dialog.dismiss() }
                dialog.show()
                return
            }
            premiumLockNotice.visibility = View.GONE

            // Initialize values
            var currentLimitValue = existingApp?.customLimitValue ?: config.defaultLimitValue
            val useDefault = existingApp?.customLimitValue == null

            useDefaultCheckbox.isChecked = useDefault
            customSection.visibility = if (useDefault) View.GONE else View.VISIBLE

            // Update seekbar (always Time in minutes)
            fun updateSeekbar() {
                limitValueSeekbar.max = 55 // 5 to 60 minutes
                limitValueSeekbar.progress = (currentLimitValue - 5).coerceIn(0, 55)
                limitValueLabel.text = "$currentLimitValue minutes"
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
                    limitValueLabel.text = "$currentLimitValue minutes"
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
                    "Per-app limit saved for ${app.appName}",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
            }

            dialog.show()
        }
    }

    // Adapter for the "Selected apps" section at the top
    inner class SelectedAppsAdapter(
        private val items: MutableList<InstalledAppInfo>,
        private val onRemove: (InstalledAppInfo) -> Unit
    ) : RecyclerView.Adapter<SelectedAppsAdapter.SelViewHolder>() {

        inner class SelViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val icon: android.widget.ImageView = view.findViewById(R.id.sel_app_icon)
            val name: android.widget.TextView = view.findViewById(R.id.sel_app_name)
            val remove: android.widget.ImageButton = view.findViewById(R.id.sel_app_remove)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_selected_app, parent, false)
            return SelViewHolder(view)
        }

        override fun onBindViewHolder(holder: SelViewHolder, position: Int) {
            val app = items[position]
            holder.name.text = app.appName
            if (app.icon != null) {
                holder.icon.setImageDrawable(app.icon)
            } else {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val icon = packageManager.getApplicationIcon(app.packageName)
                        app.icon = icon
                        withContext(Dispatchers.Main) {
                            val pos = holder.bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) holder.icon.setImageDrawable(icon)
                        }
                    } catch (_: Exception) {}
                }
            }
            holder.remove.setOnClickListener { onRemove(app) }
        }

        override fun getItemCount() = items.size
    }
}
