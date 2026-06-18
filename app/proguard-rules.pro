# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *

# Compose Compiler may keep its own; defaults are fine
-dontwarn org.jetbrains.annotations.**

# Room database implementations (pulled in transitively via
# androidx.glance:glance-appwidget → androidx.work → androidx.room).
# The widgets rely on Glance, whose WorkManager backend stores state in a Room
# database. Under R8 full mode (AGP 9 default) the generated *_Impl subclasses
# of RoomDatabase lose their usable no-arg constructor / are marked abstract,
# so Room's reflective instantiation throws InstantiationException and the app
# crashes at startup with "Failed to create an instance of ...WorkDatabase".
# Keep the generated Room database implementations fully intact.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
