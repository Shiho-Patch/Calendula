package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class CalendarPrefsTest {

    private fun newDataStore(tempDir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("test_prefs.preferences_pb").toFile() },
        )

    @Test
    fun `hiddenCalendarIds defaults to empty when unset`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.hiddenCalendarIds.first()).isEmpty()
    }

    @Test
    fun `setHiddenCalendarIds round-trips through DataStore`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        prefs.setHiddenCalendarIds(setOf(1L, 42L, 7L))
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L, 42L, 7L))
    }

    @Test
    fun `setting empty set clears storage`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setHiddenCalendarIds(setOf(1L))
        prefs.setHiddenCalendarIds(emptySet())
        assertThat(prefs.hiddenCalendarIds.first()).isEmpty()
    }

    @Test
    fun `garbage stored string is parsed defensively`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        store.updateData { p ->
            val mutable = p.toMutablePreferences()
            mutable[CalendarPrefs.HIDDEN_IDS_KEY] = "1,abc,3"
            mutable
        }
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L, 3L))
    }

    @Test
    fun `the pending disabled set reads back what an older version stored`(
        @TempDir tempDir: Path,
    ) = runTest {
        // Same key as the retired app-local "disabled calendars" model: an
        // upgrade inherits that set as switch-offs still owed to the provider.
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        store.updateData { p ->
            p.toMutablePreferences().apply { this[CalendarPrefs.DISABLED_IDS_KEY] = "2,9" }
        }
        assertThat(prefs.pendingDisabledCalendarIds.first()).isEqualTo(setOf(2L, 9L))
    }

    @Test
    fun `the pending disabled set is empty when nothing was ever stored`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.pendingDisabledCalendarIds.first()).isEmpty()
    }

    @Test
    fun `pending ids are added and dropped one at a time`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.addPendingDisabledCalendarIds(listOf(2L, 9L))
        prefs.addPendingDisabledCalendarIds(listOf(4L))

        prefs.removePendingDisabledCalendarIds(setOf(9L))

        assertThat(prefs.pendingDisabledCalendarIds.first()).isEqualTo(setOf(2L, 4L))
    }

    @Test
    fun `draining the pending set leaves the hidden set alone`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setHiddenCalendarIds(setOf(1L))
        prefs.addPendingDisabledCalendarIds(setOf(2L))

        prefs.removePendingDisabledCalendarIds(setOf(2L))

        assertThat(prefs.pendingDisabledCalendarIds.first()).isEmpty()
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L))
    }

    @Test
    fun `the visibility notice is unevaluated until it is written`(
        @TempDir tempDir: Path,
    ) = runTest {
        // Null is what makes the evaluation one-shot: "no" is stored just as
        // firmly as "yes", so the notice can't resurface on a later launch.
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.visibilityNoticePending.first()).isNull()

        prefs.setVisibilityNoticePending(true)
        assertThat(prefs.visibilityNoticePending.first()).isTrue()

        prefs.setVisibilityNoticePending(false)
        assertThat(prefs.visibilityNoticePending.first()).isFalse()
    }
}
