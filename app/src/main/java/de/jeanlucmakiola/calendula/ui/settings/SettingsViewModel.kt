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
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.theme.AppFontSettings
import de.jeanlucmakiola.calendula.ui.theme.FONT_CUSTOM_TOKEN
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_PAST_DISPLAY_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_RANGE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_SHOW_TODAY_STATE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AGENDA_SIZE_KEY
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.month.MONTH_SIZE_KEY
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
     * per-calendar reminder override. A calendar switched off in Settings →
     * Calendars is `VISIBLE = 0`, so the provider schedules no alarms for it and
     * a default reminder configured there could never fire (#75).
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
            ) { overrides, allDayOverrides, calendars, managedIds ->
                ReminderOverrides(overrides, allDayOverrides, calendars, managedIds)
            },
            combine(
                prefs.defaultView,
                prefs.agendaScreenRange,
                prefs.agendaWidgetRange,
                prefs.timeFormat,
                // Display toggles folded into one flow so they fit this group —
                // the outer combine is already at its five-arg limit.
                combine(
                    prefs.showHourLines,
                    prefs.showWeekNumbers,
                    prefs.agendaShowToday,
                    prefs.softenCalendarColors,
                    prefs.todayButtonInToolbar,
                ) { hourLines, weekNumbers, showToday, soften, todayInToolbar ->
                    DisplayToggles(hourLines, weekNumbers, showToday, soften, todayInToolbar)
                },
            ) { view, screenRange, widgetRange, timeFormat, toggles ->
                ViewSettings(
                    view, screenRange, widgetRange, timeFormat,
                    showHourLines = toggles.showHourLines,
                    showWeekNumbers = toggles.showWeekNumbers,
                    agendaShowToday = toggles.agendaShowToday,
                    softenColors = toggles.softenColors,
                    todayButtonInToolbar = toggles.todayButtonInToolbar,
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
                ) { quickSwitch, drawer, monthStyle, widgetSize ->
                    ViewCustomization(quickSwitch, drawer, monthStyle, widgetSize)
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
                showHourLines = views.showHourLines,
                showWeekNumbers = views.showWeekNumbers,
                agendaShowToday = views.agendaShowToday,
                softenColors = views.softenColors,
                todayButtonInToolbar = views.todayButtonInToolbar,
                agendaShowRangeBar = misc.showRangeBar,
                autofocusEventTitle = misc.autofocusEventTitle,
                pastEventDisplay = misc.pastEventDisplay,
                dimCompletedEvents = misc.dimCompletedEvents,
                quickSwitchConfig = misc.viewCustomization.quickSwitch,
                drawerViewOrder = misc.viewCustomization.drawerOrder,
                monthViewStyle = misc.viewCustomization.monthViewStyle,
                widgetSize = misc.viewCustomization.widgetSize,
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
     * The launcher-label choice (issue #44). Backed by the manifest aliases'
     * component-enabled state rather than a stored preference, so it's read
     * imperatively via [LauncherNameManager] and held here in its own flow — the
     * main settings combine is already at its arity limit and this isn't a
     * DataStore flow anyway.
     *
     * Seeded with the manifest default and corrected off-thread rather than read
     * in the initializer: `getComponentEnabledSetting` is a binder round-trip to
     * system_server, and this ViewModel is constructed on the main thread while
     * Settings is opening. The row it feeds is well below the fold, so the
     * one-frame correction is never visible.
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
        val showWeekNumbers: Boolean,
        val agendaShowToday: Boolean,
        val softenColors: Boolean,
        val todayButtonInToolbar: Boolean,
    )

    private data class DisplayToggles(
        val showHourLines: Boolean,
        val showWeekNumbers: Boolean,
        val agendaShowToday: Boolean,
        val softenColors: Boolean,
        val todayButtonInToolbar: Boolean,
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
            // Both widgets paint event colours through the same softener, so a
            // change has to redraw them too (they read the flag in their data
            // preamble, like is24Hour).
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
                // Bump the stamp so replacing an already-active custom font (same
                // file, same "custom" token) still breaks font-settings equality
                // and refreshes the resolved typeface.
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

    /**
     * Set the size step both widgets draw at (#103, #51). Pushed into every
     * instance's Glance state and recomposed — the same reliable-update path as
     * [setAgendaWidgetRange], since `updateAll` alone won't re-run the data
     * preamble a live session already ran.
     */
    fun setWidgetSize(size: WidgetSize) {
        viewModelScope.launch {
            prefs.setWidgetSize(size)
            widgetRefreshMutex.withLock {
                val manager = GlanceAppWidgetManager(appContext)
                manager.getGlanceIds(AgendaWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[AGENDA_SIZE_KEY] = size.name }
                }
                manager.getGlanceIds(MonthWidget::class.java).forEach { id ->
                    updateAppWidgetState(appContext, id) { it[MONTH_SIZE_KEY] = size.name }
                }
                AgendaWidget().updateAll(appContext)
                MonthWidget().updateAll(appContext)
            }
        }
    }

    fun setAgendaShowToday(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAgendaShowToday(enabled)
            // The agenda widget reads this reactively, so push it into each
            // instance's Glance state and recompose — same reliable-update path as
            // setPastEventDisplay (updateAll alone won't re-run the data preamble).
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

    fun setShowHourLines(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowHourLines(enabled) }
    }

    fun setShowWeekNumbers(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowWeekNumbers(enabled) }
    }

    fun setTodayButtonInToolbar(enabled: Boolean) {
        viewModelScope.launch { prefs.setTodayButtonInToolbar(enabled) }
    }

    /**
     * Switch the launcher label between "Calendula" and "Calendar" (issue #44).
     * The card highlights immediately, then settles on whatever the component
     * state actually reports — the two `setComponentEnabledSetting` calls are
     * binder round-trips, so they don't belong on the main thread either.
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

    /**
     * Enable or disable [view] in the quick-switch cycle. Goes through an atomic
     * read-modify-write so a concurrent reorder can't clobber this toggle (and
     * vice versa) by re-serialising a stale snapshot. The MIN_ENABLED floor is
     * re-checked inside the transform: the screen's own guard reads the
     * async-echoed snapshot, so two quick disable-taps could both look allowed
     * yet compose to a below-minimum cycle.
     */
    fun setQuickSwitchViewEnabled(view: CalendarView, enabled: Boolean) {
        viewModelScope.launch {
            prefs.updateQuickSwitch { config ->
                val next = if (enabled) config.enabled + view else config.enabled - view
                if (next.size < QuickSwitchConfig.MIN_ENABLED) config else config.copy(enabled = next)
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

    fun setDrawerViewOrder(order: List<CalendarView>) {
        viewModelScope.launch { prefs.setDrawerViewOrder(order) }
    }

    /**
     * A scan follows the write, in that order. Switching reminders off cancels
     * the scan alarm, so switching them back on has to arm a new one — nothing
     * else would until a provider change or the daily worker came along (#75).
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
