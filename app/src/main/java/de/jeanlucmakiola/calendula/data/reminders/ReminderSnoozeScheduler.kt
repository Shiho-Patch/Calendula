package de.jeanlucmakiola.calendula.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules a one-off exact alarm that re-shows a snoozed reminder.
 *
 * The app otherwise relies entirely on the calendar provider's `EVENT_REMINDER`
 * broadcast (the Etar model), but a snoozed reminder has no provider backing —
 * its `CalendarAlerts` row is already fired — so we must re-fire it ourselves.
 * A snooze that lands late is a broken snooze, hence an *exact* alarm; we fall
 * back to an inexact allow-while-idle alarm only if the OS withholds the
 * exact-alarm capability (API 31–32 where the user revoked it).
 */
@Singleton
class ReminderSnoozeScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun schedule(alert: ReminderAlert, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ReminderActionReceiver.requestCode(alert, ReminderActionReceiver.ACTION_SHOW),
            ReminderActionReceiver.intent(context, ReminderActionReceiver.ACTION_SHOW, alert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        } else {
            // Exact alarms revoked (API 31–32): an inexact wake is the honest
            // best we can do without nagging for SCHEDULE_EXACT_ALARM.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        }
    }

    /**
     * True on API < 31 (no restriction), and on 31+ when the exact-alarm
     * capability is held — auto-granted via `USE_EXACT_ALARM` on API 33+
     * (Calendula is a calendar app), user-revocable on 31–32.
     */
    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
