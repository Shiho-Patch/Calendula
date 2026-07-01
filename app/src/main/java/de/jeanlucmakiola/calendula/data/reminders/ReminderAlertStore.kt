package de.jeanlucmakiola.calendula.data.reminders

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One due row of the provider's `CalendarAlerts` table (a join with Events).
 * Stays in the data layer: alerts feed the notification path only and never
 * reach a screen, so there is no domain model for them.
 */
data class ReminderAlert(
    val alertId: Long,
    val eventId: Long,
    val calendarId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    /** Raw event title; may be blank — the notifier substitutes "(no title)". */
    val title: String,
    val location: String?,
    val isAllDay: Boolean,
)

/**
 * Seam over the `CalendarAlerts` table so the receiver logic can be exercised
 * without a ContentResolver. The provider creates these rows itself — only
 * for `METHOD_ALERT` reminders (verified in AOSP `CalendarAlarmManager`), so
 * email reminders never show up here.
 */
interface ReminderAlertStore {

    /** Alerts that are due (`ALARM_TIME` has passed) and still unhandled. */
    fun dueAlerts(nowMillis: Long): List<ReminderAlert>

    /**
     * Mark the given alerts handled (`STATE_FIRED`) so a later broadcast does
     * not surface them again. Best effort: this write needs `WRITE_CALENDAR`,
     * which the user may have declined — then re-broadcasts silently replace
     * the already-posted notifications instead (same tag, alert-once).
     */
    fun markFired(alertIds: List<Long>, nowMillis: Long)
}

@Singleton
class AndroidReminderAlertStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderAlertStore {

    override fun dueAlerts(nowMillis: Long): List<ReminderAlert> = context.contentResolver.query(
        CalendarContract.CalendarAlerts.CONTENT_URI,
        PROJECTION,
        CalendarContract.CalendarAlerts.STATE + " = ? AND " +
            CalendarContract.CalendarAlerts.ALARM_TIME + " <= ?",
        arrayOf(
            CalendarContract.CalendarAlerts.STATE_SCHEDULED.toString(),
            nowMillis.toString(),
        ),
        CalendarContract.CalendarAlerts.BEGIN + " ASC",
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    ReminderAlert(
                        alertId = c.getLong(0),
                        eventId = c.getLong(1),
                        calendarId = c.getLong(2),
                        beginMillis = c.getLong(3),
                        endMillis = c.getLong(4),
                        title = c.getString(5).orEmpty(),
                        location = c.getString(6)?.takeIf { it.isNotBlank() },
                        isAllDay = c.getInt(7) == 1,
                    ),
                )
            }
        }
    } ?: emptyList()

    override fun markFired(alertIds: List<Long>, nowMillis: Long) {
        if (alertIds.isEmpty()) return
        val values = ContentValues().apply {
            put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_FIRED)
            put(CalendarContract.CalendarAlerts.RECEIVED_TIME, nowMillis)
            put(CalendarContract.CalendarAlerts.NOTIFY_TIME, nowMillis)
        }
        try {
            context.contentResolver.update(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                values,
                CalendarContract.CalendarAlerts._ID +
                    " IN (" + alertIds.joinToString(",") + ")",
                null,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot mark alerts fired without WRITE_CALENDAR", e)
        }
    }

    private companion object {
        const val TAG = "ReminderAlertStore"
        val PROJECTION = arrayOf(
            CalendarContract.CalendarAlerts._ID,
            CalendarContract.CalendarAlerts.EVENT_ID,
            CalendarContract.CalendarAlerts.CALENDAR_ID,
            CalendarContract.CalendarAlerts.BEGIN,
            CalendarContract.CalendarAlerts.END,
            CalendarContract.CalendarAlerts.TITLE,
            CalendarContract.CalendarAlerts.EVENT_LOCATION,
            CalendarContract.CalendarAlerts.ALL_DAY,
        )
    }
}
