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

# Glance runs an @Composable's `actionRunCallback<T>()` by persisting the
# callback's fully-qualified class name into the click PendingIntent, then
# reflectively instantiating it (Class.forName(name).newInstance()) when the tap
# fires. Under R8 full mode (AGP 9 default) these ActionCallback classes — only
# ever referenced reflectively — get renamed or have their no-arg constructor
# stripped, so the lookup fails silently and the tap does nothing. In the month
# and agenda widgets that broke every run-callback control (the prev/next/today
# month arrows and the agenda refresh) in release builds while actionStartActivity
# taps, which ride a PendingIntent and need no reflection, kept working. Keep
# every ActionCallback's name and constructor intact.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(...); }

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

# Glance identifies a widget by its GlanceAppWidget subclass's *canonical name*:
# GlanceAppWidgetManager persists a providerName -> receivers map under that
# string, and `updateAll` looks the widget's app-widget ids up through it. Under
# R8 full mode (AGP 9 default) MonthWidget and AgendaWidget — same supertype,
# same overrides, no distinguishing members — were horizontally merged into one
# class, so both receivers registered under the *same* provider name and
# `AgendaWidget().updateAll()` resolved the month widget's id too, redrawing a
# placed month widget as the agenda one on the next data change (#89). Keeping
# the real names also survives app updates, which would otherwise renumber the
# obfuscated name and orphan the stored mapping.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Same hazard one level up: MonthWidgetReceiver and AgendaWidgetReceiver are
# also structurally identical (same supertype, same overrides, only a differing
# property initializer), and Glance's provider map is keyed off the receiver
# component too. Manifest-declared components are normally kept anyway, but this
# is cheap and makes the invariant explicit rather than incidental.
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
