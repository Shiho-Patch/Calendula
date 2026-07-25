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
    fun `legacy disabled set reads back what an older version stored`(
        @TempDir tempDir: Path,
    ) = runTest {
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        store.updateData { p ->
            p.toMutablePreferences().apply { this[CalendarPrefs.DISABLED_IDS_KEY] = "2,9" }
        }
        assertThat(prefs.legacyDisabledCalendarIds()).isEqualTo(setOf(2L, 9L))
    }

    @Test
    fun `legacy disabled set is empty when nothing was ever stored`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.legacyDisabledCalendarIds()).isEmpty()
    }

    @Test
    fun `clearing the legacy disabled set leaves the hidden set alone`(
        @TempDir tempDir: Path,
    ) = runTest {
        val store = newDataStore(tempDir)
        val prefs = CalendarPrefs(store)
        prefs.setHiddenCalendarIds(setOf(1L))
        store.updateData { p ->
            p.toMutablePreferences().apply { this[CalendarPrefs.DISABLED_IDS_KEY] = "2" }
        }

        prefs.clearLegacyDisabledCalendarIds()

        assertThat(prefs.legacyDisabledCalendarIds()).isEmpty()
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L))
    }

    @Test
    fun `visibility migration guard defaults to not-done and latches`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.visibilityMigrationDone.first()).isFalse()

        prefs.setVisibilityMigrationDone()

        assertThat(prefs.visibilityMigrationDone.first()).isTrue()
    }
}
