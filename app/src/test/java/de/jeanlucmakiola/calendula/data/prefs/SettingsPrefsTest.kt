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
    fun `autofocus event title defaults on and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.autofocusEventTitle.first()).isTrue()
        prefs.setAutofocusEventTitle(false)
        assertThat(prefs.autofocusEventTitle.first()).isFalse()
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
    fun `default reminder is empty until set`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultReminderMinutes.first()).isEmpty()
    }

    @Test
    fun `default reminder round-trips none, single, and multiple lead times`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setDefaultReminderMinutes(listOf(30))
        assertThat(prefs.defaultReminderMinutes.first()).containsExactly(30)
        // Several lead times persist, normalised to distinct ascending order.
        prefs.setDefaultReminderMinutes(listOf(10_080, 0, 30, 30))
        assertThat(prefs.defaultReminderMinutes.first()).containsExactly(0, 30, 10_080).inOrder()
        prefs.setDefaultReminderMinutes(emptyList())
        assertThat(prefs.defaultReminderMinutes.first()).isEmpty()
    }

    @Test
    fun `legacy single-value default reminder parses as a one-element list`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        // The pre-multi-reminder format stored a bare integer string.
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.DEFAULT_REMINDER_KEY] = "30"
            m
        }
        assertThat(prefs.defaultReminderMinutes.first()).containsExactly(30)
    }

    @Test
    fun `garbage stored default reminder reads as empty`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.DEFAULT_REMINDER_KEY] = "soon"
            m
        }
        assertThat(prefs.defaultReminderMinutes.first()).isEmpty()
    }

    @Test
    fun `per-calendar override round-trips minutes, none, and inherit`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.perCalendarReminderOverride.first()).isEmpty()

        prefs.setCalendarReminderOverride(7L, CalendarReminderOverride.Minutes(listOf(15, 10_080)))
        prefs.setCalendarReminderOverride(9L, CalendarReminderOverride.None)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, listOf(15, 10_080), 9L, emptyList<Int>())
        }

        // Inherit drops the override entirely (absent != an empty-list value).
        prefs.setCalendarReminderOverride(9L, CalendarReminderOverride.Inherit)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, listOf(15, 10_080))
            assertThat(map.containsKey(9L)).isFalse()
        }
    }

    @Test
    fun `all-day default round-trips, including none`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultAllDayReminderMinutes.first()).isEmpty()
        prefs.setDefaultAllDayReminderMinutes(listOf(1_440))
        assertThat(prefs.defaultAllDayReminderMinutes.first()).containsExactly(1_440)
        prefs.setDefaultAllDayReminderMinutes(emptyList())
        assertThat(prefs.defaultAllDayReminderMinutes.first()).isEmpty()
    }

    @Test
    fun `per-calendar all-day override round-trips independently of the timed one`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setCalendarReminderOverride(7L, CalendarReminderOverride.Minutes(listOf(15)))
        prefs.setCalendarAllDayReminderOverride(7L, CalendarReminderOverride.Minutes(listOf(1_440)))
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(7L, listOf(15))
        assertThat(prefs.perCalendarAllDayReminderOverride.first()).containsExactly(7L, listOf(1_440))
        // Clearing the all-day override leaves the timed one untouched.
        prefs.setCalendarAllDayReminderOverride(7L, CalendarReminderOverride.Inherit)
        assertThat(prefs.perCalendarAllDayReminderOverride.first()).isEmpty()
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(7L, listOf(15))
    }

    @Test
    fun `resolveDefaultReminder picks the kind-matching override or global`() {
        val timed = mapOf(7L to listOf(15, 10_080), 9L to emptyList<Int>())
        val allDay = mapOf(7L to listOf(2_880))
        fun resolve(calendarId: Long?, isAllDay: Boolean) = resolveDefaultReminder(
            timedGlobal = listOf(30),
            allDayGlobal = listOf(1_440),
            timedOverrides = timed,
            allDayOverrides = allDay,
            calendarId = calendarId,
            isAllDay = isAllDay,
        )
        // Timed: multi-value override, explicit none, inherit global, no calendar.
        assertThat(resolve(7L, isAllDay = false)).containsExactly(15, 10_080).inOrder()
        assertThat(resolve(9L, isAllDay = false)).isEmpty()
        assertThat(resolve(5L, isAllDay = false)).containsExactly(30)
        assertThat(resolve(null, isAllDay = false)).containsExactly(30)
        // All-day: its own override wins; absent → all-day global; a timed-only
        // override (cal 9) does not bleed into all-day.
        assertThat(resolve(7L, isAllDay = true)).containsExactly(2_880)
        assertThat(resolve(9L, isAllDay = true)).containsExactly(1_440)
        assertThat(resolve(5L, isAllDay = true)).containsExactly(1_440)
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
