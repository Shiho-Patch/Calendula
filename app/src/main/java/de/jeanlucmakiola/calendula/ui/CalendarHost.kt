package de.jeanlucmakiola.calendula.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaScreen
import de.jeanlucmakiola.calendula.ui.calendars.CalendarsScreen
import de.jeanlucmakiola.calendula.ui.common.calendarFadeThrough
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.drillToDay
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.selectView
import de.jeanlucmakiola.calendula.ui.common.viewBaseStack
import de.jeanlucmakiola.calendula.ui.day.DayScreen
import de.jeanlucmakiola.calendula.ui.detail.EventDetailScreen
import de.jeanlucmakiola.calendula.ui.edit.EventEditScreen
import de.jeanlucmakiola.calendula.ui.imports.ImportScreen
import de.jeanlucmakiola.calendula.ui.month.MonthScreen
import de.jeanlucmakiola.calendula.ui.search.SearchScreen
import de.jeanlucmakiola.calendula.ui.settings.SettingsScreen
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
@Composable
fun CalendarHost(
    modifier: Modifier = Modifier,
    requestedDetailKey: LongArray? = null,
    onDetailKeyConsumed: () -> Unit = {},
    widgetNavRequest: WidgetNavRequest? = null,
    onWidgetNavConsumed: () -> Unit = {},
    requestedImportUri: android.net.Uri? = null,
    onImportConsumed: () -> Unit = {},
    viewModel: CalendarHostViewModel = hiltViewModel(),
) {
    // Wait for the persisted default view before seeding the stack, so the app
    // opens straight on the user's choice instead of flashing a placeholder and
    // correcting it. Brief blank first frame, matching the onboarding gate above.
    val defaultView = viewModel.defaultView.collectAsStateWithLifecycle().value ?: return

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
    LaunchedEffect(requestedImportUri) {
        if (requestedImportUri != null) {
            importUri = requestedImportUri
            onImportConsumed()
        }
    }

    // Close every overlay that can sit over the calendar, so an externally
    // requested destination (a widget/shortcut/QS-tile launch) is revealed on
    // top instead of underneath whatever the user had open.
    fun dismissCoveringOverlays() {
        showSettings = false
        showCalendars = false
        detailKey = null
        editKey = null
        importUri = null
        importForm = null
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
                viewStack = viewBaseStack(defaultView, req.source).drillToDay()
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

    val slideSpec = rememberCalendarSlideSpec()

    // Base-level back: pop the view stack while no overlay covers it (each overlay
    // owns its own BackHandler and takes precedence). Disabled at the home view,
    // so back there falls through to the system and exits the app.
    val anyOverlayVisible = showSearch || detailKey != null || createDateIso != null ||
        editKey != null || showSettings || showCalendars || importUri != null ||
        importForm != null
    BackHandler(enabled = !anyOverlayVisible && viewStack.size > 1) {
        viewStack = viewStack.dropLast(1)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Switching between the peer views (month/week/day/agenda) is lateral
        // navigation, so it fades through rather than sliding — paging *within* a
        // view keeps the directional slide. AnimatedContent keyed on the view type.
        val viewSwitch = calendarFadeThrough()
        AnimatedContent(
            targetState = view,
            transitionSpec = { viewSwitch },
            label = "view-switch",
        ) { currentView ->
            when (currentView) {
                CalendarView.Week -> WeekScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                )
                CalendarView.Day -> DayScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                    initialDateIso = pendingDayIso,
                )
                CalendarView.Month -> MonthScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onOpenDay = onOpenDay,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                )
                CalendarView.Agenda -> AgendaScreen(
                    selectedView = currentView,
                    onSelectView = onSelectView,
                    onEventClick = onEventClick,
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                    onCreateEvent = onCreateEvent,
                )
            }
        }

        // Search overlay — below detail/edit in the Box so a tapped result's
        // detail screen draws on top, and closing it returns to the results.
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            SearchScreen(
                onBack = { showSearch = false },
                onEventClick = onEventClick,
            )
        }

        // Prefer the live key; fall back to the held one only while sliding out.
        val activeKey = detailKey ?: heldKey
        AnimatedVisibility(
            visible = detailKey != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
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
                )
            }
        }

        // Event form (v1.2) — full-screen destination, slides over the calendar.
        AnimatedVisibility(
            visible = createDateIso != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            (createDateIso ?: heldCreateIso)?.let { iso ->
                EventEditScreen(
                    initialDateIso = iso,
                    initialStartMinutes = createStartMinutes ?: heldCreateMinutes,
                    onClose = { createDateIso = null },
                    onSaved = { createDateIso = null },
                )
            }
        }

        // Edit form (v1.3) — slides over the detail screen.
        AnimatedVisibility(
            visible = editKey != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
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
                )
            }
        }

        // Settings (M4) — full-screen destination, slides over the calendar.
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            SettingsScreen(
                onBack = { showSettings = false },
                onManageCalendars = { showCalendars = true },
            )
        }

        // Calendar manager — slides over Settings.
        AnimatedVisibility(
            visible = showCalendars,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            CalendarsScreen(onBack = { showCalendars = false })
        }

        // Import flow for an opened/received .ics file. A single event routes
        // into the create form (prefilled, for review); many open the picker.
        importUri?.let { uri ->
            ImportScreen(
                uri = uri,
                onClose = { importUri = null },
                onOpenSingle = { form ->
                    importUri = null
                    importForm = form
                },
            )
        }
        importForm?.let { form ->
            EventEditScreen(
                initialDateIso = null,
                initialForm = form,
                onClose = { importForm = null },
                onSaved = { importForm = null },
            )
        }
    }
}

/** Persists the view back stack across config change / process death by ordinal. */
private val viewStackSaver = listSaver<List<CalendarView>, Int>(
    save = { stack -> stack.map(CalendarView::ordinal) },
    restore = { ordinals -> ordinals.map { CalendarView.entries[it] } },
)
