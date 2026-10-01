package de.jeanlucmakiola.calendula.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.appname.LauncherName
import de.jeanlucmakiola.calendula.data.appname.LauncherNameManager
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesCalendarSpec
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesScheduler
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesSyncEngine
import de.jeanlucmakiola.calendula.data.contacts.resolveTitleTemplate
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.fonts.CustomFontStore
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.reminderOverrideFor
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.SpecialDatesStalledReason
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.reminders.ReminderScanner
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.storageValue
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.TimelineHours
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.theme.AppFontSettings
import de.jeanlucmakiola.calendula.ui.theme.FONT_CUSTOM_TOKEN
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_PAST_DISPLAY_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_RANGE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_SHOW_TODAY_STATE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_SIZE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.month.MonthWidget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
    repository: CalendarRepository,
    private val specialDatesEngine: SpecialDatesSyncEngine,
    specialDatesSpec: SpecialDatesCalendarSpec,
    private val launcherNameManager: LauncherNameManager,
    private val reminderScanner: ReminderScanner,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Writable calendars that are switched on — the only ones that take a
     * per-calendar reminder override, since a switched-off one plans no
     * reminders at all (#75).
     */
    private val writableCalendars: Flow<List<CalendarSource>> = repository.calendars()
        .map { calendars -> calendars.filter { it.canModifyContents && it.isVisibleInSystem } }
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
                // The new-event duration defaults (#54) fold into one flow so they
                // fit this group — the outer combine is at its five-arg limit.
                combine(
                    prefs.defaultEventDurationMinutes,
                    prefs.perCalendarEventDuration,
                ) { duration, perCalendar -> EventDurations(duration, perCalendar) },
            ) { overrides, allDayOverrides, calendars, managedIds, durations ->
                ReminderOverrides(overrides, allDayOverrides, calendars, managedIds, durations)
            },
            combine(
                prefs.defaultView,
                prefs.agendaScreenRange,
                prefs.agendaWidgetRange,
                prefs.timeFormat,
                // Display toggles folded into one flow so they fit this group —
                // the outer combine is already at its five-arg limit.
                combine(
                    prefs.showHourGrid,
                    prefs.showWeekNumbers,
                    prefs.agendaShowToday,
                    prefs.softenCalendarColors,
                    combine(
                        prefs.todayButtonInToolbar,
                        prefs.dragToReschedule,
                        prefs.multiDayCount,
                        ::Triple,
                    ),
                ) { hourLines, weekNumbers, showToday, soften, (todayInToolbar, dragToMove, multiDay) ->
                    DisplayToggles(
                        hourLines, weekNumbers, showToday, soften, todayInToolbar, dragToMove, multiDay,
                    )
                },
            ) { view, screenRange, widgetRange, timeFormat, toggles ->
                ViewSettings(
                    view, screenRange, widgetRange, timeFormat,
                    showHourGrid = toggles.showHourGrid,
                    showWeekNumbers = toggles.showWeekNumbers,
                    agendaShowToday = toggles.agendaShowToday,
                    softenColors = toggles.softenColors,
                    todayButtonInToolbar = toggles.todayButtonInToolbar,
                    dragToReschedule = toggles.dragToReschedule,
                    multiDayCount = toggles.multiDayCount,
                )
            },
            combine(
                prefs.agendaShowRangeBar,
                prefs.autofocusEventTitle,
                prefs.pastEventDisplay,
                prefs.dimCompletedEvents,
                // View customisation (#24) folded into one flow so it fits this
                // group — the outer combine is already at its five-arg limit.
                combine(
                    prefs.quickSwitchConfig,
                    prefs.drawerViewOrder,
                    prefs.monthViewStyle,
                    prefs.widgetSize,
                    combine(prefs.timelineScale, prefs.timelineHours, ::Pair),
                ) { quickSwitch, drawer, monthStyle, widgetSize, (timelineScale, timelineHours) ->
                    ViewCustomization(
                        quickSwitch, drawer, monthStyle, widgetSize, timelineScale, timelineHours,
                    )
                },
            ) { showRangeBar, autofocus, pastEvents, dimCompleted, viewCustomization ->
                MiscSettings(showRangeBar, autofocus, pastEvents, dimCompleted, viewCustomization)
            },
        ) { base, defaults, overrides, views, misc ->
            base.copy(
                defaultView = views.defaultView,
                agendaScreenRange = views.agendaScreenRange,
                agendaWidgetRange = views.agendaWidgetRange,
                timeFormat = views.timeFormat,
                showHourGrid = views.showHourGrid,
                showWeekNumbers = views.showWeekNumbers,
                agendaShowToday = views.agendaShowToday,
                softenColors = views.softenColors,
                todayButtonInToolbar = views.todayButtonInToolbar,
                dragToReschedule = views.dragToReschedule,
                multiDayCount = views.multiDayCount,
                agendaShowRangeBar = misc.showRangeBar,
                autofocusEventTitle = misc.autofocusEventTitle,
                pastEventDisplay = misc.pastEventDisplay,
                dimCompletedEvents = misc.dimCompletedEvents,
                quickSwitchConfig = misc.viewCustomization.quickSwitch,
                drawerViewOrder = misc.viewCustomization.drawerOrder,
                monthViewStyle = misc.viewCustomization.monthViewStyle,
                widgetSize = misc.viewCustomization.widgetSize,
                timelineScale = misc.viewCustomization.timelineScale,
                timelineHours = misc.viewCustomization.timelineHours,
                allowColorOnUnsupportedCalendars = defaults.allowColor,
                defaultReminderMinutes = defaults.defaultReminder,
                defaultAllDayReminderMinutes = defaults.allDayReminder,
                allDayReminderTimeMinutes = defaults.allDayReminderTime,
                snoozeMinutes = defaults.snoozeMinutes,
                perCalendarReminderOverride = overrides.timed,
                perCalendarAllDayReminderOverride = overrides.allDay,
                writableCalendars = overrides.calendars,
                managedCalendarIds = overrides.managedIds,
                defaultEventDurationMinutes = overrides.durations.default,
                perCalendarEventDuration = overrides.durations.perCalendar,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(dynamicColorAvailable = dynamicColorAvailable),
        )

    /**
     * The app-wide custom-font choice (issue #19). Its own flow — both this
     * screen and [MainActivity] (which builds the typography) read it
     * independently of the main settings combine.
     */
    val fontState: StateFlow<AppFontSettings> = combine(
        prefs.brandFont,
        prefs.plainFont,
        prefs.brandFontStamp,
        prefs.plainFontStamp,
    ) { brand, plain, brandStamp, plainStamp ->
        AppFontSettings(brand = brand, plain = plain, brandStamp = brandStamp, plainStamp = plainStamp)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AppFontSettings(),
    )

    /**
     * The launcher-label choice (#44), backed by the manifest aliases'
     * component-enabled state rather than a preference. Seeded with the manifest
     * default and corrected off-thread — `getComponentEnabledSetting` is a
     * binder round-trip and this ViewModel is built on the main thread.
     */
    private val _launcherName = MutableStateFlow(LauncherName.CALENDULA)
    val launcherName: StateFlow<LauncherName> = _launcherName.asStateFlow()

    init {
        viewModelScope.launch {
            _launcherName.value = withContext(io) { launcherNameManager.current() }
        }
    }

    // Emitted when a picked font file couldn't be read as a font; the screen
    // surfaces it and the previous selection stays put.
    private val _fontImportFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val fontImportFailed: SharedFlow<Unit> = _fontImportFailed

    // Serialises widget redraws: concurrent updateAll calls coalesce around a
    // stale read and can leave the widget on an intermediate value.
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
        val durations: EventDurations,
    )

    private data class EventDurations(
        val default: Int,
        val perCalendar: Map<Long, Int>,
    )

    private data class ViewSettings(
        val defaultView: CalendarView,
        val agendaScreenRange: AgendaRange,
        val agendaWidgetRange: AgendaRange,
        val timeFormat: TimeFormatPref,
        val showHourGrid: Boolean,
        val showWeekNumbers: Boolean,
        val agendaShowToday: Boolean,
        val softenColors: Boolean,
        val todayButtonInToolbar: Boolean,
        val dragToReschedule: Boolean,
        val multiDayCount: Int,
    )

    private data class DisplayToggles(
        val showHourGrid: Boolean,
        val showWeekNumbers: Boolean,
        val agendaShowToday: Boolean,
        val softenColors: Boolean,
        val todayButtonInToolbar: Boolean,
        val dragToReschedule: Boolean,
        val multiDayCount: Int,
    )

    private data class MiscSettings(
        val showRangeBar: Boolean,
        val autofocusEventTitle: Boolean,
        val pastEventDisplay: PastEventDisplay,
        val dimCompletedEvents: Boolean,
        val viewCustomization: ViewCustomization,
    )

    private data class ViewCustomization(
        val quickSwitch: QuickSwitchConfig,
        val drawerOrder: List<CalendarView>,
        val monthViewStyle: MonthViewStyle,
        val widgetSize: WidgetSize,
        val timelineScale: TimelineScale,
        val timelineHours: TimelineHours,
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
                    specialDatesSpec.resolveTitleTemplate(type, templates)
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
            // An override is always seeded on creation; present-empty = None.
            reminderChoices = calendars.mapValues { (_, calendarId) ->
                allDayOverrides.reminderOverrideFor(calendarId)
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
    fun setSpecialDatesReminders(type: SpecialDateType, override: ReminderOverride) {
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

    fun setSoftenColors(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setSoftenCalendarColors(enabled)
            // Both widgets paint event colours through the same softener.
            widgetRefreshMutex.withLock {
                AgendaWidget().updateAll(appContext)
                MonthWidget().updateAll(appContext)
            }
        }
    }

    fun setFont(role: FontRole, token: String) {
        viewModelScope.launch {
            when (role) {
                FontRole.BRAND -> prefs.setBrandFont(token)
                FontRole.PLAIN -> prefs.setPlainFont(token)
            }
        }
    }

    /**
     * Import a user-picked font file for [role]. Copies and validates it off the
     * main thread; on success the role switches to the custom font, otherwise the
     * previous choice is kept and [fontImportFailed] fires.
     */
    fun importCustomFont(role: FontRole, uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(io) { CustomFontStore.import(appContext, role, uri) }
            if (ok) {
                // Bump the stamp so replacing an already-active custom font
                // still breaks equality and refreshes the resolved typeface.
                prefs.bumpCustomFontStamp(role)
                setFont(role, FONT_CUSTOM_TOKEN)
            } else {
                _fontImportFailed.emit(Unit)
            }
        }
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
                // Push into each instance's Glance state and recompose:
                // updateAll alone won't re-run provideGlance's data preamble.
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

    /**
     * Set the size the agenda widget draws its text at (#51), via the same
     * Glance-state path as [setAgendaWidgetRange]. Agenda-only: the month widget
     * sizes itself from the space it is given (#103).
     */
    fun setWidgetSize(size: WidgetSize) {
        viewModelScope.launch {
            prefs.setWidgetSize(size)
            widgetRefreshMutex.withLock {
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(AgendaWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[AGENDA_SIZE_KEY] = size.name }
                }
                AgendaWidget().updateAll(appContext)
            }
        }
    }

    fun setAgendaShowToday(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAgendaShowToday(enabled)
            // Same Glance-state path as setPastEventDisplay.
            widgetRefreshMutex.withLock {
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(AgendaWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[AGENDA_SHOW_TODAY_STATE_KEY] = enabled }
                }
                AgendaWidget().updateAll(appContext)
            }
        }
    }

    fun setTimeFormat(pref: TimeFormatPref) {
        viewModelScope.launch { prefs.setTimeFormat(pref) }
    }

    fun setMultiDayCount(count: Int) {
        viewModelScope.launch { prefs.setMultiDayCount(count) }
    }

    fun setShowHourGrid(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowHourGrid(enabled) }
    }

    fun setShowWeekNumbers(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowWeekNumbers(enabled) }
    }

    fun setTodayButtonInToolbar(enabled: Boolean) {
        viewModelScope.launch { prefs.setTodayButtonInToolbar(enabled) }
    }

    fun setDragToReschedule(enabled: Boolean) {
        viewModelScope.launch { prefs.setDragToReschedule(enabled) }
    }

    /**
     * Switch the launcher label (#44). The card highlights at once, then settles
     * on what the component state reports; the writes are binder round-trips and
     * run off the main thread.
     */
    fun setLauncherName(name: LauncherName) {
        _launcherName.value = name
        viewModelScope.launch {
            _launcherName.value = withContext(io) {
                launcherNameManager.set(name)
                launcherNameManager.current()
            }
        }
    }

    fun setPastEventDisplay(mode: PastEventDisplay) {
        viewModelScope.launch {
            prefs.setPastEventDisplay(mode)
            // Same Glance-state path as setAgendaWidgetRange.
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

    /**
     * Enable or disable [view] in the quick-switch cycle, via an atomic
     * read-modify-write so a concurrent reorder can't clobber it. Any number of
     * views may be turned off; below two the pill hides itself (#150).
     */
    fun setQuickSwitchViewEnabled(view: CalendarView, enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateQuickSwitch { config ->
                config.copy(
                    enabled = if (enabled) config.enabled + view else config.enabled - view,
                )
            }
        }
    }

    /** Reorder the quick-switch views (atomic read-modify-write; keeps the enabled set). */
    fun setQuickSwitchOrder(order: List<CalendarView>) {
        viewModelScope.launch { prefs.updateQuickSwitch { it.copy(order = order) } }
    }

    fun setMonthViewStyle(style: MonthViewStyle) {
        viewModelScope.launch { prefs.setMonthViewStyle(style) }
    }

    fun setTimelineScale(scale: TimelineScale) {
        viewModelScope.launch { prefs.setTimelineScale(scale) }
    }

    fun setTimelineHours(hours: TimelineHours) {
        viewModelScope.launch { prefs.setTimelineHours(hours) }
    }

    fun setDrawerViewOrder(order: List<CalendarView>) {
        viewModelScope.launch { prefs.setDrawerViewOrder(order) }
    }

    /**
     * A scan follows the write: switching reminders off cancels the scan alarm,
     * so switching them back on has to arm a new one (#75).
     */
    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setRemindersEnabled(enabled)
            reminderScanner.scan()
        }
    }

    fun setAutofocusEventTitle(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutofocusEventTitle(enabled) }
    }

    fun setDefaultEventDuration(minutes: Int) {
        viewModelScope.launch { prefs.setDefaultEventDurationMinutes(minutes) }
    }

    /** [minutes] null clears the override, so the calendar inherits the default. */
    fun setCalendarEventDuration(calendarId: Long, minutes: Int?) {
        viewModelScope.launch { prefs.setCalendarEventDuration(calendarId, minutes) }
    }

    fun setDefaultReminderMinutes(minutes: List<Int>) {
        viewModelScope.launch { prefs.setDefaultReminderMinutes(minutes) }
    }

    fun setDefaultAllDayReminderMinutes(minutes: List<Int>) {
        viewModelScope.launch { prefs.setDefaultAllDayReminderMinutes(minutes) }
    }

    /** The armed alarm was planned for the old hour, so re-plan against the new one. */
    fun setAllDayReminderTimeMinutes(minutesOfDay: Int) {
        viewModelScope.launch {
            prefs.setAllDayReminderTimeMinutes(minutesOfDay)
            reminderScanner.scan()
        }
    }

    fun setSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { prefs.setSnoozeMinutes(minutes) }
    }

    fun setCalendarReminderOverride(calendarId: Long, override: ReminderOverride) {
        viewModelScope.launch { prefs.setCalendarReminderOverride(calendarId, override) }
    }

    fun setCalendarAllDayReminderOverride(calendarId: Long, override: ReminderOverride) {
        viewModelScope.launch { prefs.setCalendarAllDayReminderOverride(calendarId, override) }
    }

    fun setAllowColorOnUnsupportedCalendars(enabled: Boolean) {
        viewModelScope.launch { prefs.setAllowColorOnUnsupportedCalendars(enabled) }
    }
}
