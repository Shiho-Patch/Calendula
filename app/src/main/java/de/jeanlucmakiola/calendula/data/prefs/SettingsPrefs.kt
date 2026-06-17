package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.jeanlucmakiola.calendula.domain.EventFormField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Light/dark override. SYSTEM follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Week-start override. AUTO derives the first day from the active locale. */
enum class WeekStartPref { AUTO, MONDAY, SUNDAY }

/**
 * Resolve the preference to a concrete first-day-of-week. AUTO reads the
 * locale's convention (e.g. Monday in DE, Sunday in en-US).
 */
fun WeekStartPref.resolveFirstDay(locale: Locale): DayOfWeek = when (this) {
    WeekStartPref.MONDAY -> DayOfWeek.MONDAY
    WeekStartPref.SUNDAY -> DayOfWeek.SUNDAY
    // java.time.DayOfWeek.value is ISO 1..7 (Mon..Sun) — same numbering kotlinx uses.
    WeekStartPref.AUTO -> DayOfWeek(WeekFields.of(locale).firstDayOfWeek.value)
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
        prefs[WEEK_START_KEY].toEnum(WeekStartPref.AUTO)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE_KEY] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    suspend fun setWeekStart(pref: WeekStartPref) {
        store.edit { it[WEEK_START_KEY] = pref.name }
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
        internal val FORM_FIELDS_KEY = stringPreferencesKey("event_form_default_fields")
        internal val REMINDERS_ENABLED_KEY = booleanPreferencesKey("reminders_enabled")
        internal val REMINDER_ONBOARDING_KEY = booleanPreferencesKey("reminder_onboarding_done")
        internal val ALLOW_COLOR_UNSUPPORTED_KEY =
            booleanPreferencesKey("allow_color_unsupported_calendars")
        internal val DEFAULT_FORM_FIELDS =
            setOf(EventFormField.Location, EventFormField.Description)
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
