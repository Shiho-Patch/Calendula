package de.jeanlucmakiola.calendula.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.isoDayNumber
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts one notification per due reminder alert on a dedicated channel.
 * Tapping opens the event's detail screen; the tag is the alert id, so a
 * re-broadcast of an alert we couldn't mark fired replaces its notification
 * silently ([NotificationCompat.Builder.setOnlyAlertOnce]) instead of
 * duplicating it.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPrefs: SettingsPrefs,
    private val calendarPrefs: CalendarPrefs,
    private val calendarDataSource: CalendarDataSource,
) {

    /** False when the user declined `POST_NOTIFICATIONS` or muted the app. */
    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * The single choke point for "this calendar is switched off". The provider
     * side needs no help — with `VISIBLE = 0` it creates no alert rows at all —
     * but two paths reach [post] without one: a snooze we re-show from our own
     * exact alarm, scheduled before the calendar was switched off, and a
     * read-only install whose switch lives app-side ([CalendarPrefs]) because it
     * may not write the flag. Both are covered here rather than in either
     * receiver.
     */
    private suspend fun isSilenced(calendarId: Long): Boolean =
        calendarId in calendarPrefs.pendingDisabledCalendarIds.first() ||
            calendarDataSource.isCalendarVisible(calendarId) == false

    suspend fun post(alert: ReminderAlert) {
        if (isSilenced(alert.calendarId)) return
        ensureChannel()
        val title = alert.title.ifBlank { context.getString(R.string.event_untitled) }
        val is24Hour = settingsPrefs.timeFormat.first()
            .is24Hour(android.text.format.DateFormat.is24HourFormat(context))
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        // resolveFirstDay yields a kotlinx.datetime day; bridge it to java.time by
        // its shared ISO number (1..7) for the date math in reminderTimeText.
        val firstDayOfWeek = DayOfWeek.of(settingsPrefs.weekStart.first().resolveFirstDay(locale).isoDayNumber)
        val time = reminderTimeText(
            beginMillis = alert.beginMillis,
            endMillis = alert.endMillis,
            isAllDay = alert.isAllDay,
            zone = zone,
            locale = locale,
            is24Hour = is24Hour,
            today = Instant.now().atZone(zone).toLocalDate(),
            firstDayOfWeek = firstDayOfWeek,
            tomorrowLabel = context.getString(R.string.reminder_day_tomorrow),
            yesterdayLabel = context.getString(R.string.reminder_day_yesterday),
        )
        val text = listOfNotNull(time, alert.location).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(alert.beginMillis)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(detailIntent(alert))
            .addAction(
                R.drawable.ic_notification_snooze,
                context.getString(R.string.reminder_action_snooze),
                actionIntent(alert, ReminderActionReceiver.ACTION_SNOOZE),
            )
            .addAction(
                R.drawable.ic_notification_dismiss,
                context.getString(R.string.reminder_action_dismiss),
                actionIntent(alert, ReminderActionReceiver.ACTION_DISMISS),
            )
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(alert.alertId.toString(), NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between canPost() and here.
            Log.w(TAG, "Could not post reminder for event ${alert.eventId}", e)
        }
    }

    /** Remove a posted reminder (snooze re-shows it later; dismiss is final). */
    fun cancel(alert: ReminderAlert) {
        NotificationManagerCompat.from(context).cancel(alert.alertId.toString(), NOTIFICATION_ID)
    }

    private fun actionIntent(alert: ReminderAlert, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ReminderActionReceiver.requestCode(alert, action),
            ReminderActionReceiver.intent(context, action, alert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun detailIntent(alert: ReminderAlert): PendingIntent = PendingIntent.getActivity(
        context,
        /* requestCode = */ alert.alertId.toInt(),
        MainActivity.eventDetailIntent(context, alert.eventId, alert.beginMillis, alert.endMillis),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Channel creation is idempotent; re-running refreshes the localized name. */
    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "ReminderNotifier"
        const val CHANNEL_ID = "reminders"

        // One id, distinct tags: the tag (alert id) already keys the
        // notification, so a fixed id keeps cancellation/replacement simple.
        const val NOTIFICATION_ID = 1
    }
}
