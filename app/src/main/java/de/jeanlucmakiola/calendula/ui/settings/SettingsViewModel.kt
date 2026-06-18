package de.jeanlucmakiola.calendula.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
    repository: CalendarRepository,
) : ViewModel() {

    private val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Writable calendars — the only ones that take a per-calendar reminder override. */
    private val writableCalendars: Flow<List<CalendarSource>> = repository.calendars()
        .map { calendars -> calendars.filter { it.canModifyContents } }
        .catch { emit(emptyList()) }

    val state: StateFlow<SettingsUiState> =
        combine(
            // combine() types up to five flows, so the prefs split into two
            // groups that fold together in the outer combine.
            combine(
                prefs.themeMode,
                prefs.dynamicColor,
                prefs.weekStart,
                prefs.defaultFormFields,
                prefs.remindersEnabled,
            ) { theme, dynamic, weekStart, formFields, reminders ->
                SettingsUiState(
                    themeMode = theme,
                    dynamicColor = dynamic && dynamicColorAvailable,
                    dynamicColorAvailable = dynamicColorAvailable,
                    weekStart = weekStart,
                    defaultFormFields = formFields,
                    remindersEnabled = reminders,
                )
            },
            combine(
                prefs.allowColorOnUnsupportedCalendars,
                prefs.defaultReminderMinutes,
                prefs.defaultAllDayReminderMinutes,
                prefs.allDayReminderTimeMinutes,
            ) { allowColor, defaultReminder, allDayReminder, allDayReminderTime ->
                ReminderDefaults(allowColor, defaultReminder, allDayReminder, allDayReminderTime)
            },
            combine(
                prefs.perCalendarReminderOverride,
                prefs.perCalendarAllDayReminderOverride,
                writableCalendars,
            ) { overrides, allDayOverrides, calendars ->
                ReminderOverrides(overrides, allDayOverrides, calendars)
            },
        ) { base, defaults, overrides ->
            base.copy(
                allowColorOnUnsupportedCalendars = defaults.allowColor,
                defaultReminderMinutes = defaults.defaultReminder,
                defaultAllDayReminderMinutes = defaults.allDayReminder,
                allDayReminderTimeMinutes = defaults.allDayReminderTime,
                perCalendarReminderOverride = overrides.timed,
                perCalendarAllDayReminderOverride = overrides.allDay,
                writableCalendars = overrides.calendars,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(dynamicColorAvailable = dynamicColorAvailable),
        )

    private data class ReminderDefaults(
        val allowColor: Boolean,
        val defaultReminder: Int?,
        val allDayReminder: Int?,
        val allDayReminderTime: Int,
    )

    private data class ReminderOverrides(
        val timed: Map<Long, Int?>,
        val allDay: Map<Long, Int?>,
        val calendars: List<CalendarSource>,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(enabled) }
    }

    fun setWeekStart(pref: WeekStartPref) {
        viewModelScope.launch { prefs.setWeekStart(pref) }
    }

    fun setFormFieldDefault(field: EventFormField, enabled: Boolean) {
        viewModelScope.launch { prefs.setFormFieldDefault(field, enabled) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setRemindersEnabled(enabled) }
    }

    fun setDefaultReminderMinutes(minutes: Int?) {
        viewModelScope.launch { prefs.setDefaultReminderMinutes(minutes) }
    }

    fun setDefaultAllDayReminderMinutes(minutes: Int?) {
        viewModelScope.launch { prefs.setDefaultAllDayReminderMinutes(minutes) }
    }

    fun setAllDayReminderTimeMinutes(minutesOfDay: Int) {
        viewModelScope.launch { prefs.setAllDayReminderTimeMinutes(minutesOfDay) }
    }

    fun setCalendarReminderOverride(calendarId: Long, override: CalendarReminderOverride) {
        viewModelScope.launch { prefs.setCalendarReminderOverride(calendarId, override) }
    }

    fun setCalendarAllDayReminderOverride(calendarId: Long, override: CalendarReminderOverride) {
        viewModelScope.launch { prefs.setCalendarAllDayReminderOverride(calendarId, override) }
    }

    fun setAllowColorOnUnsupportedCalendars(enabled: Boolean) {
        viewModelScope.launch { prefs.setAllowColorOnUnsupportedCalendars(enabled) }
    }
}
