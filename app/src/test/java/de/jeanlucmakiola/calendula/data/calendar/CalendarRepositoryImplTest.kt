package de.jeanlucmakiola.calendula.data.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarRepositoryImplTest {

    private fun newPrefs(tempDir: Path): CalendarPrefs =
        CalendarPrefs(newDataStore(tempDir))

    private fun newSettings(tempDir: Path): SettingsPrefs =
        SettingsPrefs(
            PreferenceDataStoreFactory.create(
                produceFile = { tempDir.resolve("repo_test_settings.preferences_pb").toFile() },
            ),
        )

    private fun newDataStore(tempDir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("repo_test_prefs.preferences_pb").toFile() },
        )

    private fun makeCal(
        id: Long,
        name: String = "Cal $id",
        visible: Boolean = true,
    ): CalendarSource =
        CalendarSource(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), visible)

    private fun makeEvent(
        id: Long,
        title: String = "E $id",
        calendarId: Long = 1L,
    ): EventInstance = EventInstance(
        instanceId = id, eventId = id, calendarId = calendarId,
        title = title,
        start = Instant.fromEpochMilliseconds(1_000_000_000L),
        end = Instant.fromEpochMilliseconds(1_000_003_600L),
        isAllDay = false, color = 0xFF000000.toInt(), location = null,
    )

    @Test
    fun `calendars emits initial query result on subscribe`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            val first = awaitItem()
            assertThat(first.map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars re-emits after change listener tick`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L))
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            assertThat(awaitItem().map { it.id }).containsExactly(1L)

            fake.calendarsResult = listOf(makeCal(1L), makeCal(2L))
            fake.tick()

            assertThat(awaitItem().map { it.id }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances forwards epoch-millis bounds to data source`(@TempDir tempDir: Path) = runTest {
        var observedBegin: Long? = null
        var observedEnd: Long? = null
        val fake = FakeCalendarDataSource().apply {
            instancesResult = {b, e ->
                observedBegin = b
                observedEnd = e
                listOf(makeEvent(10L))
            }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(1_000L)..Instant.fromEpochMilliseconds(2_000L)
        repo.instances(range).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(observedBegin).isEqualTo(1_000L)
        assertThat(observedEnd).isEqualTo(2_000L)
    }

    @Test
    fun `instances passes-through whatever the data source returns`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            instancesResult = {_, _ -> listOf(makeEvent(10L, "Good")) }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            val first = awaitItem()
            assertThat(first.map { it.title }).containsExactly("Good")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances drops events whose calendar the user hid`(@TempDir tempDir: Path) = runTest {
        val prefs = newPrefs(tempDir)
        prefs.setHiddenCalendarIds(setOf(2L))
        val fake = FakeCalendarDataSource().apply {
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "Visible", calendarId = 1L),
                    makeEvent(11L, "Hidden", calendarId = 2L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("Visible")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances re-emits when the hidden set changes`(@TempDir tempDir: Path) = runTest {
        val prefs = newPrefs(tempDir)
        val fake = FakeCalendarDataSource().apply {
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "A", calendarId = 1L),
                    makeEvent(11L, "B", calendarId = 2L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A", "B").inOrder()

            prefs.setHiddenCalendarIds(setOf(2L))

            assertThat(awaitItem().map { it.title }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances drops events whose calendar is hidden at system level`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L, visible = false))
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "Shown", calendarId = 1L),
                    makeEvent(11L, "Switched off", calendarId = 2L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("Shown")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances applies the union of hidden and system-invisible calendars`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = newPrefs(tempDir)
        prefs.setHiddenCalendarIds(setOf(2L))
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L), makeCal(3L, visible = false))
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "Shown", calendarId = 1L),
                    makeEvent(11L, "Hidden", calendarId = 2L),
                    makeEvent(12L, "Switched off", calendarId = 3L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("Shown")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `instances re-emit after a calendar is switched off in the provider`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "A", calendarId = 1L),
                    makeEvent(11L, "B", calendarId = 2L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A", "B").inOrder()

            // The write itself is what the provider notifies about; the observer
            // tick is what makes the views re-query.
            repo.setCalendarsVisible(listOf(2L), false)
            fake.tick()

            assertThat(awaitItem().map { it.title }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCalendarsVisible addresses each calendar on its own`(
        @TempDir tempDir: Path,
    ) = runTest {
        // An _id IN (…) batch would skip the provider's own reminder-alarm
        // reschedule, so every calendar must be written by appended id.
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L), makeCal(3L))
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.setCalendarsVisible(listOf(1L, 3L), false)

        assertThat(fake.visibilityWrites).containsExactly(1L to false, 3L to false).inOrder()
    }

    @Test
    fun `without write permission the switch is kept app-side and still filters`(
        @TempDir tempDir: Path,
    ) = runTest {
        // READ granted, WRITE denied: the provider flag can't be written, so the
        // choice is parked in the pending set — and honoured from there, or the
        // user's switched-off calendars would come back on upgrade (#75).
        val prefs = newPrefs(tempDir)
        val fake = FakeCalendarDataSource().apply {
            canWrite = false
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
            instancesResult = { _, _ ->
                listOf(
                    makeEvent(10L, "A", calendarId = 1L),
                    makeEvent(11L, "B", calendarId = 2L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A", "B").inOrder()

            repo.setCalendarsVisible(listOf(2L), false)

            assertThat(awaitItem().map { it.title }).containsExactly("A")
            assertThat(fake.visibilityWrites).isEmpty()
            assertThat(prefs.pendingDisabledCalendarIds.first()).containsExactly(2L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars reports a pending switch-off as off`(@TempDir tempDir: Path) = runTest {
        // Otherwise the Settings switch would snap straight back on for a
        // read-only install, and the pickers would keep offering the calendar.
        val prefs = newPrefs(tempDir)
        val fake = FakeCalendarDataSource().apply {
            canWrite = false
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            assertThat(awaitItem().map { it.isVisibleInSystem }).containsExactly(true, true)

            repo.setCalendarsVisible(listOf(2L), false)

            assertThat(awaitItem().single { it.id == 2L }.isVisibleInSystem).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching a calendar back on without write permission retires its entry`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = newPrefs(tempDir)
        prefs.addPendingDisabledCalendarIds(setOf(2L))
        val fake = FakeCalendarDataSource().apply {
            canWrite = false
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), Dispatchers.Unconfined)

        repo.setCalendarsVisible(listOf(2L), true)

        assertThat(prefs.pendingDisabledCalendarIds.first()).isEmpty()
    }

    @Test
    fun `a provider write clears anything still pending for that calendar`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = newPrefs(tempDir)
        prefs.addPendingDisabledCalendarIds(setOf(2L))
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), Dispatchers.Unconfined)

        repo.setCalendarsVisible(listOf(2L), false)

        assertThat(fake.visibilityWrites).containsExactly(2L to false)
        assertThat(prefs.pendingDisabledCalendarIds.first()).isEmpty()
    }

    @Test
    fun `one tick costs one calendar query however many collectors there are`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
            instancesResult = { _, _ -> listOf(makeEvent(10L, "A", calendarId = 1L)) }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.calendars().test {
            awaitItem()
            repo.instances(range).test {
                awaitItem()
                // Both flows listed/filtered off the same snapshot.
                assertThat(fake.calendarQueries).isEqualTo(1)
                cancelAndIgnoreRemainingEvents()
            }

            // The next tick invalidates it — one fresh read, not one per flow.
            // (The list has to change: an identical one is collapsed.)
            fake.calendarsResult = listOf(makeCal(1L), makeCal(2L), makeCal(3L))
            fake.tick()
            awaitItem()
            assertThat(fake.calendarQueries).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `calendars does not re-emit an unchanged list`(@TempDir tempDir: Path) = runTest {
        // The store is shared with SettingsPrefs, so an unrelated write would
        // otherwise re-run every view's combine for an identical list.
        val prefs = newPrefs(tempDir)
        val fake = FakeCalendarDataSource().apply { calendarsResult = listOf(makeCal(1L)) }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))

        repo.calendars().test {
            assertThat(awaitItem().map { it.id }).containsExactly(1L)

            prefs.setLastUsedCalendarId(1L)
            fake.tick()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a flushed switch-off never reads as on again before the provider ticks`(
        @TempDir tempDir: Path,
    ) = runTest {
        // The reconciler's shape: write VISIBLE = 0 straight to the provider,
        // then release the id app-side. The provider's notification only arrives
        // afterwards (it is dispatched through the main looper), so the release
        // must not be read against the snapshot from before the write — that
        // would flash exactly the events being hidden back into every view.
        val prefs = newPrefs(tempDir)
        prefs.addPendingDisabledCalendarIds(setOf(2L))
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L))
            instancesResult = { _, _ ->
                listOf(makeEvent(10L, "A", calendarId = 1L), makeEvent(11L, "B", calendarId = 2L))
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), UnconfinedTestDispatcher(testScheduler))
        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)

        // Warm the snapshot the way an open view would.
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }

        fake.setCalendarVisible(2L, false) // no tick(): the observer hasn't fired yet
        prefs.removePendingDisabledCalendarIds(setOf(2L))

        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }
        repo.calendars().test {
            assertThat(awaitItem().single { it.id == 2L }.isVisibleInSystem).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searchEvents drops results from calendars that are off or hidden`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = newPrefs(tempDir)
        prefs.setHiddenCalendarIds(setOf(3L))
        val fake = FakeCalendarDataSource().apply {
            calendarsResult = listOf(makeCal(1L), makeCal(2L, visible = false), makeCal(3L))
            searchResult = {
                listOf(
                    makeEvent(10L, "Shown", calendarId = 1L),
                    makeEvent(11L, "Switched off", calendarId = 2L),
                    makeEvent(12L, "Hidden", calendarId = 3L),
                )
            }
        }
        val repo = CalendarRepositoryImpl(fake, prefs, newSettings(tempDir), Dispatchers.Unconfined)

        assertThat(repo.searchEvents("e").map { it.title }).containsExactly("Shown")
    }

    @Test
    fun `createEvent delegates and returns the new id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 77L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            title = "Stand-up",
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 15)),
        )

        val id = repo.createEvent(form)

        assertThat(id).isEqualTo(77L)
        assertThat(fake.insertedForms).containsExactly(form)
    }

    @Test
    fun `createEvent passes the configured all-day reminder time to the data source`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource()
        val settings = newSettings(tempDir)
        settings.setAllDayReminderTimeMinutes(8 * 60) // 08:00
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), settings, Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(0, 0)),
        )

        repo.createEvent(form)

        assertThat(fake.allDayReminderTimes).containsExactly(480)
    }

    @Test
    fun `createEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("insert event")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(10, 0)),
        )

        try {
            repo.createEvent(form)
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("insert")
        }
    }

    @Test
    fun `updateEvent forwards id and both forms`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val original = EventForm(
            calendarId = 1L,
            title = "Stand-up",
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 15)),
        )
        val updated = original.copy(title = "Daily")

        repo.updateEvent(eventId = 42L, original = original, updated = updated)

        assertThat(fake.updatedEvents).containsExactly(Triple(42L, original, updated))
    }

    @Test
    fun `moveEvent forwards id, target calendar and both forms`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 77L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val original = EventForm(
            calendarId = 1L,
            title = "Stand-up",
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 15)),
        )
        val updated = original.copy(calendarId = 3L)

        val newId = repo.moveEvent(eventId = 42L, targetCalendarId = 3L, original = original, updated = updated)

        assertThat(newId).isEqualTo(77L)
        assertThat(fake.movedEvents).containsExactly(
            FakeCalendarDataSource.MovedEvent(42L, 3L, original, updated),
        )
    }

    @Test
    fun `moveEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("insert moved event into calendar id=3")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(10, 0)),
        )

        try {
            repo.moveEvent(eventId = 42L, targetCalendarId = 3L, original = form, updated = form.copy(calendarId = 3L))
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("3")
        }
    }

    @Test
    fun `updateEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("update event id=42")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(10, 0)),
        )

        try {
            repo.updateEvent(eventId = 42L, original = form, updated = form.copy(title = "X"))
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("42")
        }
    }

    @Test
    fun `deleteEvent delegates to the data source`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.deleteEvent(eventId = 42L)

        assertThat(fake.deletedEventIds).containsExactly(42L)
        assertThat(fake.deletedOccurrences).isEmpty()
    }

    @Test
    fun `deleteOccurrence forwards event id and begin time`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.deleteOccurrence(eventId = 42L, beginMillis = 1_000L)

        assertThat(fake.deletedOccurrences).containsExactly(42L to 1_000L)
        assertThat(fake.deletedEventIds).isEmpty()
    }

    @Test
    fun `deleteEventFromOccurrence forwards event id and begin time`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.deleteEventFromOccurrence(eventId = 42L, beginMillis = 1_000L)

        assertThat(fake.deletedFromOccurrences).containsExactly(42L to 1_000L)
        assertThat(fake.deletedEventIds).isEmpty()
        assertThat(fake.deletedOccurrences).isEmpty()
    }

    @Test
    fun `updateOccurrence forwards target and form, returns the exception id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 88L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val form = EventForm(
            calendarId = 1L,
            title = "Moved",
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(10, 0)),
        )

        val id = repo.updateOccurrence(eventId = 42L, beginMillis = 1_000L, form = form)

        assertThat(id).isEqualTo(88L)
        assertThat(fake.updatedOccurrences).containsExactly(Triple(42L, 1_000L, form))
        assertThat(fake.updatedEvents).isEmpty()
    }

    @Test
    fun `updateEventFromOccurrence forwards target and forms, returns the new id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 99L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val original = EventForm(
            calendarId = 1L,
            title = "Weekly",
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(10, 0)),
            rrule = "FREQ=WEEKLY",
        )
        val updated = original.copy(title = "Weekly, renamed")

        val id = repo.updateEventFromOccurrence(
            eventId = 42L,
            beginMillis = 1_000L,
            original = original,
            updated = updated,
        )

        assertThat(id).isEqualTo(99L)
        assertThat(fake.updatedFromOccurrences).containsExactly(Triple(42L, 1_000L, updated))
        assertThat(fake.updatedEvents).isEmpty()
    }

    @Test
    fun `deleteEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("delete event id=42")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        try {
            repo.deleteEvent(eventId = 42L)
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("42")
        }
    }

    @Test
    fun `createLocalCalendar delegates and returns the new id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextCalendarId = 501L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        val id = repo.createLocalCalendar(
            displayName = "Home",
            color = 0xFF33B679.toInt(),
            description = "House stuff",
        )

        assertThat(id).isEqualTo(501L)
        assertThat(fake.createdCalendars).containsExactly(
            FakeCalendarDataSource.CreatedCalendar("Home", 0xFF33B679.toInt(), "House stuff"),
        )
    }

    @Test
    fun `updateCalendar forwards id, name, color and description`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.updateCalendar(
            id = 5L,
            displayName = "Renamed",
            color = 0xFF039BE5.toInt(),
            description = null,
        )

        assertThat(fake.updatedCalendars).containsExactly(
            FakeCalendarDataSource.UpdatedCalendar(5L, "Renamed", 0xFF039BE5.toInt(), null),
        )
    }

    @Test
    fun `deleteCalendar delegates to the data source`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.deleteCalendar(id = 7L)

        assertThat(fake.deletedCalendarIds).containsExactly(7L)
    }

    @Test
    fun `createLocalCalendar propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("create local calendar 'Home'")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        try {
            repo.createLocalCalendar(displayName = "Home", color = 0, description = null)
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("Home")
        }
    }

    @Test
    fun `eventDetail throws NoSuchEventException when data source returns null`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { null }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        try {
            repo.eventDetail(eventId = 999L)
            error("Expected NoSuchEventException")
        } catch (expected: NoSuchEventException) {
            assertThat(expected.message).contains("999")
        }
    }

    @Test
    fun `eventColorPalette delegates to the data source for the given calendar`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource().apply {
            eventColorPaletteResult = { id ->
                if (id == 7L) listOf(EventColorOption("5", 0xFF33B679.toInt())) else emptyList()
            }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        assertThat(repo.eventColorPalette(7L))
            .containsExactly(EventColorOption("5", 0xFF33B679.toInt()))
        assertThat(repo.eventColorPalette(8L)).isEmpty()
    }

    @Test
    fun `importEvents skips events whose UID already exists and inserts the rest`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource().apply {
            existingUidsResult = setOf("dup@x")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)
        val events = listOf(
            parsedEvent("dup@x"), // already present → skipped
            parsedEvent("new@x"), // inserted
            parsedEvent(null), // no UID → always inserted
        )

        val summary = repo.importEvents(targetCalendarId = 3L, events = events)

        assertThat(summary.imported).isEqualTo(2)
        assertThat(summary.skippedDuplicate).isEqualTo(1)
        assertThat(fake.importedEvents.map { it.first.uid }).containsExactly("new@x", null)
        assertThat(fake.importedEvents.map { it.second }).containsExactly(3L, 3L)
    }

    @Test
    fun `exportEvents forwards the chosen calendar-id subset to the data source`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.exportEvents(calendarIds = setOf(1L, 4L))

        assertThat(fake.lastExportableEventsCalendarIds).containsExactly(1L, 4L)
    }

    @Test
    fun `exportEvents defaults to null so every eligible calendar is exported`(
        @TempDir tempDir: Path,
    ) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), newSettings(tempDir), Dispatchers.Unconfined)

        repo.exportEvents()

        assertThat(fake.lastExportableEventsCalendarIds).isNull()
    }

    private fun parsedEvent(uid: String?) = de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent(
        uid = uid,
        summary = "E",
        start = Instant.fromEpochMilliseconds(1_000_000_000L),
        end = Instant.fromEpochMilliseconds(1_000_003_600L),
        isAllDay = false,
        zoneId = "UTC",
    )
}
