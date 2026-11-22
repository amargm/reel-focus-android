package com.reelfocus.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.reelfocus.app.models.MonitoredApp
import com.reelfocus.app.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val suggested: Boolean
)

private val suggestedAppNames = listOf(
    "instagram", "youtube", "facebook", "snapchat", "tiktok", "twitter", " x ",
    "reddit", "sharechat", "moj", "josh", "threads", "pinterest", "whatsapp",
    "telegram", "twitch", "netflix", "linkedin", "messenger"
)
private val excludedAppTerms = listOf(
    "launcher", "package installer", "permission controller", "system ui",
    "settings", "setup wizard", "device health services", "print spooler", "installer", "calculator", "file manager", "clock", "camera", "gallery", "contacts", "wallpaper", "system update", "backup"
)

class AppSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = PreferencesHelper(this)
        setContent {
            AppSelectionScreen(
                initialSelection = prefs.loadConfig().monitoredApps.filter { it.isEnabled }.map { it.packageName },
                onSave = { selected ->
                    val current = prefs.loadConfig()
                    val installed = packageManager
                    val updated = selected.mapNotNull { packageName ->
                        val existing = current.monitoredApps.firstOrNull { it.packageName == packageName }
                        val label = try {
                            installed.getApplicationLabel(installed.getApplicationInfo(packageName, 0)).toString()
                        } catch (_: Exception) { existing?.appName ?: return@mapNotNull null }
                        MonitoredApp(packageName, label, true, existing?.customLimitValue)
                    }
                    prefs.saveConfig(current.copy(monitoredApps = updated))
                    finish()
                },
                onBack = { finish() }
            )
        }
    }
}

@Composable
private fun AppSelectionScreen(
    initialSelection: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val page = if (isDark) Color(0xFF050505) else Color(0xFFF4F4F2)
    val surface = if (isDark) Color(0xFF1B1D17) else Color.White
    val foreground = if (isDark) Color.White else Color(0xFF111111)
    val muted = if (isDark) Color(0xFFB3B4AD) else Color(0xFF777873)
    val lime = Color(0xFFC6E36B)
    val selectedPackages = remember { mutableStateListOf<String>().apply { addAll(initialSelection) } }
    var searchInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var bannerVisible by remember {
        mutableStateOf(!context.getSharedPreferences("reel_focus_prefs", 0).getBoolean("app_selection_banner_dismissed", false))
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val iconCache = remember { LruCache<String, Bitmap>(96) }
    val iconSizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val allInstalledApps by produceState<List<InstalledApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.Default) {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { info -> packageManager.getLaunchIntentForPackage(info.packageName) != null }
                .mapNotNull { info ->
                    val label = try { info.loadLabel(packageManager).toString().trim() } catch (_: Exception) { "" }
                    if (label.isBlank()) return@mapNotNull null
                    val normalized = label.lowercase()
                    val packageNormalized = info.packageName.lowercase()
                    if (excludedAppTerms.any { normalized.contains(it) || packageNormalized.contains(it.replace(" ", "")) }) return@mapNotNull null
                    val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    val namedSuggestion = suggestedAppNames.any { term -> if (term.trim() == "x") normalized == "x" || packageNormalized.contains("twitter") else normalized.contains(term.trim()) || packageNormalized.contains(term.trim()) }
                    val categorySuggestion = android.os.Build.VERSION.SDK_INT >= 26 &&
                        (info.category == ApplicationInfo.CATEGORY_SOCIAL || info.category == ApplicationInfo.CATEGORY_VIDEO)
                    InstalledApp(info.packageName, label, system, namedSuggestion || categorySuggestion)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
                .toList()
        }
    }
    LaunchedEffect(searchInput) {
        delay(250)
        searchQuery = searchInput.trim()
    }

    val visibleApps = remember(allInstalledApps, showSystemApps) {
        allInstalledApps.filter { showSystemApps || !it.isSystem }
    }
    val duplicateLabels = remember(allInstalledApps) {
        allInstalledApps.groupBy { it.appName.lowercase() }.filterValues { it.size > 1 }
            .flatMap { (_, group) ->
                group.sortedBy { it.packageName }.mapIndexed { index, app ->
                    app.packageName to if (app.packageName.contains("work", ignoreCase = true)) "Work profile" else "Variant ${index + 1}"
                }
            }.toMap()
    }
    val queryApps = remember(visibleApps, searchQuery) {
        if (searchQuery.isBlank()) visibleApps
        else visibleApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
    }
    val selectedApps = remember(allInstalledApps, searchQuery, selectedPackages.toList()) {
        allInstalledApps.filter { it.packageName in selectedPackages && (searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true)) }.sortedBy { it.appName.lowercase() }
    }
    val suggestedApps = remember(queryApps, selectedPackages.toList()) {
        queryApps.filter { it.suggested && it.packageName !in selectedPackages }.sortedBy { it.appName.lowercase() }
    }
    val suggestedPackages = remember(suggestedApps) { suggestedApps.map { it.packageName }.toSet() }
    val remainingApps = remember(queryApps, selectedPackages.toList(), suggestedPackages) {
        queryApps.filter { it.packageName !in selectedPackages && it.packageName !in suggestedPackages }
    }
    val saveableSelection = remember(allInstalledApps, selectedPackages.toList()) {
        val installedPackages = allInstalledApps.map { it.packageName }.toSet()
        selectedPackages.filter { it in installedPackages }
    }

    fun toggleApp(app: InstalledApp) {
        if (app.packageName in selectedPackages) {
            selectedPackages.remove(app.packageName)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Removed ${app.appName}",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )
                if (result == SnackbarResult.ActionPerformed && app.packageName !in selectedPackages) {
                    selectedPackages.add(app.packageName)
                }
            }
            scope.launch {
                delay(4000)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
        } else {
            selectedPackages.add(app.packageName)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    Box(Modifier.fillMaxSize().background(page)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp)
                    .clip(RoundedCornerShape(30.dp)).background(surface).heightIn(min = 56.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = foreground)
                }
                Text("Choose apps", Modifier.weight(1f), fontSize = 17.sp, color = foreground, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
            Text(
                "Pick the apps where you want more breathing room.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 12.dp),
                fontSize = 13.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (bannerVisible) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color(0xFF2A2D23) else Color(0xFFEEF4D9))
                        .padding(start = 16.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "We picked likely apps for you. Keep the ones you want.",
                        Modifier.weight(1f), fontSize = 12.sp,
                        color = if (isDark) Color(0xFFE9EBD9) else Color(0xFF303522)
                    )
                    IconButton(
                        onClick = {
                            context.getSharedPreferences("reel_focus_prefs", 0).edit()
                                .putBoolean("app_selection_banner_dismissed", true).apply()
                            bannerVisible = false
                        },
                        modifier = Modifier.size(48.dp)
                    ) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss suggestion banner", tint = foreground) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp)
                    .clip(RoundedCornerShape(16.dp)).background(surface)
                    .border(1.dp, if (isDark) Color(0xFF3A3E30) else Color(0xFFE5E6E0), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, "Search apps", tint = muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchInput, onValueChange = { searchInput = it },
                    modifier = Modifier.weight(1f), singleLine = true,
                    textStyle = TextStyle(color = foreground, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (searchInput.isEmpty()) Text("Search apps", color = muted, fontSize = 14.sp)
                        inner()
                    }
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show system apps", Modifier.weight(1f), fontSize = 13.sp, color = foreground)
                Switch(modifier = Modifier.semantics { contentDescription = "Show system apps" }, checked = showSystemApps, onCheckedChange = { showSystemApps = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF202512), checkedTrackColor = lime, uncheckedThumbColor = muted, uncheckedTrackColor = if (isDark) Color(0xFF3A3E30) else Color(0xFFD7D9CF)))
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "section-selected") { AppSectionHeader("Selected", selectedPackages.size, foreground, muted, lime) }
                if (selectedApps.isEmpty()) {
                    item(key = "selected-empty") {
                        Text("Apps you choose will appear here.", fontSize = 12.sp, color = muted, modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    items(selectedApps, key = { it.packageName }) { app ->
                        AppSelectionRow(app, true, isDark, surface, foreground, muted, lime, iconCache, iconSizePx, duplicateLabels[app.packageName]) { toggleApp(app) }
                    }
                }
                item(key = "section-suggested") { AppSectionHeader("Suggested for you", suggestedApps.size, foreground, muted, lime) }
                if (suggestedApps.isEmpty()) {
                    item(key = "suggested-empty") {
                        Text("No suggested apps match this search.", fontSize = 12.sp, color = muted, modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    items(suggestedApps, key = { it.packageName }) { app ->
                        AppSelectionRow(app, false, isDark, surface, foreground, muted, lime, iconCache, iconSizePx, duplicateLabels[app.packageName]) { toggleApp(app) }
                    }
                }
                item(key = "section-all") { AppSectionHeader("All apps", remainingApps.size, foreground, muted, lime) }
                items(remainingApps, key = { it.packageName }) { app ->
                    AppSelectionRow(app, false, isDark, surface, foreground, muted, lime, iconCache, iconSizePx, duplicateLabels[app.packageName]) { toggleApp(app) }
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, page.copy(alpha = .96f), page)))
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp)
        ) {
            if (selectedPackages.isNotEmpty()) {
                Button(
                    onClick = { onSave(saveableSelection) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = lime, contentColor = Color(0xFF202512))
                ) {
                    Text("Save ${selectedPackages.size} ${if (selectedPackages.size == 1) "app" else "apps"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                OutlinedButton(
                    onClick = {}, enabled = false,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF55584C) else Color(0xFFB9BAAF)),
                    colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = muted)
                ) { Text("Select at least 1 app", fontSize = 14.sp) }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp)
        )
    }
}

@Composable
private fun AppSectionHeader(title: String, count: Int, foreground: Color, muted: Color, lime: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = foreground)
        Surface(shape = CircleShape, color = lime.copy(alpha = .16f)) {
            Text(count.toString(), modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), fontSize = 11.sp, color = if (foreground == Color.White) lime else Color(0xFF526B20))
        }
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    selected: Boolean,
    isDark: Boolean,
    surface: Color,
    foreground: Color,
    muted: Color,
    lime: Color,
    iconCache: LruCache<String, Bitmap>,
    iconSizePx: Int,
    disambiguator: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = iconCache.get(app.packageName), app.packageName, iconSizePx) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                try {
                    context.packageManager.getApplicationIcon(app.packageName)
                        .toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
                        .also { iconCache.put(app.packageName, it) }
                } catch (_: Exception) { null }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp))
            .background(surface)
            .border(1.dp, if (selected) lime.copy(alpha = .65f) else if (isDark) Color(0xFF303329) else Color(0xFFE5E6E0), RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { contentDescription = app.appName; role = Role.Checkbox; stateDescription = if (selected) "Selected" else "Not selected" }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) Color(0xFF303329) else Color(0xFFECEDE8)))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.appName, fontSize = 14.sp, color = foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (disambiguator != null) Text(disambiguator, fontSize = 11.sp, color = muted, maxLines = 1)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (selected) lime else Color.Transparent)
                .border(1.5.dp, if (selected) lime else muted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, "Selected", tint = Color(0xFF202512), modifier = Modifier.size(17.dp))
        }
    }
}
