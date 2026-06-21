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

# WorkManager instantiates an InputMerger reflectively (Class.newInstance) from
# the fully-qualified class name persisted in the WorkSpec, so the class must
# keep both its name and a no-arg constructor. Glance renders every widget
# through a WorkManager worker (androidx.glance.session.SessionWorker) whose
# default merger is androidx.work.OverwritingInputMerger. Under R8 full mode
# (AGP 9 default) that unused no-arg constructor was stripped, so WorkManager
# threw "OverwritingInputMerger has no zero argument constructor", the
# SessionWorker never ran, and widgets were stuck on their loading layout
# (a blank spinner) in release builds. Keep every InputMerger's name + ctor.
-keep class * extends androidx.work.InputMerger { <init>(...); }
