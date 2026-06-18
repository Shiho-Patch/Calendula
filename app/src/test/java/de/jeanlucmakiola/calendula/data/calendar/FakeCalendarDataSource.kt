package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent

/**
 * Test-only fake. Tunable via the three `var` properties; `tick()` simulates
 * a provider change so the repository re-queries.
 */
internal class FakeCalendarDataSource : CalendarDataSource {

    var calendarsResult: List<CalendarSource> = emptyList()
    var instancesResult: (Long, Long) -> List<EventInstance> = { _, _ -> emptyList() }
    var eventDetailResult: (Long) -> EventDetail? = { null }
    var eventColorPaletteResult: (Long) -> List<EventColorOption> = { emptyList() }
    var exportableEventsResult: List<IcsEvent> = emptyList()
    /** UIDs the target calendar already holds, for import dedup. */
    var existingUidsResult: Set<String> = emptySet()
    /** Set to make the next write call throw. */
    var writeError: Exception? = null
    /** Id returned by the next [insertEvent]. */
    var nextInsertId: Long = 100L

    val insertedForms = mutableListOf<EventForm>()
    val updatedEvents = mutableListOf<Triple<Long, EventForm, EventForm>>()
    val updatedOccurrences = mutableListOf<Triple<Long, Long, EventForm>>()
    val updatedFromOccurrences = mutableListOf<Triple<Long, Long, EventForm>>()
    val deletedEventIds = mutableListOf<Long>()
    val deletedOccurrences = mutableListOf<Pair<Long, Long>>()
    val deletedFromOccurrences = mutableListOf<Pair<Long, Long>>()
    /** Id returned by the next [createLocalCalendar]. */
    var nextCalendarId: Long = 500L
    data class CreatedCalendar(val displayName: String, val color: Int, val description: String?)
    data class UpdatedCalendar(
        val id: Long,
        val displayName: String,
        val color: Int,
        val description: String?,
    )
    val createdCalendars = mutableListOf<CreatedCalendar>()
    val updatedCalendars = mutableListOf<UpdatedCalendar>()
    val deletedCalendarIds = mutableListOf<Long>()

    private val listeners = mutableListOf<() -> Unit>()

    override fun calendars(): List<CalendarSource> = calendarsResult
    override fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> =
        instancesResult(beginMillis, endMillis)
    override fun eventDetail(eventId: Long): EventDetail? = eventDetailResult(eventId)
    override fun eventColorPalette(calendarId: Long): List<EventColorOption> =
        eventColorPaletteResult(calendarId)
    override fun exportableEvents(): List<IcsEvent> = exportableEventsResult

    override fun existingUids(calendarId: Long): Set<String> = existingUidsResult

    /** (event, targetCalendarId) pairs passed to [insertImportedEvent]. */
    val importedEvents = mutableListOf<Pair<ParsedIcsEvent, Long>>()

    override fun insertImportedEvent(event: ParsedIcsEvent, calendarId: Long): Long {
        writeError?.let { throw it }
        importedEvents += event to calendarId
        return nextInsertId
    }

    override fun createLocalCalendar(displayName: String, color: Int, description: String?): Long {
        writeError?.let { throw it }
        createdCalendars += CreatedCalendar(displayName, color, description)
        return nextCalendarId
    }

    override fun updateCalendar(id: Long, displayName: String, color: Int, description: String?) {
        writeError?.let { throw it }
        updatedCalendars += UpdatedCalendar(id, displayName, color, description)
    }

    override fun deleteCalendar(id: Long) {
        writeError?.let { throw it }
        deletedCalendarIds += id
    }

    /** All-day reminder fire-time minute-of-day passed into the last write. */
    val allDayReminderTimes = mutableListOf<Int>()

    override fun insertEvent(form: EventForm, allDayReminderTimeMinutes: Int): Long {
        writeError?.let { throw it }
        insertedForms += form
        allDayReminderTimes += allDayReminderTimeMinutes
        return nextInsertId
    }

    override fun updateEvent(
        eventId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ) {
        writeError?.let { throw it }
        updatedEvents += Triple(eventId, original, updated)
        allDayReminderTimes += allDayReminderTimeMinutes
    }

    override fun updateOccurrence(
        eventId: Long,
        beginMillis: Long,
        form: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        writeError?.let { throw it }
        updatedOccurrences += Triple(eventId, beginMillis, form)
        allDayReminderTimes += allDayReminderTimeMinutes
        return nextInsertId
    }

    override fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        writeError?.let { throw it }
        updatedFromOccurrences += Triple(eventId, beginMillis, updated)
        allDayReminderTimes += allDayReminderTimeMinutes
        return nextInsertId
    }

    override fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long) {
        writeError?.let { throw it }
        deletedFromOccurrences += eventId to beginMillis
    }

    override fun deleteEvent(eventId: Long) {
        writeError?.let { throw it }
        deletedEventIds += eventId
    }

    override fun deleteOccurrence(eventId: Long, beginMillis: Long) {
        writeError?.let { throw it }
        deletedOccurrences += eventId to beginMillis
    }
    override fun registerChangeListener(listener: () -> Unit) {
        listeners += listener
    }
    override fun unregisterChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun tick() {
        listeners.forEach { it() }
    }
}
