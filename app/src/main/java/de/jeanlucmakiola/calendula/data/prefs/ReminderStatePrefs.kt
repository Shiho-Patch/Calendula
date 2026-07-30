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
 * How far reminder delivery has got. One number, and it replaces everything the
 * provider's `CalendarAlerts.STATE` used to do for us (#75).
 *
 * A scan posts the reminders whose moment falls after this watermark and up to
 * now, then moves it to now. That single rule gives both halves of what the
 * retired path got from the provider: a scan that runs twice cannot post the
 * same reminder again, and a scan that runs *late* — after a reboot, an app
 * update or a doze window swallowed the alarm — still posts everything the
 * missed alarm would have.
 *
 * Unset means "never scanned". It is deliberately not treated as zero: the first
 * scan after an install or an upgrade would otherwise consider every reminder
 * since the epoch overdue and bury the user in notifications.
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
