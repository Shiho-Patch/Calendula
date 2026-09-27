package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSpec
import de.jeanlucmakiola.calendula.ui.common.jumpToPage
import de.jeanlucmakiola.calendula.ui.common.pagedHeight
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.LocalChipGround
import de.jeanlucmakiola.calendula.ui.common.eventPaint
import de.jeanlucmakiola.calendula.ui.common.eventSurface
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarTitleButton
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.BLOCK_OUTER_INSET
import de.jeanlucmakiola.calendula.ui.common.BLOCK_TEXT_PADDING
import de.jeanlucmakiola.calendula.ui.common.BlockTimeLabel
import de.jeanlucmakiola.calendula.ui.common.BlockTitle
import de.jeanlucmakiola.calendula.ui.common.blockTextLines
import de.jeanlucmakiola.calendula.ui.common.blockTimeLines
import de.jeanlucmakiola.calendula.ui.common.rememberBlockTextMetrics
import de.jeanlucmakiola.calendula.ui.common.animatedBlockPlacement
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.TimelineDragController
import de.jeanlucmakiola.calendula.ui.common.TimelineDragOverlay
import de.jeanlucmakiola.calendula.ui.common.TimelineDrop
import de.jeanlucmakiola.calendula.ui.common.clipOffsetMinutes
import de.jeanlucmakiola.calendula.ui.common.continuesAfter
import de.jeanlucmakiola.calendula.ui.common.continuesBefore
import de.jeanlucmakiola.calendula.ui.common.ChipCuts
import de.jeanlucmakiola.calendula.ui.common.timedBlockCuts
import de.jeanlucmakiola.calendula.ui.common.timedBlockShape
import de.jeanlucmakiola.calendula.ui.common.eventDragAllowed
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.eventTitleOverflow
import de.jeanlucmakiola.calendula.ui.common.rememberEventDragSource
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineDragController
import de.jeanlucmakiola.calendula.ui.common.startInstant
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.EventChipShape
import de.jeanlucmakiola.calendula.ui.common.trimmedLines
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.TimelineGrounds
import de.jeanlucmakiola.calendula.ui.common.ViewMorphKey
import de.jeanlucmakiola.calendula.ui.common.ViewMorphPage
import de.jeanlucmakiola.calendula.ui.common.morphDay
import de.jeanlucmakiola.calendula.ui.common.morphEvent
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineScrollState
import de.jeanlucmakiola.calendula.ui.common.morphPageActive
import de.jeanlucmakiola.calendula.ui.common.withTitleWeight
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourGrid
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.MIN_EVENT_FRACTION
import de.jeanlucmakiola.calendula.ui.common.hourHeight
import de.jeanlucmakiola.calendula.ui.common.rememberTimelinePinchZoom
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.GUTTER_CONTENT_START_INSET
import de.jeanlucmakiola.calendula.ui.common.GUTTER_WIDTH
import de.jeanlucmakiola.calendula.ui.common.HourGutter
import de.jeanlucmakiola.calendula.ui.common.TIMELINE_CONTENT_END_INSET
import de.jeanlucmakiola.calendula.ui.common.hourCellBlockInset
import de.jeanlucmakiola.calendula.ui.common.hourGridCells
import de.jeanlucmakiola.calendula.ui.common.tappedMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.time.isoWeekNumber
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** One lane of the all-day strip, sized to a trimmed bar line (#190). */
private val ALL_DAY_ROW_HEIGHT = 20.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp
/** Gap between day columns; part of the column pitch a drag maps positions through. */
private val COLUMN_GAP = 2.dp

/** Breathing room between the header block and the scrolling timeline below. */
private val TIMELINE_TOP_GAP = 8.dp
private val HEADER_TOP_PADDING = 4.dp
private val HEADER_BOTTOM_PADDING = 8.dp

/** The header's date slot, reserved whether or not it holds today's circle. */
private val DATE_SLOT_SIZE = 28.dp

/** The weekday header's height, which the day view takes off to fit the week's hours (#184). */
@Composable
internal fun weekDayHeaderHeight(): Dp {
    val label = MaterialTheme.typography.labelSmall
    val line = label.lineHeight.takeIf { it.isSp } ?: label.fontSize
    return HEADER_TOP_PADDING + with(LocalDensity.current) { line.toDp() } + 2.dp +
        DATE_SLOT_SIZE + HEADER_BOTTOM_PADDING
}

/** Total all-day strip height for a week (0 when there are no all-day events). */
internal fun WeekUiState.Success.allDayStripHeight(): Dp {
    if (allDaySpans.isEmpty()) return 0.dp
    val lanes = allDaySpans.maxOf { it.lane } + 1
    return ALL_DAY_ROW_HEIGHT * lanes + ALL_DAY_VERTICAL_PADDING * 2
}

@Composable
fun WeekScreen(
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
    viewModel: WeekViewModel = hiltViewModel(),
) = PagedTimelineScreen(
    viewModel = viewModel,
    showWeekNumber = true,
    selectedView = selectedView,
    onSelectView = onSelectView,
    onOpenDay = onOpenDay,
    onEventClick = onEventClick,
    onOpenSettings = onOpenSettings,
    onOpenSearch = onOpenSearch,
    onCreateEvent = onCreateEvent,
    quickSwitchViews = quickSwitchViews,
    drawerViewOrder = drawerViewOrder,
    todayInToolbar = todayInToolbar,
    modifier = modifier,
)

/** The multi-day view (#193): the week view's timeline over the day count from Settings. */
@Composable
fun MultiDayScreen(
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
    viewModel: MultiDayViewModel = hiltViewModel(),
) = PagedTimelineScreen(
    viewModel = viewModel,
    // A run of days often straddles two weeks, so no single number fits it.
    showWeekNumber = false,
    selectedView = selectedView,
    onSelectView = onSelectView,
    onOpenDay = onOpenDay,
    onEventClick = onEventClick,
    onOpenSettings = onOpenSettings,
    onOpenSearch = onOpenSearch,
    onCreateEvent = onCreateEvent,
    quickSwitchViews = quickSwitchViews,
    drawerViewOrder = drawerViewOrder,
    todayInToolbar = todayInToolbar,
    modifier = modifier,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedTimelineScreen(
    viewModel: PagedTimelineViewModel,
    showWeekNumber: Boolean,
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView>,
    drawerViewOrder: List<CalendarView>,
    todayInToolbar: Boolean,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val grid = position.grid
    val anchorPage = position.page
    val dimCompleted by viewModel.dimCompletedEvents.collectAsStateWithLifecycle()
    // The instant before which an event counts as completed, or null when dimming
    // is off. derivedStateOf keeps the per-minute "now" from recomposing the
    // screen while the setting is off (it stays null regardless of the tick).
    val nowState = rememberCurrentMinute()
    val dimCutoff by remember(dimCompleted) {
        derivedStateOf { if (dimCompleted) nowState.value else null }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // A new grid numbers the pages differently, so it gets a pager of its own,
    // opened straight on the anchor's page: the first day of the week changing,
    // or a multi-day jump moving another date into the first column.
    val pagerState = key(grid) {
        rememberPagerState(initialPage = anchorPage) { grid.pageCount }
    }
    val pageSpec = rememberCalendarPageSpec()
    val reduceMotion = rememberReduceMotion()
    // The pager leads and the anchor follows once it settles, so this only
    // catches the anchor moving on its own. Snapped, since that is a correction
    // rather than a move.
    LaunchedEffect(pagerState, anchorPage) {
        if (pagerState.currentPage != anchorPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(anchorPage)
        }
    }
    // The page it opened on is skipped: a grid change landing in between would
    // read it as a different page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect(viewModel::onPageSettled)
    }
    // The page under the finger, so the title turns over mid-swipe, not after it.
    val weekStart = grid.startForPage(pagerState.currentPage)
    val weekEnd = weekStart.plus(grid.dayCount - 1, kotlinx.datetime.DateTimeUnit.DAY)

    // Off the ticking clock rather than the loaded page, so it turns over at
    // midnight; derived, so the per-minute tick doesn't recompose the screen.
    val zone = remember { TimeZone.currentSystemDefault() }
    val today by remember(zone) { derivedStateOf { nowState.value.toLocalDateTime(zone).date } }
    val isOnCurrentWeek = state !is WeekUiState.Success || (today >= weekStart && today <= weekEnd)

    // Straight to the pager: a tap mid-fling must still land, and the anchor
    // hasn't caught up with the fling yet to tell the difference. A date that
    // would open mid-page on this grid re-frames the pages around it instead.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        val page = grid.pageFor(target)
        if (grid is DaysGrid && grid.startForPage(page) != target) {
            viewModel.jumpTo(target)
        } else {
            scope.launch { pagerState.jumpToPage(page, reduceMotion, pageSpec) }
        }
    }
    val jumpToToday = { jumpToDate(today) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the week swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = weekStart,
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
            modifier = modifier,
            topBar = {
                WeekTopBar(
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    currentYear = today.year,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next(quickSwitchViews)) },
                    quickSwitchViews = quickSwitchViews,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    onJumpToDate = jumpToDate,
                    showTodayButton = todayInToolbar,
                    onToday = jumpToToday,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnCurrentWeek && !todayInToolbar,
                    todayText = stringResource(R.string.week_today_action),
                    onToday = jumpToToday,
                    onCreate = {
                        // Anchor on today when it's in view, else the week's first day.
                        onCreateEvent(if (isOnCurrentWeek) today else weekStart, null)
                    },
                )
            },
        ) { innerPadding ->
            CompositionLocalProvider(LocalDimCutoff provides dimCutoff) {
                WeekContent(
                    state = state,
                    pagerState = pagerState,
                    grid = grid,
                    showWeekNumber = showWeekNumber,
                    span = viewModel::span,
                    onRetry = viewModel::goToToday,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onCreateAt = { d, minutes -> onCreateEvent(d, minutes) },
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WeekContent(
    state: WeekUiState,
    pagerState: PagerState,
    grid: PageGrid,
    showWeekNumber: Boolean,
    span: (DaySpan) -> StateFlow<WeekUiState>,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = rememberCalendarFadeSpec()

    // Shared by every page and the gutter, so the vertical scroll position
    // survives week-to-week swipes (e.g. 18:00 stays centred), and carried over
    // from the timeline view before this one.
    val stripHeight = (state as? WeekUiState.Success)?.allDayStripHeight() ?: 0.dp
    val scrollState = rememberTimelineScrollState(weekDayHeaderHeight() + stripHeight + TIMELINE_TOP_GAP)

    // Above the pager: a page change mid-drag would strand the floating block
    // inside the outgoing page.
    val dragController = rememberTimelineDragController()
    val move = LocalEventMove.current
    val zone = remember { TimeZone.currentSystemDefault() }
    val onDrop: (TimelineDrop) -> Unit = { drop ->
        move?.move(
            MoveRequest(
                eventId = drop.event.eventId,
                beginMillis = drop.event.start.toEpochMilliseconds(),
                endMillis = drop.event.end.toEpochMilliseconds(),
                target = MoveTarget.Start(drop.startInstant(zone)),
            ),
        )
    }

    Box(modifier = modifier) {
        // Loading and failure concern the provider as a whole, so they stand in
        // for the pager; a page still loading its own week handles that itself.
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it::class },
            transitionSpec = { fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec)) },
            label = "week-state",
        ) { s ->
            when (s) {
                WeekUiState.Loading -> WeekLoading(dayCount = grid.dayCount)
                is WeekUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
                is WeekUiState.Success -> WeekPager(
                    pagerState = pagerState,
                    grid = grid,
                    showWeekNumber = showWeekNumber,
                    span = span,
                    today = s.today,
                    initialStripHeight = s.allDayStripHeight(),
                    scrollState = scrollState,
                    dragController = dragController,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onCreateAt = onCreateAt,
                    onDrop = onDrop,
                )
            }
        }
        TimelineDragOverlay(dragController)
    }
}

/**
 * The weeks as pages that follow the finger and snap once a swipe passes half a
 * page or is flung (#131), beside a gutter that stays put.
 */
@Composable
private fun WeekPager(
    pagerState: PagerState,
    grid: PageGrid,
    showWeekNumber: Boolean,
    span: (DaySpan) -> StateFlow<WeekUiState>,
    today: LocalDate,
    initialStripHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    val stripHeights = remember { mutableStateMapOf<Int, Dp>() }
    val allDayHeight = pagedHeight(pagerState, stripHeights, initialStripHeight)
    WeekFrame(
        weekStart = grid.startForPage(pagerState.currentPage),
        showWeekNumber = showWeekNumber,
        allDayHeight = allDayHeight,
        scrollState = scrollState,
        dragController = dragController,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The weeks either side are composed ahead, so a swipe lands on a
            // loaded week rather than watching it fill in.
            beyondViewportPageCount = 1,
            // A held block moves within its week; the page stays put under it.
            userScrollEnabled = !dragController.isDragging,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = rememberCalendarPageSpec(),
            ),
        ) { page ->
            val pageSpan = DaySpan(grid.startForPage(page), grid.dayCount)
            val pageState by remember(pageSpan) { span(pageSpan) }.collectAsStateWithLifecycle()
            val loaded = pageState as? WeekUiState.Success
            // Until its days arrive a page shows their frame without events.
            val empty = remember(pageSpan, today) { emptySpan(pageSpan, today) }
            val stripHeight = loaded?.allDayStripHeight()
            DisposableEffect(page, stripHeight) {
                if (stripHeight != null) stripHeights[page] = stripHeight
                onDispose { stripHeights.remove(page) }
            }
            ViewMorphPage(active = morphPageActive(page, pagerState.settledPage)) {
                WeekPage(
                    state = loaded ?: empty,
                    allDayHeight = allDayHeight,
                    scrollState = scrollState,
                    dragController = dragController,
                    active = page == pagerState.currentPage,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onCreateAt = onCreateAt,
                    onDrop = onDrop,
                )
            }
        }
    }
}

/** A page with no events yet, for one whose days are still loading. */
private fun emptySpan(span: DaySpan, today: LocalDate): WeekUiState.Success {
    val days = (0 until span.count).map { span.start.plus(it, kotlinx.datetime.DateTimeUnit.DAY) }
    return WeekUiState.Success(
        weekStart = span.start,
        today = today,
        days = days,
        allDaySpans = emptyList(),
        timedByDay = emptyMap(),
    )
}

/**
 * What stays put while the weeks swipe: the week-number badge and the hour
 * gutter, with [pages] filling the rest.
 */
@Composable
private fun WeekFrame(
    weekStart: LocalDate,
    showWeekNumber: Boolean,
    allDayHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    pages: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                WeekNumberCell(weekStart, showBadge = showWeekNumber)
                Spacer(Modifier.height(allDayHeight))
            }
            Spacer(Modifier.height(TIMELINE_TOP_GAP))
            // Resolves the hour height off the same viewport height the pages'
            // timelines have, so labels and columns agree.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val zoom = LocalTimelineZoom.current
                val fitHeight = maxHeight + allDayHeight
                val hourHeight = zoom.scale.hourHeight(fitHeight)
                HourGutter(
                    scrollState = scrollState,
                    hourHeight = hourHeight,
                    dragController = dragController,
                    modifier = rememberTimelinePinchZoom(scrollState, fitHeight, hourHeight, zoom),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { pages() }
    }
}

/** A single week in its frame, without the pager — for the Settings preview. */
@Composable
internal fun WeekSuccess(
    state: WeekUiState.Success,
    showWeekNumber: Boolean = true,
    scrollState: ScrollState,
    allDayHeight: Dp,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    WeekFrame(
        weekStart = state.weekStart,
        showWeekNumber = showWeekNumber,
        allDayHeight = allDayHeight,
        scrollState = scrollState,
        dragController = dragController,
    ) {
        WeekPage(
            state = state,
            allDayHeight = allDayHeight,
            scrollState = scrollState,
            dragController = dragController,
            active = true,
            onEventClick = onEventClick,
            onOpenDay = onOpenDay,
            onCreateAt = onCreateAt,
            onDrop = onDrop,
        )
    }
}

/**
 * One week's page: header, all-day strip and day columns.
 *
 * @param active whether this is the page on screen, the only one that may
 *   publish the drag geometry.
 */
@Composable
private fun WeekPage(
    state: WeekUiState.Success,
    allDayHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    active: Boolean,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            WeekDayHeader(days = state.days, today = state.today, onOpenDay = onOpenDay)
            // This strip is painted on `surface`, so an outlined chip has to
            // fill with that and not with the columns' container (#230).
            CompositionLocalProvider(
                LocalChipGround provides MaterialTheme.colorScheme.surface,
            ) {
                AllDayStrip(state = state, height = allDayHeight, onEventClick = onEventClick)
            }
        }
        Spacer(Modifier.height(TIMELINE_TOP_GAP))
        Timeline(
            state = state,
            allDayHeight = allDayHeight,
            scrollState = scrollState,
            dragController = dragController,
            active = active,
            onEventClick = onEventClick,
            onCreateAt = onCreateAt,
            onDrop = onDrop,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTopBar(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    currentYear: Int,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    quickSwitchViews: List<CalendarView>,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
) {
    val locale = currentLocale()
    val (title, shortTitle) = remember(weekStart, weekEnd, locale, currentYear) {
        formatWeekTitle(weekStart, weekEnd, locale, currentYear) to
            formatWeekTitle(weekStart, weekEnd, locale, currentYear, abbreviated = true)
    }
    TopAppBar(
        title = {
            CalendarTitleButton(
                title = title,
                currentDate = weekStart,
                onJumpToDate = onJumpToDate,
                shortTitle = shortTitle,
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
        // Deliberately flat: M3 lifts the bar to mark content scrolling under
        // it, but here the bar meets the header on the same surface and the
        // tint is what makes that seam look like a separate block (#186).
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun WeekDayHeader(
    days: List<LocalDate>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HEADER_TOP_PADDING, bottom = HEADER_BOTTOM_PADDING, end = TIMELINE_CONTENT_END_INSET),
    ) {
        days.forEach { date ->
            val javaDow = java.time.DayOfWeek.of(date.dayOfWeek.ordinal + 1)
            val isToday = date == today
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenDay(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One line, as the gutter cell beside it assumes (see WeekNumberCell).
                Text(
                    text = javaDow.getDisplayName(JavaTextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                // Always reserve the circle slot so the header height is
                // identical whether or not the week contains today.
                Box(
                    modifier = Modifier.size(DATE_SLOT_SIZE),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isToday) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.morphDay(date).fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = date.day.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = date.day.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.morphDay(date),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The header's gutter cell, laid out like a day's (empty weekday line, spacer,
 * date slot) so the badge lines up with the date numbers and the cell is exactly
 * as tall as the header beside it. The start inset centres the badge on the top
 * bar's hamburger (see GUTTER_CONTENT_START_INSET).
 */
@Composable
private fun WeekNumberCell(weekStart: LocalDate, showBadge: Boolean) {
    val weekNumber = remember(weekStart) { weekStart.toJavaLocalDate().isoWeekNumber() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = GUTTER_CONTENT_START_INSET,
                top = HEADER_TOP_PADDING,
                bottom = HEADER_BOTTOM_PADDING,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = " ", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Box(modifier = Modifier.height(DATE_SLOT_SIZE), contentAlignment = Alignment.TopCenter) {
            if (showBadge) WeekNumberBadge(weekNumber = weekNumber)
        }
    }
}

/** Calendar-week badge shown in the header gutter, deliberately set apart with a
 *  filled box and bold number — at the month grid's size, so the two agree (#213). */
@Composable
private fun WeekNumberBadge(weekNumber: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.week_number_label)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.semantics { contentDescription = "$label $weekNumber" },
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun AllDayStrip(
    state: WeekUiState.Success,
    height: Dp,
    onEventClick: (EventInstance) -> Unit,
) {
    val dark = isSystemInDarkTheme()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Height is hoisted + animated so it slides and resizes smoothly;
            // padding sits inside it so the content area is lanes * row height.
            .height(height)
            .padding(
                top = ALL_DAY_VERTICAL_PADDING,
                bottom = ALL_DAY_VERTICAL_PADDING,
                end = TIMELINE_CONTENT_END_INSET,
            ),
    ) {
        // Span bars are positioned absolutely so a multi-day event is one
        // connected bar across columns rather than a chip per day. clipToBounds
        // keeps bars from spilling out while the height animates.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            val colWidth = maxWidth / state.days.size
            state.allDaySpans.forEach { span ->
                val spanCols = span.endCol - span.startCol + 1
                AllDayBar(
                    event = span.event,
                    dark = dark,
                    onClick = { onEventClick(span.event) },
                    modifier = Modifier
                        .offset(
                            x = colWidth * span.startCol,
                            y = ALL_DAY_ROW_HEIGHT * span.lane,
                        )
                        .morphEvent(ViewMorphKey.event(span.event, state.days[span.startCol]))
                        .width(colWidth * spanCols)
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
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && event.hasEnded(dimCutoff)
    val paint = eventPaint(event, dark)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .eventSurface(paint, EventChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        val titleOverflow = eventTitleOverflow()
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.trimmedLines(),
            maxLines = 1,
            overflow = titleOverflow.overflow,
            softWrap = titleOverflow.softWrap,
            color = paint.titleInk,
            fontWeight = paint.titleWeight,
            textDecoration = paint.decoration,
        )
    }
}

@Composable
private fun Timeline(
    state: WeekUiState.Success,
    allDayHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    active: Boolean,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val zoom = LocalTimelineZoom.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // What the hour grid takes off a block's ends, for the floating copy to
    // take off its own. Per-end, a cut edge keeps it — that much the drag
    // geometry decides itself, from the slice it is drawing.
    val blockInsetPx = with(density) {
        hourCellBlockInset(LocalShowHourGrid.current, cut = false).toPx()
    }

    // BoxWithConstraints rather than Box: the fit-the-whole-day scale needs the
    // timeline's own viewport height, which is only known here — below the top
    // bar, day header and all-day strip. The strip is given back, so the scale
    // holds while paging and matches the day view's (#184).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fitHeight = maxHeight + allDayHeight
        val hourHeight = zoom.scale.hourHeight(fitHeight)
        val totalHeight = hourHeight * 24
        // The pinch sits above the scroll viewport: it has to outrank the
        // vertical scroll, and it does that by watching the initial pass, which
        // only reaches it if it is the viewport's ancestor.
        val pinch = rememberTimelinePinchZoom(scrollState, fitHeight, hourHeight, zoom)
        TimelineGrounds(
            days = state.days,
            scrollState = scrollState,
            hourHeight = hourHeight,
            columnGap = COLUMN_GAP,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = TIMELINE_CONTENT_END_INSET)
                .clip(RoundedCornerShape(16.dp)),
        )
        // Scrolls on the same state as the gutter and the other pages, so they
        // all stay aligned. A static, rounded-clipped window — the content
        // scrolls inside it, so the soft corners are permanent at any scroll
        // position (not just at the day's start/end).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pinch)
                .padding(end = TIMELINE_CONTENT_END_INSET)
                .clip(RoundedCornerShape(16.dp))
                .verticalScroll(scrollState)
                .onGloballyPositioned { if (active) dragController.geometry.viewport = it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    // The scrolling content itself, so its root position
                    // already folds in the scroll offset. Only the page on
                    // screen publishes it: the ones either side are laid out too.
                    .onGloballyPositioned { coords ->
                        if (!active) return@onGloballyPositioned
                        val gap = with(density) { COLUMN_GAP.toPx() }
                        dragController.geometry.let {
                            it.grid = coords
                            it.scroll = scrollState
                            it.hourPx = with(density) { hourHeight.toPx() }
                            it.blockInsetPx = blockInsetPx
                            it.columnGapPx = gap
                            it.columnWidthPx = (coords.size.width + gap) / state.days.size
                            it.days = state.days
                            it.isRtl = isRtl
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
            ) {
                state.days.forEach { day ->
                    DayColumnCard(
                        blocks = state.timedByDay[day].orEmpty(),
                        dark = dark,
                        date = day,
                        today = state.today,
                        hourHeight = hourHeight,
                        dragController = dragController,
                        onEventClick = onEventClick,
                        onCreateAt = onCreateAt,
                        onDrop = onDrop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
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
    hourHeight: Dp,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    val showHourGrid = LocalShowHourGrid.current
    val zone = remember { TimeZone.currentSystemDefault() }
    // Tells a settled drop when this column has caught up with it.
    LaunchedEffect(blocks, dragController.settling) {
        dragController.noteGrid(date, blocks)
    }
    // The tap detector outlives the composition that installed it — a pinch or a
    // Settings change moves the hour height without restarting it — so it reads
    // the height and the callback through state handles instead of capturing
    // them, or taps land on the scale the column had before the zoom (#148).
    val currentHourPx = rememberUpdatedState(hourPx)
    val currentOnCreateAt = rememberUpdatedState(onCreateAt)
    Card(
        // Plain rectangular columns — the soft corners come from the outer
        // rounded scroll viewport, so inner rounding would look odd at the edges.
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            // With the grid on, the container colour moves onto the hour cells
            // and the column behind them recedes to surface, so the gap between
            // two cells reads as the same negative space that separates the
            // week's columns.
            containerColor = if (showHourGrid) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // The hour cells sit over the column background but under the
                // event blocks (drawBehind paints before the children).
                .hourGridCells(showHourGrid, hourPx, MaterialTheme.colorScheme.surfaceContainer)
                // Tap an empty slot to create an event there; taps on event
                // blocks are consumed by their own handler first. Snaps to hour.
                .pointerInput(date) {
                    detectTapGestures { offset ->
                        currentOnCreateAt.value(
                            date,
                            tappedMinuteOfDay(offset.y, currentHourPx.value),
                        )
                    }
                },
        ) {
            val colWidth = maxWidth
            val minEventHeight = hourHeight * MIN_EVENT_FRACTION
            // Keyed by event, so a block that changes time or lane is the *same*
            // composable afterwards and tweens there. The ordinal disambiguates
            // a column holding two occurrences of one series.
            val ordinals = mutableMapOf<Long, Int>()
            blocks.forEach { block ->
                val ordinal = ordinals.merge(block.event.eventId, 1, Int::plus)!! - 1
                key(block.event.eventId, ordinal) {
                    val laneWidth = colWidth / block.laneCount
                    val top = hourHeight * (block.startMin / 60f)
                    val rawHeight = hourHeight * ((block.endMin - block.startMin) / 60f)
                    val height = if (rawHeight < minEventHeight) minEventHeight else rawHeight
                    val place = animatedBlockPlacement(
                        x = laneWidth * block.lane,
                        y = top,
                        width = laneWidth,
                        height = height,
                    )
                    // Where this block is cut at midnight, which decides both
                    // its shape and the half-gap it gives up to the hour cells.
                    val cuts = remember(block, date, zone) {
                        timedBlockCuts(
                            block.continuesBefore(date, zone),
                            block.continuesAfter(date, zone),
                        )
                    }
                    val topInset = hourCellBlockInset(showHourGrid, cuts.top)
                    val bottomInset = hourCellBlockInset(showHourGrid, cuts.bottom)
                    val blockHeight = (place.height - topInset - bottomInset)
                        .coerceAtLeast(0.dp)
                    EventBlock(
                        block = block,
                        dark = dark,
                        height = blockHeight,
                        width = place.width,
                        date = date,
                        cuts = cuts,
                        topInset = topInset,
                        dragController = dragController,
                        onClick = { onEventClick(block.event) },
                        onDrop = onDrop,
                        modifier = Modifier
                            .offset(x = place.x, y = place.y + topInset)
                            .morphEvent(ViewMorphKey.event(block.event, date))
                            .width(place.width)
                            .height(blockHeight)
                            .padding(horizontal = BLOCK_OUTER_INSET),
                    )
                }
            }
            // Current-time line, on top of the events, only on today's column.
            if (date == today) {
                NowLine(date = date, hourHeight = hourHeight)
            }
        }
    }
}

@Composable
private fun EventBlock(
    block: TimedBlock,
    dark: Boolean,
    height: Dp,
    width: Dp,
    date: LocalDate,
    cuts: ChipCuts,
    topInset: Dp,
    dragController: TimelineDragController,
    onClick: () -> Unit,
    onDrop: (TimelineDrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = block.event.title.ifBlank { stringResource(R.string.event_untitled) }
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val timeLabel = "${minToHm(block.startMin, use24Hour, locale)}–" +
        minToHm(block.endMin, use24Hour, locale)
    val density = LocalDensity.current
    val metrics = rememberBlockTextMetrics(height)
    // Only full-width (non-overlapping) blocks that are tall enough show the
    // time. On narrow overlapping columns we drop it so the title can wrap to
    // fill the whole block, mirroring Google Calendar — and a block that cannot
    // afford both lines spends its space on the title. Height decides that on
    // its own: a duration threshold would keep hiding the time on a half-hour
    // block the user has pinched open to three times the room it needs.
    val showTime = block.laneCount == 1 &&
        metrics.available >= metrics.titleLine + metrics.timeLine
    val textWidth = width - (BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING) * 2
    // A short block drops the title rather than serving a horizontally sliced
    // one: half a letter reads as a rendering fault, while a bare colour chip
    // reads as what it is — an event too brief to label. Tap still opens it, and
    // the semantics description carries the full title either way.
    val showTitle = metrics.fitsTitle
    // The title is served first, out of everything the block has left once the
    // time is down to one line — but only takes the lines it will actually use,
    // and only wraps at all once a line is wide enough to hold more than a
    // syllable. Below that the extra lines just stack fragments of the word.
    val contentHeight = metrics.available - if (showTime) metrics.timeLine else 0.dp
    val titleBudget = metrics.titleBudget(contentHeight).coerceAtLeast(1)
    val paint = eventPaint(block.event, dark)
    // Every line the height affords, however narrow the lane: two events side by
    // side leave columns well under a word wide, and cutting the title to one
    // line there lost it outright where the block had the room to wrap it.
    val titleMaxLines = blockTextLines(
        text = title,
        // At the weight BlockTitle will set it in, or an invited block — drawn a
        // weight lighter — is budgeted a line it never fills (#230).
        style = MaterialTheme.typography.labelMedium.withTitleWeight(paint),
        textWidth = textWidth,
        max = titleBudget,
    )
    val spare = metrics.available - metrics.titleHeight(titleMaxLines) -
        if (showTime) metrics.timeLine else 0.dp
    val timeMaxLines = if (showTime) blockTimeLines(timeLabel, textWidth, spare) else 1
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && block.event.hasEnded(dimCutoff)
    val zone = remember { TimeZone.currentSystemDefault() }
    val moveAction = eventMoveAction(block.event)
    val draggable = eventDragAllowed(block.event)
    // The drop takes this offset back off, so a tail clipped at midnight lands
    // where the event's own start belongs (#253).
    val clipOffset = remember(block, date, zone) { block.clipOffsetMinutes(date, zone) }
    val shape = remember(cuts) { timedBlockShape(cuts.top, cuts.bottom) }
    val topInsetPx = with(density) { topInset.toPx() }
    val dragModifier = rememberEventDragSource(
        enabled = draggable,
        key = block.event.instanceId,
        onPickUp = { pointer, blockRoot, _ ->
            dragController.begin(
                block = block,
                clipOffsetMin = clipOffset,
                titleLines = if (showTitle) titleMaxLines else 0,
                pointerInRoot = pointer,
                // Measured off the block's placement rather than off where the
                // hour grid seats it: the copy is drawn from that placement and
                // re-seated the same half-gap down (#267).
                blockInRoot = blockRoot.copy(y = blockRoot.y - topInsetPx),
            )
        },
        onMove = dragController::move,
        onDrop = { dragController.finish()?.let(onDrop) },
        onCancel = dragController::cancel,
    )
    val lifted = draggable && dragController.ghosts(block)
    val ghost = ghostAlpha(lifted)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            // The source stays put as a ghost while its floating copy travels.
            .then(if (ghost < 1f) Modifier.alpha(ghost) else Modifier)
            .eventSurface(paint, shape, cuts)
            .clickable(onClick = onClick)
            // After clickable, so it is the inner node and wins the main pass;
            // the tap still works, since a drag consumes the up.
            .then(dragModifier)
            .padding(horizontal = BLOCK_TEXT_PADDING, vertical = metrics.inset)
            .semantics {
                contentDescription = "$title, $timeLabel"
                if (moveAction != null) customActions = listOf(moveAction)
            },
    ) {
        Column {
            if (showTitle) {
                BlockTitle(
                    title = title,
                    maxLines = titleMaxLines,
                    color = paint.titleInk,
                    textDecoration = paint.decoration,
                    fontWeight = paint.titleWeight,
                )
            }
            if (showTime) {
                BlockTimeLabel(
                    label = timeLabel,
                    color = paint.secondaryInk,
                    maxLines = timeMaxLines,
                )
            }
        }
    }
}

private val SKELETON_HEADER_HEIGHT = 36.dp
private val SKELETON_HEADER_PADDING = 8.dp

@Composable
private fun WeekLoading(dayCount: Int) {
    val scale = LocalTimelineZoom.current.scale
    val scrollState = rememberScrollState()
    val headerHeight = weekDayHeaderHeight()
    Column(modifier = Modifier.fillMaxSize()) {
        // Header skeleton
        Row(modifier = Modifier.fillMaxWidth().padding(SKELETON_HEADER_PADDING)) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            repeat(dayCount) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(SKELETON_HEADER_HEIGHT)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Same scale resolution as the loaded timeline, so the skeleton's
            // columns don't resize the moment the real week arrives.
            val fitHeight = maxHeight + SKELETON_HEADER_HEIGHT + SKELETON_HEADER_PADDING * 2 -
                headerHeight - TIMELINE_TOP_GAP
            val hourHeight = scale.hourHeight(fitHeight)
            val totalHeight = hourHeight * 24
            // The skeleton wears the loaded columns' own ground, so the arrival
            // of the real week doesn't flash solid blocks into a gapped grid.
            val showHourGrid = LocalShowHourGrid.current
            val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
            val columnGround = if (showHourGrid) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceContainer
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = TIMELINE_CONTENT_END_INSET)
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.width(GUTTER_WIDTH))
                repeat(dayCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight)
                            .padding(horizontal = 2.dp)
                            .background(columnGround)
                            .hourGridCells(
                                showHourGrid,
                                hourPx,
                                MaterialTheme.colorScheme.surfaceContainer,
                            ),
                    )
                }
            }
        }
    }
}

private fun minToHm(min: Int, is24Hour: Boolean, locale: java.util.Locale): String =
    formatMinuteOfDay(min, is24Hour, locale)

/**
 * The week's title: just the month [weekStart] falls in.
 *
 * The day numbers are already in the column headers directly below, so spelling
 * out "13.–19. Jul" restates them in the widest string in the bar. Naming the
 * month of [weekStart] means a week straddling a boundary keeps the outgoing
 * month until it is fully gone — a week is seven contiguous days, so the earlier
 * month has a day in it exactly while [weekStart] is still inside it. That also
 * makes the *month* depend on nothing but [weekStart], so it cannot drift with
 * the direction you paged in from.
 *
 * The *year* is decided from the whole week, not just its start: a week running
 * Dec 28 – Jan 3 has four of its seven visible columns in the new year, so
 * "December" with no year would be actively misleading while the reader is
 * looking straight at January dates.
 */
private fun formatWeekTitle(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    locale: Locale,
    currentYear: Int,
    abbreviated: Boolean = false,
): String {
    return formatCalendarTitle(
        date = java.time.LocalDate.of(weekStart.year, weekStart.month.ordinal + 1, 1),
        locale = locale,
        currentYear = currentYear,
        skeleton = if (abbreviated) "LLL" else "LLLL",
        forceYear = weekEnd.year != weekStart.year,
    )
}
