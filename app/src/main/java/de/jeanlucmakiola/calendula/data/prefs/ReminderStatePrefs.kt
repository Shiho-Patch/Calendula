package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How far reminder delivery has got — the one number replacing the provider's
 * `CalendarAlerts.STATE` (#75). A scan posts the reminders falling after this
 * watermark and up to now, then moves it to now, so a scan running twice cannot
 * post twice while a late one still catches up.
 *
 * Unset means "never scanned", not zero: the first scan after an install would
 * otherwise treat every reminder since the epoch as overdue.
 */
@Singleton
class ReminderStatePrefs @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    /** The watermark, or `null` before the first scan has ever run. */
    suspend fun lastScanMillis(): Long? = store.data.map { it[LAST_SCAN_KEY] }.first()

    suspend fun setLastScanMillis(millis: Long) {
        store.edit { prefs -> prefs[LAST_SCAN_KEY] = millis }
    }

    private companion object {
        val LAST_SCAN_KEY = longPreferencesKey("reminder_last_scan_millis")
    }
}
