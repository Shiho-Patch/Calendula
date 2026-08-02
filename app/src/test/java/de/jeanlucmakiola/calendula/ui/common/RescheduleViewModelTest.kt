package de.jeanlucmakiola.calendula.ui.common

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * The drop pipeline: which repository call a scope maps to, when a recurring drop
 * has to ask first, and every case that must refuse to write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RescheduleViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    // Monday 2026-06-08, midday in the device zone — the zone the drop path
    // reads dates back in. The BYDAY assertions depend on it being a Monday.
    private val monday = LocalDate(2026, 6, 8)
    private val beginMillis = LocalDateTime(monday, LocalTime(12, 0))
        .toInstant(TimeZone.currentSystemDefault())
        .toEpochMilliseconds()
    private val endMillis = beginMillis + 3_600_000L

    private fun cal(
        id: Long,
        canModify: Boolean = true,
        managed: Boolean = false,
    ): CalendarSource = CalendarSource(
        id = id, displayName = "Cal $id", accountName = "acc@local", accountType = "LOCAL",
        color = 0xFF112233.toInt(), isVisibleInSystem = true, canModifyContents = canModify,
        isManaged = managed,
    )

    private fun detail(
        rrule: String? = null,
        isAllDay: Boolean = false,
        isException: Boolean = false,
        anchorMillis: Long = beginMillis,
    ): EventDetail = EventDetail(
        instance = EventInstance(
            instanceId = 42L, eventId = 42L, calendarId = 1L, title = "Standup",
            start = Instant.fromEpochMilliseconds(anchorMillis),
            end = Instant.fromEpochMilliseconds(anchorMillis + (endMillis - beginMillis)),
            isAllDay = isAllDay, color = 0xFF000000.toInt(), location = null,
        ),
        description = null, organizer = null, attendees = emptyList(), rrule = rrule,
        isException = isException,
    )

    private fun viewModel(tempDir: Path, fake: FakeCalendarDataSource): RescheduleViewModel {
        val prefs = CalendarPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("move_prefs.preferences_pb").toFile() },
            ),
        )
        val settings = SettingsPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("move_settings.preferences_pb").toFile() },
            ),
        )
        val repo = CalendarRepositoryImpl(fake, prefs, settings, dispatcher as CoroutineDispatcher)
        return RescheduleViewModel(repo, dispatcher)
    }

    private fun CoroutineScope.activate(vm: RescheduleViewModel): Job =
        launch { vm.movableCalendarIds.collect {} }

    /** A drop two days later, expressed the way the month grid expresses one. */
    private fun toWednesday() = MoveRequest(
        eventId = 42L,
        beginMillis = beginMillis,
        endMillis = endMillis,
        target = MoveTarget.ByDays(2),
    )

    /** A drop an hour later, expressed the way a timeline drag expresses one. */
    private fun oneHourLater() = MoveRequest(
        eventId = 42L,
        beginMillis = beginMillis,
        endMillis = endMillis,
        target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis + 3_600_000L)),
    )

    @Test
    fun `a one-off drop writes straight through with no scope prompt`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).hasSize(1)
        val (id, original, updated) = fake.updatedEvents.single()
        assertThat(id).isEqualTo(42L)
        assertThat(updated.start).isNotEqualTo(original.start)
        assertThat(vm.outcome.value).isInstanceOf(MoveOutcome.Moved::class.java)
    }

    @Test
    fun `a recurring drop parks for the scope instead of writing`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = false))
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `each scope maps to its own repository call`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val occurrence = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("a").also { it.toFile().mkdirs() }, occurrence).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.ThisEvent)
        }
        advanceUntilIdle()
        assertThat(occurrence.updatedOccurrences).hasSize(1)
        assertThat(occurrence.updatedOccurrences.single().second).isEqualTo(beginMillis)

        val following = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("b").also { it.toFile().mkdirs() }, following).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.ThisAndFollowing)
        }
        advanceUntilIdle()
        assertThat(following.updatedFromOccurrences).hasSize(1)

        val series = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        viewModel(tempDir.resolve("c").also { it.toFile().mkdirs() }, series).run {
            move(oneHourLater())
            moveWithScope(RecurringWriteScope.AllEvents)
        }
        advanceUntilIdle()
        assertThat(series.updatedEvents).hasSize(1)
    }

    @Test
    fun `a weekly BYDAY rule is realigned when the whole series moves weekday`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        // Without this the anchor becomes a Wednesday while the rule still says
        // Monday, and the series does not move at all.
        assertThat(fake.updatedEvents.single().third.rrule).isEqualTo("FREQ=WEEKLY;BYDAY=WE")
    }

    @Test
    fun `a rule that cannot be realigned offers only the single occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a same-time drop within one day still needs no realignment`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO,WE") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        // The weekday is unchanged, so the ambiguous BYDAY is not in the way.
        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = false))
    }

    @Test
    fun `a time-only drop that would carry the anchor past midnight offers only the occurrence`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        // The anchor sits at 23:30 while the dragged occurrence is at midday —
        // only possible when the row pins no zone, so the two resolve an hour
        // apart. +1h leaves the occurrence on its day but rolls the anchor onto
        // the next one, which would leave BYDAY naming the wrong weekday.
        val anchor = LocalDateTime(LocalDate(2026, 6, 1), LocalTime(23, 30))
            .toInstant(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO", anchorMillis = anchor) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a second drop is refused while the first is still in flight`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        assertThat(vm.move(toWednesday())).isTrue()
        advanceUntilIdle()

        // Parked on the scope dialog, so the first drop still owns the pipeline.
        assertThat(vm.move(oneHourLater())).isFalse()
        advanceUntilIdle()
        assertThat(fake.updatedEvents).isEmpty()
    }

    @Test
    fun `undo is refused while a write is running`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)
        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo!!

        // A second drop parks on its scope dialog, which holds the pipeline.
        fake.eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        vm.move(toWednesday())
        advanceUntilIdle()

        assertThat(vm.undo(undo)).isFalse()
        advanceUntilIdle()
        assertThat(fake.updatedEvents).hasSize(1)
    }

    @Test
    fun `an exception row is written as a plain event, never as a nested exception`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            // A sync adapter that left the series' rule on the override row.
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY", isException = true) }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).hasSize(1)
        assertThat(fake.updatedOccurrences).isEmpty()
    }

    @Test
    fun `a drop past the series' own UNTIL is refused, not written`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            // Midday UTC, so the device zone can't push the UNTIL date onto the
            // day the event is being dragged to.
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;UNTIL=20260608T120000Z") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        vm.moveWithScope(RecurringWriteScope.AllEvents)
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.BlockedSeriesEnd)
        assertThat(fake.updatedEvents).isEmpty()
    }

    @Test
    fun `dragging a bounded series' last occurrence still allows moving just it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;UNTIL=20260608T120000Z") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(toWednesday())
        advanceUntilIdle()
        // An exception row is constrained by no UNTIL, so the scope stays on offer.
        assertThat(vm.scopePrompt.value).isNotNull()

        vm.moveWithScope(RecurringWriteScope.ThisEvent)
        advanceUntilIdle()

        assertThat(fake.updatedOccurrences).hasSize(1)
        assertThat(vm.outcome.value).isInstanceOf(MoveOutcome.Moved::class.java)
    }

    @Test
    fun `a drag that changes both the day and the time cannot move the series`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY;BYDAY=MO") }
        }
        val vm = viewModel(tempDir, fake)

        // Onto the next day *and* two hours later: a late enough anchor would
        // cross two midnights under the same wall-clock shift.
        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Start(
                    Instant.fromEpochMilliseconds(beginMillis + 26 * 3_600_000L),
                ),
            ),
        )
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isEqualTo(MoveScopePrompt(occurrenceOnly = true))
    }

    @Test
    fun `a zero-distance drop writes nothing and says nothing`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(
            MoveRequest(
                eventId = 42L,
                beginMillis = beginMillis,
                endMillis = endMillis,
                target = MoveTarget.Start(Instant.fromEpochMilliseconds(beginMillis)),
            ),
        )
        advanceUntilIdle()

        assertThat(fake.updatedEvents).isEmpty()
        assertThat(vm.outcome.value).isNull()
    }

    @Test
    fun `a vanished event reports itself as gone`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { null } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Gone)
    }

    @Test
    fun `a revoked write permission is reported apart from a plain failure`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail() }
            writeError = SecurityException("revoked")
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()

        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.WriteDenied)
    }

    @Test
    fun `undo writes the move back the other way`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply { eventDetailResult = { detail() } }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        advanceUntilIdle()
        val undo = (vm.outcome.value as MoveOutcome.Moved).undo
        assertThat(undo).isNotNull()

        vm.undo(undo!!)
        advanceUntilIdle()

        assertThat(fake.updatedEvents).hasSize(2)
        val forward = fake.updatedEvents[0]
        val back = fake.updatedEvents[1]
        assertThat(back.second).isEqualTo(forward.third)
        assertThat(back.third).isEqualTo(forward.second)
        assertThat(vm.outcome.value).isEqualTo(MoveOutcome.Undone)
    }

    @Test
    fun `an occurrence write offers no undo, since shifting back cannot restore it`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.moveWithScope(RecurringWriteScope.ThisEvent)
        advanceUntilIdle()

        assertThat((vm.outcome.value as MoveOutcome.Moved).undo).isNull()
    }

    @Test
    fun `only writable, unmanaged calendars can be dragged from`(
        @TempDir tempDir: Path,
    ) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(
                cal(1L),
                cal(2L, canModify = false),
                cal(3L, managed = true),
            )
        }
        val vm = viewModel(tempDir, fake)
        val job = activate(vm)
        advanceUntilIdle()

        assertThat(vm.movableCalendarIds.value).containsExactly(1L)
        job.cancel()
    }

    @Test
    fun `cancelling the scope dialog writes nothing`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { detail(rrule = "FREQ=WEEKLY") }
        }
        val vm = viewModel(tempDir, fake)

        vm.move(oneHourLater())
        vm.cancelScope()
        advanceUntilIdle()

        assertThat(vm.scopePrompt.value).isNull()
        assertThat(fake.updatedEvents).isEmpty()
        assertThat(fake.updatedOccurrences).isEmpty()
    }
}
