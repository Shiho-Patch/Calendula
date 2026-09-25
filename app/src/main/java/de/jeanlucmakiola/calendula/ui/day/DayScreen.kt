package de.jeanlucmakiola.calendula.ui.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import de.jeanlucmakiola.calendula.ui.common.jumpToPage
import de.jeanlucmakiola.calendula.ui.common.pagedStripHeight
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSpec
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
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
import de.jeanlucmakiola.calendula.ui.common.BLOCK_OUTER_INSET
import de.jeanlucmakiola.calendula.ui.common.BLOCK_TEXT_PADDING
import de.jeanlucmakiola.calendula.ui.common.BlockTimeLabel
import de.jeanlucmakiola.calendula.ui.common.BlockTitle
import de.jeanlucmakiola.calendula.ui.common.rememberBlockTextMetrics
import de.jeanlucmakiola.calendula.ui.common.blockTimeLines
import de.jeanlucmakiola.calendula.ui.common.animatedBlockPlacement
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.NowLine
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
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.EventChipShape
import de.jeanlucmakiola.calendula.ui.common.trimmedLines
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourGrid
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.MIN_EVENT_FRACTION
import de.jeanlucmakiola.calendula.ui.common.hourHeight
import de.jeanlucmakiola.calendula.ui.common.rememberTimelinePinchZoom
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.GUTTER_WIDTH
import de.jeanlucmakiola.calendula.ui.common.HourGutter
import de.jeanlucmakiola.calendula.ui.common.TIMELINE_CONTENT_END_INSET
import de.jeanlucmakiola.calendula.ui.common.hourCellBlockInset
import de.jeanlucmakiola.calendula.ui.common.hourGridCells
import de.jeanlucmakiola.calendula.ui.common.tappedMinuteOfDay
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

/** One lane of the all-day strip, sized to a trimmed bar line (#190). */
private val ALL_DAY_ROW_HEIGHT = 20.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp

/** Breathing room between the all-day strip and the scrolling timeline below. */
private val TIMELINE_TOP_GAP = 8.dp

/** Total all-day strip height for the day (0 when there are no all-day events). */
internal fun DayUiState.Success.allDayStripHeight(): Dp {
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
    val anchorPage by viewModel.anchorPage.collectAsStateWithLifecycle()

    // When opened from the month grid, anchor to the tapped date.
    LaunchedEffect(initialDateIso) {
        initialDateIso?.let { viewModel.goToDate(LocalDate.parse(it)) }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Opens on the tapped date directly, rather than on today and then correcting.
    val pagerState = rememberPagerState(
        initialPage = initialDateIso?.let { dayPageFor(LocalDate.parse(it)) } ?: anchorPage,
    ) { DAY_PAGE_COUNT }
    val pageSpec = rememberCalendarPageSpec()
    val reduceMotion = rememberReduceMotion()
    // The pager leads and the anchor follows once it settles, so the anchor only
    // moves on its own to correct the pager — e.g. re-entry from the month grid
    // on another date. Snapped, since that is a correction rather than a move.
    LaunchedEffect(anchorPage) {
        if (pagerState.currentPage != anchorPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(anchorPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect(viewModel::onPageSettled)
    }
    // The day under the finger, so the title turns over mid-swipe, not after it.
    val date = dayForPage(pagerState.currentPage)

    // Off the ticking clock rather than the loaded day, so it turns over at
    // midnight; derived, so the per-minute tick doesn't recompose the screen.
    val nowState = rememberCurrentMinute()
    val zone = remember { TimeZone.currentSystemDefault() }
    val today by remember(zone) { derivedStateOf { nowState.value.toLocalDateTime(zone).date } }
    val isOnToday = state !is DayUiState.Success || date == today

    // Straight to the pager: a tap mid-fling must still land, and the anchor
    // hasn't caught up with the fling yet to tell the difference.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        scope.launch { pagerState.jumpToPage(dayPageFor(target), reduceMotion, pageSpec) }
    }
    val jumpToToday = { jumpToDate(today) }

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
            modifier = modifier,
            topBar = {
                DayTopBar(
                    date = date,
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
                    todayVisible = !isOnToday && !todayInToolbar,
                    todayText = stringResource(R.string.day_today_action),
                    onToday = jumpToToday,
                    onCreate = { onCreateEvent(date, null) },
                )
            },
        ) { innerPadding ->
            DayContent(
                state = state,
                pagerState = pagerState,
                day = viewModel::day,
                onRetry = viewModel::goToToday,
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
    pagerState: PagerState,
    day: (LocalDate) -> StateFlow<DayUiState>,
    onRetry: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = rememberCalendarFadeSpec()

    // Shared by every page and the gutter, so the vertical scroll position
    // survives day-to-day swipes. We only centre on noon once, on first entry
    // into the day view (i.e. when arriving from the month/week view).
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        // Half the scroll range *is* noon: the content spans a full 24 hours, so
        // centring the range centres midday at whatever hour height is in force.
        scrollState.scrollTo(scrollState.maxValue / 2)
    }

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
        // for the pager; a page still loading its own day handles that itself.
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize(),
            contentKey = { it::class },
            transitionSpec = { fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec)) },
            label = "day-state",
        ) { s ->
            when (s) {
                DayUiState.Loading -> DayLoading()
                is DayUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
                is DayUiState.Success -> DayPager(
                    pagerState = pagerState,
                    day = day,
                    today = s.today,
                    initialStripHeight = s.allDayStripHeight(),
                    scrollState = scrollState,
                    dragController = dragController,
                    onEventClick = onEventClick,
                    onCreateAt = onCreateAt,
                    onDrop = onDrop,
                )
            }
        }
        TimelineDragOverlay(dragController)
    }
}

/**
 * The days as pages that follow the finger and snap once a swipe passes half a
 * page or is flung (#336), beside a gutter that stays put.
 */
@Composable
private fun DayPager(
    pagerState: PagerState,
    day: (LocalDate) -> StateFlow<DayUiState>,
    today: LocalDate,
    initialStripHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    val stripHeights = remember { mutableStateMapOf<Int, Dp>() }
    val allDayHeight = pagedStripHeight(pagerState, stripHeights, initialStripHeight)
    DayFrame(allDayHeight = allDayHeight, scrollState = scrollState, dragController = dragController) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The days either side are composed ahead, so a swipe lands on a
            // loaded day rather than watching it fill in.
            beyondViewportPageCount = 1,
            // A held block moves within its day; the page stays put under it.
            userScrollEnabled = !dragController.isDragging,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = rememberCalendarPageSpec(),
            ),
        ) { page ->
            val date = dayForPage(page)
            val pageState by remember(date) { day(date) }.collectAsStateWithLifecycle()
            val loaded = pageState as? DayUiState.Success
            // Until its day arrives a page shows the empty column.
            val empty = remember(date, today) {
                DayUiState.Success(date = date, today = today, allDay = emptyList(), timed = emptyList())
            }
            val stripHeight = loaded?.allDayStripHeight()
            DisposableEffect(page, stripHeight) {
                if (stripHeight != null) stripHeights[page] = stripHeight
                onDispose { stripHeights.remove(page) }
            }
            DayPage(
                state = loaded ?: empty,
                allDayHeight = allDayHeight,
                scrollState = scrollState,
                dragController = dragController,
                active = page == pagerState.currentPage,
                onEventClick = onEventClick,
                onCreateAt = onCreateAt,
                onDrop = onDrop,
            )
        }
    }
}

/** What stays put while the days swipe: the hour gutter, with [pages] filling the rest. */
@Composable
private fun DayFrame(
    allDayHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    pages: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(allDayHeight)
                    .background(MaterialTheme.colorScheme.surface),
            )
            Spacer(Modifier.height(TIMELINE_TOP_GAP))
            // Resolves the hour height off the same viewport height the pages'
            // timelines have, so labels and column agree.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val zoom = LocalTimelineZoom.current
                val hourHeight = zoom.scale.hourHeight(maxHeight)
                // Start inset so the labels centre on the top bar hamburger,
                // matching the week view.
                HourGutter(
                    scrollState = scrollState,
                    hourHeight = hourHeight,
                    dragController = dragController,
                    modifier = rememberTimelinePinchZoom(scrollState, maxHeight, hourHeight, zoom),
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { pages() }
    }
}

/** A single day in its frame, without the pager — for the Settings preview. */
@Composable
internal fun DaySuccess(
    state: DayUiState.Success,
    scrollState: ScrollState,
    allDayHeight: Dp,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    DayFrame(allDayHeight = allDayHeight, scrollState = scrollState, dragController = dragController) {
        DayPage(
            state = state,
            allDayHeight = allDayHeight,
            scrollState = scrollState,
            dragController = dragController,
            active = true,
            onEventClick = onEventClick,
            onCreateAt = onCreateAt,
            onDrop = onDrop,
        )
    }
}

/**
 * One day's page: all-day strip and column.
 *
 * @param active whether this is the page on screen, the only one that may
 *   publish the drag geometry.
 */
@Composable
private fun DayPage(
    state: DayUiState.Success,
    allDayHeight: Dp,
    scrollState: ScrollState,
    dragController: TimelineDragController,
    active: Boolean,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // All-day strip collapses to nothing when the day has no all-day events,
        // so the timeline sits directly under the app bar.
        // This strip is painted on `surface`, so an outlined chip has to fill
        // with that and not with the column's container (#230).
        CompositionLocalProvider(
            LocalChipGround provides MaterialTheme.colorScheme.surface,
        ) {
            AllDayStrip(
                state = state,
                height = allDayHeight,
                onEventClick = onEventClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        Spacer(Modifier.height(TIMELINE_TOP_GAP))
        Timeline(
            state = state,
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
private fun DayTopBar(
    date: LocalDate,
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
    val (title, shortTitle) = remember(date, locale, currentYear) {
        formatDayTitle(date, locale, currentYear) to
            formatDayTitle(date, locale, currentYear, abbreviated = true)
    }
    TopAppBar(
        title = {
            CalendarTitleButton(
                title = title,
                currentDate = date,
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
            .padding(
                top = ALL_DAY_VERTICAL_PADDING,
                bottom = ALL_DAY_VERTICAL_PADDING,
                end = TIMELINE_CONTENT_END_INSET,
            ),
    ) {
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
    val paint = eventPaint(event, dark)
    Box(
        modifier = modifier
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
    state: DayUiState.Success,
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
    // bar, date header and all-day strip.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hourHeight = zoom.scale.hourHeight(maxHeight)
        val totalHeight = hourHeight * 24
        // The pinch sits above the scroll viewport: it has to outrank the
        // vertical scroll, and it does that by watching the initial pass, which
        // only reaches it if it is the viewport's ancestor.
        val pinch = rememberTimelinePinchZoom(scrollState, maxHeight, hourHeight, zoom)
        // Scrolls on the same state as the gutter and the other pages, so they
        // all stay aligned. A static, rounded-clipped window — the content
        // scrolls inside it, so the soft corners are permanent at any scroll
        // position.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(pinch)
                .padding(end = TIMELINE_CONTENT_END_INSET)
                .clip(RoundedCornerShape(16.dp))
                .verticalScroll(scrollState)
                .onGloballyPositioned { if (active) dragController.geometry.viewport = it },
        ) {
            DayColumnCard(
                blocks = state.timed,
                dark = dark,
                date = state.date,
                today = state.today,
                hourHeight = hourHeight,
                dragController = dragController,
                onEventClick = onEventClick,
                onCreateAt = onCreateAt,
                onDrop = onDrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
                    // The scrolling content itself, so its root position
                    // already folds in the scroll offset. Only the page on
                    // screen publishes it: the ones either side are laid out too.
                    .onGloballyPositioned { coords ->
                        if (!active) return@onGloballyPositioned
                        dragController.geometry.let {
                            it.grid = coords
                            it.scroll = scrollState
                            it.hourPx = with(density) { hourHeight.toPx() }
                            it.blockInsetPx = blockInsetPx
                            it.columnGapPx = 0f
                            it.columnWidthPx = coords.size.width.toFloat()
                            it.days = listOf(state.date)
                            it.isRtl = isRtl
                        }
                    },
            )
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
        // Plain rectangular column — the soft corners come from the outer
        // rounded scroll viewport, so inner rounding would look odd at the edges.
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            // With the grid on, the container colour moves onto the hour cells
            // and the column behind them recedes to surface, so an hour boundary
            // reads as negative space between two surfaces.
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
                // Tap an empty slot to create an event there. Taps on event
                // blocks are consumed by their own click handler first, so this
                // only fires on the column background. Snaps to the tapped hour.
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
    // A block that cannot afford both lines spends its space on the title, and
    // one too short even for that drops the title rather than serving a sliced one.
    // Height alone decides: a duration threshold would keep hiding the time on a
    // half-hour block the user has pinched open to three times the room it needs.
    val showTime = metrics.available >= metrics.titleLine + metrics.timeLine
    val showTitle = metrics.fitsTitle
    val textWidth = width - (BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING) * 2
    // Only lines the block can actually draw: a block too short for the time is
    // too short for a second title line too, and asking for one served a sliced
    // one — as well as handing the drag copy a count it couldn't honour, so the
    // title re-wrapped the moment the block was lifted (#267).
    val titleBudget = metrics.titleBudget(metrics.available).coerceAtLeast(1)
    val titleMaxLines = if (showTime) 1 else titleBudget.coerceAtMost(2)
    // On a day column — wide enough for "09:30–11:00" several times over — the
    // range never needs the second line, until lanes cut the column down.
    val spare = metrics.available - metrics.titleHeight(titleMaxLines) -
        if (showTime) metrics.timeLine else 0.dp
    val timeMaxLines = if (showTime) blockTimeLines(timeLabel, textWidth, spare) else 1
    val paint = eventPaint(block.event, dark)
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
        modifier = modifier
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

@Composable
private fun DayLoading() {
    val scale = LocalTimelineZoom.current.scale
    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Same scale resolution as the loaded timeline, so the skeleton's column
        // doesn't resize the moment the real day arrives.
        val hourHeight = scale.hourHeight(maxHeight)
        val totalHeight = hourHeight * 24
        // The skeleton wears the loaded column's own ground, so the arrival of
        // the real day doesn't flash a solid block into a gapped grid.
        val showHourGrid = LocalShowHourGrid.current
        val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = TIMELINE_CONTENT_END_INSET)
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
                    .padding(horizontal = 2.dp)
                    .background(
                        if (showHourGrid) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceContainer,
                    )
                    .hourGridCells(showHourGrid, hourPx, MaterialTheme.colorScheme.surfaceContainer),
            )
        }
    }
}

private fun minToHm(min: Int, is24Hour: Boolean, locale: Locale): String =
    formatMinuteOfDay(min, is24Hour, locale)

/** [abbreviated] drops the weekday, leaving the date itself (#165). */
private fun formatDayTitle(
    date: LocalDate,
    locale: Locale,
    currentYear: Int,
    abbreviated: Boolean = false,
): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day),
        locale = locale,
        currentYear = currentYear,
        skeleton = if (abbreviated) "dMMM" else "EEEdMMM",
    )
