package de.jeanlucmakiola.calendula.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * True on API < 31 (no restriction), and on 31+ when the exact-alarm capability
 * is held — auto-granted via `USE_EXACT_ALARM` on API 33+ (Calendula is a
 * calendar app), user-revocable on 31–32.
 */
internal fun AlarmManager.canScheduleExactCompat(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()

/**
 * Holds the app's own wake-up for the next reminder (#75). Exactly one alarm
 * exists at a time, for the earliest reminder ahead; every firing re-scans and
 * re-arms. Exact, with an inexact allow-while-idle fallback where the OS
 * withholds the capability (API 31–32 with the permission revoked).
 */
@Singleton
class ReminderAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun scheduleScan(triggerAtMillis: Long) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = scanPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT)
        if (alarmManager.canScheduleExactCompat()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
            )
        }
    }

    /** Drop the pending wake-up — reminders are off, or there is nothing to wait for. */
    fun cancelScan() {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        alarmManager.cancel(scanPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun scanPendingIntent(flags: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        SCAN_REQUEST_CODE,
        Intent(context, ReminderScheduleReceiver::class.java)
            .setAction(ReminderScheduleReceiver.ACTION_SCAN),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        // Fixed: there is only ever one scan alarm, and re-arming must replace it.
        const val SCAN_REQUEST_CODE = 0x5CA1
    }
}
