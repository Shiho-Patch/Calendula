package de.jeanlucmakiola.calendula.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles the "Snooze" and "Dismiss" actions on a reminder notification, plus
 * the internal re-show when a snooze elapses. All three are app-internal
 * intents (notification action buttons and our own [ReminderSnoozeScheduler]
 * alarm), so the receiver is not exported.
 *
 * - **Dismiss** just cancels the notification — the `CalendarAlerts` row is
 *   already fired, so nothing re-posts it.
 * - **Snooze** cancels the notification and schedules an exact alarm to re-show
 *   it after the user's snooze delay.
 * - **Show** (the alarm) re-posts the same notification, so the user can snooze
 *   or dismiss it again — unless the calendar was switched off during the
 *   snooze, which [ReminderNotifier.post] catches (this alarm is ours, so no
 *   provider alert row stands between it and the notification).
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var scheduler: ReminderSnoozeScheduler
    @Inject lateinit var settingsPrefs: SettingsPrefs

    override fun onReceive(context: Context, intent: Intent) {
        val alert = alertFrom(intent) ?: return
        when (intent.action) {
            ACTION_DISMISS -> notifier.cancel(alert)

            ACTION_SNOOZE -> {
                // Cancel now so the notification doesn't linger until the alarm;
                // the snooze delay read is the only async work.
                notifier.cancel(alert)
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val minutes = settingsPrefs.snoozeMinutes.first()
                        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
                        scheduler.schedule(alert, triggerAt)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_SHOW -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        if (settingsPrefs.remindersEnabled.first() && notifier.canPost()) {
                            notifier.post(alert)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "de.jeanlucmakiola.calendula.reminders.SNOOZE"
        const val ACTION_DISMISS = "de.jeanlucmakiola.calendula.reminders.DISMISS"
        const val ACTION_SHOW = "de.jeanlucmakiola.calendula.reminders.SHOW"

        private const val EXTRA_ALERT_ID = "alert_id"
        private const val EXTRA_EVENT_ID = "event_id"
        private const val EXTRA_CALENDAR_ID = "calendar_id"
        private const val EXTRA_BEGIN = "begin"
        private const val EXTRA_END = "end"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_ALL_DAY = "all_day"

        /** An explicit intent to this receiver carrying [alert] as extras. */
        fun intent(context: Context, action: String, alert: ReminderAlert): Intent =
            Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_ALERT_ID, alert.alertId)
                putExtra(EXTRA_EVENT_ID, alert.eventId)
                putExtra(EXTRA_CALENDAR_ID, alert.calendarId)
                putExtra(EXTRA_BEGIN, alert.beginMillis)
                putExtra(EXTRA_END, alert.endMillis)
                putExtra(EXTRA_TITLE, alert.title)
                putExtra(EXTRA_LOCATION, alert.location)
                putExtra(EXTRA_ALL_DAY, alert.isAllDay)
            }

        /**
         * A stable request code per (alert, action) so the three PendingIntents
         * of one notification stay distinct and don't clobber each other.
         */
        fun requestCode(alert: ReminderAlert, action: String): Int {
            val actionOffset = when (action) {
                ACTION_SNOOZE -> 1
                ACTION_DISMISS -> 2
                ACTION_SHOW -> 3
                else -> 0
            }
            return alert.alertId.toInt() * 8 + actionOffset
        }

        private fun alertFrom(intent: Intent): ReminderAlert? {
            if (!intent.hasExtra(EXTRA_ALERT_ID)) return null
            return ReminderAlert(
                alertId = intent.getLongExtra(EXTRA_ALERT_ID, 0L),
                eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L),
                calendarId = intent.getLongExtra(EXTRA_CALENDAR_ID, 0L),
                beginMillis = intent.getLongExtra(EXTRA_BEGIN, 0L),
                endMillis = intent.getLongExtra(EXTRA_END, 0L),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                location = intent.getStringExtra(EXTRA_LOCATION),
                isAllDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false),
            )
        }
    }
}
