package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.IcsImportSummary
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface CalendarRepository {
    fun calendars(): Flow<List<CalendarSource>>
    fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>>
    suspend fun eventDetail(eventId: Long): EventDetail

    /**
     * Events whose title, description or location contains [query], with hidden
     * calendars removed and newest first. Empty when [query] is blank. Searches
     * the whole history/future (see [CalendarDataSource.searchEvents]).
     */
    suspend fun searchEvents(query: String): List<EventInstance>

    /**
     * The event-colour palette a calendar's account publishes; empty when it
     * exposes none (see [CalendarDataSource.eventColorPalette]).
     */
    suspend fun eventColorPalette(calendarId: Long): List<EventColorOption>

    /** Create a device-only (LOCAL) calendar the app owns; returns its id. */
    suspend fun createLocalCalendar(displayName: String, color: Int, description: String?): Long

    /** Update name, color and description of a local calendar the app owns. */
    suspend fun updateCalendar(id: Long, displayName: String, color: Int, description: String?)

    /** Permanently delete a local calendar the app owns, with all its events. */
    suspend fun deleteCalendar(id: Long)

    /**
     * Show or hide [ids] device-wide (`Calendars.VISIBLE`), which also gates
     * their reminders — see [CalendarDataSource.setCalendarVisible]. Written one
     * at a time, in order; a failure part-way leaves the earlier writes standing.
     *
     * Without `WRITE_CALENDAR` the choice is kept app-side instead (see
     * [de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs.pendingDisabledCalendarIds]).
     */
    suspend fun setCalendarsVisible(ids: Collection<Long>, visible: Boolean)

    /**
     * Every event of the writable local calendars, ready to serialise into a
     * whole-calendar `.ics` backup (see [CalendarDataSource.exportableEvents]).
     * [calendarIds] narrows the export to a chosen subset; `null` exports all.
     */
    suspend fun exportEvents(calendarIds: Set<Long>? = null): List<IcsEvent>

    /**
     * Bulk-import parsed `.ics` [events] into [targetCalendarId]. Events whose
     * UID already exists in the target are skipped (idempotent restore); the
     * rest are inserted. See [CalendarDataSource.insertImportedEvent].
     */
    suspend fun importEvents(targetCalendarId: Long, events: List<ParsedIcsEvent>): IcsImportSummary

    /** Create a new event from a validated form; returns the new `Events._ID`. */
    suspend fun createEvent(form: EventForm): Long

    /**
     * Update an event (recurring: the whole series) from a validated form.
     * [original] is the prefilled form, used to write only what changed.
     */
    suspend fun updateEvent(eventId: Long, original: EventForm, updated: EventForm)

    /**
     * Move an event (recurring: the whole series, with its exceptions) to
     * [targetCalendarId] and apply the [original]→[updated] field edits; returns
     * the new event's `Events._ID`. Copy+delete under the hood
     * (see [CalendarDataSource.moveEvent]) — `CALENDAR_ID` can't be updated in
     * place.
     */
    suspend fun moveEvent(
        eventId: Long,
        targetCalendarId: Long,
        original: EventForm,
        updated: EventForm,
    ): Long

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
