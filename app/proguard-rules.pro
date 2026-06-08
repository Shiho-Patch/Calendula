# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Compose Compiler may keep its own; defaults are fine
-dontwarn org.jetbrains.annotations.**
