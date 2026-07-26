package de.jeanlucmakiola.calendula.ui.edit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Focuses on the save-time branch that decides between an in-place update and a
 * calendar *move* (copy+delete). The provider-level move itself is verified
 * on-device; here the fake records which repository call the ViewModel chose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventEditViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private val beginMillis = 1_781_164_800_000L
    private val endMillis = beginMillis + 3_600_000L

    private fun cal(
        id: Long,
        visible: Boolean = true,
        syncsEvents: Boolean = true,
    ): CalendarSource = CalendarSource(
        id = id, displayName = "Cal $id", accountName = "acc@local", accountType = "LOCAL",
        color = 0xFF112233.toInt(), isVisibleInSystem = visible, canModifyContents = true,
        syncsEvents = syncsEvents,
    )

    private fun detail(calendarId: Long, rrule: String? = null): EventDetail = EventDetail(
        instance = EventInstance(
            instanceId = 42L, eventId = 42L, calendarId = calendarId, title = "Standup",
            start = Instant.fromEpochMilliseconds(beginMillis),
            end = Instant.fromEpochMilliseconds(endMillis),
            isAllDay = false, color = 0xFF000000.toInt(), location = null,
        ),
        description = null, organizer = null, attendees = emptyList(), rrule = rrule,
    )

    // Pin the DataStore's scope to the test dispatcher so a settings write (e.g.
    // last-used calendar) completes under advanceUntilIdle instead of on a real
    // IO thread the virtual clock can't observe.
    private fun prefs(tempDir: Path): CalendarPrefs = CalendarPrefs(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tempDir.resolve("vm_prefs.preferences_pb").toFile() },
        ),
    )

    private fun settings(tempDir: Path): SettingsPrefs = SettingsPrefs(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(dispatcher),
            produceFile = { tempDir.resolve("vm_settings.preferences_pb").toFile() },
        ),
    )

    private fun viewModel(
        tempDir: Path,
        fake: FakeCalendarDataSource,
    ): EventEditViewModel {
        val p = prefs(tempDir)
        val s = settings(tempDir)
        val repo = CalendarRepositoryImpl(fake, p, s, dispatcher as CoroutineDispatcher)
        return EventEditViewModel(repo, p, s, dispatcher)
    }

    /** Keep [EventEditViewModel.state] hot so it computes while the test drives it. */
    private fun CoroutineScope.activate(vm: EventEditViewModel): Job = launch { vm.state.collect {} }

    @Test
    fun `a calendar switched off in settings is not offered as a target`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, visible = false))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `a calendar whose account is not synced to this device is not a target`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // Writable and switched on, but the account keeps its events off the
        // device: nothing saved here ever reaches it, and the provider drops the
        // rows when the subscription comes back (#76).
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, syncsEvents = false))
            eventDetailResult = { detail(calendarId = 1L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `editing an event in a switched-off calendar keeps it in the picker`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // Otherwise the calendar row renders as the "no calendar" error and any
        // pick routes the save through a move the user never asked for.
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L, visible = false))
            eventDetailResult = { detail(calendarId = 2L) }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        advanceUntilIdle()

        assertThat(vm.state.value?.calendars?.map { it.id }).containsExactly(1L, 2L)
        assertThat(vm.state.value?.form?.calendarId).isEqualTo(2L)
        job.cancel()
    }

    @Test
    fun `changing the calendar routes the save through a move, not an update`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L) }
            nextInsertId = 99L
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setCalendar(2L)
        vm.save()
        advanceUntilIdle()

        assertThat(fake.movedEvents).hasSize(1)
        assertThat(fake.movedEvents.single().eventId).isEqualTo(42L)
        assertThat(fake.movedEvents.single().targetCalendarId).isEqualTo(2L)
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Saved)
        job.cancel()
    }

    @Test
    fun `moving a recurring event skips the scope dialog`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L, rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setCalendar(2L)
        vm.save()
        advanceUntilIdle()

        // No AwaitingScope park: a move is inherently whole-series.
        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.Saved)
        assertThat(fake.movedEvents).hasSize(1)
        job.cancel()
    }

    @Test
    fun `editing a recurring event without moving still asks for the scope`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(cal(1L), cal(2L))
            eventDetailResult = { detail(calendarId = 1L, rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)

        vm.openForEdit(eventId = 42L, beginMillis = beginMillis, endMillis = endMillis)
        vm.setTitle("Renamed")
        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value?.saveState).isEqualTo(SaveUiState.AwaitingScope)
        assertThat(fake.movedEvents).isEmpty()
        job.cancel()
    }
}
