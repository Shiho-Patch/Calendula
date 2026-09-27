package de.jeanlucmakiola.calendula.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaScreen
import de.jeanlucmakiola.calendula.ui.calendars.BackupScreen
import de.jeanlucmakiola.calendula.ui.calendars.CalendarsScreen
import de.jeanlucmakiola.calendula.ui.common.BelowTopBarClip
import de.jeanlucmakiola.floret.identity.fadeThrough
import de.jeanlucmakiola.floret.identity.predictiveBackExit
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.EventMoveHost
import de.jeanlucmakiola.calendula.ui.common.EventMoveScope
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.LocalManageCalendars
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineScroll
import de.jeanlucmakiola.calendula.ui.common.LocalViewMorph
import de.jeanlucmakiola.calendula.ui.common.RescheduleViewModel
import de.jeanlucmakiola.calendula.ui.common.TimelineScrollMemory
import de.jeanlucmakiola.calendula.ui.common.ViewMorphScope
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.rememberViewMorphBoundsTransform
import de.jeanlucmakiola.calendula.ui.common.drillToDay
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.selectView
import de.jeanlucmakiola.calendula.ui.common.viewBaseStack
import de.jeanlucmakiola.calendula.ui.week.DayScreen
import de.jeanlucmakiola.calendula.ui.detail.EventDetailScreen
import de.jeanlucmakiola.calendula.ui.edit.EventEditScreen
import de.jeanlucmakiola.calendula.ui.edit.ImportSource
import de.jeanlucmakiola.calendula.ui.imports.ImportScreen
import de.jeanlucmakiola.calendula.ui.month.MonthScreen
import de.jeanlucmakiola.calendula.ui.search.SearchScreen
import de.jeanlucmakiola.calendula.ui.settings.SettingsScreen
import de.jeanlucmakiola.calendula.ui.week.MultiDayScreen
import de.jeanlucmakiola.calendula.ui.week.WeekScreen
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Holds the top-level view back stack (spec M1) and swaps between the calendar
 * screens. Each screen owns its own ViewModel and date anchor; the view-switcher
 * pill in their top bars writes back here via [onSelectView].
 *
 * The stack's bottom is the user's [CalendarHostViewModel.defaultView] home view.
 * A lateral switch (pill / drawer) builds a visit history so back retraces it
 * (see [selectView]); a widget launch resets the stack to its own view; a date
 * tap drills the day view on top. Pressing back pops one level, and the
 * base-level [BackHandler] hands off to the system to exit once only the home
 * view remains. So back from a widget-opened screen returns to that widget's
 * view, then home; and back through pill switches walks the views in reverse.
 *
 * [requestedDetailKey] is an externally requested occurrence (a tapped
 * reminder notification routed through MainActivity): it opens the detail
 * overlay exactly like an event tap and is cleared via [onDetailKeyConsumed]
 * so a later recomposition can't re-open it. (A widget event tap instead arrives
 * as [WidgetNavRequest.OpenEvent], which also roots the back stack in the
 * widget's view.)
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CalendarHost(
    modifier: Modifier = Modifier,
    requestedDetailKey: LongArray? = null,
    onDetailKeyConsumed: () -> Unit = {},
    widgetNavRequest: WidgetNavRequest? = null,
    onWidgetNavConsumed: () -> Unit = {},
    requestedImportUri: android.net.Uri? = null,
    onImportConsumed: () -> Unit = {},
    requestedInsertForm: EventForm? = null,
    requestedInsertSource: ImportSource = ImportSource.Insert,
    onInsertConsumed: () -> Unit = {},
    requestedEditKey: LongArray? = null,
    onEditKeyConsumed: () -> Unit = {},
    viewModel: CalendarHostViewModel = hiltViewModel(),
) {
    // Wait for the persisted default view before seeding the stack, so the app
    // opens straight on the user's choice instead of flashing a placeholder and
    // correcting it. Brief blank first frame, matching the onboarding gate above.
    val defaultView = viewModel.defaultView.collectAsStateWithLifecycle().value ?: return

    // View customisation (#24): the quick-switch cycle and drawer order. Both have
    // sensible non-empty initial values, so they're ready before the first frame.
    val quickSwitchViews = viewModel.quickSwitchViews.collectAsStateWithLifecycle().value
    val drawerViewOrder = viewModel.drawerViewOrder.collectAsStateWithLifecycle().value
    // Whether the jump-to-today control sits in each view's top bar or the FAB (#60).
    val todayInToolbar = viewModel.todayButtonInToolbar.collectAsStateWithLifecycle().value

    var viewStack by rememberSaveable(stateSaver = viewStackSaver) {
        mutableStateOf(listOf(defaultView))
    }
    val view = viewStack.last()
    val onSelectView: (CalendarView) -> Unit = { viewStack = viewStack.selectView(it) }

    // Tapping a day in the month grid opens the day view anchored to that date.
    var pendingDayIso by rememberSaveable { mutableStateOf<String?>(null) }
    val onOpenDay: (LocalDate) -> Unit = { date ->
        pendingDayIso = date.toString()
        viewStack = viewStack.drillToDay()
    }

    // The event-detail screen (S4) is a full-screen destination hoisted here so
    // it overlays whichever calendar view is active. We forward the tapped
    // occurrence's own times (eventId + begin + end, packed as a saveable
    // long[]) so recurring events show the correct date, not the series start.
    // [heldKey] keeps the last shown key alive through the slide-out (when
    // [detailKey] is cleared); it is set in the tap callback — never a [0,0,0]
    // placeholder — so the destination never loads a bogus id=0 on first frame.
    var detailKey by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var heldKey by remember { mutableStateOf<LongArray?>(null) }
    val onEventClick: (EventInstance) -> Unit = { event ->
        val key = longArrayOf(
            event.eventId,
            event.start.toEpochMilliseconds(),
            event.end.toEpochMilliseconds(),
        )
        heldKey = key
        detailKey = key
    }

    // A tapped reminder notification asks for a specific occurrence.
    LaunchedEffect(requestedDetailKey) {
        if (requestedDetailKey != null) {
            heldKey = requestedDetailKey
            detailKey = requestedDetailKey
            onDetailKeyConsumed()
        }
    }

    // Settings (M4) is hoisted here so it overlays whichever calendar view is
    // active and survives view switches. (The calendar filter now lives inline
    // in the navigation drawer, so no overlay state is needed for it.)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val onOpenSettings = { showSettings = true }

    // Full-text search — its own overlay, opened from each calendar screen's
    // top bar. Sits below the detail/edit overlays so tapping a result reveals
    // the detail on top and backing out returns to the results.
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val onOpenSearch = { showSearch = true }

    // Calendar manager (reached from Settings) — its own overlay so it slides
    // over Settings and survives view switches.
    var showCalendars by rememberSaveable { mutableStateOf(false) }

    // Backup & restore (#69) — hoisted like the manager, being driven by the
    // calendar list rather than by preferences. Reached from both.
    var showBackup by rememberSaveable { mutableStateOf(false) }

    // Event form (v1.2 create) — same held-key pattern as the detail screen:
    // [heldCreateIso] keeps the prefill date alive through the slide-out.
    // [createStartMinutes] is the tapped slot's start (minutes from midnight)
    // when the form is opened from a day/week grid tap; null from the FAB.
    var createDateIso by rememberSaveable { mutableStateOf<String?>(null) }
    var heldCreateIso by remember { mutableStateOf<String?>(null) }
    var createStartMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var heldCreateMinutes by remember { mutableStateOf<Int?>(null) }
    val onCreateEvent: (LocalDate, Int?) -> Unit = { date, startMinutes ->
        heldCreateIso = date.toString()
        createDateIso = date.toString()
        heldCreateMinutes = startMinutes
        createStartMinutes = startMinutes
    }

    // Edit form (v1.3) — reuses the detail screen's occurrence key; for
    // recurring events the form itself asks for the write scope at save
    // time. A saved edit closes the detail screen too: the occurrence the
    // user tapped may not exist anymore (time moved, recurrence changed), so
    // falling back to the auto-refreshing calendar is the only honest
    // destination.
    var editKey by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var heldEditKey by remember { mutableStateOf<LongArray?>(null) }

    // An opened/received .ics file. [ImportScreen] parses it and either opens
    // the prefilled create form (one event → [importForm]) or its own bulk
    // picker (many). A plain conditional overlay (no slide) — it's transient.
    var importUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importForm by remember { mutableStateOf<EventForm?>(null) }
    // Which channel filled [importForm]: an .ics file (prompt to apply the default
    // reminder) or an ACTION_INSERT intent (apply it automatically) — #49.
    var importFormSource by remember { mutableStateOf(ImportSource.File) }
    // A restore (in-app "Restore from .ics" button) always runs the full import
    // flow — picker + summary — even for a single-event file, because the intent
    // is "restore a backup", not "add this one event". An externally opened .ics
    // keeps routing a single event straight into the prefilled create form.
    var importForceMany by remember { mutableStateOf(false) }
    // Where a restore came from, so closing the import puts it back: the import
    // overlays are declared under Backup & restore and the manager, so starting
    // one has to close them — without this, finishing a restore drops you on the
    // calendar and the next file means walking in through Settings again.
    var backupAfterImport by rememberSaveable { mutableStateOf(false) }
    var calendarsAfterImport by rememberSaveable { mutableStateOf(false) }
    // One import run. The import VM lives in the Activity's store, so this is
    // what tells it a *re-import of the same file* is new work and not the run
    // it already finished; a rotation keeps the number and keeps the result.
    var importSession by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(requestedImportUri) {
        if (requestedImportUri != null) {
            importUri = requestedImportUri
            importForceMany = false
            importSession++
            onImportConsumed()
        }
    }
    // An external ACTION_INSERT launch (another app/widget creating an event,
    // issue #30) arrives already prefilled — open it in the same create form the
    // single-event .ics path uses. [importForm] is the topmost overlay, so it
    // reveals on top of whatever was open without extra dismissal.
    LaunchedEffect(requestedInsertForm) {
        if (requestedInsertForm != null) {
            importFormSource = requestedInsertSource
            importForm = requestedInsertForm
            onInsertConsumed()
        }
    }

    // Close every overlay that can sit over the calendar, so an externally
    // requested destination (a widget/shortcut/QS-tile launch) is revealed on
    // top instead of underneath whatever the user had open.
    fun dismissCoveringOverlays() {
        showSettings = false
        showCalendars = false
        showBackup = false
        detailKey = null
        editKey = null
        importUri = null
        importForm = null
    }

    // An external "edit this event" (ACTION_EDIT, e.g. an assistant/task app or
    // widget) opens the occurrence straight in the edit form. Drop any covering
    // overlay first — the edit overlay sits below Settings/import in the Box, so
    // without this it would open hidden underneath them. Same held-key pattern as
    // a detail-screen "Edit" tap; a saved edit just returns to the calendar.
    LaunchedEffect(requestedEditKey) {
        if (requestedEditKey != null) {
            dismissCoveringOverlays()
            heldEditKey = requestedEditKey
            editKey = requestedEditKey
            onEditKeyConsumed()
        }
    }

    // A home-screen widget launch asks to open a date (→ day view), open an
    // event's detail, or start a create. Handled once and cleared, mirroring
    // [requestedDetailKey]. Date/event opens root the stack in the widget's own
    // view so backing out returns there (then home), not to the default.
    LaunchedEffect(widgetNavRequest) {
        when (val req = widgetNavRequest) {
            is WidgetNavRequest.OpenDate -> {
                // Drill the day view in over the widget's view: drop any overlay
                // that would cover it, so the open doesn't land under Settings/form.
                dismissCoveringOverlays()
                createDateIso = null
                pendingDayIso = req.dateIso
                // No widget source (an external date tap) roots over the default
                // home view, so backing out of the day returns home then exits.
                viewStack = viewBaseStack(defaultView, req.source ?: defaultView).drillToDay()
                onWidgetNavConsumed()
            }
            is WidgetNavRequest.OpenEvent -> {
                // Root the stack in the widget's view, then open the occurrence
                // detail over it (same key shape as a tapped event / reminder).
                dismissCoveringOverlays()
                createDateIso = null
                viewStack = viewBaseStack(defaultView, req.source)
                val key = longArrayOf(req.eventId, req.beginMillis, req.endMillis)
                heldKey = key
                detailKey = key
                onWidgetNavConsumed()
            }
            is WidgetNavRequest.OpenView -> {
                // A widget header tap: land on a top-level view with no date
                // drill-in. Reveal it by dropping any covering overlay, then root
                // the stack on the target (null → the default home) over the
                // default home — so backing out returns to the default, then exits.
                dismissCoveringOverlays()
                createDateIso = null
                pendingDayIso = null
                viewStack = viewBaseStack(defaultView, req.view ?: defaultView)
                onWidgetNavConsumed()
            }
            is WidgetNavRequest.Create -> {
                // External "new event" entries (QS tile / launcher shortcut /
                // widget) must land on top of whatever is open — the form overlay
                // sits below Settings/calendars in the Box, so without this it
                // would open hidden underneath them.
                dismissCoveringOverlays()
                val iso = req.dateIso ?: Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                heldCreateIso = iso
                createDateIso = iso
                heldCreateMinutes = null
                createStartMinutes = null
                onWidgetNavConsumed()
            }
            null -> {}
        }
    }

    // Drag to reschedule (#68). Hosted here so one instance serves every view and
    // survives view switches; the calendar surfaces read it out of LocalEventMove.
    val reschedule: RescheduleViewModel = hiltViewModel()
    val movableCalendarIds by reschedule.movableCalendarIds.collectAsStateWithLifecycle()
    val onEditEvent: (EventInstance) -> Unit = { event ->
        val key = longArrayOf(
            event.eventId,
            event.start.toEpochMilliseconds(),
            event.end.toEpochMilliseconds(),
        )
        heldEditKey = key
        editKey = key
    }
    // Off by preference (#173) switches off the gesture, not the scope: blocks
    // register no drag, while the edit form and its TalkBack action stay put.
    val dragToReschedule = viewModel.dragToReschedule.collectAsStateWithLifecycle().value
    val moveScope = remember(movableCalendarIds, reschedule, dragToReschedule) {
        EventMoveScope(
            movableCalendarIds = movableCalendarIds,
            dragEnabled = dragToReschedule,
            move = { reschedule.move(it) },
            inFlight = reschedule.inFlight,
            undoStarted = reschedule.undoStarted,
            abandoned = reschedule.abandoned,
            edit = onEditEvent,
        )
    }

    val slideSpec = rememberCalendarSlideSpec()
    // Overlays leave from the predictive-back preview (#184): shrinking on from
    // where the gesture let go instead of snapping back and sliding off.
    val overlayExit = predictiveBackExit()

    // Base-level back: pop the view stack while no overlay covers it (each overlay
    // owns its own BackHandler and takes precedence). Disabled at the home view,
    // so back there falls through to the system and exits the app.
    val anyOverlayVisible = showSearch || detailKey != null || createDateIso != null ||
        editKey != null || showSettings || showCalendars || showBackup ||
        importUri != null || importForm != null
    BackHandler(enabled = !anyOverlayVisible && viewStack.size > 1) {
        viewStack = viewStack.dropLast(1)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Switching between the peer views (month/week/day/agenda) is lateral
        // navigation, so it fades through rather than sliding — paging *within* a
        // view keeps the directional slide. What both views show morphs across
        // (#184): see [ViewMorphKey]. Reduced motion keeps the plain fade.
        val viewSwitch = fadeThrough()
        val reduceMotion = rememberReduceMotion()
        val morphBounds = rememberViewMorphBoundsTransform()
        val morphFade = rememberCalendarFadeSpec()
        val morphClip = remember { BelowTopBarClip() }
        val density = LocalDensity.current
        val topBarBottom = TopAppBarDefaults.windowInsets.getTop(density) +
            with(density) { TopAppBarDefaults.TopAppBarExpandedHeight.toPx() }
        val timelineScroll = remember { TimelineScrollMemory() }
        CompositionLocalProvider(
            LocalEventMove provides moveScope,
            LocalTimelineScroll provides timelineScroll,
            // The failure state's way out when every calendar is switched off
            // (#239); nothing else in the calendar surfaces navigates here.
            LocalManageCalendars provides { showCalendars = true },
        ) {
        SharedTransitionLayout(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { morphClip.top = topBarBottom - it.positionInWindow().y },
        ) {
        AnimatedContent(
            targetState = view,
            transitionSpec = { viewSwitch },
            label = "view-switch",
        ) { currentView ->
            val sharedScope = this@SharedTransitionLayout
            val visibilityScope = this@AnimatedContent
            val viewMorph = remember(reduceMotion, sharedScope, visibilityScope, morphBounds, morphFade, morphClip) {
                if (reduceMotion) null else ViewMorphScope(sharedScope, visibilityScope, morphBounds, morphFade, morphClip)
            }
            CompositionLocalProvider(LocalViewMorph provides viewMorph) {
            when (currentView) {
                CalendarView.Week -> WeekScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    quickSwitchViews = quickSwitchViews,
                    drawerViewOrder = drawerViewOrder,
                    todayInToolbar = todayInToolbar,
                )
                CalendarView.MultiDay -> MultiDayScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    quickSwitchViews = quickSwitchViews,
                    drawerViewOrder = drawerViewOrder,
                    todayInToolbar = todayInToolbar,
                )
                CalendarView.Day -> DayScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    initialDateIso = pendingDayIso,
                    quickSwitchViews = quickSwitchViews,
                    drawerViewOrder = drawerViewOrder,
                    todayInToolbar = todayInToolbar,
                )
                CalendarView.Month -> MonthScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    quickSwitchViews = quickSwitchViews,
                    drawerViewOrder = drawerViewOrder,
                    todayInToolbar = todayInToolbar,
                )
                CalendarView.Agenda -> AgendaScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    quickSwitchViews = quickSwitchViews,
                    drawerViewOrder = drawerViewOrder,
                    todayInToolbar = todayInToolbar,
                )
            }
            }
        }
        }
        }

        // Scope prompt + confirmation/undo snackbar for a dropped event, declared
        // right after the calendar views so later overlays cover it.
        EventMoveHost(reschedule, modifier = Modifier.fillMaxSize())

        // Search overlay — below detail/edit in the Box so a tapped result's
        // detail screen draws on top, and closing it returns to the results.
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            SearchScreen(
                onBack = { showSearch = false },
                onEventClick = onEventClick,
                visible = showSearch,
            )
        }

        // Prefer the live key; fall back to the held one only while sliding out.
        val activeKey = detailKey ?: heldKey
        AnimatedVisibility(
            visible = detailKey != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            activeKey?.let { key ->
                EventDetailScreen(
                    eventId = key[0],
                    beginMillis = key[1],
                    endMillis = key[2],
                    onBack = { detailKey = null },
                    onEdit = {
                        heldEditKey = key
                        editKey = key
                    },
                    onDuplicate = { form ->
                        // Reuse the prefilled-create overlay: a duplicate is a
                        // fresh event, so it applies the default reminder like an
                        // in-app new event (#52).
                        importFormSource = ImportSource.Insert
                        importForm = form
                        detailKey = null
                    },
                    visible = detailKey != null,
                )
            }
        }

        // Event form (v1.2) — full-screen destination, slides over the calendar.
        AnimatedVisibility(
            visible = createDateIso != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            (createDateIso ?: heldCreateIso)?.let { iso ->
                EventEditScreen(
                    initialDateIso = iso,
                    initialStartMinutes = createStartMinutes ?: heldCreateMinutes,
                    onClose = { createDateIso = null },
                    onSaved = { createDateIso = null },
                    onManageCalendars = { showCalendars = true },
                    visible = createDateIso != null,
                )
            }
        }

        // Edit form (v1.3) — slides over the detail screen.
        AnimatedVisibility(
            visible = editKey != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            (editKey ?: heldEditKey)?.let { key ->
                EventEditScreen(
                    initialDateIso = null,
                    editKey = key,
                    onClose = { editKey = null },
                    onSaved = {
                        editKey = null
                        detailKey = null
                    },
                    onManageCalendars = { showCalendars = true },
                    visible = editKey != null,
                )
            }
        }

        // Settings (M4) — full-screen destination, slides over the calendar.
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            SettingsScreen(
                onBack = { showSettings = false },
                onManageCalendars = { showCalendars = true },
                onOpenBackup = { showBackup = true },
                visible = showSettings,
            )
        }

        // Import flow for an opened/received .ics file. A single event routes
        // into the create form (prefilled, for review); many open the picker.
        importUri?.let { uri ->
            ImportScreen(
                uri = uri,
                session = importSession,
                forceMany = importForceMany,
                onClose = {
                    importUri = null
                    // Back to the surface the restore started from, ready for
                    // the next file.
                    showCalendars = calendarsAfterImport
                    showBackup = backupAfterImport
                    calendarsAfterImport = false
                    backupAfterImport = false
                },
                onManageCalendars = {
                    // The manager is where this leads, so it must not be
                    // reopened underneath on close.
                    calendarsAfterImport = false
                    backupAfterImport = false
                    showCalendars = true
                },
                onOpenSingle = { form ->
                    importUri = null
                    calendarsAfterImport = false
                    backupAfterImport = false
                    importFormSource = ImportSource.File
                    importForm = form
                },
            )
        }
        importForm?.let { form ->
            EventEditScreen(
                initialDateIso = null,
                initialForm = form,
                initialFormSource = importFormSource,
                onClose = { importForm = null },
                onSaved = { importForm = null },
                onManageCalendars = { showCalendars = true },
            )
        }

        // Declared last so it covers every overlay that can open it: Settings,
        // both event forms, and the .ics import picker (#76).
        AnimatedVisibility(
            visible = showCalendars,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            CalendarsScreen(
                onBack = { showCalendars = false },
                onOpenBackup = { showBackup = true },
                visible = showCalendars,
            )
        }

        // Backup & restore — over the manager, since the manager links into it.
        AnimatedVisibility(
            visible = showBackup,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = overlayExit,
        ) {
            // Settings can open this screen without the manager underneath, so
            // its failure states' "Manage calendars" way out has to open the
            // manager rather than just pop — popping alone lands on Settings
            // (#304). Coming from the manager the flag is already set, so this
            // is the same pop-back there.
            CompositionLocalProvider(
                LocalManageCalendars provides {
                    showCalendars = true
                    showBackup = false
                },
            ) {
                BackupScreen(
                    onBack = { showBackup = false },
                    // Restore runs the normal .ics import, and both this screen
                    // and the manager that can have opened it are declared above
                    // the import overlays — so both have to step aside.
                    onImport = {
                        importUri = it
                        importForceMany = true
                        importSession++
                        backupAfterImport = true
                        calendarsAfterImport = showCalendars
                        showBackup = false
                        showCalendars = false
                    },
                    visible = showBackup,
                )
            }
        }
    }
}

/** Persists the view back stack across config change / process death by ordinal. */
private val viewStackSaver = listSaver<List<CalendarView>, Int>(
    save = { stack -> stack.map(CalendarView::ordinal) },
    restore = { ordinals -> ordinals.map { CalendarView.entries[it] } },
)
