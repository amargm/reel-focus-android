# Keep model fields used by Gson-backed preference/history serialization.
-keepattributes Signature,*Annotation*
-keep class com.reelfocus.app.models.** { *; }
-keepclassmembers class com.reelfocus.app.models.** { <fields>; }

# Android-instantiated components and the accessibility service.
-keep class com.reelfocus.app.OverlayService { *; }
-keep class com.reelfocus.app.ReelDetectionAccessibilityService { *; }
