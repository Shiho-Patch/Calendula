package de.jeanlucmakiola.calendula.ui.settings

import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField

/**
 * Settings screen state (M4). Persisted preferences are instant to read, so
 * there is no Loading/Failure here — only a populated Success snapshot.
 * [dynamicColorAvailable] is false below Android 12, where the toggle is shown
 * disabled.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val dynamicColorAvailable: Boolean = true,
    val weekStart: WeekStartPref = WeekStartPref.AUTO,
    /** Optional event-form fields shown by default (rest behind "more fields"). */
    val defaultFormFields: Set<EventFormField> = SettingsPrefs.DEFAULT_FORM_FIELDS,
    /** Whether Calendula posts reminder notifications (v1.4). */
    val remindersEnabled: Boolean = true,
    /**
     * The default reminder lead time (minutes) prefilled on new timed events;
     * null = no default reminder. Per-calendar overrides take precedence.
     */
    val defaultReminderMinutes: Int? = null,
    /** The default reminder lead time prefilled on new all-day events; null = none. */
    val defaultAllDayReminderMinutes: Int? = null,
    /** Wall-clock time (minutes from midnight) all-day reminders fire at; default 09:00. */
    val allDayReminderTimeMinutes: Int = SettingsPrefs.DEFAULT_ALLDAY_REMINDER_TIME,
    /** How long the notification "Snooze" action defers a reminder; default 10 min. */
    val snoozeMinutes: Int = SettingsPrefs.DEFAULT_SNOOZE_MINUTES,
    /**
     * Per-calendar overrides of [defaultReminderMinutes] for timed events: a
     * calendar present in the map overrides the global default (null value = no
     * reminder); absent = inherit the global default.
     */
    val perCalendarReminderOverride: Map<Long, Int?> = emptyMap(),
    /** Per-calendar overrides of [defaultAllDayReminderMinutes] for all-day events. */
    val perCalendarAllDayReminderOverride: Map<Long, Int?> = emptyMap(),
    /** Writable calendars, shown as per-calendar reminder-override rows. */
    val writableCalendars: List<CalendarSource> = emptyList(),
    /**
     * Whether the event-colour picker is offered on calendars that publish no
     * colour palette (the colour may then not survive their next sync).
     */
    val allowColorOnUnsupportedCalendars: Boolean = false,
)
