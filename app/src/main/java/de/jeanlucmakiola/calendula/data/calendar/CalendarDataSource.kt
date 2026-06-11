package de.jeanlucmakiola.calendula.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.rruleTruncatedAt
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-shaped seam over Android's ContentResolver. Returns parsed lists so
 * the repository can be unit-tested without constructing Cursors or
 * ContentObservers on the JVM.
 *
 * Cursor handling and the ContentObserver-to-listener bridge live entirely
 * in AndroidCalendarDataSource.
 */
interface CalendarDataSource {
    fun calendars(): List<CalendarSource>
    fun instances(beginMillis: Long, endMillis: Long): List<EventInstance>
    fun eventDetail(eventId: Long): EventDetail?

    /** Insert a new event; returns the new `Events._ID`. */
    fun insertEvent(form: EventForm): Long

    /**
     * Update an existing event (for recurring events: the whole series) to
     * match [updated]. [original] is the form as it was prefilled from the
     * event, so only fields the user actually changed are written and the
     * reminder rows can be diffed instead of wiped.
     */
    fun updateEvent(eventId: Long, original: EventForm, updated: EventForm)

    /**
     * Change a single occurrence of a recurring event by inserting a
     * modified-occurrence exception at [beginMillis] (the occurrence's
     * `Instances.BEGIN`) carrying [form]'s values; returns the exception
     * row's `Events._ID`.
     */
    fun updateOccurrence(eventId: Long, beginMillis: Long, form: EventForm): Long

    /**
     * Change a recurring event from the occurrence at [beginMillis] onwards
     * by splitting the series: the existing RRULE ends just before the
     * occurrence and a new event with [updated]'s values (and rule) starts
     * there; returns the new event's `Events._ID`. From the first occurrence
     * this is a plain series update. A carried-over COUNT restarts counting
     * in the new series (we don't recompute the remaining occurrences).
     */
    fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
    ): Long

    /**
     * Delete a recurring event from the occurrence at [beginMillis] onwards
     * by ending the series RRULE just before it. Deleting from the first
     * occurrence removes the whole event.
     */
    fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long)

    /** Delete the whole event (for recurring events: the entire series). */
    fun deleteEvent(eventId: Long)

    /**
     * Cancel a single occurrence of a recurring event by inserting a
     * cancelled exception at [beginMillis] (the occurrence's `Instances.BEGIN`).
     */
    fun deleteOccurrence(eventId: Long, beginMillis: Long)

    fun registerChangeListener(listener: () -> Unit)
    fun unregisterChangeListener(listener: () -> Unit)
}

@Singleton
class AndroidCalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarDataSource {

    private val resolver: ContentResolver get() = context.contentResolver
    private val observers = mutableMapOf<() -> Unit, ContentObserver>()

    override fun calendars(): List<CalendarSource> = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        CalendarProjection.COLUMNS,
        null, null,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
    )?.use { it.mapAll(::toCalendarSource) } ?: emptyList()

    override fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, beginMillis)
            ContentUris.appendId(this, endMillis)
        }.build()
        return resolver.query(
            uri,
            InstanceProjection.COLUMNS,
            null, null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c -> c.mapAllNotNull { CursorColumnReader(c).toEventInstance() } } ?: emptyList()
    }

    override fun eventDetail(eventId: Long): EventDetail? {
        val attendees = queryAttendees(eventId)
        val reminders = queryReminders(eventId)
        return resolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            EventDetailProjection.COLUMNS,
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) null
            else CursorColumnReader(c).toEventDetailCore(attendees, reminders)
        }
    }

    override fun insertEvent(form: EventForm): Long {
        val times = form.toWriteTimes(ZoneId.systemDefault())
        val values = ContentValues().apply {
            put(
                CalendarContract.Events.CALENDAR_ID,
                requireNotNull(form.calendarId) { "EventForm.calendarId is required" },
            )
            put(CalendarContract.Events.TITLE, form.title.trim())
            put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
            put(CalendarContract.Events.DTSTART, times.dtStartMillis)
            // The provider's invariant: recurring rows carry RRULE+DURATION
            // (and no DTEND), one-off rows carry DTEND.
            if (form.rrule == null) {
                put(CalendarContract.Events.DTEND, times.dtEndMillis)
            } else {
                put(CalendarContract.Events.RRULE, form.rrule)
                put(CalendarContract.Events.DURATION, times.toRfc2445Duration(form.isAllDay))
            }
            put(CalendarContract.Events.EVENT_TIMEZONE, times.timezone)
            put(CalendarContract.Events.AVAILABILITY, form.availability.toProviderValue())
            put(CalendarContract.Events.ACCESS_LEVEL, form.accessLevel.toProviderValue())
            form.location.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            form.description.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw WriteFailedException("insert event into calendar id=${form.calendarId}")
        val eventId = ContentUris.parseId(uri)
        // Best effort (spec §8): the event exists at this point — a reminder
        // that fails to attach is logged, not surfaced as a failed create.
        form.reminders.distinct().forEach { minutes ->
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
                Log.w(TAG, "Failed to attach reminder ($minutes min) to event $eventId")
            }
        }
        return eventId
    }

    override fun updateEvent(eventId: Long, original: EventForm, updated: EventForm) {
        val values = buildEventUpdateValues(
            original = original,
            updated = updated,
            seriesDtStartMillis = querySeriesRow(eventId).dtStartMillis,
            zone = ZoneId.systemDefault(),
        )
        if (values.isNotEmpty()) {
            val rows = resolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                values.toContentValues(),
                null, null,
            )
            if (rows == 0) throw WriteFailedException("update event id=$eventId")
        }
        // Untouched reminder sets are left alone so unrelated edits can't
        // disturb provider rows the form never knew about.
        if (updated.reminders.toSet() != original.reminders.toSet()) {
            reconcileReminders(eventId, updated.reminders)
        }
    }

    override fun updateOccurrence(eventId: Long, beginMillis: Long, form: EventForm): Long {
        // The provider clones the series row and applies these values on top.
        val values = buildOccurrenceExceptionValues(
            form = form,
            originalInstanceMillis = beginMillis,
            zone = ZoneId.systemDefault(),
        )
        val uri = resolver.insert(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId),
            values.toContentValues(),
        ) ?: throw WriteFailedException("modify occurrence event id=$eventId begin=$beginMillis")
        val exceptionId = ContentUris.parseId(uri)
        // Whether the provider copied the parent's reminder rows is its
        // business — reconciling against the actual rows handles both ways.
        reconcileReminders(exceptionId, form.reminders)
        return exceptionId
    }

    override fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
    ): Long {
        val row = querySeriesRow(eventId)
        // From the first occurrence on (or with no rule to split) this is
        // just a series update.
        if (row.rrule.isNullOrBlank() || beginMillis <= row.dtStartMillis) {
            updateEvent(eventId, original, updated)
            return eventId
        }
        // Insert the new series first: if it fails, the original is untouched.
        val newEventId = insertEvent(updated)
        truncateSeries(eventId, row, beginMillis)
        return newEventId
    }

    override fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long) {
        val row = querySeriesRow(eventId)
        // From the first occurrence on = the whole series; also the fallback
        // when there is no RRULE to truncate.
        if (row.rrule.isNullOrBlank() || beginMillis <= row.dtStartMillis) {
            deleteEvent(eventId)
            return
        }
        truncateSeries(eventId, row, beginMillis)
    }

    /**
     * End [row]'s series just before the occurrence at [beginMillis]. The
     * provider regenerates an event's cached instances only from the values
     * carried by the update itself — an RRULE-only update leaves the old
     * instances standing (observed on-device: the truncated occurrence kept
     * showing) — so the entire time-related set travels together, with only
     * the RRULE actually changing.
     */
    private fun truncateSeries(eventId: Long, row: SeriesRow, beginMillis: Long) {
        requireNotNull(row.rrule) { "truncateSeries needs a recurring row" }
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, row.dtStartMillis)
            put(CalendarContract.Events.DURATION, row.duration)
            put(CalendarContract.Events.EVENT_TIMEZONE, row.timezone)
            put(CalendarContract.Events.ALL_DAY, row.allDay)
            put(
                CalendarContract.Events.RRULE,
                rruleTruncatedAt(row.rrule, untilUtcMillis = row.truncationCutoff(beginMillis)),
            )
        }
        val rows = resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            values,
            null, null,
        )
        if (rows == 0) {
            throw WriteFailedException("truncate series event id=$eventId begin=$beginMillis")
        }
    }

    /** The series anchor: every time-related column of the Events row. */
    private fun querySeriesRow(eventId: Long): SeriesRow = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        arrayOf(
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
        ),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            SeriesRow(
                dtStartMillis = c.getLong(0),
                rrule = c.getString(1),
                timezone = c.getString(2),
                duration = c.getString(3),
                allDay = c.getInt(4),
            )
        } else {
            null
        }
    } ?: throw WriteFailedException("read series row of event id=$eventId")

    private data class SeriesRow(
        val dtStartMillis: Long,
        val rrule: String?,
        val timezone: String?,
        val duration: String?,
        val allDay: Int,
    ) {
        /** UNTIL cutoff for ending the series before the occurrence at [beginMillis]. */
        fun truncationCutoff(beginMillis: Long): Long = previousLocalDayEndUtcMillis(
            beginMillis = beginMillis,
            zone = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneOffset.UTC),
        )
    }

    /**
     * Make the event's reminder rows match [targetMinutes]: rows with other
     * lead times are deleted, missing ones inserted as best-effort ALERTs
     * (like insertEvent). Rows whose minutes survive keep their method.
     */
    private fun reconcileReminders(eventId: Long, targetMinutes: List<Int>) {
        val target = targetMinutes.toSet()
        val existing = queryReminders(eventId).map { it.minutes }.toSet()
        (existing - target).forEach { minutes ->
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                CalendarContract.Reminders.EVENT_ID + " = ? AND " +
                    CalendarContract.Reminders.MINUTES + " = ?",
                arrayOf(eventId.toString(), minutes.toString()),
            )
        }
        (target - existing).forEach { minutes ->
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
                Log.w(TAG, "Failed to attach reminder ($minutes min) to event $eventId")
            }
        }
    }

    private fun Map<String, Any?>.toContentValues(): ContentValues =
        ContentValues().also { cv ->
            forEach { (column, value) ->
                when (value) {
                    null -> cv.putNull(column)
                    is String -> cv.put(column, value)
                    is Long -> cv.put(column, value)
                    is Int -> cv.put(column, value)
                    else -> error("Unsupported value for $column: $value")
                }
            }
        }

    override fun deleteEvent(eventId: Long) {
        val deleted = resolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null, null,
        )
        if (deleted == 0) throw WriteFailedException("delete event id=$eventId")
    }

    override fun deleteOccurrence(eventId: Long, beginMillis: Long) {
        // A cancelled exception row hides exactly this occurrence; the sync
        // adapter turns it into an EXDATE/cancelled VEVENT upstream.
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, beginMillis)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId,
        )
        resolver.insert(uri, values)
            ?: throw WriteFailedException("cancel occurrence event id=$eventId begin=$beginMillis")
    }

    override fun registerChangeListener(listener: () -> Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                listener()
            }
        }
        observers[listener] = obs
        resolver.registerContentObserver(
            CalendarContract.CONTENT_URI,
            /* notifyForDescendants = */ true,
            obs,
        )
    }

    override fun unregisterChangeListener(listener: () -> Unit) {
        observers.remove(listener)?.let { resolver.unregisterContentObserver(it) }
    }

    private fun queryAttendees(eventId: Long): List<Attendee> = resolver.query(
        CalendarContract.Attendees.CONTENT_URI,
        AttendeeProjection.COLUMNS,
        CalendarContract.Attendees.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c -> c.mapAll { CursorColumnReader(c).toAttendee() } } ?: emptyList()

    private fun queryReminders(eventId: Long): List<Reminder> = resolver.query(
        CalendarContract.Reminders.CONTENT_URI,
        ReminderProjection.COLUMNS,
        CalendarContract.Reminders.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c -> c.mapAll { CursorColumnReader(c).toReminder() } } ?: emptyList()

    private fun toCalendarSource(c: Cursor): CalendarSource = CursorColumnReader(c).toCalendarSource()

    /** Iterate every row and map; skips nothing. */
    private inline fun <T> Cursor.mapAll(mapper: (Cursor) -> T): List<T> = buildList {
        while (moveToNext()) add(mapper(this@mapAll))
    }

    /** Iterate every row and map; drops nulls. */
    private inline fun <T : Any> Cursor.mapAllNotNull(mapper: (Cursor) -> T?): List<T> = buildList {
        while (moveToNext()) mapper(this@mapAllNotNull)?.let(::add)
    }

    private companion object {
        const val TAG = "CalendarDataSource"
    }
}
