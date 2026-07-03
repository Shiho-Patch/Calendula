package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.parseAgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.storageValue
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.theme.FONT_SYSTEM_TOKEN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Light/dark override. SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Week-start override. [Auto] derives the first day from the active locale;
 * [Day] pins a specific weekday (any of the seven).
 */
sealed interface WeekStartPref {
    data object Auto : WeekStartPref
    data class Day(val day: DayOfWeek) : WeekStartPref
}

/**
 * Clock convention for time-of-day labels. AUTO follows the device's 24-hour
 * system setting; the others force a 12- or 24-hour clock app-wide.
 */
enum class TimeFormatPref { AUTO, TWELVE_HOUR, TWENTY_FOUR_HOUR }

/**
 * How the Agenda screen treats events that have already finished today: [SHOW]
 * leaves them as-is (the historical behaviour), [DIM] fades them, [HIDE] drops
 * them from the list entirely.
 */
enum class PastEventDisplay { SHOW, DIM, HIDE }

/** Parse a stored [PastEventDisplay.name]; unknown/null falls back to [default]. */
fun parsePastEventDisplay(
    stored: String?,
    default: PastEventDisplay = PastEventDisplay.SHOW,
): PastEventDisplay = PastEventDisplay.entries.firstOrNull { it.name == stored } ?: default

/**
 * Resolve to a concrete 24-hour flag. AUTO defers to [systemIs24Hour] (the
 * device's `DateFormat.is24HourFormat` value).
 */
fun TimeFormatPref.is24Hour(systemIs24Hour: Boolean): Boolean = when (this) {
    TimeFormatPref.AUTO -> systemIs24Hour
    TimeFormatPref.TWELVE_HOUR -> false
    TimeFormatPref.TWENTY_FOUR_HOUR -> true
}

/**
 * Resolve the preference to a concrete first-day-of-week. [WeekStartPref.Auto]
 * reads the locale's convention (e.g. Monday in DE, Sunday in en-US).
 */
fun WeekStartPref.resolveFirstDay(locale: Locale): DayOfWeek = when (this) {
    is WeekStartPref.Day -> day
    // java.time.DayOfWeek.value is ISO 1..7 (Mon..Sun) — same numbering kotlinx uses.
    WeekStartPref.Auto -> DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
}

/**
 * Display settings (M4) persisted app-side: theme override, Material You
 * dynamic colour, and week start. Language is handled separately through
 * AppCompatDelegate (which persists its own per-app locale).
 *
 * Enum prefs round-trip by [Enum.name]; an unknown/garbage stored value falls
 * back to the default rather than throwing (see SettingsPrefsTest).
 */
@Singleton
class SettingsPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        prefs[THEME_MODE_KEY].toEnum(ThemeMode.SYSTEM)
    }

    val dynamicColor: Flow<Boolean> = store.data.map { prefs ->
        prefs[DYNAMIC_COLOR_KEY] ?: true
    }

    val weekStart: Flow<WeekStartPref> = store.data.map { prefs ->
        parseWeekStart(prefs[WEEK_START_KEY])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE_KEY] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    /**
     * Custom-font tokens per Material typeface role (issue #19). Stored as opaque
     * strings — "system", "custom", or a bundled font's token — resolved to a
     * FontFamily at the theme layer; an unknown token degrades to the default.
     */
    val brandFont: Flow<String> = store.data.map { prefs ->
        prefs[BRAND_FONT_KEY] ?: FONT_SYSTEM_TOKEN
    }

    val plainFont: Flow<String> = store.data.map { prefs ->
        prefs[PLAIN_FONT_KEY] ?: FONT_SYSTEM_TOKEN
    }

    suspend fun setBrandFont(token: String) {
        store.edit { it[BRAND_FONT_KEY] = token }
    }

    suspend fun setPlainFont(token: String) {
        store.edit { it[PLAIN_FONT_KEY] = token }
    }

    /**
     * A per-role bump counter for the user-loaded custom font. Re-importing
     * overwrites the same file under the same "custom" token, so the token alone
     * can't signal the change; this stamp — carried into AppFontSettings — breaks
     * value equality so the resolved FontFamily (and its previews) refresh. A
     * missing key is 0 (existing installs); the first import bumps it to 1.
     */
    val brandFontStamp: Flow<Int> = store.data.map { prefs -> prefs[BRAND_FONT_STAMP_KEY] ?: 0 }

    val plainFontStamp: Flow<Int> = store.data.map { prefs -> prefs[PLAIN_FONT_STAMP_KEY] ?: 0 }

    /** Bump [role]'s custom-font stamp after a re-import so equality-keyed caches refresh. */
    suspend fun bumpCustomFontStamp(role: FontRole) {
        val key = when (role) {
            FontRole.BRAND -> BRAND_FONT_STAMP_KEY
            FontRole.PLAIN -> PLAIN_FONT_STAMP_KEY
        }
        store.edit { it[key] = (it[key] ?: 0) + 1 }
    }

    suspend fun setWeekStart(pref: WeekStartPref) {
        store.edit { it[WEEK_START_KEY] = pref.storageValue() }
    }

    /** Clock convention for time-of-day labels (v2.11). Defaults to AUTO (system). */
    val timeFormat: Flow<TimeFormatPref> = store.data.map { prefs ->
        prefs[TIME_FORMAT_KEY].toEnum(TimeFormatPref.AUTO)
    }

    suspend fun setTimeFormat(pref: TimeFormatPref) {
        store.edit { it[TIME_FORMAT_KEY] = pref.name }
    }

    /**
     * Whether the week/day timeline draws a faint separator line at each hour
     * (v2.11). Defaults to OFF — the historical clean look; users opt in.
     */
    val showHourLines: Flow<Boolean> = store.data.map { prefs ->
        prefs[SHOW_HOUR_LINES_KEY] ?: false
    }

    suspend fun setShowHourLines(enabled: Boolean) {
        store.edit { it[SHOW_HOUR_LINES_KEY] = enabled }
    }

    /**
     * How the Agenda screen treats events that already ended today. Defaults to
     * [PastEventDisplay.SHOW] — the historical behaviour; users opt into dimming
     * or hiding.
     */
    val pastEventDisplay: Flow<PastEventDisplay> = store.data.map { prefs ->
        prefs[PAST_EVENT_DISPLAY_KEY].toEnum(PastEventDisplay.SHOW)
    }

    suspend fun setPastEventDisplay(mode: PastEventDisplay) {
        store.edit { it[PAST_EVENT_DISPLAY_KEY] = mode.name }
    }

    /**
     * Whether the month/week grids fade events that have already finished.
     * Defaults to OFF — independent of the Agenda's [pastEventDisplay].
     */
    val dimCompletedEvents: Flow<Boolean> = store.data.map { prefs ->
        prefs[DIM_COMPLETED_EVENTS_KEY] ?: false
    }

    suspend fun setDimCompletedEvents(enabled: Boolean) {
        store.edit { it[DIM_COMPLETED_EVENTS_KEY] = enabled }
    }

    /**
     * How far ahead the in-app Agenda screen shows events (v2.11). Defaults to
     * [AgendaRange.Month] — a month of upcoming events. Independent of the
     * widget's [agendaWidgetRange].
     */
    val agendaScreenRange: Flow<AgendaRange> = store.data.map { prefs ->
        parseAgendaRange(prefs[AGENDA_SCREEN_RANGE_KEY], AgendaRange.Month)
    }

    suspend fun setAgendaScreenRange(range: AgendaRange) {
        store.edit { it[AGENDA_SCREEN_RANGE_KEY] = range.storageValue() }
    }

    /** How far ahead the agenda **widget** shows events (v2.11). Defaults to Month. */
    val agendaWidgetRange: Flow<AgendaRange> = store.data.map { prefs ->
        parseAgendaRange(prefs[AGENDA_WIDGET_RANGE_KEY], AgendaRange.Month)
    }

    suspend fun setAgendaWidgetRange(range: AgendaRange) {
        store.edit { it[AGENDA_WIDGET_RANGE_KEY] = range.storageValue() }
    }

    /**
     * Whether the agenda shows its top range bar — the "showing …" header and
     * the session range switcher (v2.11). Default ON.
     */
    val agendaShowRangeBar: Flow<Boolean> = store.data.map { prefs ->
        prefs[AGENDA_SHOW_RANGE_BAR_KEY] ?: true
    }

    suspend fun setAgendaShowRangeBar(enabled: Boolean) {
        store.edit { it[AGENDA_SHOW_RANGE_BAR_KEY] = enabled }
    }

    /**
     * The calendar view the app opens on (M1). Defaults to [CalendarView.Week] —
     * the historical hard-coded startup view — so existing users see no change
     * until they pick another. Also the bottom of the in-app view back stack, so
     * back from any other view returns here before exiting.
     */
    val defaultView: Flow<CalendarView> = store.data.map { prefs ->
        prefs[DEFAULT_VIEW_KEY].toEnum(CalendarView.Week)
    }

    suspend fun setDefaultView(view: CalendarView) {
        store.edit { it[DEFAULT_VIEW_KEY] = view.name }
    }

    /**
     * Quick-switch button customisation (#24): the ordered full view list plus
     * which views are enabled in the cycle. Stored comma-joined by enum name, a
     * "!" prefix marking a disabled view (e.g. "Month,Week,!Day,Agenda"). Missing
     * views are appended enabled and unknown names dropped, so a future view
     * defaults into the cycle. An absent key means [QuickSwitchConfig.Default].
     */
    val quickSwitchConfig: Flow<QuickSwitchConfig> = store.data.map { prefs ->
        parseQuickSwitch(prefs[QUICK_SWITCH_VIEWS_KEY])
    }

    suspend fun setQuickSwitchConfig(config: QuickSwitchConfig) {
        store.edit { it[QUICK_SWITCH_VIEWS_KEY] = serializeQuickSwitch(config) }
    }

    /**
     * Atomic read-modify-write of the quick-switch config: [transform] sees the
     * value currently stored, parsed inside the edit block, not the async-echoed
     * UI snapshot. Two rapid intents (a toggle then a drag) therefore compose
     * instead of each re-serialising a stale copy of the other field over it.
     */
    suspend fun updateQuickSwitch(transform: (QuickSwitchConfig) -> QuickSwitchConfig) {
        store.edit { prefs ->
            val current = parseQuickSwitch(prefs[QUICK_SWITCH_VIEWS_KEY])
            prefs[QUICK_SWITCH_VIEWS_KEY] = serializeQuickSwitch(transform(current))
        }
    }

    /**
     * Navigation-drawer view order (#24). Comma-joined enum names; missing views
     * are appended in default order and unknown names dropped. Absent key means
     * [IMPLEMENTED_VIEWS] (the historical fixed order).
     */
    val drawerViewOrder: Flow<List<CalendarView>> = store.data.map { prefs ->
        parseViewOrder(prefs[DRAWER_VIEW_ORDER_KEY])
    }

    suspend fun setDrawerViewOrder(order: List<CalendarView>) {
        store.edit { it[DRAWER_VIEW_ORDER_KEY] = order.joinToString(",") { view -> view.name } }
    }

    /**
     * Optional event-form fields shown by default (the rest hide behind
     * "more fields"). Stored comma-joined by enum name: an absent key means
     * the factory default, an empty string means "none". Unknown names are
     * dropped defensively, like the other enum prefs.
     */
    val defaultFormFields: Flow<Set<EventFormField>> = store.data.map { prefs ->
        parseFormFields(prefs[FORM_FIELDS_KEY])
    }

    suspend fun setFormFieldDefault(field: EventFormField, enabled: Boolean) {
        store.edit { prefs ->
            val current = parseFormFields(prefs[FORM_FIELDS_KEY])
            val updated = if (enabled) current + field else current - field
            prefs[FORM_FIELDS_KEY] = updated.joinToString(",") { it.name }
        }
    }

    /**
     * Whether opening the new-event form focuses the title field and raises the
     * keyboard straight away (issue #10). Default ON — a new event almost always
     * gets a title, so this saves a tap; users who set the time/calendar first
     * can turn it off. Only the create form auto-focuses — editing never does.
     */
    val autofocusEventTitle: Flow<Boolean> = store.data.map { prefs ->
        prefs[AUTOFOCUS_EVENT_TITLE_KEY] ?: true
    }

    suspend fun setAutofocusEventTitle(enabled: Boolean) {
        store.edit { it[AUTOFOCUS_EVENT_TITLE_KEY] = enabled }
    }

    /**
     * Whether Calendula posts reminder notifications (v1.4). Defaults to ON —
     * for users whose only calendar app this is, reminders are essential; the
     * onboarding step and Settings warn about duplicates from a second app.
     */
    val remindersEnabled: Flow<Boolean> = store.data.map { prefs ->
        prefs[REMINDERS_ENABLED_KEY] ?: true
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        store.edit { it[REMINDERS_ENABLED_KEY] = enabled }
    }

    /**
     * Whether to offer a custom event colour even on calendars that publish no
     * colour palette (most local calendars handle it fine; synced calendars
     * without a palette — some CalDAV — may drop or overwrite a raw colour on
     * their next sync). Defaults to OFF: such calendars hide the colour picker
     * until the user opts in, accepting the limitation. Local calendars and
     * palette-backed calendars (Google, …) are unaffected by this flag.
     */
    val allowColorOnUnsupportedCalendars: Flow<Boolean> = store.data.map { prefs ->
        prefs[ALLOW_COLOR_UNSUPPORTED_KEY] ?: false
    }

    suspend fun setAllowColorOnUnsupportedCalendars(enabled: Boolean) {
        store.edit { it[ALLOW_COLOR_UNSUPPORTED_KEY] = enabled }
    }

    /**
     * Whether the one-time reminder onboarding step (after the calendar
     * grant) has been shown — also true for users who tapped "not now".
     */
    val reminderOnboardingDone: Flow<Boolean> = store.data.map { prefs ->
        prefs[REMINDER_ONBOARDING_KEY] ?: false
    }

    suspend fun setReminderOnboardingDone() {
        store.edit { it[REMINDER_ONBOARDING_KEY] = true }
    }

    /**
     * The default reminder lead times (minutes before start) prefilled on new
     * **timed** events. The empty list = no default reminder — the prior
     * behaviour, kept as the factory default so existing users aren't surprised by
     * reminders they never asked for. Stored as a comma-joined list of minutes, or
     * "none"/empty for no reminder (also the unset state). A legacy single value
     * ("30") parses transparently to a one-element list. Per-calendar overrides in
     * [perCalendarReminderOverride] take precedence; all-day events instead use
     * [defaultAllDayReminderMinutes]. Resolve with [resolveDefaultReminder].
     */
    val defaultReminderMinutes: Flow<List<Int>> = store.data.map { prefs ->
        prefs[DEFAULT_REMINDER_KEY].toReminderList()
    }

    suspend fun setDefaultReminderMinutes(minutes: List<Int>) {
        store.edit { it[DEFAULT_REMINDER_KEY] = minutes.toStoredReminders() }
    }

    /**
     * The default reminder lead times prefilled on new **all-day** events, in
     * minutes before the start of the day. All-day events want day-scale lead
     * times ("1 day before"), so they have their own default rather than reusing
     * the timed one. Empty list = no default. Per-calendar overrides do **not**
     * apply to all-day events — they always use this global value.
     */
    val defaultAllDayReminderMinutes: Flow<List<Int>> = store.data.map { prefs ->
        prefs[DEFAULT_ALLDAY_REMINDER_KEY].toReminderList()
    }

    suspend fun setDefaultAllDayReminderMinutes(minutes: List<Int>) {
        store.edit { it[DEFAULT_ALLDAY_REMINDER_KEY] = minutes.toStoredReminders() }
    }

    /**
     * Wall-clock time, as minutes from local midnight, at which **all-day**
     * reminders fire. All-day events live at UTC midnight, so a raw "1 day
     * before" would fire at an off hour (02:00 local in CEST); this time is
     * encoded into the provider offset so the reminder lands at, e.g., 09:00 the
     * day before instead. Global for every all-day reminder; default 09:00.
     * Stored/clamped to a valid 0..1439 minute-of-day.
     */
    val allDayReminderTimeMinutes: Flow<Int> = store.data.map { prefs ->
        (prefs[ALLDAY_REMINDER_TIME_KEY] ?: DEFAULT_ALLDAY_REMINDER_TIME)
            .coerceIn(0, MINUTES_PER_DAY - 1)
    }

    suspend fun setAllDayReminderTimeMinutes(minutesOfDay: Int) {
        store.edit { it[ALLDAY_REMINDER_TIME_KEY] = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1) }
    }

    /**
     * How long the notification "Snooze" action defers a reminder, in minutes.
     * Tapping Snooze cancels the notification and re-posts it after this delay
     * via an exact alarm (the calendar provider won't re-fire). Default 10 min;
     * clamped to at least 1.
     */
    val snoozeMinutes: Flow<Int> = store.data.map { prefs ->
        (prefs[SNOOZE_MINUTES_KEY] ?: DEFAULT_SNOOZE_MINUTES).coerceAtLeast(1)
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        store.edit { it[SNOOZE_MINUTES_KEY] = minutes.coerceAtLeast(1) }
    }

    /**
     * Per-calendar overrides of [defaultReminderMinutes] for **timed** events,
     * keyed by calendar id. A calendar **present** in the map overrides the global
     * timed default for its new events: an **empty** list means "no reminder", a
     * non-empty list means those lead times. A calendar **absent** from the map
     * inherits the global default. Serialised as `id=value;id=value`, where a value
     * is a comma-joined minute list or `none` for an explicit no-reminder override.
     * (All-day events ignore this and use [defaultAllDayReminderMinutes].)
     */
    val perCalendarReminderOverride: Flow<Map<Long, List<Int>>> = store.data.map { prefs ->
        parseReminderOverrides(prefs[CALENDAR_REMINDER_OVERRIDE_KEY])
    }

    suspend fun setCalendarReminderOverride(calendarId: Long, override: CalendarReminderOverride) {
        store.edit { prefs ->
            val current = parseReminderOverrides(prefs[CALENDAR_REMINDER_OVERRIDE_KEY]).toMutableMap()
            current.applyOverride(calendarId, override)
            prefs[CALENDAR_REMINDER_OVERRIDE_KEY] = serializeReminderOverrides(current)
        }
    }

    /**
     * Per-calendar overrides of [defaultAllDayReminderMinutes] for **all-day**
     * events, with the same semantics as [perCalendarReminderOverride] (absent =
     * inherit the global all-day default; present null = no reminder).
     */
    val perCalendarAllDayReminderOverride: Flow<Map<Long, List<Int>>> = store.data.map { prefs ->
        parseReminderOverrides(prefs[CALENDAR_ALLDAY_REMINDER_OVERRIDE_KEY])
    }

    suspend fun setCalendarAllDayReminderOverride(
        calendarId: Long,
        override: CalendarReminderOverride,
    ) {
        store.edit { prefs ->
            val current =
                parseReminderOverrides(prefs[CALENDAR_ALLDAY_REMINDER_OVERRIDE_KEY]).toMutableMap()
            current.applyOverride(calendarId, override)
            prefs[CALENDAR_ALLDAY_REMINDER_OVERRIDE_KEY] = serializeReminderOverrides(current)
        }
    }

    // --- Automatic backup (issue #8) ------------------------------------

    /** Whether periodic automatic `.ics` export of local calendars is on. Default OFF. */
    val autoBackupEnabled: Flow<Boolean> = store.data.map { it[AUTO_BACKUP_ENABLED_KEY] ?: false }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        store.edit { it[AUTO_BACKUP_ENABLED_KEY] = enabled }
    }

    /** Export interval in minutes. Default daily; floored at [MIN_BACKUP_INTERVAL]. */
    val autoBackupIntervalMinutes: Flow<Long> = store.data.map { prefs ->
        (prefs[AUTO_BACKUP_INTERVAL_KEY] ?: DEFAULT_BACKUP_INTERVAL).coerceAtLeast(MIN_BACKUP_INTERVAL)
    }

    suspend fun setAutoBackupIntervalMinutes(minutes: Long) {
        store.edit { it[AUTO_BACKUP_INTERVAL_KEY] = minutes.coerceAtLeast(MIN_BACKUP_INTERVAL) }
    }

    /** Persisted SAF tree Uri of the destination folder, or null if unset. */
    val autoBackupFolderUri: Flow<String?> = store.data.map { it[AUTO_BACKUP_FOLDER_KEY] }

    suspend fun setAutoBackupFolderUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(AUTO_BACKUP_FOLDER_KEY) else prefs[AUTO_BACKUP_FOLDER_KEY] = uri
        }
    }

    /** Outcome of the last automatic run, for the settings status line. */
    val autoBackupStatus: Flow<BackupStatus> = store.data.map { prefs ->
        BackupStatus(
            lastRun = prefs[AUTO_BACKUP_LAST_RUN_KEY] ?: 0L,
            lastSuccess = prefs[AUTO_BACKUP_LAST_SUCCESS_KEY] ?: true,
            consecutiveFailures = prefs[AUTO_BACKUP_FAILURES_KEY] ?: 0,
        )
    }

    /** Record an automatic run's outcome; resets the failure streak on success. */
    suspend fun recordAutoBackupRun(success: Boolean, atMillis: Long) {
        store.edit { prefs ->
            prefs[AUTO_BACKUP_LAST_RUN_KEY] = atMillis
            prefs[AUTO_BACKUP_LAST_SUCCESS_KEY] = success
            val failures = prefs[AUTO_BACKUP_FAILURES_KEY] ?: 0
            prefs[AUTO_BACKUP_FAILURES_KEY] = if (success) 0 else failures + 1
        }
    }

    // --- Contact special dates (issue #15) ------------------------------

    /** Master switch for the contact special-dates mirror. Default OFF (opt-in). */
    val specialDatesEnabled: Flow<Boolean> = store.data.map { it[SPECIAL_DATES_ENABLED_KEY] ?: false }

    suspend fun setSpecialDatesEnabled(enabled: Boolean) {
        store.edit { it[SPECIAL_DATES_ENABLED_KEY] = enabled }
    }

    /** The date types the user wants mirrored (default: all three). */
    val specialDatesTypes: Flow<Set<SpecialDateType>> = store.data.map { prefs ->
        SpecialDateType.entries.filterTo(mutableSetOf()) { prefs[typeEnabledKey(it)] ?: true }
    }

    suspend fun setSpecialDateTypeEnabled(type: SpecialDateType, enabled: Boolean) {
        store.edit { it[typeEnabledKey(type)] = enabled }
    }

    /** The managed calendar id per type, for the ones that currently exist. */
    val specialDatesCalendars: Flow<Map<SpecialDateType, Long>> = store.data.map { prefs ->
        SpecialDateType.entries.mapNotNull { type ->
            prefs[calendarIdKey(type)]?.let { type to it }
        }.toMap()
    }

    /** Every managed calendar id — the editor locks title/date/recurrence for their events. */
    val managedCalendarIds: Flow<Set<Long>> = specialDatesCalendars.map { it.values.toSet() }

    suspend fun setSpecialDatesCalendarId(type: SpecialDateType, calendarId: Long?) {
        store.edit { prefs ->
            if (calendarId == null) prefs.remove(calendarIdKey(type)) else prefs[calendarIdKey(type)] = calendarId
        }
    }

    /**
     * The title template per type ("{name}'s birthday"); an empty string means
     * unset, so the settings screen can seed the localized default. `{name}` is
     * the contact name, `{age}` the age at the upcoming date (empty when the
     * birth year is unknown or [specialDatesShowAge] is off).
     */
    val specialDatesTitleTemplates: Flow<Map<SpecialDateType, String>> = store.data.map { prefs ->
        SpecialDateType.entries.associateWith { prefs[titleTemplateKey(it)].orEmpty() }
    }

    suspend fun setSpecialDatesTitleTemplate(type: SpecialDateType, template: String) {
        store.edit { it[titleTemplateKey(type)] = template }
    }

    /** Whether `{year}` resolves in the title (only meaningful when the year is known). Default ON. */
    val specialDatesShowYear: Flow<Boolean> = store.data.map { it[SPECIAL_DATES_SHOW_YEAR_KEY] ?: true }

    suspend fun setSpecialDatesShowYear(enabled: Boolean) {
        store.edit { it[SPECIAL_DATES_SHOW_YEAR_KEY] = enabled }
    }

    /** Outcome of the last mirror sync, for the settings status line. */
    val specialDatesStatus: Flow<SpecialDatesStatus> = store.data.map { prefs ->
        SpecialDatesStatus(
            lastRun = prefs[SPECIAL_DATES_LAST_RUN_KEY] ?: 0L,
            stalled = prefs[SPECIAL_DATES_STALLED_KEY]
                ?.let { name -> SpecialDatesStalledReason.entries.firstOrNull { it.name == name } },
        )
    }

    /** Record a sync outcome; [stalled] non-null marks the feature paused (e.g. permission revoked). */
    suspend fun recordSpecialDatesRun(atMillis: Long, stalled: SpecialDatesStalledReason?) {
        store.edit { prefs ->
            prefs[SPECIAL_DATES_LAST_RUN_KEY] = atMillis
            if (stalled == null) prefs.remove(SPECIAL_DATES_STALLED_KEY) else prefs[SPECIAL_DATES_STALLED_KEY] = stalled.name
        }
    }

    /** Epoch millis of the last foreground-triggered sync, to debounce ON_RESUME. */
    val specialDatesLastForegroundSync: Flow<Long> =
        store.data.map { it[SPECIAL_DATES_LAST_FG_SYNC_KEY] ?: 0L }

    suspend fun setSpecialDatesLastForegroundSync(atMillis: Long) {
        store.edit { it[SPECIAL_DATES_LAST_FG_SYNC_KEY] = atMillis }
    }

    private fun typeEnabledKey(type: SpecialDateType) =
        booleanPreferencesKey("special_dates_type_${type.name}")

    private fun calendarIdKey(type: SpecialDateType) =
        longPreferencesKey("special_dates_calendar_${type.name}")

    private fun titleTemplateKey(type: SpecialDateType) =
        stringPreferencesKey("special_dates_title_${type.name}")

    private fun parseFormFields(stored: String?): Set<EventFormField> = when (stored) {
        null -> DEFAULT_FORM_FIELDS
        else -> stored.split(',')
            .mapNotNull { name -> EventFormField.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
    }

    /** Parse a plain comma-joined view order, completed to every implemented view. */
    private fun parseViewOrder(stored: String?): List<CalendarView> =
        completeViewOrder(
            stored?.split(',').orEmpty()
                .mapNotNull { name -> CalendarView.entries.firstOrNull { it.name == name.trim() } },
        )

    /** Parse the quick-switch config; "!"-prefixed names are disabled. */
    private fun parseQuickSwitch(stored: String?): QuickSwitchConfig {
        if (stored == null) return QuickSwitchConfig.Default
        val parsed = stored.split(',').mapNotNull { raw ->
            val token = raw.trim()
            val disabled = token.startsWith("!")
            val name = if (disabled) token.drop(1) else token
            CalendarView.entries.firstOrNull { it.name == name }?.let { it to disabled }
        }
        val order = completeViewOrder(parsed.map { it.first })
        // Only views explicitly stored disabled are excluded; anything appended
        // (a view added in a later release) defaults into the cycle.
        val disabled = parsed.filter { it.second }.map { it.first }.toSet()
        return QuickSwitchConfig(order, order.filterNot { it in disabled }.toSet())
    }

    private fun serializeQuickSwitch(config: QuickSwitchConfig): String =
        completeViewOrder(config.order).joinToString(",") { view ->
            if (view in config.enabled) view.name else "!${view.name}"
        }

    /** Keep the given order (de-duplicated), then append any views it omits. */
    private fun completeViewOrder(seen: List<CalendarView>): List<CalendarView> {
        val ordered = seen.distinct()
        return ordered + IMPLEMENTED_VIEWS.filterNot { it in ordered }
    }

    companion object {
        internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        internal val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        internal val BRAND_FONT_KEY = stringPreferencesKey("brand_font")
        internal val PLAIN_FONT_KEY = stringPreferencesKey("plain_font")
        internal val BRAND_FONT_STAMP_KEY = intPreferencesKey("brand_font_stamp")
        internal val PLAIN_FONT_STAMP_KEY = intPreferencesKey("plain_font_stamp")
        internal val WEEK_START_KEY = stringPreferencesKey("week_start")
        internal val AGENDA_SCREEN_RANGE_KEY = stringPreferencesKey("agenda_screen_range")
        internal val AGENDA_WIDGET_RANGE_KEY = stringPreferencesKey("agenda_widget_range")
        internal val AGENDA_SHOW_RANGE_BAR_KEY = booleanPreferencesKey("agenda_show_range_bar")
        internal val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
        internal val SHOW_HOUR_LINES_KEY = booleanPreferencesKey("show_hour_lines")
        internal val PAST_EVENT_DISPLAY_KEY = stringPreferencesKey("agenda_past_event_display")
        internal val DIM_COMPLETED_EVENTS_KEY = booleanPreferencesKey("dim_completed_events")
        internal val DEFAULT_VIEW_KEY = stringPreferencesKey("default_view")
        internal val QUICK_SWITCH_VIEWS_KEY = stringPreferencesKey("quick_switch_views")
        internal val DRAWER_VIEW_ORDER_KEY = stringPreferencesKey("drawer_view_order")
        internal val FORM_FIELDS_KEY = stringPreferencesKey("event_form_default_fields")
        internal val AUTOFOCUS_EVENT_TITLE_KEY = booleanPreferencesKey("autofocus_event_title")
        internal val REMINDERS_ENABLED_KEY = booleanPreferencesKey("reminders_enabled")
        internal val REMINDER_ONBOARDING_KEY = booleanPreferencesKey("reminder_onboarding_done")
        internal val ALLOW_COLOR_UNSUPPORTED_KEY =
            booleanPreferencesKey("allow_color_unsupported_calendars")
        internal val DEFAULT_REMINDER_KEY = stringPreferencesKey("default_reminder_minutes")
        internal val DEFAULT_ALLDAY_REMINDER_KEY =
            stringPreferencesKey("default_allday_reminder_minutes")
        internal val ALLDAY_REMINDER_TIME_KEY =
            intPreferencesKey("allday_reminder_time_minutes")
        /** 09:00 as minutes from midnight; the default all-day reminder fire time. */
        internal const val DEFAULT_ALLDAY_REMINDER_TIME = 540
        private const val MINUTES_PER_DAY = 1_440
        internal val SNOOZE_MINUTES_KEY = intPreferencesKey("snooze_minutes")
        /** Default snooze delay for the notification "Snooze" action. */
        const val DEFAULT_SNOOZE_MINUTES = 10
        internal val CALENDAR_REMINDER_OVERRIDE_KEY =
            stringPreferencesKey("per_calendar_reminder_override")
        internal val CALENDAR_ALLDAY_REMINDER_OVERRIDE_KEY =
            stringPreferencesKey("per_calendar_allday_reminder_override")
        internal val DEFAULT_FORM_FIELDS =
            setOf(EventFormField.Location, EventFormField.Description)
        internal val AUTO_BACKUP_ENABLED_KEY = booleanPreferencesKey("auto_backup_enabled")
        internal val AUTO_BACKUP_INTERVAL_KEY = longPreferencesKey("auto_backup_interval_minutes")
        internal val AUTO_BACKUP_FOLDER_KEY = stringPreferencesKey("auto_backup_folder_uri")
        internal val AUTO_BACKUP_LAST_RUN_KEY = longPreferencesKey("auto_backup_last_run")
        internal val AUTO_BACKUP_LAST_SUCCESS_KEY = booleanPreferencesKey("auto_backup_last_success")
        internal val AUTO_BACKUP_FAILURES_KEY = intPreferencesKey("auto_backup_failures")
        /** Default automatic-backup interval: daily. */
        const val DEFAULT_BACKUP_INTERVAL = 1_440L
        /** Floor for the automatic-backup interval (also above WorkManager's 15-min limit). */
        const val MIN_BACKUP_INTERVAL = 30L

        internal val SPECIAL_DATES_ENABLED_KEY = booleanPreferencesKey("special_dates_enabled")
        internal val SPECIAL_DATES_SHOW_YEAR_KEY = booleanPreferencesKey("special_dates_show_year")
        internal val SPECIAL_DATES_LAST_RUN_KEY = longPreferencesKey("special_dates_last_run")
        internal val SPECIAL_DATES_STALLED_KEY = stringPreferencesKey("special_dates_stalled_reason")
        internal val SPECIAL_DATES_LAST_FG_SYNC_KEY =
            longPreferencesKey("special_dates_last_foreground_sync")
    }
}

/** Snapshot of the special-dates mirror's last outcome (see [SettingsPrefs.specialDatesStatus]). */
data class SpecialDatesStatus(
    /** Epoch millis of the last sync, or 0 if it has never run. */
    val lastRun: Long,
    /** Non-null when the mirror is paused and why; null when healthy. */
    val stalled: SpecialDatesStalledReason?,
)

/** Why the special-dates mirror is paused. */
enum class SpecialDatesStalledReason {
    /** READ_CONTACTS was revoked in system settings after the feature was enabled. */
    PermissionRevoked,
}

/** Snapshot of the automatic backup's last outcome (see [SettingsPrefs.autoBackupStatus]). */
data class BackupStatus(
    /** Epoch millis of the last run, or 0 if it has never run. */
    val lastRun: Long,
    val lastSuccess: Boolean,
    val consecutiveFailures: Int,
)

/** A calendar's reminder-default override (see [SettingsPrefs.perCalendarReminderOverride]). */
sealed interface CalendarReminderOverride {
    /** No override — the calendar uses the global default. */
    data object Inherit : CalendarReminderOverride
    /** Explicit "no reminder" for this calendar, regardless of the global default. */
    data object None : CalendarReminderOverride
    /** Specific lead times in minutes before the event start (non-empty). */
    data class Minutes(val minutes: List<Int>) : CalendarReminderOverride
}

/**
 * The stored override for [calendarId] in an override map, as a
 * [CalendarReminderOverride] (absent → [CalendarReminderOverride.Inherit],
 * empty → [CalendarReminderOverride.None]) — the inverse of how
 * [SettingsPrefs.setCalendarReminderOverride] stores a choice.
 */
fun Map<Long, List<Int>>.choiceFor(calendarId: Long): CalendarReminderOverride = when {
    !containsKey(calendarId) -> CalendarReminderOverride.Inherit
    getValue(calendarId).isEmpty() -> CalendarReminderOverride.None
    else -> CalendarReminderOverride.Minutes(getValue(calendarId))
}

/**
 * The lead times to prefill on a new event: the matching per-calendar override
 * if [calendarId] has one for this event kind, otherwise the global default for
 * that kind. All-day events consult [allDayOverrides] / [allDayGlobal]; timed
 * events consult [timedOverrides] / [timedGlobal]. The empty list = no reminder.
 * Pure so it can be unit-tested.
 */
fun resolveDefaultReminder(
    timedGlobal: List<Int>,
    allDayGlobal: List<Int>,
    timedOverrides: Map<Long, List<Int>>,
    allDayOverrides: Map<Long, List<Int>>,
    calendarId: Long?,
    isAllDay: Boolean,
): List<Int> {
    val overrides = if (isAllDay) allDayOverrides else timedOverrides
    val global = if (isAllDay) allDayGlobal else timedGlobal
    return if (calendarId != null && overrides.containsKey(calendarId)) {
        overrides.getValue(calendarId)
    } else {
        global
    }
}

/**
 * Apply a [CalendarReminderOverride] to an override map ([Inherit] removes the
 * key; [None] and an empty [Minutes] both store the empty list).
 */
private fun MutableMap<Long, List<Int>>.applyOverride(
    calendarId: Long,
    override: CalendarReminderOverride,
) {
    when (override) {
        CalendarReminderOverride.Inherit -> remove(calendarId)
        CalendarReminderOverride.None -> put(calendarId, emptyList())
        is CalendarReminderOverride.Minutes -> put(calendarId, override.minutes.normalizeReminders())
    }
}

/** Sentinel stored for [WeekStartPref.Auto]; days store their [DayOfWeek.name]. */
private const val WEEK_START_AUTO = "AUTO"

private fun WeekStartPref.storageValue(): String = when (this) {
    WeekStartPref.Auto -> WEEK_START_AUTO
    is WeekStartPref.Day -> day.name
}

/**
 * Parse the stored week-start value. "AUTO"/null/garbage → [WeekStartPref.Auto];
 * a [DayOfWeek] name → [WeekStartPref.Day]. The legacy "MONDAY"/"SUNDAY" enum
 * values migrate transparently, since both are valid day names.
 */
private fun parseWeekStart(stored: String?): WeekStartPref = when (stored) {
    null, WEEK_START_AUTO -> WeekStartPref.Auto
    else -> DayOfWeek.entries.firstOrNull { it.name == stored }
        ?.let { WeekStartPref.Day(it) } ?: WeekStartPref.Auto
}

private const val NONE = "none"
private const val ENTRY_SEP = ";"
private const val KEY_VALUE_SEP = "="
private const val LIST_SEP = ","

/** Distinct, ascending lead times — the canonical form stored and resolved. */
private fun List<Int>.normalizeReminders(): List<Int> = distinct().sorted()

/**
 * Parse a stored reminder value into lead times. `null`/empty/"none" → empty
 * list; a comma-joined list → its minutes; a legacy single value ("30") → a
 * one-element list. Non-numeric parts are dropped defensively.
 */
private fun String?.toReminderList(): List<Int> = when {
    this == null || isEmpty() || this == NONE -> emptyList()
    else -> split(LIST_SEP).mapNotNull { it.trim().toIntOrNull() }.normalizeReminders()
}

/** Serialise lead times for storage: "none" when empty, else comma-joined. */
private fun List<Int>.toStoredReminders(): String =
    if (isEmpty()) NONE else normalizeReminders().joinToString(LIST_SEP) { it.toString() }

private fun parseReminderOverrides(stored: String?): Map<Long, List<Int>> {
    if (stored.isNullOrBlank()) return emptyMap()
    return stored.split(ENTRY_SEP).mapNotNull { entry ->
        val parts = entry.split(KEY_VALUE_SEP).takeIf { it.size == 2 } ?: return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        // Only the deliberate "none" sentinel means an explicit no-reminder
        // override (empty list); a non-sentinel value that parses to no valid
        // minutes is garbage and drops the entry, so the calendar inherits the
        // global default rather than silently reading as "no reminder".
        when (val value = parts[1]) {
            NONE -> id to emptyList()
            else -> value.toReminderList().takeIf { it.isNotEmpty() }?.let { id to it }
                ?: return@mapNotNull null
        }
    }.toMap()
}

private fun serializeReminderOverrides(map: Map<Long, List<Int>>): String =
    map.entries.joinToString(ENTRY_SEP) { (id, minutes) -> "$id$KEY_VALUE_SEP${minutes.toStoredReminders()}" }

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
