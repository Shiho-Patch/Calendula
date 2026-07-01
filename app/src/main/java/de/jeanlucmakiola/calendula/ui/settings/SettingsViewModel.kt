package de.jeanlucmakiola.calendula.ui.settings

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesCalendarSpec
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesScheduler
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesSyncEngine
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.SpecialDatesStalledReason
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.storageValue
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_PAST_DISPLAY_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_RANGE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.month.MonthWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
    repository: CalendarRepository,
    private val specialDatesEngine: SpecialDatesSyncEngine,
    specialDatesSpec: SpecialDatesCalendarSpec,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationContext private val appContext: Context,
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
                prefs.snoozeMinutes,
            ) { allowColor, defaultReminder, allDayReminder, allDayReminderTime, snooze ->
                ReminderDefaults(
                    allowColor, defaultReminder, allDayReminder, allDayReminderTime, snooze,
                )
            },
            combine(
                prefs.perCalendarReminderOverride,
                prefs.perCalendarAllDayReminderOverride,
                writableCalendars,
                prefs.managedCalendarIds,
            ) { overrides, allDayOverrides, calendars, managedIds ->
                ReminderOverrides(overrides, allDayOverrides, calendars, managedIds)
            },
            combine(
                prefs.defaultView,
                prefs.agendaScreenRange,
                prefs.agendaWidgetRange,
                prefs.timeFormat,
                prefs.showHourLines,
            ) { view, screenRange, widgetRange, timeFormat, showHourLines ->
                ViewSettings(view, screenRange, widgetRange, timeFormat, showHourLines)
            },
            combine(
                prefs.agendaShowRangeBar,
                prefs.autofocusEventTitle,
                prefs.pastEventDisplay,
                prefs.dimCompletedEvents,
                ::MiscSettings,
            ),
        ) { base, defaults, overrides, views, misc ->
            base.copy(
                defaultView = views.defaultView,
                agendaScreenRange = views.agendaScreenRange,
                agendaWidgetRange = views.agendaWidgetRange,
                timeFormat = views.timeFormat,
                showHourLines = views.showHourLines,
                agendaShowRangeBar = misc.showRangeBar,
                autofocusEventTitle = misc.autofocusEventTitle,
                pastEventDisplay = misc.pastEventDisplay,
                dimCompletedEvents = misc.dimCompletedEvents,
                allowColorOnUnsupportedCalendars = defaults.allowColor,
                defaultReminderMinutes = defaults.defaultReminder,
                defaultAllDayReminderMinutes = defaults.allDayReminder,
                allDayReminderTimeMinutes = defaults.allDayReminderTime,
                snoozeMinutes = defaults.snoozeMinutes,
                perCalendarReminderOverride = overrides.timed,
                perCalendarAllDayReminderOverride = overrides.allDay,
                writableCalendars = overrides.calendars,
                managedCalendarIds = overrides.managedIds,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(dynamicColorAvailable = dynamicColorAvailable),
        )

    // Serialises widget redraws so a rapid flip-and-flip-back can't run two
    // updateAll calls at once — concurrent calls coalesce around a stale read
    // and can leave the widget on the intermediate value. Held sequentially,
    // the last call reads the final committed pref and renders it.
    private val widgetRefreshMutex = Mutex()

    private data class ReminderDefaults(
        val allowColor: Boolean,
        val defaultReminder: List<Int>,
        val allDayReminder: List<Int>,
        val allDayReminderTime: Int,
        val snoozeMinutes: Int,
    )

    private data class ReminderOverrides(
        val timed: Map<Long, List<Int>>,
        val allDay: Map<Long, List<Int>>,
        val calendars: List<CalendarSource>,
        val managedIds: Set<Long>,
    )

    private data class ViewSettings(
        val defaultView: CalendarView,
        val agendaScreenRange: AgendaRange,
        val agendaWidgetRange: AgendaRange,
        val timeFormat: TimeFormatPref,
        val showHourLines: Boolean,
    )

    private data class MiscSettings(
        val showRangeBar: Boolean,
        val autofocusEventTitle: Boolean,
        val pastEventDisplay: PastEventDisplay,
        val dimCompletedEvents: Boolean,
    )

    /** Contact special-dates sub-page (issue #15); its own flow — the main state is full. */
    val specialDatesState: StateFlow<SpecialDatesUiState> = combine(
        combine(
            prefs.specialDatesEnabled,
            prefs.specialDatesTypes,
            prefs.specialDatesTitleTemplates,
            prefs.specialDatesShowYear,
            prefs.specialDatesStatus,
        ) { enabled, types, templates, showYear, status ->
            SpecialDatesUiState(
                enabled = enabled,
                types = types,
                // A blank stored template resolves to the localized default so the
                // editor always shows something sensible.
                titleTemplates = SpecialDateType.entries.associateWith { type ->
                    templates[type]?.takeIf { it.isNotBlank() }
                        ?: specialDatesSpec.defaultTitleTemplate(type)
                },
                showYear = showYear,
                stalledPermission = status.stalled == SpecialDatesStalledReason.PermissionRevoked,
                lastRun = status.lastRun,
            )
        },
        prefs.specialDatesCalendars,
        prefs.perCalendarAllDayReminderOverride,
    ) { base, calendars, allDayOverrides ->
        base.copy(
            reminderChoices = calendars.mapValues { (_, calendarId) ->
                // An override is always seeded on creation; present-empty = None.
                when (val minutes = allDayOverrides[calendarId]) {
                    null -> CalendarReminderOverride.Inherit
                    emptyList<Int>() -> CalendarReminderOverride.None
                    else -> CalendarReminderOverride.Minutes(minutes)
                }
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SpecialDatesUiState(),
    )

    /**
     * Turn the mirror on or off. Enabling schedules the daily job and runs an
     * immediate sync (which creates the calendars); disabling cancels the job and
     * tears the managed calendars down. The caller must have READ_CONTACTS granted
     * before enabling.
     */
    fun setSpecialDatesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDatesEnabled(enabled)
            if (enabled) {
                SpecialDatesScheduler.apply(appContext, enabled = true)
                SpecialDatesScheduler.runNow(appContext)
            } else {
                SpecialDatesScheduler.apply(appContext, enabled = false)
                withContext(io) { specialDatesEngine.teardown() }
            }
        }
    }

    fun setSpecialDateTypeEnabled(type: SpecialDateType, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDateTypeEnabled(type, enabled)
            // Reconcile now: create the new type's calendar, or delete a disabled one.
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    fun setSpecialDatesTitleTemplate(type: SpecialDateType, template: String) {
        viewModelScope.launch {
            prefs.setSpecialDatesTitleTemplate(type, template.trim())
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    fun setSpecialDatesShowYear(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSpecialDatesShowYear(enabled)
            SpecialDatesScheduler.runNow(appContext)
        }
    }

    /**
     * Set a type's managed-calendar reminder default and apply it to all its
     * existing events (managed calendars own their reminders calendar-wide).
     */
    fun setSpecialDatesReminders(type: SpecialDateType, override: CalendarReminderOverride) {
        viewModelScope.launch { withContext(io) { specialDatesEngine.applyReminders(type, override) } }
    }

    /** Kick off an immediate reconcile (the "Sync now" action). */
    fun syncSpecialDatesNow() = SpecialDatesScheduler.runNow(appContext)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(enabled) }
    }

    fun setWeekStart(pref: WeekStartPref) {
        viewModelScope.launch {
            prefs.setWeekStart(pref)
            // Both widgets depend on the week start (month weekday header; the
            // agenda widget's "this week" range).
            widgetRefreshMutex.withLock {
                AgendaWidget().updateAll(appContext)
                MonthWidget().updateAll(appContext)
            }
        }
    }

    fun setAgendaScreenRange(range: AgendaRange) {
        viewModelScope.launch { prefs.setAgendaScreenRange(range) }
    }

    fun setAgendaWidgetRange(range: AgendaRange) {
        viewModelScope.launch {
            prefs.setAgendaWidgetRange(range)
            widgetRefreshMutex.withLock {
                // Push the range into each instance's Glance state and recompose.
                // The widget reads it via currentState, so this reflects reliably
                // even when updateAll only recomposes a live session (it does not
                // re-run provideGlance's data preamble).
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(AgendaWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[AGENDA_RANGE_KEY] = range.storageValue() }
                }
                AgendaWidget().updateAll(appContext)
            }
        }
    }

    fun setAgendaShowRangeBar(enabled: Boolean) {
        viewModelScope.launch { prefs.setAgendaShowRangeBar(enabled) }
    }

    fun setTimeFormat(pref: TimeFormatPref) {
        viewModelScope.launch { prefs.setTimeFormat(pref) }
    }

    fun setShowHourLines(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowHourLines(enabled) }
    }

    fun setPastEventDisplay(mode: PastEventDisplay) {
        viewModelScope.launch {
            prefs.setPastEventDisplay(mode)
            // The agenda widget honours this setting too, so push it into each
            // instance's Glance state and recompose — same reliable-update path as
            // setAgendaWidgetRange (updateAll alone won't re-run the data preamble).
            widgetRefreshMutex.withLock {
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(AgendaWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[AGENDA_PAST_DISPLAY_KEY] = mode.name }
                }
                AgendaWidget().updateAll(appContext)
            }
        }
    }

    fun setDimCompletedEvents(enabled: Boolean) {
        viewModelScope.launch { prefs.setDimCompletedEvents(enabled) }
    }

    fun setDefaultView(view: CalendarView) {
        viewModelScope.launch { prefs.setDefaultView(view) }
    }

    fun setFormFieldDefault(field: EventFormField, enabled: Boolean) {
        viewModelScope.launch { prefs.setFormFieldDefault(field, enabled) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setRemindersEnabled(enabled) }
    }

    fun setAutofocusEventTitle(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutofocusEventTitle(enabled) }
    }

    fun setDefaultReminderMinutes(minutes: List<Int>) {
        viewModelScope.launch { prefs.setDefaultReminderMinutes(minutes) }
    }

    fun setDefaultAllDayReminderMinutes(minutes: List<Int>) {
        viewModelScope.launch { prefs.setDefaultAllDayReminderMinutes(minutes) }
    }

    fun setAllDayReminderTimeMinutes(minutesOfDay: Int) {
        viewModelScope.launch { prefs.setAllDayReminderTimeMinutes(minutesOfDay) }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setSnoozeMinutes(minutes) }
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
