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
) {

    /** False when the user declined `POST_NOTIFICATIONS` or muted the app. */
    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun post(alert: ReminderAlert) {
        ensureChannel()
        val title = alert.title.ifBlank { context.getString(R.string.event_untitled) }
        val time = reminderTimeText(
            beginMillis = alert.beginMillis,
            endMillis = alert.endMillis,
            isAllDay = alert.isAllDay,
            zone = ZoneId.systemDefault(),
            locale = Locale.getDefault(),
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
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify(alert.alertId.toString(), NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS was revoked between canPost() and here.
            Log.w(TAG, "Could not post reminder for event ${alert.eventId}", e)
        }
    }

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
