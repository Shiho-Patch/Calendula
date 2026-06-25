package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.ics.IcsImportSummary
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One ContentResolver-backed observer for the lifetime of the App process.
 * Each public flow re-queries on subscribe and after every tick from the
 * data source.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val dataSource: CalendarDataSource,
    private val prefs: CalendarPrefs,
    private val settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : CalendarRepository {

    /** The configured wall-clock fire time for all-day reminders, read per write. */
    private suspend fun allDayReminderTimeMinutes(): Int =
        settingsPrefs.allDayReminderTimeMinutes.first()

    private val ticks = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
    )

    init {
        dataSource.registerChangeListener { ticks.tryEmit(Unit) }
    }

    override fun calendars(): Flow<List<CalendarSource>> =
        ticks
            .onStart { emit(Unit) }
            .reQuery { dataSource.calendars() }
            .flowOn(io)

    // Instances are filtered by the app-side hidden-calendar set (M3): an event
    // is dropped whenever the user has hidden its calendar. Re-runs when the
    // provider ticks *or* the hidden set changes — toggling a calendar in the
    // filter sheet updates every view immediately. [calendars] stays unfiltered
    // so the filter sheet can list and re-enable hidden calendars.
    override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> =
        combine(
            ticks
                .onStart { emit(Unit) }
                .reQuery {
                    dataSource.instances(
                        beginMillis = range.start.toEpochMillis(),
                        endMillis = range.endInclusive.toEpochMillis(),
                    )
                },
            prefs.hiddenCalendarIds,
        ) { instances, hidden ->
            if (hidden.isEmpty()) instances
            else instances.filterNot { it.calendarId in hidden }
        }.flowOn(io)

    override suspend fun eventDetail(eventId: Long): EventDetail = withContext(io) {
        dataSource.eventDetail(eventId) ?: throw NoSuchEventException(eventId)
    }

    override suspend fun searchEvents(query: String): List<EventInstance> = withContext(io) {
        if (query.isBlank()) return@withContext emptyList()
        val hidden = prefs.hiddenCalendarIds.first()
        dataSource.searchEvents(query)
            .let { if (hidden.isEmpty()) it else it.filterNot { e -> e.calendarId in hidden } }
    }

    override suspend fun eventColorPalette(calendarId: Long): List<EventColorOption> =
        withContext(io) { dataSource.eventColorPalette(calendarId) }

    override suspend fun createLocalCalendar(
        displayName: String,
        color: Int,
        description: String?,
    ): Long = withContext(io) {
        dataSource.createLocalCalendar(displayName, color, description)
    }

    override suspend fun updateCalendar(
        id: Long,
        displayName: String,
        color: Int,
        description: String?,
    ) = withContext(io) { dataSource.updateCalendar(id, displayName, color, description) }

    override suspend fun deleteCalendar(id: Long) =
        withContext(io) { dataSource.deleteCalendar(id) }

    override suspend fun exportEvents() = withContext(io) { dataSource.exportableEvents() }

    override suspend fun importEvents(
        targetCalendarId: Long,
        events: List<ParsedIcsEvent>,
    ): IcsImportSummary = withContext(io) {
        val existing = dataSource.existingUids(targetCalendarId)
        var imported = 0
        var skipped = 0
        for (event in events) {
            // A known UID means the event is already in this calendar — skip,
            // keeping a restore idempotent (no overwrite this pass).
            if (event.uid != null && event.uid in existing) {
                skipped++
            } else {
                dataSource.insertImportedEvent(event, targetCalendarId)
                imported++
            }
        }
        IcsImportSummary(imported = imported, skippedDuplicate = skipped)
    }

    override suspend fun createEvent(form: EventForm): Long = withContext(io) {
        dataSource.insertEvent(form, allDayReminderTimeMinutes())
    }

    override suspend fun updateEvent(
        eventId: Long,
        original: EventForm,
        updated: EventForm,
    ) = withContext(io) {
        dataSource.updateEvent(eventId, original, updated, allDayReminderTimeMinutes())
    }

    override suspend fun deleteEvent(eventId: Long) = withContext(io) {
        dataSource.deleteEvent(eventId)
    }

    override suspend fun updateOccurrence(
        eventId: Long,
        beginMillis: Long,
        form: EventForm,
    ): Long = withContext(io) {
        dataSource.updateOccurrence(eventId, beginMillis, form, allDayReminderTimeMinutes())
    }

    override suspend fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
    ): Long = withContext(io) {
        dataSource.updateEventFromOccurrence(
            eventId, beginMillis, original, updated, allDayReminderTimeMinutes(),
        )
    }

    override suspend fun deleteOccurrence(eventId: Long, beginMillis: Long) = withContext(io) {
        dataSource.deleteOccurrence(eventId, beginMillis)
    }

    override suspend fun deleteEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
    ) = withContext(io) {
        dataSource.deleteEventFromOccurrence(eventId, beginMillis)
    }
}

private fun <T> Flow<Unit>.reQuery(block: suspend () -> T): Flow<T> = flow {
    collect { emit(block()) }
}
