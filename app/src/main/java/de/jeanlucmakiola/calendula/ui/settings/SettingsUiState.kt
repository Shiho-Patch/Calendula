package de.jeanlucmakiola.calendula.ui.settings

import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.widget.WidgetSize

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
    /** Whether raw provider colours are softened to theme-fitting pastels (#36). */
    val softenColors: Boolean = true,
    val weekStart: WeekStartPref = WeekStartPref.Auto,
    /** Clock convention for time labels (v2.11). AUTO follows the system setting. */
    val timeFormat: TimeFormatPref = TimeFormatPref.AUTO,
    /** Whether the week/day timeline draws an hour separator line (v2.11). */
    val showHourLines: Boolean = false,
    /** How the Agenda screen treats events that already ended today. */
    val pastEventDisplay: PastEventDisplay = PastEventDisplay.SHOW,
    /** Whether the month/week grids fade events that have already finished. */
    val dimCompletedEvents: Boolean = false,
    /** Whether the Month grid shows calendar-week numbers in a left gutter (#25). */
    val showWeekNumbers: Boolean = false,
    /** Whether the jump-to-today control sits in the top bar instead of the FAB (#60). */
    val todayButtonInToolbar: Boolean = false,
    /** How far ahead the in-app Agenda screen shows events (v2.11). */
    val agendaScreenRange: AgendaRange = AgendaRange.Month,
    /** How far ahead the agenda widget shows events (v2.11). */
    val agendaWidgetRange: AgendaRange = AgendaRange.Month,
    /** Whether the agenda (screen + widget) always anchors today at the top (#35). */
    val agendaShowToday: Boolean = true,
    /** Whether the agenda shows its top range bar — header + switcher (v2.11). */
    val agendaShowRangeBar: Boolean = true,
    /** The size step the agenda widget draws its text at (#51). */
    val widgetSize: WidgetSize = WidgetSize.SMALL,
    /** The calendar view the app opens on, and the home of the view back stack (M1). */
    val defaultView: CalendarView = CalendarView.Week,
    /** Which views the top-bar quick-switch button cycles through, and their order (#24). */
    val quickSwitchConfig: QuickSwitchConfig = QuickSwitchConfig.Default,
    /** How the Month view lays itself out: pages, continuous scroll, or split (#38, #53). */
    val monthViewStyle: MonthViewStyle = MonthViewStyle.Paged,
    /** How tall an hour is drawn in the week and day timelines (#56). */
    val timelineScale: TimelineScale = TimelineScale.Regular,
    /** Order of the views in the navigation drawer (#24); every view is always listed. */
    val drawerViewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
    /** Optional event-form fields shown by default (rest behind "more fields"). */
    val defaultFormFields: Set<EventFormField> = SettingsPrefs.DEFAULT_FORM_FIELDS,
    /** Whether the new-event form auto-focuses the title and shows the keyboard (#10). */
    val autofocusEventTitle: Boolean = true,
    /** Whether Calendula posts reminder notifications (v1.4). */
    val remindersEnabled: Boolean = true,
    /**
     * The default reminder lead times (minutes) prefilled on new timed events;
     * empty = no default reminder. Per-calendar overrides take precedence.
     */
    val defaultReminderMinutes: List<Int> = emptyList(),
    /** The default reminder lead times prefilled on new all-day events; empty = none. */
    val defaultAllDayReminderMinutes: List<Int> = emptyList(),
    /** Wall-clock time (minutes from midnight) all-day reminders fire at; default 09:00. */
    val allDayReminderTimeMinutes: Int = SettingsPrefs.DEFAULT_ALLDAY_REMINDER_TIME,
    /** How long the notification "Snooze" action defers a reminder; default 10 min. */
    val snoozeMinutes: Int = SettingsPrefs.DEFAULT_SNOOZE_MINUTES,
    /**
     * Per-calendar overrides of [defaultReminderMinutes] for timed events: a
     * calendar present in the map overrides the global default (empty value = no
     * reminder); absent = inherit the global default.
     */
    val perCalendarReminderOverride: Map<Long, List<Int>> = emptyMap(),
    /** Per-calendar overrides of [defaultAllDayReminderMinutes] for all-day events. */
    val perCalendarAllDayReminderOverride: Map<Long, List<Int>> = emptyMap(),
    /** Writable calendars, shown as per-calendar reminder-override rows. */
    val writableCalendars: List<CalendarSource> = emptyList(),
    /**
     * Whether the event-colour picker is offered on calendars that publish no
     * colour palette (the colour may then not survive their next sync).
     */
    val allowColorOnUnsupportedCalendars: Boolean = false,
    /**
     * Ids of the contact special-dates calendars. Their reminders are owned by
     * the Contact special dates section, so the per-calendar override list links
     * out to it instead of offering an override here.
     */
    val managedCalendarIds: Set<Long> = emptySet(),
)

/**
 * State for the contact special-dates sub-page (issue #15). Exposed as its own
 * flow rather than folded into [SettingsUiState] (whose combine is already full).
 * [titleTemplates] is always populated — a blank stored template resolves to the
 * localized default so the editor never shows an empty field.
 */
data class SpecialDatesUiState(
    val enabled: Boolean = false,
    val types: Set<SpecialDateType> = SpecialDateType.entries.toSet(),
    val titleTemplates: Map<SpecialDateType, String> = emptyMap(),
    /** The reminder choice per type's managed calendar (applied to all its events). */
    val reminderChoices: Map<SpecialDateType, ReminderOverride> = emptyMap(),
    /** Whether {year} resolves in titles (the source year is static and always correct). */
    val showYear: Boolean = true,
    /** True when READ_CONTACTS was revoked after enabling — the mirror is paused. */
    val stalledPermission: Boolean = false,
    /** Epoch millis of the last sync, or 0 if it has never run. */
    val lastRun: Long = 0L,
)
