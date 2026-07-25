package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.floret.time.toEpochMillis
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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // Instances are filtered by the system's per-calendar VISIBLE flag ∪ the
    // app-side hidden set: an event is dropped when the user switched its
    // calendar off in Settings → Calendars (which also stops the provider
    // scheduling its reminders) *or* hid it in the filter sheet. Re-runs when
    // the provider ticks — writing VISIBLE notifies, so switching a calendar
    // updates every view — or when the hidden set changes. [calendars] stays
    // unfiltered so those screens can list and re-enable invisible calendars.
    override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> =
        combine(
            ticks
                .onStart { emit(Unit) }
                .reQuery {
                    // Both reads in one pass, so a list of instances is never
                    // filtered against a visibility snapshot from another tick.
                    QueriedInstances(
                        instances = dataSource.instances(
                            beginMillis = range.start.toEpochMillis(),
                            endMillis = range.endInclusive.toEpochMillis(),
                        ),
                        invisibleCalendarIds = invisibleCalendarIds(),
                    )
                },
            prefs.hiddenCalendarIds,
        ) { queried, hidden ->
            val excluded = hidden + queried.invisibleCalendarIds
            if (excluded.isEmpty()) queried.instances
            else queried.instances.filterNot { it.calendarId in excluded }
        }
            // Any DataStore edit re-emits the hidden set even when it is
            // unchanged (e.g. writing the last-used calendar), which would
            // re-surface an identical list — collapse those so views don't
            // re-render for them.
            .distinctUntilChanged()
            .flowOn(io)

    /** One instances query plus the visibility it must be filtered against. */
    private data class QueriedInstances(
        val instances: List<EventInstance>,
        val invisibleCalendarIds: Set<Long>,
    )

    /** Calendars switched off at system level — hidden, and never reminded about. */
    private fun invisibleCalendarIds(): Set<Long> = dataSource.calendars()
        .filterNot { it.isVisibleInSystem }
        .mapTo(mutableSetOf()) { it.id }

    override suspend fun eventDetail(eventId: Long): EventDetail = withContext(io) {
        dataSource.eventDetail(eventId) ?: throw NoSuchEventException(eventId)
    }

    override suspend fun searchEvents(query: String): List<EventInstance> = withContext(io) {
        if (query.isBlank()) return@withContext emptyList()
        val excluded = prefs.hiddenCalendarIds.first() + invisibleCalendarIds()
        dataSource.searchEvents(query)
            .let { if (excluded.isEmpty()) it else it.filterNot { e -> e.calendarId in excluded } }
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

    override suspend fun setCalendarsVisible(ids: Collection<Long>, visible: Boolean) =
        withContext(io) { ids.forEach { dataSource.setCalendarVisible(it, visible) } }

    override suspend fun exportEvents(calendarIds: Set<Long>?) =
        withContext(io) { dataSource.exportableEvents(calendarIds) }

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

    override suspend fun moveEvent(
        eventId: Long,
        targetCalendarId: Long,
        original: EventForm,
        updated: EventForm,
    ): Long = withContext(io) {
        dataSource.moveEvent(
            eventId, targetCalendarId, original, updated, allDayReminderTimeMinutes(),
        )
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
