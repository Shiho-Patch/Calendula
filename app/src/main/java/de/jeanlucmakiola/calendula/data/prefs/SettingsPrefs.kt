package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.ui.common.CalendarView
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
     * The default reminder lead time (minutes before start) prefilled on new
     * **timed** events. `null` = no default reminder — the prior behaviour, kept
     * as the factory default so existing users aren't surprised by reminders they
     * never asked for. Stored as a string so "none" is distinct from a numeric
     * value (and from an unset key, which is also "none"). Per-calendar overrides
     * in [perCalendarReminderOverride] take precedence; all-day events instead use
     * [defaultAllDayReminderMinutes]. Resolve with [resolveDefaultReminder].
     */
    val defaultReminderMinutes: Flow<Int?> = store.data.map { prefs ->
        prefs[DEFAULT_REMINDER_KEY].toReminderMinutes()
    }

    suspend fun setDefaultReminderMinutes(minutes: Int?) {
        store.edit { it[DEFAULT_REMINDER_KEY] = minutes?.toString() ?: NONE }
    }

    /**
     * The default reminder lead time prefilled on new **all-day** events, in
     * minutes before the start of the day. All-day events want day-scale lead
     * times ("1 day before"), so they have their own default rather than reusing
     * the timed one. `null` = no default. Per-calendar overrides do **not** apply
     * to all-day events — they always use this global value.
     */
    val defaultAllDayReminderMinutes: Flow<Int?> = store.data.map { prefs ->
        prefs[DEFAULT_ALLDAY_REMINDER_KEY].toReminderMinutes()
    }

    suspend fun setDefaultAllDayReminderMinutes(minutes: Int?) {
        store.edit { it[DEFAULT_ALLDAY_REMINDER_KEY] = minutes?.toString() ?: NONE }
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
     * timed default for its new events: a `null` value means "no reminder", an int
     * means that lead time. A calendar **absent** from the map inherits the global
     * default. Serialised as `id=value;id=value`, with `none` for an explicit
     * no-reminder override. (All-day events ignore this and use
     * [defaultAllDayReminderMinutes].)
     */
    val perCalendarReminderOverride: Flow<Map<Long, Int?>> = store.data.map { prefs ->
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
    val perCalendarAllDayReminderOverride: Flow<Map<Long, Int?>> = store.data.map { prefs ->
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

    private fun parseFormFields(stored: String?): Set<EventFormField> = when (stored) {
        null -> DEFAULT_FORM_FIELDS
        else -> stored.split(',')
            .mapNotNull { name -> EventFormField.entries.firstOrNull { it.name == name.trim() } }
            .toSet()
    }

    companion object {
        internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        internal val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        internal val WEEK_START_KEY = stringPreferencesKey("week_start")
        internal val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
        internal val SHOW_HOUR_LINES_KEY = booleanPreferencesKey("show_hour_lines")
        internal val DEFAULT_VIEW_KEY = stringPreferencesKey("default_view")
        internal val FORM_FIELDS_KEY = stringPreferencesKey("event_form_default_fields")
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
    }
}

/** A calendar's reminder-default override (see [SettingsPrefs.perCalendarReminderOverride]). */
sealed interface CalendarReminderOverride {
    /** No override — the calendar uses the global default. */
    data object Inherit : CalendarReminderOverride
    /** Explicit "no reminder" for this calendar, regardless of the global default. */
    data object None : CalendarReminderOverride
    /** A specific lead time in minutes before the event start. */
    data class Minutes(val minutes: Int) : CalendarReminderOverride
}

/**
 * The lead time to prefill on a new event: the matching per-calendar override
 * if [calendarId] has one for this event kind, otherwise the global default for
 * that kind. All-day events consult [allDayOverrides] / [allDayGlobal]; timed
 * events consult [timedOverrides] / [timedGlobal]. `null` = no reminder. Pure so
 * it can be unit-tested.
 */
fun resolveDefaultReminder(
    timedGlobal: Int?,
    allDayGlobal: Int?,
    timedOverrides: Map<Long, Int?>,
    allDayOverrides: Map<Long, Int?>,
    calendarId: Long?,
    isAllDay: Boolean,
): Int? {
    val overrides = if (isAllDay) allDayOverrides else timedOverrides
    val global = if (isAllDay) allDayGlobal else timedGlobal
    return if (calendarId != null && overrides.containsKey(calendarId)) {
        overrides[calendarId]
    } else {
        global
    }
}

/** Apply a [CalendarReminderOverride] to an override map ([Inherit] removes the key). */
private fun MutableMap<Long, Int?>.applyOverride(
    calendarId: Long,
    override: CalendarReminderOverride,
) {
    when (override) {
        CalendarReminderOverride.Inherit -> remove(calendarId)
        CalendarReminderOverride.None -> put(calendarId, null)
        is CalendarReminderOverride.Minutes -> put(calendarId, override.minutes)
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

private fun String?.toReminderMinutes(): Int? = when (this) {
    null, "", NONE -> null
    else -> toIntOrNull()
}

private fun parseReminderOverrides(stored: String?): Map<Long, Int?> {
    if (stored.isNullOrBlank()) return emptyMap()
    return stored.split(ENTRY_SEP).mapNotNull { entry ->
        val parts = entry.split(KEY_VALUE_SEP).takeIf { it.size == 2 } ?: return@mapNotNull null
        val id = parts[0].toLongOrNull() ?: return@mapNotNull null
        val value = if (parts[1] == NONE) null else parts[1].toIntOrNull() ?: return@mapNotNull null
        id to value
    }.toMap()
}

private fun serializeReminderOverrides(map: Map<Long, Int?>): String =
    map.entries.joinToString(ENTRY_SEP) { (id, minutes) -> "$id$KEY_VALUE_SEP${minutes ?: NONE}" }

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
