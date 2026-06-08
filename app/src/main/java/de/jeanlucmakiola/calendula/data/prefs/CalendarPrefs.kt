package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    companion object {
        internal val HIDDEN_IDS_KEY = stringPreferencesKey("hidden_calendar_ids")
    }
}
