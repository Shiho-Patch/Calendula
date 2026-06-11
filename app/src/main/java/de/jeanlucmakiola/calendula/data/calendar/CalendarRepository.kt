package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface CalendarRepository {
    fun calendars(): Flow<List<CalendarSource>>
    fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>>
    suspend fun eventDetail(eventId: Long): EventDetail

    /** Delete the whole event (for recurring events: the entire series). */
    suspend fun deleteEvent(eventId: Long)

    /** Cancel a single occurrence of a recurring event at its `Instances.BEGIN` time. */
    suspend fun deleteOccurrence(eventId: Long, beginMillis: Long)
}

class NoSuchEventException(eventId: Long) :
    NoSuchElementException("No event with id=$eventId")

/** A ContentResolver write affected no rows or returned no URI. */
class WriteFailedException(operation: String) :
    RuntimeException("Calendar write failed: $operation")
