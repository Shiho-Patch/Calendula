package de.jeanlucmakiola.calendula.data.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Still relevant while the event has not ended: a reminder for an event that is
 * already over is pointless to re-surface. Falls back to the begin time when the
 * end is unknown (0L). Used both to decide what to re-post and to purge the stash.
 */
internal fun ReminderAlert.isRelevantAt(nowMillis: Long): Boolean =
    (endMillis.takeIf { it > 0L } ?: beginMillis) >= nowMillis

/**
 * Local stash of reminder alerts that fired while their calendar was disabled
 * in-app. [EventReminderReceiver] marks every due alert `STATE_FIRED` regardless
 * (so the provider stops re-broadcasting the suppressed ones), which would
 * otherwise lose those reminders forever — there is no re-scan. Stashing lets
 * [de.jeanlucmakiola.calendula.ui.calendars.CalendarsViewModel] re-post them if
 * the user re-enables the calendar before the event is over.
 *
 * Persisted in the shared preferences DataStore as a set of self-describing
 * strings (one per alert); the stash never reaches a screen, so there is no
 * domain model. Entries whose event has already ended are dropped on the next
 * stash/recover/purge, so the stash only ever holds a handful of pending alerts.
 */
@Singleton
class SuppressedReminderStore @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    /** Add [alerts] to the stash, replacing any existing entry with the same id. */
    suspend fun stash(alerts: List<ReminderAlert>, nowMillis: Long) {
        if (alerts.isEmpty()) return
        store.edit { prefs ->
            val byId = decodeAll(prefs).associateByTo(mutableMapOf()) { it.alertId }
            alerts.forEach { byId[it.alertId] = it }
            prefs.putStash(byId.values.filter { it.isRelevantAt(nowMillis) })
        }
    }

    /**
     * Remove and return the still-relevant stashed alerts belonging to any of
     * [calendarIds]; drops expired entries for every calendar in passing.
     */
    suspend fun recoverFor(calendarIds: Set<Long>, nowMillis: Long): List<ReminderAlert> {
        val recovered = mutableListOf<ReminderAlert>()
        store.edit { prefs ->
            val kept = decodeAll(prefs).filter { alert ->
                when {
                    !alert.isRelevantAt(nowMillis) -> false // expired: drop
                    alert.calendarId in calendarIds -> { recovered += alert; false }
                    else -> true
                }
            }
            prefs.putStash(kept)
        }
        return recovered
    }

    /** Drop entries whose event has already ended — cheap opportunistic cleanup. */
    suspend fun purgeExpired(nowMillis: Long) {
        store.edit { prefs ->
            prefs.putStash(decodeAll(prefs).filter { it.isRelevantAt(nowMillis) })
        }
    }

    private fun decodeAll(prefs: Preferences): List<ReminderAlert> =
        prefs[KEY].orEmpty().mapNotNull { decodeStashEntry(it) }

    private fun MutablePreferences.putStash(alerts: List<ReminderAlert>) {
        val encoded = alerts.map { encodeStashEntry(it) }.toSet()
        if (encoded.isEmpty()) remove(KEY) else set(KEY, encoded)
    }

    private companion object {
        val KEY = stringSetPreferencesKey("suppressed_reminders")
    }
}

// One stash entry as a delimited string. The '|' separator is safe because every
// free-text field is Base64-encoded first (that alphabet never contains '|'), and
// a null location is stored as a distinct sentinel that Base64 also never yields.
private const val FIELD_SEP = "|"
private const val NULL_LOCATION = "-"

internal fun encodeStashEntry(alert: ReminderAlert): String = listOf(
    alert.alertId.toString(),
    alert.eventId.toString(),
    alert.calendarId.toString(),
    alert.beginMillis.toString(),
    alert.endMillis.toString(),
    if (alert.isAllDay) "1" else "0",
    alert.title.toBase64(),
    alert.location?.toBase64() ?: NULL_LOCATION,
).joinToString(FIELD_SEP)

/** Reverse of [encodeStashEntry]; returns null for a malformed entry (dropped). */
internal fun decodeStashEntry(raw: String): ReminderAlert? {
    val parts = raw.split(FIELD_SEP)
    if (parts.size != 8) return null
    return try {
        ReminderAlert(
            alertId = parts[0].toLong(),
            eventId = parts[1].toLong(),
            calendarId = parts[2].toLong(),
            beginMillis = parts[3].toLong(),
            endMillis = parts[4].toLong(),
            title = parts[6].fromBase64(),
            location = parts[7].takeIf { it != NULL_LOCATION }?.fromBase64(),
            isAllDay = parts[5] == "1",
        )
    } catch (e: NumberFormatException) {
        null
    } catch (e: IllegalArgumentException) { // bad Base64
        null
    }
}

private fun String.toBase64(): String =
    Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))

private fun String.fromBase64(): String =
    String(Base64.getDecoder().decode(this), Charsets.UTF_8)
