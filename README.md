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
- **SYSTEM_ALERT_WINDOW**: To display overlay over other apps
- **FOREGROUND_SERVICE**: To keep the timer running in the background
- **POST_NOTIFICATIONS**: For Android 13+ notification support

## Usage

1. **Grant Overlay Permission**: 
   - Tap "Grant Overlay Permission" button
   - Enable permission in system settings
   
2. **Start Overlay**:
   - Tap "Start Overlay" button
   - Timer overlay will appear in the top-right corner
   - Runs as a foreground service with notification

3. **Stop Overlay**:
   - Tap "Stop Overlay" in the app
   - Or swipe away the notification

## Architecture

```
com.reelfocus.app/
├── MainActivity.kt              # Main UI and permission handling
├── OverlayService.kt           # Foreground service managing overlay
├── models/                     # Data models
│   ├── LimitType.kt
│   ├── OverlayPosition.kt
│   ├── SessionState.kt
│   └── TextSize.kt
└── ui/
    └── OverlayView.kt          # Custom view rendering the overlay
```

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

- [ ] Settings screen for user customization
- [ ] App usage detection (requires AccessibilityService)
- [ ] Persistent storage using Room/DataStore
- [ ] Lock screen when time expires
- [ ] Statistics and usage reports
- [ ] Multiple app profiles (Instagram, TikTok, etc.)

## License

This project is licensed under the MIT License - see the LICENSE file for details.