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
 * Posts one notification per due reminder on a dedicated channel. Tapping opens
 * the event's detail screen.
 *
 * The tag is the reminder's stable key, so a scan that posts the same reminder
 * again — a catch-up pass overlapping the alarm that already fired — replaces
 * its notification silently ([NotificationCompat.Builder.setOnlyAlertOnce])
 * instead of stacking a second one.
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
     * The single choke point for "this calendar is switched off", covering the
     * two paths that reach [post] around the scan's own filter: a snooze armed
     * before the switch-off, and a read-only install whose switch lives in
     * [CalendarPrefs].
     */
    private suspend fun isSilenced(calendarId: Long): Boolean =
        calendarId in calendarPrefs.pendingDisabledCalendarIds.first() ||
            calendarDataSource.isCalendarVisible(calendarId) == false

    /**
     * Post [alert], unless its calendar is switched off. Returns whether the
     * notification was put up, which the snooze re-show path uses to tell a
     * silenced reminder from a delivered one.
     */
    suspend fun post(alert: ReminderAlert): Boolean {
        if (isSilenced(alert.calendarId)) return false
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
            todayLabel = context.getString(R.string.reminder_day_today),
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
                .notify(alert.key.toString(), NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between canPost() and here.
            Log.w(TAG, "Could not post reminder for event ${alert.eventId}", e)
        }
        // Handled either way — a retry hits the same revoked permission.
        return true
    }

    /** Remove a posted reminder (snooze re-shows it later; dismiss is final). */
    fun cancel(alert: ReminderAlert) {
        NotificationManagerCompat.from(context).cancel(alert.key.toString(), NOTIFICATION_ID)
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
        // Shares the per-(alert, action) request-code scheme with the buttons.
        /* requestCode = */ ReminderActionReceiver.requestCode(
            alert, ReminderActionReceiver.ACTION_OPEN,
        ),
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
