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
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.Reminder
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
}
