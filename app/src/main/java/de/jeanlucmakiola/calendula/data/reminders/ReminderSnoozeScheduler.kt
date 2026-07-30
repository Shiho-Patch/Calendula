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
 * Schedules a one-off exact alarm that re-shows a snoozed reminder. Separate
 * from [ReminderAlarmScheduler]'s single moving scan alarm: a snooze is pinned
 * to one reminder and has to outlive the watermark moving past it, so it carries
 * the reminder in its own intent.
 *
 * Falls back to an inexact allow-while-idle alarm where the OS withholds the
 * exact-alarm capability (API 31–32 with the permission revoked).
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
            // Exact alarms revoked (API 31–32); an inexact wake is the best
            // available without nagging for SCHEDULE_EXACT_ALARM.
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
