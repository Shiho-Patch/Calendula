package de.jeanlucmakiola.calendula.ui.debug

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepo(
        val calendarsFlow: MutableStateFlow<List<CalendarSource>> = MutableStateFlow(emptyList()),
        val instancesFlow: MutableStateFlow<List<EventInstance>> = MutableStateFlow(emptyList()),
    ) : CalendarRepository {
        override fun calendars(): Flow<List<CalendarSource>> = calendarsFlow
        override fun instances(range: ClosedRange<Instant>): Flow<List<EventInstance>> = instancesFlow
        override suspend fun eventDetail(eventId: Long): EventDetail =
            throw NoSuchEventException(eventId)
    }

    private fun makeCal(id: Long, name: String = "C $id"): CalendarSource =
        CalendarSource(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), true)

    private fun makeEvent(id: Long, title: String = "E $id") = EventInstance(
        instanceId = id, eventId = id, calendarId = 1L,
        title = title,
        start = Instant.fromEpochMilliseconds(0L),
        end = Instant.fromEpochMilliseconds(60_000L),
        isAllDay = false, color = 0xFF000000.toInt(), location = null,
    )

    @Test
    fun `initial state value is Loading before any subscriber`() {
        val repo = FakeRepo()
        val vm = DebugViewModel(repo, testDispatcher)
        assertThat(vm.state.value).isEqualTo(DebugUiState.Loading)
    }

    @Test
    fun `Success contains calendars and capped events after subscription`() = runTest {
        val repo = FakeRepo(
            calendarsFlow = MutableStateFlow(listOf(makeCal(1L))),
            instancesFlow = MutableStateFlow(listOf(makeEvent(10L, "X"))),
        )
        val vm = DebugViewModel(repo, testDispatcher)
        vm.state.test {
            val success = awaitItem() as DebugUiState.Success
            assertThat(success.calendars.map { it.id }).containsExactly(1L)
            assertThat(success.nextEvents.map { it.title }).containsExactly("X")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances are capped at 50`() = runTest {
        val repo = FakeRepo(
            calendarsFlow = MutableStateFlow(listOf(makeCal(1L))),
            instancesFlow = MutableStateFlow((1L..100L).map { makeEvent(it, "E$it") }),
        )
        val vm = DebugViewModel(repo, testDispatcher)
        vm.state.test {
            val success = awaitItem() as DebugUiState.Success
            assertThat(success.nextEvents).hasSize(50)
            assertThat(success.nextEvents.first().instanceId).isEqualTo(1L)
            assertThat(success.nextEvents.last().instanceId).isEqualTo(50L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state updates when repository emits new data`() = runTest {
        val repo = FakeRepo()
        val vm = DebugViewModel(repo, testDispatcher)
        vm.state.test {
            // Empty initial: combine fires once because both StateFlows have initial empty value
            val empty = awaitItem() as DebugUiState.Success
            assertThat(empty.calendars).isEmpty()
            assertThat(empty.nextEvents).isEmpty()

            repo.calendarsFlow.value = listOf(makeCal(1L), makeCal(2L))
            val updated = awaitItem() as DebugUiState.Success
            assertThat(updated.calendars.map { it.id }).containsExactly(1L, 2L).inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }
}
