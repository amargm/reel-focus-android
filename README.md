# Reel Focus - Android App

A native Android application that helps users manage their social media consumption with a persistent overlay timer.

## Features

- **Floating Overlay Timer**: Displays a countdown timer over any app
- **Session Management**: Track multiple daily sessions (e.g., Session 2/5)
- **Time & Count Modes**: 
  - TIME mode: Countdown in minutes:seconds
  - COUNT mode: Shows remaining reels
- **Visual Warnings**: Red pulsing overlay when running low on time/count
- **Material Design 3**: Modern UI with dark theme

## Setup

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK API 34
- Kotlin 1.9.0+
- Minimum Android version: 6.0 (API 23)

### Building the App

1. Open the project in Android Studio
2. Sync Gradle files
3. Build the project: `Build > Make Project`
4. Run on device or emulator

### Permissions

The app requires the following permissions:

#### Required Permissions
- **SYSTEM_ALERT_WINDOW**: To display overlay over other apps
- **FOREGROUND_SERVICE**: To keep the timer running in the background
- **POST_NOTIFICATIONS**: For Android 13+ notification support
- **PACKAGE_USAGE_STATS**: To detect which app is in foreground (dangerous-level permission)

#### Android 11+ Package Visibility
For Android 11 and later, the app includes `<queries>` element in AndroidManifest.xml to query installed launcher apps. This allows the app to display all user-installed apps in the app selection screen.

#### UsageStats Permission (Critical)
The app uses UsageStats API to detect when monitored apps are in the foreground. This permission:
- **Must be granted manually** through Android Settings → Apps → Special app access → Usage access
- Cannot be granted programmatically (security restriction)
- The app will guide you to the correct Settings screen
- **Verification**: Use AppOpsManager to check if permission mode is MODE_ALLOWED
- **Known Issue**: On some emulators, UsageStats API may have intermittent reliability issues. If overlay behavior is inconsistent, test on a physical device.

## Usage

1. **Grant Overlay Permission**: 
   - Tap "Grant Overlay Permission" button
   - Enable permission in system settings
   
2. **Grant UsageStats Permission**:
   - Tap "Grant Usage Stats Permission" button
   - Navigate to "ReelFocus" in the Settings screen
   - Enable "Permit usage access"
   - Return to the app
   
3. **Select Apps to Monitor**:
   - Tap "Select Apps" button
   - Choose apps to monitor (e.g., YouTube, Instagram, TikTok)
   - Tap "Save" to confirm
   
4. **Start Monitoring**:
   - Tap "Start Monitoring" button
   - Timer overlay will appear when you open monitored apps
   - Overlay disappears when you leave the monitored app
   
5. **Stop Monitoring**:
   - Tap "Stop Monitoring" in the app
   - Or swipe away the notification

### Troubleshooting

**Overlay not appearing:**
- Verify all permissions are granted (check UI indicators)
- Ensure at least one app is selected for monitoring
- Try stopping and restarting monitoring
- On emulators: If UsageStats detection is unreliable, test on a physical device

**Permission shows as "Not Granted" after enabling:**
- Wait 1-2 seconds for permission sync
- Restart the app if permission status doesn't update
- Check Settings → Apps → ReelFocus → Permissions manually

## Architecture

```
com.reelfocus.app/
├── MainActivity.kt              # Main UI and permission handling
├── OverlayService.kt           # Foreground service managing overlay and monitoring
├── InterruptActivity.kt        # Full-screen interruption when time expires
├── models/                     # Data models
│   ├── AppConfig.kt           # App-specific configuration
│   ├── LimitType.kt           # TIME or COUNT mode
│   ├── MonitoredApp.kt        # Monitored app data
│   ├── OverlayPosition.kt     # Overlay position (TOP_RIGHT, etc.)
│   ├── SessionState.kt        # Session tracking state
│   └── TextSize.kt            # Text size options
├── ui/
│   └── OverlayView.kt          # Custom view rendering the overlay
└── utils/
    ├── AppUsageMonitor.kt      # UsageStats API wrapper for foreground detection
    └── PreferencesHelper.kt    # SharedPreferences management
```

### Key Components

**AppUsageMonitor.kt**:
- Wraps UsageStatsManager for foreground app detection
- Uses AppOpsManager for proper permission verification
- Implements caching (800ms validity) to reduce query frequency
- Query window: 10 seconds for reliable detection

**OverlayService.kt**:
- LifecycleService with coroutine-based monitoring loop
- 1-second detection interval
- Manages overlay lifecycle (show/hide based on active app)
- Session state management and timer updates

**DetectionManager.kt** (if implemented):
- Two-tier detection: AccessibilityService → UsageStats fallback
- Provides robust foreground app detection

## Customization

### Change Overlay Position
Edit `OverlayService.kt` line 58:
```kotlin
val position = OverlayPosition.TOP_RIGHT // Change to TOP_LEFT, BOTTOM_LEFT, etc.
```

### Adjust Timer Settings
Edit `SessionState.kt` default values:
```kotlin
var limitValue: Int = 20,        // Default 20 minutes
var maxSessions: Int = 5,        // Default 5 sessions
```

### Modify Colors
Edit `res/values/colors.xml` for Material Design 3 theming.

## Future Enhancements

- [ ] AccessibilityService for more reliable app detection
- [ ] Persistent storage using Room/DataStore
- [ ] Settings screen for user customization (timer values, colors, etc.)
- [ ] Lock screen when time expires
- [ ] Statistics and usage reports
- [ ] Multiple app profiles with different limits
- [ ] Weekly/monthly usage analytics
- [ ] Gamification (achievements, streaks)

## License

This project is licensed under the MIT License - see the LICENSE file for details.