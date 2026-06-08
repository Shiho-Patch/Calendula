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
}

class NoSuchEventException(eventId: Long) :
    NoSuchElementException("No event with id=$eventId")
