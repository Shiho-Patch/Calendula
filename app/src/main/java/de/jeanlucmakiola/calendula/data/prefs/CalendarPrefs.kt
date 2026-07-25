package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side preference for "calendars the user has hidden in this app" — the
 * drawer's filter sheet, a purely in-app declutter. It deliberately does *not*
 * suppress reminders; switching a calendar off entirely is the system's
 * `Calendars.VISIBLE` flag, written straight to the provider (#75).
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
     * The retired app-local "disabled calendars" set, readable only so the
     * one-shot visibility migration can reconcile it into `Calendars.VISIBLE`
     * (see CalendarVisibilityMigration). Nothing else may consult it — it is a
     * second visibility model, which is exactly what #75 removed. Dropped by
     * [clearLegacyDisabledCalendarIds] once the migration has run.
     */
    suspend fun legacyDisabledCalendarIds(): Set<Long> = store.data.first().let { prefs ->
        prefs[DISABLED_IDS_KEY].orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    suspend fun clearLegacyDisabledCalendarIds() {
        store.edit { prefs -> prefs.remove(DISABLED_IDS_KEY) }
    }

    /**
     * Whether the one-shot reconciliation of the retired disabled set into
     * `Calendars.VISIBLE` has run. Set only once it actually wrote (it needs
     * `WRITE_CALENDAR`), so a run skipped for a missing permission is retried on
     * the next launch.
     */
    val visibilityMigrationDone: Flow<Boolean> = store.data.map { prefs ->
        prefs[VISIBILITY_MIGRATION_KEY] == true
    }

    suspend fun setVisibilityMigrationDone() {
        store.edit { prefs -> prefs[VISIBILITY_MIGRATION_KEY] = true }
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
        internal val VISIBILITY_MIGRATION_KEY = booleanPreferencesKey("visibility_migration_done")
        internal val LAST_USED_CALENDAR_KEY = longPreferencesKey("last_used_calendar_id")
    }
}
