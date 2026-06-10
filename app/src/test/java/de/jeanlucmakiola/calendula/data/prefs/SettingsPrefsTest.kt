package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Locale

class SettingsPrefsTest {

    private fun newDataStore(tempDir: Path): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("settings_test.preferences_pb").toFile() },
        )

    @Test
    fun `defaults are system theme, dynamic colour on, auto week start`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.dynamicColor.first()).isTrue()
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.AUTO)
    }

    @Test
    fun `theme mode round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setThemeMode(ThemeMode.DARK)
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `dynamic colour round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setDynamicColor(false)
        assertThat(prefs.dynamicColor.first()).isFalse()
    }

    @Test
    fun `week start round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setWeekStart(WeekStartPref.SUNDAY)
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.SUNDAY)
    }

    @Test
    fun `garbage stored enum falls back to default`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.THEME_MODE_KEY] = "PLAID"
            m
        }
        assertThat(prefs.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `explicit week-start prefs resolve regardless of locale`() {
        assertThat(WeekStartPref.MONDAY.resolveFirstDay(Locale.US)).isEqualTo(DayOfWeek.MONDAY)
        assertThat(WeekStartPref.SUNDAY.resolveFirstDay(Locale.GERMANY)).isEqualTo(DayOfWeek.SUNDAY)
    }

    @Test
    fun `auto week start follows the locale convention`() {
        assertThat(WeekStartPref.AUTO.resolveFirstDay(Locale.GERMANY)).isEqualTo(DayOfWeek.MONDAY)
        assertThat(WeekStartPref.AUTO.resolveFirstDay(Locale.US)).isEqualTo(DayOfWeek.SUNDAY)
    }
}
