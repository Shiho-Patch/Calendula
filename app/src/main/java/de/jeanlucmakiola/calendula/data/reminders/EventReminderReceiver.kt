package de.jeanlucmakiola.calendula.data.reminders

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * True when [this] alert belongs to a calendar the user disabled in-app, so its
 * reminder must be suppressed (mirroring the event filtering in
 * CalendarRepositoryImpl). Alerts whose calendar is unknown (id 0L — e.g. a
 * pre-upgrade snooze PendingIntent minted before EXTRA_CALENDAR_ID existed) are
 * never treated as disabled. This is the one predicate the disabled-calendar
 * gate is built from: [postableAlerts] here and the choke point in
 * [ReminderNotifier.post] both use it.
 */
internal fun ReminderAlert.isForDisabledCalendar(disabledCalendarIds: Set<Long>): Boolean =
    calendarId != 0L && calendarId in disabledCalendarIds

/**
 * The due alerts that should actually surface as notifications: everything
 * except alerts whose calendar the user has disabled in-app. The caller still
 * marks the full due set fired, so suppressed alerts are not re-broadcast by the
 * provider.
 */
internal fun postableAlerts(
    due: List<ReminderAlert>,
    disabledCalendarIds: Set<Long>,
): List<ReminderAlert> = due.filterNot { it.isForDisabledCalendar(disabledCalendarIds) }

/**
 * Becomes the app that turns the calendar provider's reminder alarms into
 * visible notifications (the Etar model — the provider broadcasts
 * `EVENT_REMINDER` at reminder time but posts nothing itself).
 *
 * The broadcast's data URI only carries the alarm time, so it is ignored:
 * we query every still-scheduled, due `CalendarAlerts` row ourselves, post
 * them, and mark them fired. Posting happens before marking — a crash in
 * between re-posts silently (same tag) rather than losing the reminder.
 */
@AndroidEntryPoint
class EventReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var alertStore: ReminderAlertStore
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var settingsPrefs: SettingsPrefs
    @Inject lateinit var calendarPrefs: CalendarPrefs
    @Inject lateinit var suppressedStore: SuppressedReminderStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CalendarContract.ACTION_EVENT_REMINDER) return
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (!readGranted || !notifier.canPost()) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settingsPrefs.remindersEnabled.first()) {
                    val now = System.currentTimeMillis()
                    val due = alertStore.dueAlerts(now)
                    val disabled = calendarPrefs.disabledCalendarIds.first()
                    val postable = postableAlerts(due, disabled)
                    // Suppress reminders for disabled calendars, but still mark
                    // every due alert fired so the provider stops re-broadcasting
                    // the suppressed ones. Stash those suppressed alerts so
                    // re-enabling their calendar can recover them (they would
                    // otherwise stay STATE_FIRED forever with no re-scan).
                    postable.forEach { notifier.post(it) }
                    alertStore.markFired(due.map { it.alertId }, now)
                    suppressedStore.stash(due - postable.toSet(), now)
                    suppressedStore.purgeExpired(now)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
