package de.jeanlucmakiola.calendula.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
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
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.Auto)
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
    fun `week start round-trips any day`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setWeekStart(WeekStartPref.Day(DayOfWeek.SATURDAY))
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.Day(DayOfWeek.SATURDAY))
    }

    @Test
    fun `legacy MONDAY week-start value migrates to Day`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.WEEK_START_KEY] = "MONDAY"
            m
        }
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.Day(DayOfWeek.MONDAY))
    }

    @Test
    fun `garbage week-start value falls back to Auto`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.WEEK_START_KEY] = "FUNDAY"
            m
        }
        assertThat(prefs.weekStart.first()).isEqualTo(WeekStartPref.Auto)
    }

    @Test
    fun `time format defaults to auto and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.timeFormat.first()).isEqualTo(TimeFormatPref.AUTO)
        prefs.setTimeFormat(TimeFormatPref.TWELVE_HOUR)
        assertThat(prefs.timeFormat.first()).isEqualTo(TimeFormatPref.TWELVE_HOUR)
    }

    @Test
    fun `hour lines default off and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.showHourLines.first()).isFalse()
        prefs.setShowHourLines(true)
        assertThat(prefs.showHourLines.first()).isTrue()
    }

    @Test
    fun `agenda ranges default to month and round-trip independently`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.agendaScreenRange.first()).isEqualTo(AgendaRange.Month)
        assertThat(prefs.agendaWidgetRange.first()).isEqualTo(AgendaRange.Month)

        prefs.setAgendaScreenRange(AgendaRange.Custom(14))
        prefs.setAgendaWidgetRange(AgendaRange.Week)
        assertThat(prefs.agendaScreenRange.first()).isEqualTo(AgendaRange.Custom(14))
        assertThat(prefs.agendaWidgetRange.first()).isEqualTo(AgendaRange.Week)
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
    fun `form fields default to location and description`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultFormFields.first()).containsExactly(
            EventFormField.Location,
            EventFormField.Description,
        )
    }

    @Test
    fun `form field toggle round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setFormFieldDefault(EventFormField.Reminders, enabled = true)
        prefs.setFormFieldDefault(EventFormField.Location, enabled = false)
        assertThat(prefs.defaultFormFields.first()).containsExactly(
            EventFormField.Description,
            EventFormField.Reminders,
        )
    }

    @Test
    fun `disabling every form field persists as none, not factory default`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        EventFormField.entries.forEach { prefs.setFormFieldDefault(it, enabled = false) }
        assertThat(prefs.defaultFormFields.first()).isEmpty()
    }

    @Test
    fun `unknown stored form-field names are dropped`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.FORM_FIELDS_KEY] = "Location,Hologram"
            m
        }
        assertThat(prefs.defaultFormFields.first()).containsExactly(EventFormField.Location)
    }

    @Test
    fun `reminders default to enabled, onboarding to not done`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.remindersEnabled.first()).isTrue()
        assertThat(prefs.reminderOnboardingDone.first()).isFalse()
    }

    @Test
    fun `reminders toggle round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setRemindersEnabled(false)
        assertThat(prefs.remindersEnabled.first()).isFalse()
    }

    @Test
    fun `reminder onboarding completes one-way`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setReminderOnboardingDone()
        assertThat(prefs.reminderOnboardingDone.first()).isTrue()
    }

    @Test
    fun `default reminder is none until set`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultReminderMinutes.first()).isNull()
    }

    @Test
    fun `default reminder round-trips, including none`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setDefaultReminderMinutes(30)
        assertThat(prefs.defaultReminderMinutes.first()).isEqualTo(30)
        prefs.setDefaultReminderMinutes(null)
        assertThat(prefs.defaultReminderMinutes.first()).isNull()
    }

    @Test
    fun `garbage stored default reminder reads as none`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.DEFAULT_REMINDER_KEY] = "soon"
            m
        }
        assertThat(prefs.defaultReminderMinutes.first()).isNull()
    }

    @Test
    fun `per-calendar override round-trips minutes, none, and inherit`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.perCalendarReminderOverride.first()).isEmpty()

        prefs.setCalendarReminderOverride(7L, CalendarReminderOverride.Minutes(15))
        prefs.setCalendarReminderOverride(9L, CalendarReminderOverride.None)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, 15, 9L, null)
        }

        // Inherit drops the override entirely (absent != null value).
        prefs.setCalendarReminderOverride(9L, CalendarReminderOverride.Inherit)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, 15)
            assertThat(map.containsKey(9L)).isFalse()
        }
    }

    @Test
    fun `all-day default round-trips, including none`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultAllDayReminderMinutes.first()).isNull()
        prefs.setDefaultAllDayReminderMinutes(1_440)
        assertThat(prefs.defaultAllDayReminderMinutes.first()).isEqualTo(1_440)
        prefs.setDefaultAllDayReminderMinutes(null)
        assertThat(prefs.defaultAllDayReminderMinutes.first()).isNull()
    }

    @Test
    fun `per-calendar all-day override round-trips independently of the timed one`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setCalendarReminderOverride(7L, CalendarReminderOverride.Minutes(15))
        prefs.setCalendarAllDayReminderOverride(7L, CalendarReminderOverride.Minutes(1_440))
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(7L, 15)
        assertThat(prefs.perCalendarAllDayReminderOverride.first()).containsExactly(7L, 1_440)
        // Clearing the all-day override leaves the timed one untouched.
        prefs.setCalendarAllDayReminderOverride(7L, CalendarReminderOverride.Inherit)
        assertThat(prefs.perCalendarAllDayReminderOverride.first()).isEmpty()
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(7L, 15)
    }

    @Test
    fun `resolveDefaultReminder picks the kind-matching override or global`() {
        val timed = mapOf(7L to 15, 9L to null)
        val allDay = mapOf(7L to 2_880)
        fun resolve(calendarId: Long?, isAllDay: Boolean) = resolveDefaultReminder(
            timedGlobal = 30,
            allDayGlobal = 1_440,
            timedOverrides = timed,
            allDayOverrides = allDay,
            calendarId = calendarId,
            isAllDay = isAllDay,
        )
        // Timed: minutes override, explicit none, inherit global, no calendar.
        assertThat(resolve(7L, isAllDay = false)).isEqualTo(15)
        assertThat(resolve(9L, isAllDay = false)).isNull()
        assertThat(resolve(5L, isAllDay = false)).isEqualTo(30)
        assertThat(resolve(null, isAllDay = false)).isEqualTo(30)
        // All-day: its own override wins; absent → all-day global; a timed-only
        // override (cal 9) does not bleed into all-day.
        assertThat(resolve(7L, isAllDay = true)).isEqualTo(2_880)
        assertThat(resolve(9L, isAllDay = true)).isEqualTo(1_440)
        assertThat(resolve(5L, isAllDay = true)).isEqualTo(1_440)
    }

    @Test
    fun `all-day reminder time defaults to 9am`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.allDayReminderTimeMinutes.first()).isEqualTo(540)
    }

    @Test
    fun `all-day reminder time round-trips and clamps to a valid minute-of-day`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setAllDayReminderTimeMinutes(8 * 60 + 30)
        assertThat(prefs.allDayReminderTimeMinutes.first()).isEqualTo(510)
        prefs.setAllDayReminderTimeMinutes(5_000)
        assertThat(prefs.allDayReminderTimeMinutes.first()).isEqualTo(1_439)
        prefs.setAllDayReminderTimeMinutes(-10)
        assertThat(prefs.allDayReminderTimeMinutes.first()).isEqualTo(0)
    }

    @Test
    fun `snooze duration defaults to 10 minutes`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.snoozeMinutes.first()).isEqualTo(10)
    }

    @Test
    fun `snooze duration round-trips and clamps to at least one minute`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setSnoozeMinutes(30)
        assertThat(prefs.snoozeMinutes.first()).isEqualTo(30)
        prefs.setSnoozeMinutes(0)
        assertThat(prefs.snoozeMinutes.first()).isEqualTo(1)
    }

    @Test
    fun `explicit week-start prefs resolve regardless of locale`() {
        assertThat(WeekStartPref.Day(DayOfWeek.MONDAY).resolveFirstDay(Locale.US))
            .isEqualTo(DayOfWeek.MONDAY)
        assertThat(WeekStartPref.Day(DayOfWeek.SATURDAY).resolveFirstDay(Locale.GERMANY))
            .isEqualTo(DayOfWeek.SATURDAY)
    }

    @Test
    fun `auto week start follows the locale convention`() {
        assertThat(WeekStartPref.Auto.resolveFirstDay(Locale.GERMANY)).isEqualTo(DayOfWeek.MONDAY)
        assertThat(WeekStartPref.Auto.resolveFirstDay(Locale.US)).isEqualTo(DayOfWeek.SUNDAY)
    }
}
