package de.jeanlucmakiola.calendula.ui.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.floret.identity.animateItemMotion
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import java.util.Locale

private val zone = TimeZone.currentSystemDefault()

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
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val pastDisplay by viewModel.pastEventDisplay.collectAsStateWithLifecycle()
    val showToday by viewModel.showToday.collectAsStateWithLifecycle()

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
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnToday,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // end aligns the selector's right edge with the top-bar view
                        // switcher (its 8.dp margin + the app bar's 4.dp inset).
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
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
private fun AgendaContent(
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
                    AgendaEmptyDayRow(onClick = { onOpenDay(day.date) })
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
private fun AgendaDayHeader(
    date: LocalDate,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDay(date) },
    ) {
        Text(
            text = agendaDayLabel(date, today),
            style = MaterialTheme.typography.titleSmall,
            color = if (date == today) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

/**
 * A card under an anchored, event-less today (#35) — the same coffee-cup motif
 * as the full-screen [AgendaEmpty] state, boxed into a card so today keeps a
 * visible slot when nothing is left rather than a bare header.
 */
@Composable
private fun AgendaEmptyDayRow(onClick: () -> Unit) {
    Card(
        // Match a single event row's resting corner radius (floret groupedShape).
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Coffee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.agenda_no_more_today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AgendaEventRow(
    event: EventInstance,
    day: LocalDate,
    position: Position,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val soften = LocalSoftenColors.current
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    GroupedRow(
        modifier = if (dimmed) modifier.alpha(EventDimAlpha) else modifier,
        title = title,
        summary = agendaTimeSummary(event, day),
        position = position,
        minHeight = 64.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(eventFill(event.color, dark, soften)),
            )
        },
        onClick = onClick,
    )
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
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.view_agenda),
                style = MaterialTheme.typography.titleLarge,
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
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_action),
                )
            }
            ViewSwitcherPill(
                current = selectedView,
                onCycle = onCycleView,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/** "Today · Wed, 17. Jun 2026" — relative word for today/tomorrow, else the date. */
@Composable
private fun agendaDayLabel(date: LocalDate, today: LocalDate): String {
    val relative = when (date) {
        today -> stringResource(R.string.agenda_header_today)
        today.plus(1, DateTimeUnit.DAY) -> stringResource(R.string.agenda_header_tomorrow)
        else -> null
    }
    val formatted = formatAgendaDate(date)
    return if (relative != null) "$relative · $formatted" else formatted
}

/**
 * Time line under the title: "09:00 – 10:00 · Location", "All day", etc.
 *
 * A multi-day event shows only the part relevant to [day], spelled out so each
 * day reads on its own: its first day names the start ("Starts 14:00"), its last
 * day the end ("Ends 10:00"), and any whole day in between reads as "All day".
 * An all-day multi-day event is simply "All day" on every day it covers.
 */
@Composable
private fun agendaTimeSummary(event: EventInstance, day: LocalDate): String {
    val is24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()

    val time = when (val label = agendaTimeLabel(event, day, zone)) {
        AgendaTimeLabel.AllDay -> stringResource(R.string.event_detail_all_day)
        is AgendaTimeLabel.Starts ->
            stringResource(R.string.agenda_span_starts, formatTime(label.start, is24Hour, locale))
        is AgendaTimeLabel.Ends ->
            stringResource(R.string.agenda_span_ends, formatTime(label.end, is24Hour, locale))
        is AgendaTimeLabel.Range ->
            "${formatTime(label.start, is24Hour, locale)} – ${formatTime(label.end, is24Hour, locale)}"
    }

    val location = event.location?.takeIf { it.isNotBlank() }
    return if (location != null) "$time · $location" else time
}

private fun formatTime(instant: Instant, is24Hour: Boolean, locale: Locale): String {
    val t = instant.toLocalDateTime(zone).time
    return formatTimeOfDay(t.hour, t.minute, is24Hour, locale)
}

private fun formatAgendaDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    val java = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
    // Weekday + date in the locale's own field order (e.g. "Wed, Jun 17, 2026"
    // vs "Mi., 17. Juni 2026") rather than a hardcoded day-month-year layout.
    return localizedDateFormatter(locale, "EEEdMMMy").format(java)
}
