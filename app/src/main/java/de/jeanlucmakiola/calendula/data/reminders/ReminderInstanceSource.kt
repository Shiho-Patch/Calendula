package de.jeanlucmakiola.calendula.data.reminders

import android.content.Context
import android.content.ContentUris
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.reminders.ReminderEventInstance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The read side of in-house reminder delivery: occurrences and their reminder
 * offsets, read from `Instances` and `Reminders` rather than `CalendarAlerts`,
 * which cannot be assumed to be written (#75).
 *
 * An interface so [ReminderScanner] can be exercised on the JVM.
 */
interface ReminderInstanceSource {

    /** Occurrences overlapping `[fromMillis, toMillis]`, of switched-on calendars. */
    fun occurrences(fromMillis: Long, toMillis: Long): List<ReminderEventInstance>

    /** `METHOD_ALERT` reminder offsets per event id, for the given events. */
    fun reminderMinutes(eventIds: Collection<Long>): Map<Long, List<Int>>

    /**
     * The largest `METHOD_ALERT` offset anywhere in the table, so the query
     * window can be stretched to cover it and a long-lead reminder is planned
     * before it comes due rather than firing late.
     */
    fun longestReminderMinutes(): Int
}

@Singleton
class ProviderReminderInstanceSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderInstanceSource {

    override fun occurrences(fromMillis: Long, toMillis: Long): List<ReminderEventInstance> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMillis)
            ContentUris.appendId(this, toMillis)
        }.build()
        // `visible` is the flag the app's one visibility model writes (#75).
        // The status clause mirrors CalendarDataSource.instances: NULL means
        // "normal", so a bare `!= CANCELED` would drop every ordinary event.
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
        return context.contentResolver.query(
            uri, OCCURRENCE_PROJECTION, selection, null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ReminderEventInstance(
                            eventId = c.getLong(0),
                            calendarId = c.getLong(1),
                            beginMillis = c.getLong(2),
                            endMillis = if (c.isNull(3)) 0L else c.getLong(3),
                            title = c.getString(4).orEmpty(),
                            location = c.getString(5)?.takeIf { it.isNotBlank() },
                            isAllDay = c.getInt(6) == 1,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    override fun reminderMinutes(eventIds: Collection<Long>): Map<Long, List<Int>> {
        if (eventIds.isEmpty()) return emptyMap()
        val out = mutableMapOf<Long, MutableList<Int>>()
        // Batched: the ids go into the selection literally, and an unbounded
        // `IN (...)` would grow the SQL past what SQLite takes.
        eventIds.distinct().chunked(EVENT_ID_BATCH).forEach { batch ->
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                REMINDER_PROJECTION,
                "${CalendarContract.Reminders.METHOD} = " +
                    "${CalendarContract.Reminders.METHOD_ALERT} AND " +
                    "${CalendarContract.Reminders.EVENT_ID} IN (${batch.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out.getOrPut(c.getLong(0)) { mutableListOf() } += c.getInt(1)
                }
            }
        }
        return out
    }

    override fun longestReminderMinutes(): Int = context.contentResolver.query(
        CalendarContract.Reminders.CONTENT_URI,
        arrayOf(CalendarContract.Reminders.MINUTES),
        "${CalendarContract.Reminders.METHOD} = ${CalendarContract.Reminders.METHOD_ALERT}",
        null,
        // One row is enough: the provider passes the sort order to SQLite.
        "${CalendarContract.Reminders.MINUTES} DESC",
    )?.use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } ?: 0

    private companion object {
        const val EVENT_ID_BATCH = 50

        val OCCURRENCE_PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
        )

        val REMINDER_PROJECTION = arrayOf(
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES,
        )
    }
}
