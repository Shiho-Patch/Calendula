package de.jeanlucmakiola.calendula.data.calendar

import android.util.Log
import de.jeanlucmakiola.floret.time.toEpochMillis
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.SearchCandidate
import de.jeanlucmakiola.calendula.domain.ics.IcsImportSummary
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
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

    /**
     * Bumped on every provider notification, so one tick's calendar read can be
     * shared by everything that needs it (see [calendarsSnapshot]).
     */
    private val generation = AtomicLong(0L)

    init {
        dataSource.registerChangeListener {
            generation.incrementAndGet()
            ticks.tryEmit(Unit)
        }
    }

    /**
     * Re-query signal for everything filtered by visibility: the provider's own
     * notifications, plus every change to the pending switch-off set.
     */
    private fun visibilityTicks(): Flow<Unit> = merge(
        ticks.onStart { emit(Unit) },
        // drop(1): the current value is already covered by the tick above.
        prefs.pendingDisabledCalendarIds.drop(1).map {},
    )

    // A switch-off not yet written to the provider is folded into the flag
    // itself, so every consumer reads one visibility (#75). The reconciler goes
    // to the data source directly — it needs the provider's own answer.
    override fun calendars(): Flow<List<CalendarSource>> =
        visibilityTicks().reQuery {
            val calendars = calendarsSnapshot()
            val pendingDisabled = prefs.pendingDisabledCalendarIds.first()
            if (pendingDisabled.isEmpty()) calendars
            else calendars.map {
                if (it.id in pendingDisabled) it.copy(isVisibleInSystem = false) else it
            }
        }
            .distinctUntilChanged()
            .flowOn(io)

    // Instances are filtered by the system's VISIBLE flag ∪ the switch-offs
    // still waiting to be written to it ∪ the app-side hidden set from the
    // filter sheet. [calendars] stays unfiltered so those screens can list and
    // re-enable invisible calendars.
    override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> =
        combine(
            visibilityTicks().reQuery {
                // All three reads in one pass, so a list of instances is never
                // filtered against a visibility snapshot from another tick.
                QueriedInstances(
                    instances = dataSource.instances(
                        beginMillis = range.start.toEpochMillis(),
                        endMillis = range.endInclusive.toEpochMillis(),
                    ),
                    switchedOffCalendarIds = invisibleCalendarIds() +
                        prefs.pendingDisabledCalendarIds.first(),
                )
            },
            prefs.hiddenCalendarIds,
        ) { queried, hidden ->
            val excluded = hidden + queried.switchedOffCalendarIds
            if (excluded.isEmpty()) queried.instances
            else queried.instances.filterNot { it.calendarId in excluded }
        }
            // Any DataStore edit re-emits the hidden set even when unchanged;
            // collapse those so views don't re-render for them.
            .distinctUntilChanged()
            .flowOn(io)

    /** One instances query plus the visibility it must be filtered against. */
    private data class QueriedInstances(
        val instances: List<EventInstance>,
        val switchedOffCalendarIds: Set<Long>,
    )

    /** Calendars switched off at system level — hidden, and never reminded about. */
    private suspend fun invisibleCalendarIds(): Set<Long> = calendarsSnapshot()
        .filterNot { it.isVisibleInSystem }
        .mapTo(mutableSetOf()) { it.id }

    private val calendarsLock = Mutex()
    private var cachedGeneration = -1L
    private var cachedPending: Set<Long>? = null
    private var cachedCalendars: List<CalendarSource> = emptyList()

    /**
     * The calendar list for the current tick, queried once and shared, so every
     * open view doesn't pay for its own `Calendars` query and all of them see
     * one snapshot.
     *
     * An empty result is never cached — that is what a read without the calendar
     * permission returns, and the grant itself doesn't notify the provider. The
     * pending switch-off set keys the cache alongside the tick, because an id
     * leaves it before the invalidating observer is dispatched.
     */
    private suspend fun calendarsSnapshot(): List<CalendarSource> = calendarsLock.withLock {
        val current = generation.get()
        val pending = prefs.pendingDisabledCalendarIds.first()
        if (current != cachedGeneration || pending != cachedPending || cachedCalendars.isEmpty()) {
            cachedCalendars = dataSource.calendars()
            cachedGeneration = current
            cachedPending = pending
        }
        cachedCalendars
    }

    override suspend fun eventDetail(eventId: Long): EventDetail = withContext(io) {
        dataSource.eventDetail(eventId, allDayReminderTimeMinutes())
            ?: throw NoSuchEventException(eventId)
    }

    override suspend fun searchEvents(query: String): List<SearchCandidate> = withContext(io) {
        if (query.isBlank()) return@withContext emptyList()
        val excluded = prefs.hiddenCalendarIds.first() +
            prefs.pendingDisabledCalendarIds.first() +
            invisibleCalendarIds()
        dataSource.searchEvents(query)
            .let { if (excluded.isEmpty()) it else it.filterNot { c -> c.event.calendarId in excluded } }
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
        withContext(io) {
            if (dataSource.canWriteCalendars()) {
                ids.forEach { dataSource.setCalendarVisible(it, visible) }
                // Nothing of ours is left waiting for the provider once the
                // write lands (and switching one back on retires its entry).
                prefs.removePendingDisabledCalendarIds(ids)
            } else if (visible) {
                prefs.removePendingDisabledCalendarIds(ids)
            } else {
                // Read-only permission: the switch still works, app-side, and
                // the reconciler flushes it if WRITE_CALENDAR ever arrives.
                prefs.addPendingDisabledCalendarIds(ids)
            }
        }

    override suspend fun exportEvents(calendarIds: Set<Long>?) =
        withContext(io) { dataSource.exportableEvents(calendarIds) }

    override suspend fun importEvents(
        targetCalendarId: Long,
        events: List<ParsedIcsEvent>,
    ): IcsImportSummary = withContext(io) {
        val existing = dataSource.existingUids(targetCalendarId)
        // Both are per-calendar, not per-event: looking them up once keeps a
        // thousand-event restore to two extra queries.
        val palette = dataSource.eventColorPalette(targetCalendarId)
        val allDayMinutes = allDayReminderTimeMinutes()
        var imported = 0
        var skipped = 0
        var failed = 0
        for (event in events) {
            // A known UID means the event is already in this calendar — skip,
            // keeping a restore idempotent (no overwrite this pass).
            if (event.uid != null && event.uid in existing) {
                skipped++
                continue
            }
            try {
                dataSource.insertImportedEvent(event, targetCalendarId, allDayMinutes, palette)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // One event the provider won't take must not cost the user the
                // rest of the file — foreign exports do carry rows it rejects
                // outright (a malformed RRULE throws straight out of insert).
                failed++
                Log.w(TAG, "Skipped an unimportable event", e)
            }
        }
        IcsImportSummary(imported = imported, skippedDuplicate = skipped, failed = failed)
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


    private companion object {
        const val TAG = "CalendarRepository"
    }
}

private fun <T> Flow<Unit>.reQuery(block: suspend () -> T): Flow<T> = flow {
    collect { emit(block()) }
}
