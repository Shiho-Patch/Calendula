package de.jeanlucmakiola.calendula.data.reminders

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Becomes the app that turns the calendar provider's reminder alarms into
 * visible notifications (the Etar model — the provider broadcasts
 * `EVENT_REMINDER` at reminder time but posts nothing itself).
 *
 * The broadcast's data URI only carries the alarm time, so it is ignored:
 * we query every still-scheduled, due `CalendarAlerts` row ourselves, post
 * them, and mark them fired. Posting happens before marking — a crash in
 * between re-posts silently (same tag) rather than losing the reminder.
 *
 * There is no per-calendar filtering here: a calendar switched off in
 * Settings → Calendars has `Calendars.VISIBLE = 0`, and the provider creates no
 * alert rows for it in the first place (#75).
 */
@AndroidEntryPoint
class EventReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var alertStore: ReminderAlertStore
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var settingsPrefs: SettingsPrefs

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
                    due.forEach { notifier.post(it) }
                    alertStore.markFired(due.map { it.alertId }, now)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
