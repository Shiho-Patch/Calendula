package de.jeanlucmakiola.calendula.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Selecting search results and deleting the batch (#80): which repository call
 * each kind of hit routes to, and what a read-only calendar is allowed to join.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private val begin = 1_800_000_000_000L

    private fun cal(id: Long, canModify: Boolean = true) = CalendarSource(
        id = id, displayName = "Cal $id", accountName = "acc@local", accountType = "LOCAL",
        color = 0xFF112233.toInt(), isVisibleInSystem = true, canModifyContents = canModify,
    )

    private fun hit(
        id: Long,
        calendarId: Long = 1L,
        recurring: Boolean = false,
        startMillis: Long = begin,
    ) = EventInstance(
        instanceId = id, eventId = id, calendarId = calendarId, title = "Standup $id",
        start = Instant.fromEpochMilliseconds(startMillis),
        end = Instant.fromEpochMilliseconds(startMillis + 3_600_000L),
        isAllDay = false, color = 0xFF000000.toInt(), location = null, isRecurring = recurring,
    )

    private fun viewModel(tempDir: Path, fake: FakeCalendarDataSource): SearchViewModel {
        val prefs = CalendarPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("search_prefs.preferences_pb").toFile() },
            ),
        )
        val settings = SettingsPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("search_settings.preferences_pb").toFile() },
            ),
        )
        val repo = CalendarRepositoryImpl(fake, prefs, settings, dispatcher as CoroutineDispatcher)
        return SearchViewModel(repo, dispatcher)
    }

    private fun CoroutineScope.activate(vm: SearchViewModel): Job = launch { vm.state.collect {} }

    @Test
    fun `a batch of one-offs deletes each event whole`(@TempDir tempDir: Path) =
        runTest(dispatcher) {
            val fake = FakeCalendarDataSource().apply {
                calendarsResult = listOf(cal(1L))
                searchResult = { listOf(hit(1L), hit(2L), hit(3L)) }
            }
            val vm = viewModel(tempDir, fake)
            val job = activate(vm)

            vm.setQuery("standup")
            advanceUntilIdle()
            vm.toggleSelection(1L)
            vm.toggleSelection(3L)
            assertThat(vm.selectionHasRecurring()).isFalse()

            vm.deleteSelected(RecurringWriteScope.AllEvents)
            advanceUntilIdle()

            assertThat(fake.deletedEventIds).containsExactly(1L, 3L)
            assertThat(fake.deletedOccurrences).isEmpty()
            assertThat(vm.selection.value).isEmpty()
            assertThat(vm.deleteState.value).isEqualTo(BulkDeleteUiState.Done(deleted = 2, failed = 0))
            job.cancel()
        }

    @Test
    fun `the batch scope reaches the recurring hits only`(@TempDir tempDir: Path) =
        runTest(dispatcher) {
            val fake = FakeCalendarDataSource().apply {
                calendarsResult = listOf(cal(1L))
                searchResult = { listOf(hit(1L), hit(2L, recurring = true)) }
            }
            val vm = viewModel(tempDir, fake)
            val job = activate(vm)

            vm.setQuery("standup")
            advanceUntilIdle()
            vm.selectAll()
            assertThat(vm.selectionHasRecurring()).isTrue()

            vm.deleteSelected(RecurringWriteScope.ThisEvent)
            advanceUntilIdle()

            // The one-off goes whole whatever the batch decided; only the series
            // sees the scope, cancelling the occurrence the result row stands for.
            assertThat(fake.deletedEventIds).containsExactly(1L)
            assertThat(fake.deletedOccurrences).containsExactly(2L to begin)
            job.cancel()
        }

    @Test
    fun `this-and-following truncates the recurring hit from its shown occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            searchResult = { listOf(hit(9L, recurring = true)) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.setQuery("standup")
        advanceUntilIdle()
        vm.toggleSelection(9L)
        vm.deleteSelected(RecurringWriteScope.ThisAndFollowing)
        advanceUntilIdle()

        assertThat(fake.deletedFromOccurrences).containsExactly(9L to begin)
        job.cancel()
    }

    @Test
    fun `a read-only calendar's hit cannot be selected or deleted`(@TempDir tempDir: Path) =
        runTest(dispatcher) {
            val fake = FakeCalendarDataSource().apply {
                calendarsResult = listOf(cal(1L), cal(2L, canModify = false))
                searchResult = { listOf(hit(1L, calendarId = 1L), hit(2L, calendarId = 2L)) }
            }
            val vm = viewModel(tempDir, fake)
            val job = activate(vm)

            vm.setQuery("standup")
            advanceUntilIdle()

            val results = vm.state.value as SearchUiState.Results
            assertThat(results.isDeletable(results.events.single { it.eventId == 2L })).isFalse()

            vm.selectAll()
            assertThat(vm.selection.value).containsExactly(1L)

            // Even a hand-toggled read-only row is dropped before the write.
            vm.toggleSelection(2L)
            vm.deleteSelected(RecurringWriteScope.AllEvents)
            advanceUntilIdle()

            assertThat(fake.deletedEventIds).containsExactly(1L)
            job.cancel()
        }

    @Test
    fun `a failing delete leaves the rest of the batch alone`(@TempDir tempDir: Path) =
        runTest(dispatcher) {
            val fake = FakeCalendarDataSource().apply {
                calendarsResult = listOf(cal(1L))
                searchResult = { listOf(hit(1L), hit(2L)) }
                writeError = IllegalStateException("provider said no")
            }
            val vm = viewModel(tempDir, fake)
            val job = activate(vm)

            vm.setQuery("standup")
            advanceUntilIdle()
            vm.selectAll()
            vm.deleteSelected(RecurringWriteScope.AllEvents)
            advanceUntilIdle()

            assertThat(vm.deleteState.value)
                .isEqualTo(BulkDeleteUiState.Done(deleted = 0, failed = 2))
            job.cancel()
        }

    @Test
    fun `changing the query drops the selection`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L))
            searchResult = { listOf(hit(1L)) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.setQuery("standup")
        advanceUntilIdle()
        vm.toggleSelection(1L)
        assertThat(vm.selection.value).isNotEmpty()

        vm.setQuery("retro")
        assertThat(vm.selection.value).isEmpty()
        job.cancel()
    }
}
