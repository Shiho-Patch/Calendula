package de.jeanlucmakiola.calendula.data.prefs

import de.jeanlucmakiola.floret.reminders.ReminderOverride

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
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
    fun `timeline scale defaults to regular and round-trips`(@TempDir tempDir: Path) = runTest {
        // Regular is the historical 56dp scale — an existing install that never
        // opened the setting must keep the timeline it had (#56).
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.timelineScale.first()).isEqualTo(TimelineScale.Regular)
        prefs.setTimelineScale(TimelineScale.FitDay)
        assertThat(prefs.timelineScale.first()).isEqualTo(TimelineScale.FitDay)
    }

    @Test
    fun `a pinched timeline height round-trips`(@TempDir tempDir: Path) = runTest {
        // The pinch settles on a height between the presets, so the preference
        // has to store the number, not just a named step (#56).
        val prefs = SettingsPrefs(newDataStore(tempDir))
        val pinched = TimelineScale.custom(63.5f.dp)
        prefs.setTimelineScale(pinched)
        assertThat(prefs.timelineScale.first()).isEqualTo(pinched)

        // …and a preset picked afterwards replaces it, rather than the two
        // coexisting with one silently winning.
        prefs.setTimelineScale(TimelineScale.Comfortable)
        assertThat(prefs.timelineScale.first()).isEqualTo(TimelineScale.Comfortable)
    }

    @Test
    fun `week numbers default off and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.showWeekNumbers.first()).isFalse()
        prefs.setShowWeekNumbers(true)
        assertThat(prefs.showWeekNumbers.first()).isTrue()
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
    fun `month view style defaults to paged and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.monthViewStyle.first()).isEqualTo(MonthViewStyle.Paged)

        prefs.setMonthViewStyle(MonthViewStyle.Split)
        assertThat(prefs.monthViewStyle.first()).isEqualTo(MonthViewStyle.Split)

        prefs.setMonthViewStyle(MonthViewStyle.Continuous)
        assertThat(prefs.monthViewStyle.first()).isEqualTo(MonthViewStyle.Continuous)
    }

    @Test
    fun `garbage stored month view style falls back to paged`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.MONTH_VIEW_STYLE_KEY] = "Carousel"
            m
        }
        assertThat(prefs.monthViewStyle.first()).isEqualTo(MonthViewStyle.Paged)
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
    fun `quick-switch defaults to every view enabled in default order`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        val config = prefs.quickSwitchConfig.first()
        assertThat(config.order).containsExactlyElementsIn(IMPLEMENTED_VIEWS).inOrder()
        assertThat(config.enabled).containsExactlyElementsIn(IMPLEMENTED_VIEWS)
        assertThat(config.cycle).containsExactlyElementsIn(IMPLEMENTED_VIEWS).inOrder()
    }

    @Test
    fun `quick-switch config round-trips order and disabled views`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        val config = QuickSwitchConfig(
            order = listOf(CalendarView.Agenda, CalendarView.Month, CalendarView.Week, CalendarView.Day),
            enabled = setOf(CalendarView.Agenda, CalendarView.Month),
        )
        prefs.setQuickSwitchConfig(config)
        val loaded = prefs.quickSwitchConfig.first()
        assertThat(loaded.order).containsExactly(
            CalendarView.Agenda, CalendarView.Month, CalendarView.Week, CalendarView.Day,
        ).inOrder()
        assertThat(loaded.enabled).containsExactly(CalendarView.Agenda, CalendarView.Month)
        assertThat(loaded.cycle).containsExactly(CalendarView.Agenda, CalendarView.Month).inOrder()
    }

    @Test
    fun `quick-switch parse appends missing views enabled and drops unknowns`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        // Only Day (disabled) and Week stored, plus a bogus name; Month & Agenda absent.
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.QUICK_SWITCH_VIEWS_KEY] = "!Day,Week,Hologram"
            m
        }
        val config = prefs.quickSwitchConfig.first()
        // Stored order first (Day, Week), then the omitted views in default order.
        assertThat(config.order).containsExactly(
            CalendarView.Day, CalendarView.Week, CalendarView.Month, CalendarView.Agenda,
        ).inOrder()
        // Day was explicitly disabled; the appended Month & Agenda default enabled.
        assertThat(config.enabled).containsExactly(
            CalendarView.Week, CalendarView.Month, CalendarView.Agenda,
        )
    }

    @Test
    fun `updateQuickSwitch transforms the stored value, so intents compose`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        // A toggle then a reorder, each expressed against the *stored* config —
        // the reorder must not resurrect the just-disabled view (the stale-UI-
        // snapshot race this API exists to prevent).
        prefs.updateQuickSwitch { it.copy(enabled = it.enabled - CalendarView.Day) }
        val newOrder = listOf(CalendarView.Agenda, CalendarView.Month, CalendarView.Week, CalendarView.Day)
        prefs.updateQuickSwitch { it.copy(order = newOrder) }
        val config = prefs.quickSwitchConfig.first()
        assertThat(config.order).containsExactlyElementsIn(newOrder).inOrder()
        assertThat(config.enabled).containsExactly(
            CalendarView.Agenda, CalendarView.Month, CalendarView.Week,
        )
    }

    @Test
    fun `drawer order defaults to the implemented order and round-trips`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.drawerViewOrder.first()).containsExactlyElementsIn(IMPLEMENTED_VIEWS).inOrder()
        prefs.setDrawerViewOrder(
            listOf(CalendarView.Agenda, CalendarView.Day, CalendarView.Week, CalendarView.Month),
        )
        assertThat(prefs.drawerViewOrder.first()).containsExactly(
            CalendarView.Agenda, CalendarView.Day, CalendarView.Week, CalendarView.Month,
        ).inOrder()
    }

    @Test
    fun `drawer order parse appends missing views and drops unknowns`(@TempDir tempDir: Path) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.DRAWER_VIEW_ORDER_KEY] = "Agenda,Nope,Week"
            m
        }
        assertThat(prefs.drawerViewOrder.first()).containsExactly(
            CalendarView.Agenda, CalendarView.Week, CalendarView.Month, CalendarView.Day,
        ).inOrder()
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
    fun `the wizard arms on a first run`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.armOnboardingWizard()
        assertThat(prefs.onboardingWizardArmed.first()).isTrue()
    }

    @Test
    fun `the wizard does not arm once the reminder step is answered`(@TempDir tempDir: Path) = runTest {
        // An existing install re-granting the permission is not re-onboarded.
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setReminderOnboardingDone()
        prefs.armOnboardingWizard()
        assertThat(prefs.onboardingWizardArmed.first()).isFalse()
    }

    @Test
    fun `closing the wizard disarms it`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.armOnboardingWizard()

        prefs.finishOnboardingWizard()
        assertThat(prefs.onboardingDoneShown.first()).isTrue()
        assertThat(prefs.onboardingWizardArmed.first()).isFalse()
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

        prefs.setCalendarReminderOverride(7L, ReminderOverride.Minutes(listOf(15, 10_080)))
        prefs.setCalendarReminderOverride(9L, ReminderOverride.None)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, listOf(15, 10_080), 9L, emptyList<Int>())
        }

        // Inherit drops the override entirely (absent != an empty-list value).
        prefs.setCalendarReminderOverride(9L, ReminderOverride.Inherit)
        prefs.perCalendarReminderOverride.first().let { map ->
            assertThat(map).containsExactly(7L, listOf(15, 10_080))
            assertThat(map.containsKey(9L)).isFalse()
        }
    }

    @Test
    fun `garbage per-calendar override values drop the entry, the none sentinel stays explicit`(
        @TempDir tempDir: Path,
    ) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        // Only "none" deliberately means an explicit no-reminder override; a
        // value with no valid minutes is garbage and must drop its entry so the
        // calendar inherits the global default instead of silently losing
        // reminders. Partially-valid values keep their salvageable minutes.
        store.updateData { p ->
            val m = p.toMutablePreferences()
            m[SettingsPrefs.CALENDAR_REMINDER_OVERRIDE_KEY] = "5=none;7=abc;8=;9=30,abc;11=15"
            m
        }
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(
            5L, emptyList<Int>(), 9L, listOf(30), 11L, listOf(15),
        )
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
        prefs.setCalendarReminderOverride(7L, ReminderOverride.Minutes(listOf(15)))
        prefs.setCalendarAllDayReminderOverride(7L, ReminderOverride.Minutes(listOf(1_440)))
        assertThat(prefs.perCalendarReminderOverride.first()).containsExactly(7L, listOf(15))
        assertThat(prefs.perCalendarAllDayReminderOverride.first()).containsExactly(7L, listOf(1_440))
        // Clearing the all-day override leaves the timed one untouched.
        prefs.setCalendarAllDayReminderOverride(7L, ReminderOverride.Inherit)
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
    fun `event duration defaults to an hour and clamps to a sane span`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.defaultEventDurationMinutes.first()).isEqualTo(60)
        prefs.setDefaultEventDurationMinutes(480)
        assertThat(prefs.defaultEventDurationMinutes.first()).isEqualTo(480)
        prefs.setDefaultEventDurationMinutes(0)
        assertThat(prefs.defaultEventDurationMinutes.first()).isEqualTo(1)
        prefs.setDefaultEventDurationMinutes(5_000)
        assertThat(prefs.defaultEventDurationMinutes.first()).isEqualTo(1_440)
    }

    @Test
    fun `per-calendar duration round-trips and clears back to inherit`(
        @TempDir tempDir: Path,
    ) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.perCalendarEventDuration.first()).isEmpty()
        prefs.setCalendarEventDuration(7L, 480)
        prefs.setCalendarEventDuration(9L, 30)
        assertThat(prefs.perCalendarEventDuration.first()).containsExactly(7L, 480, 9L, 30)
        prefs.setCalendarEventDuration(7L, null)
        assertThat(prefs.perCalendarEventDuration.first()).containsExactly(9L, 30)
    }

    @Test
    fun `garbage per-calendar duration entries are dropped, the rest survive`(
        @TempDir tempDir: Path,
    ) = runTest {
        val store = newDataStore(tempDir)
        val prefs = SettingsPrefs(store)
        store.updateData { p ->
            val m = p.toMutablePreferences()
            // A bad id, a non-numeric length, one out of range, and a good entry.
            m[SettingsPrefs.CALENDAR_EVENT_DURATION_KEY] = "x=60;7=soon;8=99999;9=45"
            m
        }
        assertThat(prefs.perCalendarEventDuration.first()).containsExactly(9L, 45)
    }

    @Test
    fun `resolveDefaultEventDuration prefers the calendar's own length`() {
        val overrides = mapOf(7L to 480)
        assertThat(resolveDefaultEventDuration(60, overrides, calendarId = 7L)).isEqualTo(480)
        assertThat(resolveDefaultEventDuration(60, overrides, calendarId = 9L)).isEqualTo(60)
        assertThat(resolveDefaultEventDuration(60, overrides, calendarId = null)).isEqualTo(60)
    }

    @Test
    fun `custom-font stamps default to zero and bump per role independently`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        // Existing installs have no stamp key — that must read as 0.
        assertThat(prefs.brandFontStamp.first()).isEqualTo(0)
        assertThat(prefs.plainFontStamp.first()).isEqualTo(0)
        prefs.bumpCustomFontStamp(FontRole.BRAND)
        prefs.bumpCustomFontStamp(FontRole.BRAND)
        prefs.bumpCustomFontStamp(FontRole.PLAIN)
        assertThat(prefs.brandFontStamp.first()).isEqualTo(2)
        assertThat(prefs.plainFontStamp.first()).isEqualTo(1)
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

    @Test
    fun `special dates feature is off with all types on by default`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.specialDatesEnabled.first()).isFalse()
        assertThat(prefs.specialDatesTypes.first()).isEqualTo(SpecialDateType.entries.toSet())
        assertThat(prefs.specialDatesCalendars.first()).isEmpty()
        assertThat(prefs.managedCalendarIds.first()).isEmpty()
        assertThat(prefs.specialDatesShowYear.first()).isTrue()
    }

    @Test
    fun `special date type toggles round-trip`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setSpecialDateTypeEnabled(SpecialDateType.Anniversary, false)
        assertThat(prefs.specialDatesTypes.first())
            .isEqualTo(setOf(SpecialDateType.Birthday, SpecialDateType.Custom))
    }

    @Test
    fun `managed calendar ids round-trip and drive managedCalendarIds`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        prefs.setSpecialDatesCalendarId(SpecialDateType.Birthday, 7L)
        prefs.setSpecialDatesCalendarId(SpecialDateType.Anniversary, 9L)
        assertThat(prefs.specialDatesCalendars.first())
            .isEqualTo(mapOf(SpecialDateType.Birthday to 7L, SpecialDateType.Anniversary to 9L))
        assertThat(prefs.managedCalendarIds.first()).isEqualTo(setOf(7L, 9L))

        prefs.setSpecialDatesCalendarId(SpecialDateType.Birthday, null)
        assertThat(prefs.specialDatesCalendars.first())
            .isEqualTo(mapOf(SpecialDateType.Anniversary to 9L))
    }

    @Test
    fun `title template round-trips per type`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.specialDatesTitleTemplates.first()[SpecialDateType.Birthday]).isEmpty()
        prefs.setSpecialDatesTitleTemplate(SpecialDateType.Birthday, "{name}'s birthday")
        assertThat(prefs.specialDatesTitleTemplates.first()[SpecialDateType.Birthday])
            .isEqualTo("{name}'s birthday")
    }

    @Test
    fun `sync status records run and stalled reason`(@TempDir tempDir: Path) = runTest {
        val prefs = SettingsPrefs(newDataStore(tempDir))
        assertThat(prefs.specialDatesStatus.first())
            .isEqualTo(SpecialDatesStatus(lastRun = 0L, stalled = null))

        prefs.recordSpecialDatesRun(1000L, SpecialDatesStalledReason.PermissionRevoked)
        assertThat(prefs.specialDatesStatus.first())
            .isEqualTo(SpecialDatesStatus(1000L, SpecialDatesStalledReason.PermissionRevoked))

        prefs.recordSpecialDatesRun(2000L, null)
        assertThat(prefs.specialDatesStatus.first()).isEqualTo(SpecialDatesStatus(2000L, null))
    }
}
