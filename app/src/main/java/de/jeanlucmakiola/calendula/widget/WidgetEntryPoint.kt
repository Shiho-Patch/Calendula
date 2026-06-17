package de.jeanlucmakiola.calendula.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs

/**
 * Hilt bridge for the Glance widgets. A [androidx.glance.appwidget.GlanceAppWidget]
 * is instantiated by the framework, not by Hilt, so it can't take constructor
 * injection. We instead reach the singleton graph through this entry point and
 * read the same [CalendarRepository] / [SettingsPrefs] the app uses — so widget
 * data (hidden-calendar filtering, week-start preference, …) matches the app
 * one-to-one.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun calendarRepository(): CalendarRepository
    fun settingsPrefs(): SettingsPrefs
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
