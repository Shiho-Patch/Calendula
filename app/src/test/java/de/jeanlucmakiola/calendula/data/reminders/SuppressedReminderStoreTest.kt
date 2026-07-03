package de.jeanlucmakiola.calendula.data.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SuppressedReminderStoreTest {

    private fun newDataStore(tempDir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("test_prefs.preferences_pb").toFile() },
        )

    private fun alert(
        alertId: Long,
        calendarId: Long,
        endMillis: Long = Long.MAX_VALUE,
        title: String = "Event $alertId",
        location: String? = null,
    ) = ReminderAlert(
        alertId = alertId,
        eventId = alertId * 10,
        calendarId = calendarId,
        beginMillis = 0L,
        endMillis = endMillis,
        title = title,
        location = location,
        isAllDay = false,
    )

    @Test
    fun `encode then decode round-trips every field including delimiters`() {
        val original = alert(
            alertId = 7,
            calendarId = 42,
            endMillis = 123_456_789L,
            // Free-text with the field separator and other awkward characters.
            title = "Lunch | with | Alice",
            location = "Café, 3rd floor | room B",
        ).copy(beginMillis = 100L, isAllDay = true)

        val decoded = decodeStashEntry(encodeStashEntry(original))

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `decode returns null for a malformed entry`() {
        assertThat(decodeStashEntry("not-a-valid-entry")).isNull()
    }

    @Test
    fun `null location round-trips`() {
        val original = alert(1, calendarId = 1, location = null)
        assertThat(decodeStashEntry(encodeStashEntry(original))).isEqualTo(original)
    }

    @Test
    fun `recoverFor returns and removes only the re-enabled calendars`() = runTest {
        val store = SuppressedReminderStore(newDataStore(tempDir))
        val keep = alert(1, calendarId = 100)
        val recoverA = alert(2, calendarId = 200)
        val recoverB = alert(3, calendarId = 200)
        store.stash(listOf(keep, recoverA, recoverB), nowMillis = 0L)

        val recovered = store.recoverFor(setOf(200L), nowMillis = 0L)

        assertThat(recovered).containsExactly(recoverA, recoverB)
        // The still-disabled calendar's alert stays stashed; the recovered ones are gone.
        assertThat(store.recoverFor(setOf(100L, 200L), nowMillis = 0L)).containsExactly(keep)
    }

    @Test
    fun `stash drops alerts whose event already ended`() = runTest {
        val store = SuppressedReminderStore(newDataStore(tempDir))
        val past = alert(1, calendarId = 100, endMillis = 500L)
        val future = alert(2, calendarId = 100, endMillis = 2_000L)

        store.stash(listOf(past, future), nowMillis = 1_000L)

        assertThat(store.recoverFor(setOf(100L), nowMillis = 1_000L)).containsExactly(future)
    }

    @Test
    fun `purgeExpired removes only entries past their event end`() = runTest {
        val store = SuppressedReminderStore(newDataStore(tempDir))
        // Stash both before "now" so neither is dropped on write, then advance time.
        store.stash(
            listOf(
                alert(1, calendarId = 100, endMillis = 500L),
                alert(2, calendarId = 100, endMillis = 2_000L),
            ),
            nowMillis = 0L,
        )

        store.purgeExpired(nowMillis = 1_000L)

        assertThat(store.recoverFor(setOf(100L), nowMillis = 0L).map { it.alertId })
            .containsExactly(2L)
    }

    @Test
    fun `stash replaces an existing entry with the same alert id`() = runTest {
        val store = SuppressedReminderStore(newDataStore(tempDir))
        store.stash(listOf(alert(1, calendarId = 100, title = "old")), nowMillis = 0L)
        store.stash(listOf(alert(1, calendarId = 100, title = "new")), nowMillis = 0L)

        val recovered = store.recoverFor(setOf(100L), nowMillis = 0L)

        assertThat(recovered).hasSize(1)
        assertThat(recovered.single().title).isEqualTo("new")
    }

    @Test
    fun `isRelevantAt is true up to the event end and false after`() {
        val a = alert(1, calendarId = 1, endMillis = 1_000L)
        assertThat(a.isRelevantAt(999L)).isTrue()
        assertThat(a.isRelevantAt(1_000L)).isTrue()
        assertThat(a.isRelevantAt(1_001L)).isFalse()
    }

    @TempDir
    lateinit var tempDir: Path
}
