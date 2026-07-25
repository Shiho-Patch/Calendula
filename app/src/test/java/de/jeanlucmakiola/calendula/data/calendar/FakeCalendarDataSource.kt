package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import java.time.ZoneId

/**
 * Test-only fake. Tunable via the three `var` properties; `tick()` simulates
 * a provider change so the repository re-queries.
 */
internal class FakeCalendarDataSource : CalendarDataSource {

    var calendarsResult: List<CalendarSource> = emptyList()
    var instancesResult: (Long, Long) -> List<EventInstance> = { _, _ -> emptyList() }
    var searchResult: (String) -> List<EventInstance> = { _ -> emptyList() }
    var eventDetailResult: (Long) -> EventDetail? = { null }
    var eventColorPaletteResult: (Long) -> List<EventColorOption> = { emptyList() }
    var exportableEventsResult: List<IcsEvent> = emptyList()
    /** The [calendarIds] the last [exportableEvents] call received (null = all). */
    var lastExportableEventsCalendarIds: Set<Long>? = null
    /** UIDs the target calendar already holds, for import dedup. */
    var existingUidsResult: Set<String> = emptySet()
    /** Set to make the next write call throw. */
    var writeError: Exception? = null
    /** Id returned by the next [insertEvent]. */
    var nextInsertId: Long = 100L

    val insertedForms = mutableListOf<EventForm>()
    val updatedEvents = mutableListOf<Triple<Long, EventForm, EventForm>>()
    data class MovedEvent(
        val eventId: Long,
        val targetCalendarId: Long,
        val original: EventForm,
        val updated: EventForm,
    )
    val movedEvents = mutableListOf<MovedEvent>()
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
    override fun searchEvents(query: String): List<EventInstance> = searchResult(query)
    override fun eventDetail(eventId: Long): EventDetail? = eventDetailResult(eventId)
    override fun eventColorPalette(calendarId: Long): List<EventColorOption> =
        eventColorPaletteResult(calendarId)
    override fun exportableEvents(calendarIds: Set<Long>?): List<IcsEvent> {
        lastExportableEventsCalendarIds = calendarIds
        return exportableEventsResult
    }

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

    /** (id, visible) pairs passed to [setCalendarVisible], in call order. */
    val visibilityWrites = mutableListOf<Pair<Long, Boolean>>()

    override fun setCalendarVisible(id: Long, visible: Boolean) {
        writeError?.let { throw it }
        visibilityWrites += id to visible
        // Reflect the write so a follow-up [calendars] read sees it, the way the
        // provider would once its notification has re-triggered the query.
        calendarsResult = calendarsResult.map {
            if (it.id == id) it.copy(isVisibleInSystem = visible) else it
        }
    }

    override fun deleteCalendar(id: Long) {
        writeError?.let { throw it }
        deletedCalendarIds += id
        managedCalendarRows.removeAll { it.id == id }
        managedEvents.remove(id)
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

    override fun moveEvent(
        eventId: Long,
        targetCalendarId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        writeError?.let { throw it }
        movedEvents += MovedEvent(eventId, targetCalendarId, original, updated)
        allDayReminderTimes += allDayReminderTimeMinutes
        return nextInsertId
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
        managedEvents.values.forEach { rows -> rows.removeAll { it.eventId == eventId } }
    }

    // --- Managed special-dates surface (stateful, so a re-sync sees inserts) ---

    var nextManagedEventId: Long = 1_000L
    private var managedCalendarSeq: Long = 700L
    private val managedCalendarRows = mutableListOf<ManagedCalendarRow>()
    private val managedEvents = mutableMapOf<Long, MutableList<ManagedEventRow>>()

    data class CreatedManagedCalendar(val displayName: String, val color: Int, val type: SpecialDateType)
    val createdManagedCalendars = mutableListOf<CreatedManagedCalendar>()
    val insertedManagedEvents = mutableListOf<Pair<EventForm, String>>()
    val updatedManagedFields = mutableListOf<Pair<Long, Map<String, Any?>>>()

    /** Preset an orphan managed calendar (e.g. to test adoption after a prefs wipe). */
    fun presetManagedCalendar(id: Long, type: SpecialDateType) {
        managedCalendarRows += ManagedCalendarRow(id, type)
        managedEvents.getOrPut(id) { mutableListOf() }
    }

    override fun createManagedCalendar(displayName: String, color: Int, type: SpecialDateType): Long {
        writeError?.let { throw it }
        createdManagedCalendars += CreatedManagedCalendar(displayName, color, type)
        val id = managedCalendarSeq++
        managedCalendarRows += ManagedCalendarRow(id, type)
        managedEvents.getOrPut(id) { mutableListOf() }
        return id
    }

    override fun findManagedCalendars(): List<ManagedCalendarRow> = managedCalendarRows.toList()

    override fun queryManagedEvents(calendarId: Long): List<ManagedEventRow> =
        managedEvents[calendarId]?.toList() ?: emptyList()

    override fun insertManagedEvent(form: EventForm, uid: String, allDayReminderTimeMinutes: Int): Long {
        writeError?.let { throw it }
        insertedManagedEvents += form to uid
        allDayReminderTimes += allDayReminderTimeMinutes
        val id = nextManagedEventId++
        val times = form.toWriteTimes(ZoneId.systemDefault())
        managedEvents.getOrPut(requireNotNull(form.calendarId)) { mutableListOf() } +=
            ManagedEventRow(id, uid, form.title.trim(), times.dtStartMillis, form.rrule)
        return id
    }

    /** (calendarId, allDayTime, minutes) recorded from applyManagedCalendarReminders. */
    val appliedManagedReminders = mutableListOf<Triple<Long, Int, List<Int>>>()

    override fun applyManagedCalendarReminders(
        calendarId: Long,
        allDayReminderTimeMinutes: Int,
        minutes: List<Int>,
    ) {
        writeError?.let { throw it }
        appliedManagedReminders += Triple(calendarId, allDayReminderTimeMinutes, minutes)
    }

    override fun updateManagedFields(eventId: Long, values: Map<String, Any?>) {
        writeError?.let { throw it }
        if (values.isEmpty()) return
        updatedManagedFields += eventId to values
        managedEvents.values.forEach { rows ->
            val idx = rows.indexOfFirst { it.eventId == eventId }
            if (idx >= 0) {
                var row = rows[idx]
                (values[CalendarContract.Events.TITLE] as? String)?.let { row = row.copy(title = it) }
                (values[CalendarContract.Events.DTSTART] as? Long)?.let { row = row.copy(dtStartMillis = it) }
                if (values.containsKey(CalendarContract.Events.RRULE)) {
                    row = row.copy(rrule = values[CalendarContract.Events.RRULE] as? String)
                }
                rows[idx] = row
            }
        }
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
