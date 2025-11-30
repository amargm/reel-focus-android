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
        config = prefsHelper.loadConfig()

        recyclerView = findViewById(R.id.apps_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        saveButton = findViewById(R.id.save_apps_button)

        recyclerView.layoutManager = LinearLayoutManager(this)
        
        saveButton.setOnClickListener {
            saveSelectedApps()
        }

        loadInstalledApps()
    }

    /**
     * Smart filtering logic to show relevant monitorable apps.
     * Google-level product thinking: Show apps users actually want to monitor.
     * 
     * Strategy:
     * 1. Social media & entertainment apps (even if system apps)
     * 2. Apps with a launcher activity (user-facing apps)
     * 3. Non-system apps
     * 4. Exclude: Settings, launchers, keyboards, system services, our own app
     */
    private fun isMonitorableApp(appInfo: ApplicationInfo, packageManager: PackageManager): Boolean {
        val packageName = appInfo.packageName
        
        // Exclude our own app
        if (packageName == this.packageName) return false
        
        // Priority 1: Popular social media & entertainment apps (even if system)
        val popularSocialApps = setOf(
            // Social Media - Short Form Video
            "com.zhiliaoapp.musically",           // TikTok
            "com.instagram.android",              // Instagram
            "com.snapchat.android",               // Snapchat
            
            // Video & Entertainment
            "com.google.android.youtube",         // YouTube
            "com.netflix.mediaclient",            // Netflix
            "tv.twitch.android.app",              // Twitch
            "com.amazon.avod.thirdpartyclient",   // Prime Video
            "com.hulu.plus",                      // Hulu
            "com.disney.disneyplus",              // Disney+
            
            // Social Media - General
            "com.facebook.katana",                // Facebook
            "com.facebook.orca",                  // Messenger
            "com.twitter.android",                // Twitter/X
            "com.reddit.frontpage",               // Reddit
            "com.pinterest",                      // Pinterest
            "com.tumblr",                         // Tumblr
            "com.linkedin.android",               // LinkedIn
            
            // Messaging
            "com.whatsapp",                       // WhatsApp
            "org.telegram.messenger",             // Telegram
            "com.discord",                        // Discord
            "com.viber.voip",                     // Viber
            
            // Gaming
            "com.roblox.client",                  // Roblox
            "com.pubg.imobile",                   // PUBG
            "com.ea.gp.fifamobile",              // FIFA Mobile
            "com.supercell.clashofclans",        // Clash of Clans
            "com.kiloo.subwaysurf",              // Subway Surfers
            
            // Shopping & Entertainment
            "com.amazon.mShop.android.shopping",  // Amazon
            "com.spotify.music",                  // Spotify
            "com.pandora.android"                 // Pandora
        )
        
        if (packageName in popularSocialApps) return true
        
        // Exclude common system categories
        val excludedCategories = setOf(
            "android",                            // Android system
            "com.android.",                       // Android system components
            "com.google.android.gms",            // Google Play Services
            "com.google.android.gsf",            // Google Services Framework
            "com.google.android.inputmethod",    // Keyboards
            "com.google.android.packageinstaller",
            "com.samsung.android",               // Samsung system
            "com.sec.android",                   // Samsung security
            "com.miui",                          // Xiaomi MIUI
            "com.huawei",                        // Huawei system
            "com.oppo",                          // Oppo system
            "com.vivo",                          // Vivo system
            "com.coloros"                        // ColorOS system
        )
        
        // Check if package starts with excluded category
        if (excludedCategories.any { packageName.startsWith(it) }) {
            // Exception: Some Google apps are monitorable
            val allowedGoogleApps = setOf(
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
                "com.google.android.apps.photos",
                "com.google.android.videos"
            )
            if (packageName !in allowedGoogleApps) return false
        }
        
        // Priority 2: Apps with launcher activity (user launches them)
        val hasLauncherActivity = packageManager.getLaunchIntentForPackage(packageName) != null
        if (hasLauncherActivity) {
            // Exclude system launchers, settings, and utilities
            val excludedTypes = setOf(
                "launcher", "settings", "systemui", "setup", 
                "keyboard", "ime", "wallpaper", "theme"
            )
            val lowerPackage = packageName.lowercase()
            if (excludedTypes.any { lowerPackage.contains(it) }) return false
            
            return true
        }
        
        // Priority 3: Non-system apps (user installed)
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return !isSystemApp
    }

    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { appInfo ->
                    isMonitorableApp(appInfo, packageManager)
                }
                .map { appInfo ->
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(packageManager).toString(),
                        icon = appInfo.loadIcon(packageManager),
                        isSelected = config.monitoredApps.any { 
                            it.packageName == appInfo.packageName && it.isEnabled 
                        }
                    )
                }
                .sortedBy { it.appName }

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

        Toast.makeText(this, "${selectedApps.size} apps configured", Toast.LENGTH_SHORT).show()
        finish()
    }

    // Data class for installed apps
    data class InstalledAppInfo(
        val packageName: String,
        val appName: String,
        val icon: Drawable,
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
            
            holder.icon.setImageDrawable(app.icon)
            holder.name.text = app.appName
            holder.packageName.text = app.packageName
            holder.checkbox.isChecked = app.isSelected

            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onAppChecked(app, isChecked)
            }

            holder.itemView.setOnClickListener {
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
