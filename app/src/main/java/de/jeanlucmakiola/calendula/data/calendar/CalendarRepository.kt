package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface CalendarRepository {
    fun calendars(): Flow<List<CalendarSource>>
    fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>>
    suspend fun eventDetail(eventId: Long): EventDetail

    /** Create a device-only (LOCAL) calendar the app owns; returns its id. */
    suspend fun createLocalCalendar(displayName: String, color: Int, description: String?): Long

    /** Update name, color and description of a local calendar the app owns. */
    suspend fun updateCalendar(id: Long, displayName: String, color: Int, description: String?)

    /** Permanently delete a local calendar the app owns, with all its events. */
    suspend fun deleteCalendar(id: Long)

    /** Create a new event from a validated form; returns the new `Events._ID`. */
    suspend fun createEvent(form: EventForm): Long

    /**
     * Update an event (recurring: the whole series) from a validated form.
     * [original] is the prefilled form, used to write only what changed.
     */
    suspend fun updateEvent(eventId: Long, original: EventForm, updated: EventForm)

    /**
     * Change a single occurrence of a recurring event (exception row with the
     * form's values); returns the exception's `Events._ID`.
     */
    suspend fun updateOccurrence(eventId: Long, beginMillis: Long, form: EventForm): Long

    /**
     * Change a recurring event from [beginMillis] onwards (series split);
     * returns the new event's `Events._ID`.
     */
    suspend fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
    ): Long

    /** Delete the whole event (for recurring events: the entire series). */
    suspend fun deleteEvent(eventId: Long)

    /** Cancel a single occurrence of a recurring event at its `Instances.BEGIN` time. */
    suspend fun deleteOccurrence(eventId: Long, beginMillis: Long)

    /** Delete a recurring event from the occurrence at [beginMillis] onwards. */
    suspend fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long)
}

class NoSuchEventException(eventId: Long) :
    NoSuchElementException("No event with id=$eventId")

/** A ContentResolver write affected no rows or returned no URI. */
class WriteFailedException(operation: String) :
    RuntimeException("Calendar write failed: $operation")
