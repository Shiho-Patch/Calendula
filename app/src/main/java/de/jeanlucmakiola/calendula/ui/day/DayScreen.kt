package de.jeanlucmakiola.calendula.ui.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSwipe
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourLines
import de.jeanlucmakiola.calendula.ui.common.formatHourLabel
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.hourSeparatorLines
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 48.dp
/** Start inset for the gutter's hour labels so they centre on the top bar's
 *  hamburger: with a 48dp gutter, 8dp lands the centre at 28dp (the app bar's
 *  4dp inset + 24dp half icon button), matching the week view. */
private val GUTTER_CONTENT_START_INSET = 8.dp
private val MIN_EVENT_HEIGHT = 24.dp
private val ALL_DAY_ROW_HEIGHT = 24.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp

/** Total all-day strip height for the day (0 when there are no all-day events). */
private fun DayUiState.Success.allDayStripHeight(): Dp {
    if (allDay.isEmpty()) return 0.dp
    val lanes = allDay.maxOf { it.lane } + 1
    return ALL_DAY_ROW_HEIGHT * lanes + ALL_DAY_VERTICAL_PADDING * 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView> = IMPLEMENTED_VIEWS,
    drawerViewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
    todayInToolbar: Boolean = false,
    modifier: Modifier = Modifier,
    initialDateIso: String? = null,
    viewModel: DayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()

    // When opened from the month grid, anchor to the tapped date.
    LaunchedEffect(initialDateIso) {
        initialDateIso?.let { viewModel.goToDate(LocalDate.parse(it)) }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The all-day strip shares the app bar's scrolled colour so the whole top
    // region elevates together once the timeline scrolls under it.
    val topSectionColor by animateColorAsState(
        targetValue = if (scrollBehavior.state.overlappedFraction > 0.01f) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "day-top-section-color",
    )

    val isOnToday = when (val s = state) {
        is DayUiState.Success -> s.date == s.today
        else -> true
    }

    // Drives whether the title carries the year. Falls back to the clock only
    // while the first load is in flight, when there is no state to read today from.
    val currentYear = when (val s = state) {
        is DayUiState.Success -> s.today.year
        else -> Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year
    }

    // Slide direction for the day transition: +1 = next, -1 = prev, 0 = jump.
    var slideDir by remember { mutableIntStateOf(0) }
    val goNext = { slideDir = 1; viewModel.goToNext() }
    val goPrev = { slideDir = -1; viewModel.goToPrev() }
    // Slide toward today: viewing the future → today comes in from the left
    // (back), viewing the past → from the right (forward).
    val jumpToToday = {
        slideDir = when (val s = state) {
            is DayUiState.Success -> if (s.today < s.date) -1 else 1
            else -> 0
        }
        viewModel.goToToday()
    }
    // Drawer jump-to-date: slide from the side the target lies on.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        slideDir = if (target < date) -1 else 1
        viewModel.goToDate(target)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the day swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = date,
                drawerState = drawerState,
                viewOrder = drawerViewOrder,
                onSelectView = { view ->
                    onSelectView(view)
                    scope.launch { drawerState.close() }
                },
                onJumpToDate = { target ->
                    jumpToDate(target)
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
                DayTopBar(
                    date = date,
                    currentYear = currentYear,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next(quickSwitchViews)) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    showTodayButton = todayInToolbar,
                    onToday = jumpToToday,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnToday && !todayInToolbar,
                    todayText = stringResource(R.string.day_today_action),
                    onToday = jumpToToday,
                    onCreate = { onCreateEvent(date, null) },
                )
            },
        ) { innerPadding ->
            DayContent(
                state = state,
                slideDir = slideDir,
                topSectionColor = topSectionColor,
                onSwipeNext = goNext,
                onSwipePrev = goPrev,
                onRetry = jumpToToday,
                onEventClick = onEventClick,
                onCreateAt = { d, minutes -> onCreateEvent(d, minutes) },
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DayContent(
    state: DayUiState,
    slideDir: Int,
    topSectionColor: Color,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    // Hoisted above the per-day AnimatedContent so the vertical scroll position
    // survives day-to-day swipes. We only centre on noon once, on first entry
    // into the day view (i.e. when arriving from the month/week view).
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        val maxV = scrollState.maxValue
        val target = with(density) {
            (HOUR_HEIGHT.toPx() * 12 - (HOUR_HEIGHT.toPx() * 24 - maxV) / 2f).roundToInt()
        }.coerceIn(0, maxV)
        scrollState.scrollTo(target)
    }

    // Single, hoisted all-day strip height — shared by the outgoing and incoming
    // day during a swipe, so the strip slides along but never jumps in height.
    val targetAllDayHeight = (state as? DayUiState.Success)?.allDayStripHeight() ?: 0.dp
    val allDayHeight by animateDpAsState(
        targetValue = targetAllDayHeight,
        label = "day-all-day-strip-height",
    )

    // Whole-page horizontal swipe, one level above the timeline's vertical
    // scroll: a horizontal drag crosses this detector's slop, while a vertical
    // drag is consumed by the inner scroll first — the two gestures coexist.
    val swipeModifier = rememberCalendarPageSwipe(onSwipeNext, onSwipePrev)

    AnimatedContent(
        targetState = state,
        modifier = modifier.then(swipeModifier),
        contentKey = { s ->
            when (s) {
                is DayUiState.Success -> "success-${s.date}"
                is DayUiState.Failure -> "failure-${s.reason}"
                DayUiState.Loading -> "loading"
            }
        },
        transitionSpec = { calendarSlideTransition(slideDir, slideSpec, fadeSpec, reduceMotion) },
        label = "day-transition",
    ) { s ->
        when (s) {
            DayUiState.Loading -> DayLoading()
            is DayUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
            is DayUiState.Success -> DaySuccess(
                state = s,
                topSectionColor = topSectionColor,
                scrollState = scrollState,
                allDayHeight = allDayHeight,
                onEventClick = onEventClick,
                onCreateAt = onCreateAt,
            )
        }
    }
}

@Composable
private fun DaySuccess(
    state: DayUiState.Success,
    topSectionColor: Color,
    scrollState: ScrollState,
    allDayHeight: Dp,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // All-day strip collapses to nothing when the day has no all-day events,
        // so the timeline sits directly under the app bar.
        AllDayStrip(
            state = state,
            height = allDayHeight,
            onEventClick = onEventClick,
            modifier = Modifier
                .fillMaxWidth()
                .background(topSectionColor),
        )
        // Breathing room between the (colour-shifting) top section and the
        // scrolling timeline below.
        Spacer(Modifier.height(8.dp))
        Timeline(
            state = state,
            scrollState = scrollState,
            onEventClick = onEventClick,
            onCreateAt = onCreateAt,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTopBar(
    date: LocalDate,
    currentYear: Int,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    val locale = currentLocale()
    TopAppBar(
        title = {
            Text(
                text = formatDayTitle(date, locale, currentYear),
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
            TodayAction(show = showTodayButton, onToday = onToday)
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

@Composable
private fun AllDayStrip(
    state: DayUiState.Success,
    height: Dp,
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()

    Row(
        modifier = modifier
            // Height is hoisted + animated so it resizes smoothly; padding sits
            // inside it so the content area is lanes * row height.
            .height(height)
            .padding(vertical = ALL_DAY_VERTICAL_PADDING),
    ) {
        // Keep the gutter-width offset so the bars line up with the day column.
        Spacer(Modifier.width(GUTTER_WIDTH))
        // Bars are positioned absolutely by lane (vertical stacking); each spans
        // the full day-column width. clipToBounds keeps bars from spilling out
        // while the height animates.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            val barWidth = maxWidth
            state.allDay.forEach { span ->
                AllDayBar(
                    event = span.event,
                    dark = dark,
                    onClick = { onEventClick(span.event) },
                    modifier = Modifier
                        .offset(y = ALL_DAY_ROW_HEIGHT * span.lane)
                        .width(barWidth)
                        .height(ALL_DAY_ROW_HEIGHT)
                        .padding(horizontal = 1.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun AllDayBar(
    event: EventInstance,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    val soften = LocalSoftenColors.current
    val fill = eventFill(event.color, dark, soften)
    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = eventInk(fill),
        )
    }
}

@Composable
private fun Timeline(
    state: DayUiState.Success,
    scrollState: ScrollState,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
) {
    val totalHeight = HOUR_HEIGHT * 24
    val dark = isSystemInDarkTheme()
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()

    Box(modifier = Modifier.fillMaxSize()) {
        // Gutter and day column are two scroll viewports that SHARE one scroll
        // state, so they stay perfectly aligned. The day-column viewport is a
        // static, rounded-clipped window — the content scrolls inside it, so the
        // soft corners are permanent at any scroll position.
        Row(modifier = Modifier.fillMaxSize()) {
            // Hour gutter (scrolls in sync with the day column). Start inset so the
            // labels centre on the top bar hamburger, matching the week view.
            Column(
                modifier = Modifier
                    .width(GUTTER_WIDTH)
                    .padding(start = GUTTER_CONTENT_START_INSET)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            ) {
                (0 until 24).forEach { h ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HOUR_HEIGHT),
                    ) {
                        if (h > 0) {
                            Text(
                                text = formatHourLabel(h, use24Hour, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-6).dp),
                            )
                        }
                    }
                }
            }
            // Day column: rounded, clipped scroll viewport (permanent corners).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .verticalScroll(scrollState),
            ) {
                DayColumnCard(
                    blocks = state.timed,
                    dark = dark,
                    date = state.date,
                    today = state.today,
                    onEventClick = onEventClick,
                    onCreateAt = onCreateAt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight),
                )
            }
        }
    }
}

@Composable
private fun DayColumnCard(
    blocks: List<TimedBlock>,
    dark: Boolean,
    date: LocalDate,
    today: LocalDate,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    val showHourLines = LocalShowHourLines.current
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant
    Card(
        // Plain rectangular column — the soft corners come from the outer
        // rounded scroll viewport, so inner rounding would look odd at the edges.
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Faint hour separators sit over the column background but under
                // the event blocks (drawBehind paints before the children).
                .hourSeparatorLines(showHourLines, hourPx, hourLineColor)
                // Tap an empty slot to create an event there. Taps on event
                // blocks are consumed by their own click handler first, so this
                // only fires on the column background. Snaps to the tapped hour.
                .pointerInput(date) {
                    detectTapGestures { offset ->
                        val hour = (offset.y / hourPx).toInt().coerceIn(0, 23)
                        onCreateAt(date, hour * 60)
                    }
                },
        ) {
            val colWidth = maxWidth
            blocks.forEach { block ->
                val laneWidth = colWidth / block.laneCount
                val top = HOUR_HEIGHT * (block.startMin / 60f)
                val rawHeight = HOUR_HEIGHT * ((block.endMin - block.startMin) / 60f)
                val height = if (rawHeight < MIN_EVENT_HEIGHT) MIN_EVENT_HEIGHT else rawHeight
                EventBlock(
                    block = block,
                    dark = dark,
                    onClick = { onEventClick(block.event) },
                    modifier = Modifier
                        .offset(x = laneWidth * block.lane, y = top)
                        .width(laneWidth)
                        .height(height)
                        .padding(horizontal = 1.dp),
                )
            }
            // Current-time line, on top of the events, only on today's column.
            if (date == today) {
                NowLine(date = date, hourHeight = HOUR_HEIGHT)
            }
        }
    }
}

@Composable
private fun EventBlock(
    block: TimedBlock,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = block.event.title.ifBlank { stringResource(R.string.event_untitled) }
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val timeLabel = "${minToHm(block.startMin, use24Hour, locale)}–" +
        minToHm(block.endMin, use24Hour, locale)
    val showTime = block.endMin - block.startMin >= 45
    val soften = LocalSoftenColors.current
    val fill = eventFill(block.event.color, dark, soften)
    Box(
        modifier = modifier
            .background(fill, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = "$title, $timeLabel" },
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (showTime) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                color = eventInk(fill, alpha = 0.85f),
            )
            if (showTime) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun DayLoading() {
    val totalHeight = HOUR_HEIGHT * 24
    val scrollState = rememberScrollState()
    Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        Spacer(Modifier.width(GUTTER_WIDTH))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(totalHeight)
                .padding(horizontal = 2.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

private fun minToHm(min: Int, is24Hour: Boolean, locale: Locale): String =
    formatMinuteOfDay(min, is24Hour, locale)

private fun formatDayTitle(date: LocalDate, locale: Locale, currentYear: Int): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day),
        locale = locale,
        currentYear = currentYear,
        skeleton = "EEEdMMM",
    )
