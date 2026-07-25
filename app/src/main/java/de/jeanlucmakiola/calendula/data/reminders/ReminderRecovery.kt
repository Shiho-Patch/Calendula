package de.jeanlucmakiola.calendula.data.reminders

import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-posts the reminders a switched-off calendar silenced, when it is switched
 * back on while they still matter.
 *
 * Only the app-side switch needs this — a read-only install, or an upgrade the
 * reconciler hasn't flushed yet. With `Calendars.VISIBLE = 0` the provider
 * deletes the calendar's alert rows itself and re-creates them on the way back;
 * app-side the rows stay, still `STATE_SCHEDULED`, because
 * [EventReminderReceiver] deliberately leaves the ones it silenced unhandled
 * (see [handledAlertIds]). So the provider's own table is the stash, and nothing
 * is mirrored locally.
 *
 * Best effort at switch-on time: it mirrors the receiver's gates (reminders on,
 * notifications postable) and there is no later re-scan, so an alert left
 * unposted because those are closed is simply released.
 */
@Singleton
class ReminderRecovery @Inject constructor(
    private val alertStore: ReminderAlertStore,
    private val notifier: ReminderNotifier,
    private val settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun rePostFor(calendarIds: Collection<Long>) = withContext(io) {
        if (calendarIds.isEmpty()) return@withContext
        if (!settingsPrefs.remindersEnabled.first() || !notifier.canPost()) return@withContext
        val now = System.currentTimeMillis()
        val ids = calendarIds.toSet()
        val recovered = alertStore.dueAlerts(now)
            .filter { it.calendarId in ids && it.isRelevantAt(now) }
        if (recovered.isEmpty()) return@withContext
        val postedIds = recovered.filter { notifier.post(it) }.map { it.alertId }
        alertStore.markFired(postedIds, now)
    }
}
