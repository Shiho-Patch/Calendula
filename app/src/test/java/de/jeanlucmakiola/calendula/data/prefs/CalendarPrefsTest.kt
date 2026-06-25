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
    fun `disabledCalendarIds defaults to empty when unset`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        assertThat(prefs.disabledCalendarIds.first()).isEmpty()
    }

    @Test
    fun `setDisabledCalendarIds round-trips through DataStore`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setDisabledCalendarIds(setOf(1L, 42L, 7L))
        assertThat(prefs.disabledCalendarIds.first()).isEqualTo(setOf(1L, 42L, 7L))
    }

    @Test
    fun `setting empty disabled set clears storage`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setDisabledCalendarIds(setOf(1L))
        prefs.setDisabledCalendarIds(emptySet())
        assertThat(prefs.disabledCalendarIds.first()).isEmpty()
    }

    @Test
    fun `hidden and disabled sets are stored independently`(@TempDir tempDir: Path) = runTest {
        val prefs = CalendarPrefs(newDataStore(tempDir))
        prefs.setHiddenCalendarIds(setOf(1L))
        prefs.setDisabledCalendarIds(setOf(2L))
        assertThat(prefs.hiddenCalendarIds.first()).isEqualTo(setOf(1L))
        assertThat(prefs.disabledCalendarIds.first()).isEqualTo(setOf(2L))
    }
}
