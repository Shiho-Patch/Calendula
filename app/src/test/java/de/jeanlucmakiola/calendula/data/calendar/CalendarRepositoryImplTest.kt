package de.jeanlucmakiola.calendula.data.calendar

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryImplTest {

    private fun makeCal(id: Long, name: String = "Cal $id"): CalendarSource =
        CalendarSource(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), true)

    private fun makeEvent(id: Long, title: String = "E $id"): EventInstance = EventInstance(
        instanceId = id, eventId = id, calendarId = 1L,
        title = title,
        start = Instant.fromEpochMilliseconds(1_000_000_000L),
        end = Instant.fromEpochMilliseconds(1_000_003_600L),
        isAllDay = false, color = 0xFF000000.toInt(), location = null,
    )

    @Test
    fun `calendars emits initial query result on subscribe`() = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            val first = awaitItem()
            assertThat(first.map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars re-emits after change listener tick`() = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L))
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            assertThat(awaitItem().map { it.id }).containsExactly(1L)

            fake.calendarsResult = listOf(makeCal(1L), makeCal(2L))
            fake.tick()

            assertThat(awaitItem().map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances forwards epoch-millis bounds to data source`() = runTest {
        var observedBegin: Long? = null
        var observedEnd: Long? = null
        val fake = FakeCalendarDataSource().apply {
            instancesResult = {b, e ->
                observedBegin = b
                observedEnd = e
                listOf(makeEvent(10L))
            }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(1_000L)..Instant.fromEpochMilliseconds(2_000L)
        repo.instances(range).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(observedBegin).isEqualTo(1_000L)
        assertThat(observedEnd).isEqualTo(2_000L)
    }

    @Test
    fun `instances passes-through whatever the data source returns`() = runTest {
        val fake = FakeCalendarDataSource().apply {
            instancesResult = {_, _ -> listOf(makeEvent(10L, "Good")) }
        }
        val repo = CalendarRepositoryImpl(fake, UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            val first = awaitItem()
            assertThat(first.map { it.title }).containsExactly("Good")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `eventDetail throws NoSuchEventException when data source returns null`() = runTest {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { null }
        }
        val repo = CalendarRepositoryImpl(fake, Dispatchers.Unconfined)

        try {
            repo.eventDetail(eventId = 999L)
            error("Expected NoSuchEventException")
        } catch (expected: NoSuchEventException) {
            assertThat(expected.message).contains("999")
        }
    }
}
