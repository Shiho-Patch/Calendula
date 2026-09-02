package de.jeanlucmakiola.calendula.ui.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.AppBarSpacing
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.floret.identity.animateItemMotion
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

// No file-level zone constant here on purpose: it would be fixed for the process
// lifetime and drift from the zone AgendaViewModel groups in after a device
// time-zone change. The zone travels on AgendaUiState.Success instead.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView> = IMPLEMENTED_VIEWS,
    drawerViewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
    todayInToolbar: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val pastDisplay by viewModel.pastEventDisplay.collectAsStateWithLifecycle()
    val showToday by viewModel.showToday.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showRangePicker by remember { mutableStateOf(false) }

    val isOnToday = when (val s = state) {
        is AgendaUiState.Success -> s.anchor == s.today
        else -> true
    }
    val successState = state as? AgendaUiState.Success

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = anchor,
                drawerState = drawerState,
                viewOrder = drawerViewOrder,
                onSelectView = { view ->
                    onSelectView(view)
                    scope.launch { drawerState.close() }
                },
                onJumpToDate = { target ->
                    viewModel.goToDate(target)
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    onOpenSettings()
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                AgendaTopBar(
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next(quickSwitchViews)) },
                    quickSwitchViews = quickSwitchViews,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    showTodayButton = todayInToolbar,
                    onToday = viewModel::goToToday,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnToday && !todayInToolbar,
                    todayText = stringResource(R.string.agenda_today_action),
                    onToday = viewModel::goToToday,
                    onCreate = { onCreateEvent(anchor, null) },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // One bar at the top: the "showing …" header on the left and the
                // session range switcher on the right (one settings toggle).
                successState?.takeIf { it.showRangeBar }?.let { s ->
                    // end lines the selector up with whatever ends the top bar:
                    // the view switcher's background, or — once #150 hides it —
                    // the search icon's glyph.
                    val selectorEnd = if (quickSwitchViews.size >= QuickSwitchConfig.MIN_CYCLE) {
                        AppBarSpacing.Inset
                    } else {
                        AppBarSpacing.IconTrailingInset
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = RANGE_BAR_TEXT_INSET,
                                end = selectorEnd,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                    ) {
                        AgendaRangeBanner(
                            range = s.range,
                            start = s.anchor,
                            end = s.rangeEnd,
                            modifier = Modifier.weight(1f),
                        )
                        AgendaRangePill(
                            range = s.range,
                            isOverride = s.rangeIsOverride,
                            onClick = { showRangePicker = true },
                        )
                    }
                }
                AgendaContent(
                    state = state,
                    pastDisplay = pastDisplay,
                    showToday = showToday,
                    onRetry = viewModel::goToToday,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (showRangePicker) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_range),
            description = stringResource(R.string.agenda_range_override_hint),
            selected = successState?.range ?: AgendaRange.Month,
            weekStart = weekStart,
            onSelect = viewModel::setRangeOverride,
            onDismiss = { showRangePicker = false },
        )
    }
}

/**
 * The agenda's current range, tapped to open the range picker as a session-only
 * override. Shares the top-bar view switcher's button shape so the two read as a
 * family, but stays low-emphasis — a subtle neutral surface tint rather than the
 * switcher's secondary container, so it doesn't compete. Fills with the primary
 * container only while an override is active, to make that temporary state clear.
 */
@Composable
private fun AgendaRangePill(
    range: AgendaRange,
    isOverride: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isOverride) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isOverride) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier,
    ) {
        Text(
            text = agendaRangeLabel(range),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Optical start inset for the range bar's text, set against the title above it. */
private val RANGE_BAR_TEXT_INSET = 28.dp

/**
 * A header naming the concrete window currently shown under a "showing …" label,
 * e.g. "27 Jun 2026" / "27 Jun – 3 Jul 2026" / "June 2026". The range's name
 * lives on the selector button beside it, so it isn't repeated here.
 */
@Composable
private fun AgendaRangeBanner(
    range: AgendaRange,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val window = agendaRangeWindowSummary(range, start, end, locale)
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.agenda_range_showing_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Just the concrete dates — the range's name ("Next 30 days") already
        // sits on the selector button to the right, so repeating it here is noise.
        Text(
            text = window,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AgendaContent(
    state: AgendaUiState,
    pastDisplay: PastEventDisplay,
    showToday: Boolean,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        AgendaUiState.Loading -> Box(modifier)
        is AgendaUiState.Failure -> Box(modifier) {
            CalendarFailure(reason = state.reason, onRetry = onRetry)
        }
        is AgendaUiState.Success -> {
            val now by rememberCurrentMinute()
            // Hiding drops finished events — and any day they leave empty; dimming
            // keeps them but fades the row. Recomputed each minute so events fall
            // away (or fade) as they end while the screen stays open.
            val filtered = if (pastDisplay == PastEventDisplay.HIDE) {
                state.days.mapNotNull { day ->
                    val remaining = day.events.filterNot { it.hasEnded(now) }
                    if (remaining.isEmpty()) null else day.copy(events = remaining)
                }
            } else {
                state.days
            }
            // Anchor today with a "nothing left today" placeholder — but only when
            // the window actually starts on today; a jumped-to date has no today in
            // it, so anchoring there would be misleading (#35).
            val days = anchorTodayIfMissing(
                days = filtered,
                today = state.today,
                enabled = showToday && state.anchor == state.today,
            )
            if (days.isEmpty()) {
                AgendaEmpty(modifier)
            } else {
                AgendaList(
                    days = days,
                    today = state.today,
                    zone = state.zone,
                    dimPast = pastDisplay == PastEventDisplay.DIM,
                    now = now,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    modifier = modifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AgendaList(
    days: List<AgendaDay>,
    today: LocalDate,
    zone: TimeZone,
    dimPast: Boolean,
    now: Instant,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        days.forEach { day ->
            stickyHeader(key = "header-${day.date}") {
                AgendaDayHeader(date = day.date, today = today, onOpenDay = onOpenDay)
            }
            if (day.events.isEmpty()) {
                // An anchored, event-less today (#35) — "nothing left today".
                item(key = "placeholder-${day.date}") {
                    AgendaEmptyDayRow(
                        text = stringResource(R.string.agenda_no_more_today),
                        onClick = { onOpenDay(day.date) },
                    )
                }
            } else {
                itemsIndexed(
                    items = day.events,
                    // Scope the key by day: a multi-day event appears under every
                    // day it spans, so its instanceId alone is not unique across
                    // the list (LazyColumn requires unique keys).
                    key = { _, event -> "${day.date}-${event.instanceId}" },
                ) { index, event ->
                    AgendaEventRow(
                        event = event,
                        day = day.date,
                        zone = zone,
                        position = positionOf(index, day.events.size),
                        dimmed = dimPast && event.hasEnded(now),
                        modifier = animateItemMotion(),
                        onClick = { onEventClick(event) },
                    )
                }
            }
            item(key = "gap-${day.date}") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AgendaEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Coffee,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.agenda_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaTopBar(
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    quickSwitchViews: List<CalendarView>,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            // A plain label rather than a CalendarTitleButton, so it takes that
            // one's inset and one-line clamp itself.
            Text(
                text = stringResource(R.string.view_agenda),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AppBarSpacing.TitleInset),
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.month_open_menu),
                )
            }
        },
        actions = {
            TodayAction(show = showTodayButton, onToday = onToday)
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_action),
                )
            }
            ViewSwitcherPill(
                current = selectedView,
                cycle = quickSwitchViews,
                onCycle = onCycleView,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}
