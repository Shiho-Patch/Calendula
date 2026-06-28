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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
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
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.calendarAnimateItem
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.GroupedRow
import de.jeanlucmakiola.calendula.ui.common.Position
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.pastelize
import de.jeanlucmakiola.calendula.ui.common.positionOf
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val zone = TimeZone.currentSystemDefault()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AgendaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val pastDisplay by viewModel.pastEventDisplay.collectAsStateWithLifecycle()

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
                    onCycleView = { onSelectView(selectedView.next()) },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                    onRetry = viewModel::goToToday,
                    onEventClick = onEventClick,
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
 * A compact tonal pill showing the agenda's current range. Tapping it opens the
 * range picker as a session-only override. Filled with the primary container
 * while an override is active, so the temporary state is obvious.
 */
@Composable
private fun AgendaRangePill(
    range: AgendaRange,
    isOverride: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (isOverride) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (isOverride) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = agendaRangeLabel(range),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * A header naming the concrete window currently shown, e.g. "Showing all events
 * for · Today, 27 Jun 2026" / "This week, 27 Jun – 3 Jul" / "This month, June 2026".
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
        Text(
            text = "${agendaRangeLabel(range)}, $window",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AgendaContent(
    state: AgendaUiState,
    pastDisplay: PastEventDisplay,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
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
            val days = if (pastDisplay == PastEventDisplay.HIDE) {
                state.days.mapNotNull { day ->
                    val remaining = day.events.filterNot { it.hasEnded(now) }
                    if (remaining.isEmpty()) null else day.copy(events = remaining)
                }
            } else {
                state.days
            }
            if (days.isEmpty()) {
                AgendaEmpty(modifier)
            } else {
                AgendaList(
                    days = days,
                    today = state.today,
                    dimPast = pastDisplay == PastEventDisplay.DIM,
                    now = now,
                    onEventClick = onEventClick,
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        days.forEach { day ->
            stickyHeader(key = "header-${day.date}") {
                AgendaDayHeader(date = day.date, today = today)
            }
            itemsIndexed(
                items = day.events,
                key = { _, event -> event.instanceId },
            ) { index, event ->
                AgendaEventRow(
                    event = event,
                    position = positionOf(index, day.events.size),
                    dimmed = dimPast && event.hasEnded(now),
                    modifier = calendarAnimateItem(),
                    onClick = { onEventClick(event) },
                )
            }
            item(key = "gap-${day.date}") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AgendaDayHeader(date: LocalDate, today: LocalDate) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun AgendaEventRow(
    event: EventInstance,
    position: Position,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    GroupedRow(
        modifier = if (dimmed) modifier.alpha(EventDimAlpha) else modifier,
        title = title,
        summary = agendaTimeSummary(event),
        position = position,
        minHeight = 64.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(pastelize(event.color, dark)),
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

/** Time line under the title: "09:00 – 10:00 · Location", "All day", etc. */
@Composable
private fun agendaTimeSummary(event: EventInstance): String {
    val time = if (event.isAllDay) {
        stringResource(R.string.event_detail_all_day)
    } else {
        val is24Hour = LocalUse24HourFormat.current
        val locale = currentLocale()
        "${formatTime(event.start, is24Hour, locale)} – ${formatTime(event.end, is24Hour, locale)}"
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
    val weekday = java.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val monthName = java.month.getDisplayName(JavaTextStyle.SHORT, locale)
    return "$weekday, ${date.day}. $monthName ${date.year}"
}
