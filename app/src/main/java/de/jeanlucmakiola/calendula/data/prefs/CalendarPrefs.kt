package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side preference for "calendars the user has hidden in this app",
 * separate from the system's per-calendar VISIBLE flag.
 *
 * Persisted as a comma-separated string of Long ids; non-numeric tokens are
 * silently dropped (defensive — see CalendarPrefsTest).
 */
@Singleton
class CalendarPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val hiddenCalendarIds: Flow<Set<Long>> = store.data.map { prefs ->
        prefs[HIDDEN_IDS_KEY].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    suspend fun setHiddenCalendarIds(ids: Set<Long>) {
        store.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(HIDDEN_IDS_KEY)
            } else {
                prefs[HIDDEN_IDS_KEY] = ids.sorted().joinToString(",")
            }
        }
    }

    /**
     * App-side preference for "calendars the user has disabled in this app" — a
     * heavier level than [hiddenCalendarIds]. A disabled calendar is removed from
     * every surface (drawer filter, event-form picker, import picker) and its
     * events never appear; it stays listed only in Settings → Calendars so it can
     * be re-enabled. Stored exactly like the hidden set; never touches the
     * system's VISIBLE/SYNC_EVENTS flags, so other calendar apps are unaffected.
     */
    val disabledCalendarIds: Flow<Set<Long>> = store.data.map { prefs ->
        prefs[DISABLED_IDS_KEY].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    suspend fun setDisabledCalendarIds(ids: Set<Long>) {
        store.edit { prefs ->
            if (ids.isEmpty()) {
                prefs.remove(DISABLED_IDS_KEY)
            } else {
                prefs[DISABLED_IDS_KEY] = ids.sorted().joinToString(",")
            }
        }
    }

    /**
     * The calendar the user last created an event in; preselected in the
     * event form. Null until the first event is created.
     */
    val lastUsedCalendarId: Flow<Long?> = store.data.map { prefs ->
        prefs[LAST_USED_CALENDAR_KEY]
    }

    suspend fun setLastUsedCalendarId(id: Long) {
        store.edit { prefs -> prefs[LAST_USED_CALENDAR_KEY] = id }
    }

    companion object {
        internal val HIDDEN_IDS_KEY = stringPreferencesKey("hidden_calendar_ids")
        internal val DISABLED_IDS_KEY = stringPreferencesKey("disabled_calendar_ids")
        internal val LAST_USED_CALENDAR_KEY = longPreferencesKey("last_used_calendar_id")
    }
}
