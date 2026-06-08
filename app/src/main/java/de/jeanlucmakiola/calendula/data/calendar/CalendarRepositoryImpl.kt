package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    @IoDispatcher private val io: CoroutineDispatcher,
) : CalendarRepository {

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

    override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> =
        ticks
            .onStart { emit(Unit) }
            .reQuery {
                dataSource.instances(
                    beginMillis = range.start.toEpochMillis(),
                    endMillis = range.endInclusive.toEpochMillis(),
                )
            }
            .flowOn(io)

    override suspend fun eventDetail(eventId: Long): EventDetail = withContext(io) {
        dataSource.eventDetail(eventId) ?: throw NoSuchEventException(eventId)
    }
}

private fun <T> Flow<Unit>.reQuery(block: suspend () -> T): Flow<T> = flow {
    collect { emit(block()) }
}
