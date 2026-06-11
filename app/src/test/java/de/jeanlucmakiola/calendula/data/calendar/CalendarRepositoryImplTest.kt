package de.jeanlucmakiola.calendula.data.calendar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.Dispatchers
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

    private fun newDataStore(tempDir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("repo_test_prefs.preferences_pb").toFile() },
        )

    private fun makeCal(id: Long, name: String = "Cal $id"): CalendarSource =
        CalendarSource(id, name, "x@y", "LOCAL", 0xFF112233.toInt(), true)

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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), UnconfinedTestDispatcher(testScheduler))

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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), UnconfinedTestDispatcher(testScheduler))

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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), UnconfinedTestDispatcher(testScheduler))

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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), UnconfinedTestDispatcher(testScheduler))

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
        val repo = CalendarRepositoryImpl(fake, prefs, UnconfinedTestDispatcher(testScheduler))

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
        val repo = CalendarRepositoryImpl(fake, prefs, UnconfinedTestDispatcher(testScheduler))

        val range = Instant.fromEpochMilliseconds(0)..Instant.fromEpochMilliseconds(10_000L)
        repo.instances(range).test {
            assertThat(awaitItem().map { it.title }).containsExactly("A", "B").inOrder()

            prefs.setHiddenCalendarIds(setOf(2L))

            assertThat(awaitItem().map { it.title }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createEvent delegates and returns the new id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 77L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
    fun `createEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("insert event")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
    fun `updateEvent propagates write failures`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            writeError = WriteFailedException("update event id=42")
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)

        repo.deleteEvent(eventId = 42L)

        assertThat(fake.deletedEventIds).containsExactly(42L)
        assertThat(fake.deletedOccurrences).isEmpty()
    }

    @Test
    fun `deleteOccurrence forwards event id and begin time`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)

        repo.deleteOccurrence(eventId = 42L, beginMillis = 1_000L)

        assertThat(fake.deletedOccurrences).containsExactly(42L to 1_000L)
        assertThat(fake.deletedEventIds).isEmpty()
    }

    @Test
    fun `deleteEventFromOccurrence forwards event id and begin time`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource()
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)

        repo.deleteEventFromOccurrence(eventId = 42L, beginMillis = 1_000L)

        assertThat(fake.deletedFromOccurrences).containsExactly(42L to 1_000L)
        assertThat(fake.deletedEventIds).isEmpty()
        assertThat(fake.deletedOccurrences).isEmpty()
    }

    @Test
    fun `updateOccurrence forwards target and form, returns the exception id`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply { nextInsertId = 88L }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)
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
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)

        try {
            repo.deleteEvent(eventId = 42L)
            error("Expected WriteFailedException")
        } catch (expected: WriteFailedException) {
            assertThat(expected.message).contains("42")
        }
    }

    @Test
    fun `eventDetail throws NoSuchEventException when data source returns null`(@TempDir tempDir: Path) = runTest {
        val fake = FakeCalendarDataSource().apply {
            eventDetailResult = { null }
        }
        val repo = CalendarRepositoryImpl(fake, newPrefs(tempDir), Dispatchers.Unconfined)

        try {
            repo.eventDetail(eventId = 999L)
            error("Expected NoSuchEventException")
        } catch (expected: NoSuchEventException) {
            assertThat(expected.message).contains("999")
        }
    }
}
