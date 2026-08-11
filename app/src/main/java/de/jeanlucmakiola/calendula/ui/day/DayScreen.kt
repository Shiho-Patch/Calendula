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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarTitleButton
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.BlockTimeLabel
import de.jeanlucmakiola.calendula.ui.common.animatedBlockPlacement
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.TimelineDragController
import de.jeanlucmakiola.calendula.ui.common.TimelineDragOverlay
import de.jeanlucmakiola.calendula.ui.common.TimelineDrop
import de.jeanlucmakiola.calendula.ui.common.beginsOn
import de.jeanlucmakiola.calendula.ui.common.eventDragAllowed
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.rememberEventDragSource
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineDragController
import de.jeanlucmakiola.calendula.ui.common.startInstant
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
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.MIN_EVENT_FRACTION
import de.jeanlucmakiola.calendula.ui.common.SECONDARY_INK_ALPHA
import de.jeanlucmakiola.calendula.ui.common.hourHeight
import de.jeanlucmakiola.calendula.ui.common.rememberTimelinePinchZoom
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.GUTTER_WIDTH
import de.jeanlucmakiola.calendula.ui.common.HourGutter
import de.jeanlucmakiola.calendula.ui.common.hourSeparatorLines
import de.jeanlucmakiola.calendula.ui.common.tappedMinuteOfDay
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.util.Locale
import kotlin.math.roundToInt

private val ALL_DAY_ROW_HEIGHT = 24.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp

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
                    onJumpToDate = jumpToDate,
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
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    // Hoisted above the per-day AnimatedContent so the vertical scroll position
    // survives day-to-day swipes. We only centre on noon once, on first entry
    // into the day view (i.e. when arriving from the month/week view).
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        // Half the scroll range *is* noon: the content spans a full 24 hours, so
        // centring the range centres midday at whatever hour height is in force.
        scrollState.scrollTo(scrollState.maxValue / 2)
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

    // Above the AnimatedContent: a page change mid-drag would strand the
    // floating block inside the outgoing page.
    val dragController = rememberTimelineDragController()
    val move = LocalEventMove.current
    val zone = remember { TimeZone.currentSystemDefault() }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize().then(swipeModifier),
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
                    dragController = dragController,
                    onEventClick = onEventClick,
                    onCreateAt = onCreateAt,
                    onDrop = { drop ->
                        val took = move?.move(
                            MoveRequest(
                                eventId = drop.event.eventId,
                                beginMillis = drop.event.start.toEpochMilliseconds(),
                                endMillis = drop.event.end.toEpochMilliseconds(),
                                target = MoveTarget.Start(drop.startInstant(zone)),
                            ),
                        )
                        // Refused, so nothing will land: let the copy go now
                        // rather than hold it out for a settle that never comes.
                        if (took != true) dragController.release()
                    },
                )
            }
        }
        TimelineDragOverlay(dragController)
    }
}

@Composable
internal fun DaySuccess(
    state: DayUiState.Success,
    topSectionColor: Color,
    scrollState: ScrollState,
    allDayHeight: Dp,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
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
            dragController = dragController,
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
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    val locale = currentLocale()
    TopAppBar(
        title = {
            CalendarTitleButton(
                title = formatDayTitle(date, locale, currentYear),
                currentDate = date,
                onJumpToDate = onJumpToDate,
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
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val zoom = LocalTimelineZoom.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // BoxWithConstraints rather than Box: the fit-the-whole-day scale needs the
    // timeline's own viewport height, which is only known here — below the top
    // bar, date header and all-day strip.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hourHeight = zoom.scale.hourHeight(maxHeight)
        val totalHeight = hourHeight * 24
        // The pinch sits on the Row, above both scroll viewports: it has to
        // outrank the vertical scroll, and it does that by watching the initial
        // pass, which only reaches it if it is their ancestor.
        val pinch = rememberTimelinePinchZoom(scrollState, maxHeight, hourHeight, zoom)
        // Gutter and day column are two scroll viewports that SHARE one scroll
        // state, so they stay perfectly aligned. The day-column viewport is a
        // static, rounded-clipped window — the content scrolls inside it, so the
        // soft corners are permanent at any scroll position.
        Row(modifier = Modifier.fillMaxSize().then(pinch)) {
            // Hour gutter (scrolls in sync with the day column). Start inset so the
            // labels centre on the top bar hamburger, matching the week view.
            HourGutter(
                scrollState = scrollState,
                hourHeight = hourHeight,
                dragController = dragController,
            )
            // Day column: rounded, clipped scroll viewport (permanent corners).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { dragController.geometry.viewport = it },
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
                        // already folds in the scroll offset.
                        .onGloballyPositioned { coords ->
                            dragController.geometry.let {
                                it.grid = coords
                                it.scroll = scrollState
                                it.hourPx = with(density) { hourHeight.toPx() }
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
    val showHourLines = LocalShowHourLines.current
    val hourLineColor = MaterialTheme.colorScheme.outlineVariant
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
                    EventBlock(
                        block = block,
                        dark = dark,
                        height = place.height,
                        date = date,
                        dragController = dragController,
                        onClick = { onEventClick(block.event) },
                        onDrop = onDrop,
                        modifier = Modifier
                            .offset(x = place.x, y = place.y)
                            .width(place.width)
                            .height(place.height)
                            .padding(horizontal = 1.dp),
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
    date: LocalDate,
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
    val titleLineHeight = with(density) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    }
    val timeLineHeight = with(density) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    // What's left for text once the 2.dp top/bottom padding is paid for. A block
    // that cannot afford both lines spends its space on the title, and one too
    // short even for that drops the title rather than serving a sliced one.
    val available = height - 4.dp
    val showTime = block.endMin - block.startMin >= 45 &&
        available >= titleLineHeight + timeLineHeight
    val showTitle = available >= titleLineHeight
    val soften = LocalSoftenColors.current
    val fill = eventFill(block.event.color, dark, soften)
    val zone = remember { TimeZone.currentSystemDefault() }
    val moveAction = eventMoveAction(block.event)
    // A block clipped at the top continues from the previous day: its top edge
    // is midnight, not the event's start, so dragging it would invent a time.
    val draggable = eventDragAllowed(block.event) && block.beginsOn(date, zone)
    val dragModifier = rememberEventDragSource(
        enabled = draggable,
        key = block.event.instanceId,
        onPickUp = { pointer, blockRoot, _ -> dragController.begin(block, pointer, blockRoot) },
        onMove = dragController::move,
        onDrop = { dragController.finish()?.let(onDrop) },
        onCancel = dragController::cancel,
    )
    val lifted = draggable && dragController.ghosts(block, date)
    val ghost = ghostAlpha(lifted)
    Box(
        modifier = modifier
            // The source stays put as a ghost while its floating copy travels.
            .then(if (ghost < 1f) Modifier.alpha(ghost) else Modifier)
            .background(fill, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            // After clickable, so it is the inner node and wins the main pass;
            // the tap still works, since a drag consumes the up.
            .then(dragModifier)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics {
                contentDescription = "$title, $timeLabel"
                if (moveAction != null) customActions = listOf(moveAction)
            },
    ) {
        Column {
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = if (showTime) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = 0.85f),
                )
            }
            if (showTime) {
                BlockTimeLabel(
                    label = timeLabel,
                    color = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
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
        val totalHeight = scale.hourHeight(maxHeight) * 24
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
