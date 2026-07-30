package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-side calendar preferences. [hiddenCalendarIds] is the drawer's filter
 * sheet — a purely in-app declutter that deliberately does *not* suppress
 * reminders. Switching a calendar off entirely is the system's
 * `Calendars.VISIBLE` flag, written straight to the provider (#75);
 * [pendingDisabledCalendarIds] only holds those switch-offs the app has not been
 * allowed to write yet.
 *
 * Persisted as a comma-separated string of Long ids; non-numeric tokens are
 * silently dropped (defensive — see CalendarPrefsTest).
 */
@Singleton
class CalendarPrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    // Both id sets are deduped: the store is shared with SettingsPrefs, so any
    // unrelated write would otherwise re-emit an identical set.
    val hiddenCalendarIds: Flow<Set<Long>> = store.data
        .map { prefs -> prefs[HIDDEN_IDS_KEY].parseIds() }
        .distinctUntilChanged()

    suspend fun setHiddenCalendarIds(ids: Set<Long>) {
        store.edit { prefs -> prefs.writeIds(HIDDEN_IDS_KEY, ids) }
    }

    /**
     * Switch-offs the provider does not know about yet, because writing
     * `Calendars.VISIBLE` needs `WRITE_CALENDAR` (#75). Also inherits the
     * retired app-local model's set, from the same key.
     *
     * Honoured as a display and reminder filter while non-empty, but not a
     * second visibility model: `CalendarVisibilityReconciler` drains it entry by
     * entry as soon as the app may write, and nothing adds to it while it may.
     */
    val pendingDisabledCalendarIds: Flow<Set<Long>> = store.data
        .map { prefs -> prefs[DISABLED_IDS_KEY].parseIds() }
        .distinctUntilChanged()

    suspend fun addPendingDisabledCalendarIds(ids: Collection<Long>) =
        editPendingDisabled { it + ids }

    /**
     * Drop [ids] from the pending set, one at a time as the reconciler flushes
     * them, so a run that fails part-way never re-applies what already landed.
     */
    suspend fun removePendingDisabledCalendarIds(ids: Collection<Long>) =
        editPendingDisabled { it - ids.toSet() }

    private suspend fun editPendingDisabled(transform: (Set<Long>) -> Set<Long>) {
        store.edit { prefs ->
            prefs.writeIds(DISABLED_IDS_KEY, transform(prefs[DISABLED_IDS_KEY].parseIds()))
        }
    }

    /**
     * Whether the one-time "visibility follows this device" notice is still
     * owed. Null until the reconciler has evaluated it (which needs the calendar
     * permission), false once it has been shown or was never needed.
     */
    val visibilityNoticePending: Flow<Boolean?> = store.data.map { prefs ->
        prefs[VISIBILITY_NOTICE_KEY]
    }

    suspend fun setVisibilityNoticePending(pending: Boolean) {
        store.edit { prefs -> prefs[VISIBILITY_NOTICE_KEY] = pending }
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
        internal val VISIBILITY_NOTICE_KEY = booleanPreferencesKey("visibility_notice_pending")
        internal val LAST_USED_CALENDAR_KEY = longPreferencesKey("last_used_calendar_id")
    }
}

private fun String?.parseIds(): Set<Long> = orEmpty()
    .split(',')
    .mapNotNull { it.trim().toLongOrNull() }
    .toSet()

private fun MutablePreferences.writeIds(key: Preferences.Key<String>, ids: Set<Long>) {
    if (ids.isEmpty()) remove(key) else set(key, ids.sorted().joinToString(","))
}
