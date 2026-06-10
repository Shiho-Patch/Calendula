package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    companion object {
        internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        internal val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        internal val WEEK_START_KEY = stringPreferencesKey("week_start")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
