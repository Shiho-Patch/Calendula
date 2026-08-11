package de.jeanlucmakiola.calendula.ui.month

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import de.jeanlucmakiola.calendula.ui.common.rememberDragSurface
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.moveInFlight
import de.jeanlucmakiola.calendula.ui.common.OnUndoStarted
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.DragSnapHaptics
import de.jeanlucmakiola.calendula.ui.common.SETTLE_FADE_MILLIS
import de.jeanlucmakiola.calendula.ui.common.SETTLE_GRACE_MILLIS
import de.jeanlucmakiola.calendula.ui.common.EventMoveScope
import de.jeanlucmakiola.calendula.domain.spanFirstDay
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDayHeader
import de.jeanlucmakiola.calendula.ui.agenda.AgendaEmptyDayRow
import de.jeanlucmakiola.calendula.ui.agenda.AgendaEventRow
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarTitleButton
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CALENDAR_SWIPE_THRESHOLD
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.animateItemMotion
import de.jeanlucmakiola.floret.identity.itemEnter
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.time.isoWeekNumber
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
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
    viewModel: MonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val continuousState by viewModel.continuousState.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val viewStyle by viewModel.viewStyle.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val dimCompleted by viewModel.dimCompletedEvents.collectAsStateWithLifecycle()
    val showWeekNumbers by viewModel.showWeekNumbers.collectAsStateWithLifecycle()
    // The instant before which an event counts as completed, or null when dimming
    // is off. derivedStateOf keeps the per-minute "now" from recomposing the
    // screen while the setting is off (it stays null regardless of the tick).
    val nowState = rememberCurrentMinute()
    val dimCutoff by remember(dimCompleted) {
        derivedStateOf { if (dimCompleted) nowState.value else null }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val scrolling = viewStyle.isScrolling
    val dense = viewStyle == MonthViewStyle.Dense

    // Today, from whichever state is driving; the clock only covers the first
    // frame, before either has loaded.
    val today = when {
        scrolling -> (continuousState as? ContinuousMonthUiState.Success)?.today
        else -> (state as? MonthUiState.Success)?.today
    } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // The two scrolling styles index their items differently — Continuous by
    // month (which the week start can't move), Dense by week (which it can) — so
    // the list state is rebuilt when either changes rather than carrying an
    // offset into a coordinate space that no longer means the same thing.
    val listState = key(viewStyle, weekStart) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = if (dense) {
                weekIndexOf(LocalDate(month.year, month.month, 1), weekStart)
            } else {
                itemIndexForMonth(monthIndexOf(month))
            },
        )
    }

    // Which month the viewport is showing. Continuous reads it off the block
    // whose sticky header is up; Dense has no blocks, so it takes the month the
    // row below the top edge mostly sits in, which flips as that month's first
    // full week comes into view.
    val visibleMonth by remember(dense, weekStart) {
        derivedStateOf {
            if (dense) {
                val midweek = weekStartForIndex(listState.firstVisibleItemIndex + 1, weekStart)
                    .plus(3, DateTimeUnit.DAY)
                YearMonth(midweek.year, midweek.month)
            } else {
                yearMonthForIndex(monthIndexForItem(listState.firstVisibleItemIndex))
            }
        }
    }
    val titleMonth = if (scrolling) visibleMonth else month

    // Feed the visible range back so the sliding data window can follow. Both
    // styles report *months*, whatever they index by — one window serves both.
    LaunchedEffect(listState, scrolling, dense, weekStart) {
        if (!scrolling) return@LaunchedEffect
        snapshotFlow {
            // Empty before the first layout pass — and reporting a default of 0
            // for it would claim January 1900 is on screen and drag the whole
            // window back there.
            val visible = listState.layoutInfo.visibleItemsInfo
            when {
                visible.isEmpty() -> null
                dense -> monthIndexForWeek(visible.first().index, weekStart) to
                    monthIndexForWeek(visible.last().index, weekStart)
                else -> monthIndexForItem(visible.first().index) to
                    monthIndexForItem(visible.last().index)
            }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisibleMonthsChanged(first, last) }
    }

    // Keep the paged month tracking the scrolling grid. The scrolling styles
    // navigate by scroll position and never touch the paged month otherwise, so
    // without this it stays wherever paged navigation last left it (today's
    // month, on a fresh open) — and switching to a paged style, or reseeding the
    // other scrolling style's list state, which both read off it, would snap
    // back there rather than staying on the month you were looking at.
    LaunchedEffect(listState, scrolling, dense, weekStart) {
        if (!scrolling) return@LaunchedEffect
        snapshotFlow { visibleMonth }
            .distinctUntilChanged()
            .collect { viewModel.syncScrollMonth(it) }
    }

    val isOnCurrentMonth = titleMonth == YearMonth(today.year, today.month)

    // Continuous names the month on the block's own sticky header, so the bar
    // carries the year instead of repeating it two lines further down. Dense has
    // no header to defer to, so it keeps the full title.
    val locale = currentLocale()
    val topBarTitle = if (viewStyle == MonthViewStyle.Continuous) {
        titleMonth.year.toString()
    } else {
        formatMonthTitle(titleMonth, locale, currentYear = today.year)
    }

    // Slide direction for the grid transition: +1 = next, -1 = prev, 0 = jump (no slide).
    var slideDir by remember { mutableIntStateOf(0) }

    val goNext = {
        slideDir = 1
        viewModel.goToNext()
    }
    val goPrev = {
        slideDir = -1
        viewModel.goToPrev()
    }
    // Slide toward today: viewing the future → today comes in from the left
    // (back), viewing the past → from the right (forward). The continuous stream
    // scrolls to today's week instead — there is nothing to slide.
    val jumpToToday = {
        if (scrolling) {
            scope.launch {
                listState.animateScrollToItem(
                    if (dense) weekIndexOf(today, weekStart)
                    else itemIndexForMonth(monthIndexOf(YearMonth(today.year, today.month))),
                )
            }
            Unit
        } else {
            slideDir = when (val s = state) {
                is MonthUiState.Success ->
                    if (YearMonth(s.today.year, s.today.month) < s.month) -1 else 1
                else -> 0
            }
            viewModel.goToToday()
        }
    }
    // Drawer jump-to-date: slide from the side the target month lies on.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        if (scrolling) {
            scope.launch {
                listState.animateScrollToItem(
                    if (dense) weekIndexOf(LocalDate(target.year, target.month, 1), weekStart)
                    else itemIndexForMonth(monthIndexOf(YearMonth(target.year, target.month))),
                )
            }
        } else {
            slideDir = if (YearMonth(target.year, target.month) < month) -1 else 1
            viewModel.goToDate(target)
        }
    }
    // Selecting a day in the split grid can cross into a neighbour month — a
    // tapped leading/trailing day follows to its own month. Point the slide the
    // way the month is actually moving, so the incoming page travels the right
    // direction instead of reusing whatever the last swipe left in slideDir.
    val selectDay: (LocalDate) -> Unit = { date ->
        val target = YearMonth(date.year, date.month)
        if (target != month) slideDir = if (target < month) -1 else 1
        viewModel.selectDate(date)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the month swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = LocalDate(titleMonth.year, titleMonth.month, 1),
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
                MonthTopBar(
                    title = topBarTitle,
                    titleDate = LocalDate(titleMonth.year, titleMonth.month, 1),
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
                    todayVisible = !isOnCurrentMonth && !todayInToolbar,
                    todayText = stringResource(R.string.month_today_action),
                    onToday = jumpToToday,
                    onCreate = {
                        // Split has a selected day; the other styles anchor on
                        // today when its month is shown, else the 1st.
                        onCreateEvent(
                            when {
                                viewStyle == MonthViewStyle.Split -> selectedDate
                                isOnCurrentMonth -> today
                                else -> LocalDate(titleMonth.year, titleMonth.month, 1)
                            },
                            null,
                        )
                    },
                )
            },
        ) { innerPadding ->
            // Hoisted above every style's grid: a dragged chip is drawn in an
            // overlay, clear of the week row's and the list viewport's clips.
            val chipDrag = rememberMonthDragController()
            // Undo moves the event back, so the chip travels back too. The
            // signal comes from the write, not from the chip that offers it.
            OnUndoStarted { chipDrag.beginUndo() }
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WeekdayHeader(weekStart = weekStart, showWeekNumbers = showWeekNumbers)
                CompositionLocalProvider(
                    LocalDimCutoff provides dimCutoff,
                    LocalMonthDrag provides chipDrag,
                ) {
                    if (scrolling) {
                        ContinuousMonthContent(
                            state = continuousState,
                            listState = listState,
                            dense = dense,
                            showWeekNumbers = showWeekNumbers,
                            onRetry = jumpToToday,
                            onOpenDay = onOpenDay,
                        )
                    } else if (viewStyle == MonthViewStyle.Split) {
                        SplitMonthContent(
                            state = state,
                            selected = selectedDate,
                            slideDir = slideDir,
                            showWeekNumbers = showWeekNumbers,
                            onSwipeNext = goNext,
                            onSwipePrev = goPrev,
                            onRetry = jumpToToday,
                            onSelectDay = selectDay,
                            onOpenDay = onOpenDay,
                            onEventClick = onEventClick,
                            onCreateEvent = { onCreateEvent(it, null) },
                        )
                    } else {
                        MonthContent(
                            state = state,
                            slideDir = slideDir,
                            showWeekNumbers = showWeekNumbers,
                            onSwipeNext = goNext,
                            onSwipePrev = goPrev,
                            onRetry = jumpToToday,
                            onOpenDay = onOpenDay,
                        )
                    }
                }
            }
            MonthDragOverlay(chipDrag)
            }
        }
    }
}

/**
 * How long to wait for a grid that never seats the drop — it landed in a day's
 * "+N" overflow or on a day this month doesn't show, or the write failed.
 */
private const val MONTH_SETTLE_TIMEOUT_MILLIS = 450L

/** The chip in flight, drawn over the grid and following the finger. */
@Composable
private fun MonthDragOverlay(controller: MonthDragController) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val moveInFlight = moveInFlight()
    // Here rather than beside the controller: this reads [drag], which changes
    // every frame, and this composable is the one that is meant to.
    DragSnapHaptics(controller.drag?.targetDate)
    // Hold the landed chip until the write is done — including the time a
    // recurring drop's scope dialog is up — and then, unlike the timeline, carry
    // it to its seat: it was dropped at whatever height the finger was at, while
    // the grid seats it in a lane.
    var handingOver by remember(controller.settling) { mutableStateOf(false) }
    var gliding by remember(controller.settling) { mutableStateOf(false) }
    val glide = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val glideSpec = MaterialTheme.motionScheme.fastSpatialSpec<Offset>()
    val seat = controller.settledInRoot
    LaunchedEffect(controller.settling, moveInFlight, seat) {
        val landed = controller.settling
        if (landed == null || moveInFlight) return@LaunchedEffect
        if (seat == null) {
            delay(MONTH_SETTLE_TIMEOUT_MILLIS)
        } else {
            if (!gliding) glide.snapTo(landed.topLeftInRoot)
            gliding = true
            if (reduceMotion) glide.snapTo(seat) else glide.animateTo(seat, glideSpec)
            delay(SETTLE_GRACE_MILLIS)
        }
        handingOver = true
        controller.handOver()
        delay(SETTLE_FADE_MILLIS.toLong())
        controller.release()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // A copy of a chip still in the tree behind it; announcing it again
            // would duplicate the event for the drag's duration.
            .clearAndSetSemantics { }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val drag = controller.drag ?: controller.settling ?: return@Box
        // Landed: the copy sinks back to the grid's own plane while the write runs.
        val lift by animateFloatAsState(
            targetValue = if (controller.drag == null || reduceMotion) 0f else 1f,
            label = "chip-lift",
        )
        val copyAlpha by animateFloatAsState(
            targetValue = if (handingOver) 0f else 1f,
            animationSpec = tween(SETTLE_FADE_MILLIS),
            label = "chip-handover",
        )
        MonthBar(
            event = drag.event,
            dark = dark,
            continuesLeft = false,
            continuesRight = false,
            modifier = Modifier
                // Absolute: these are root coordinates, and the direction-aware
                // offset would mirror them across the screen in an RTL layout.
                .absoluteOffset {
                    val at = if (gliding) glide.value else drag.topLeftInRoot
                    IntOffset(
                        (at.x - origin.x).roundToInt(),
                        (at.y - origin.y).roundToInt(),
                    )
                }
                .width(with(density) { drag.sizePx.width.toDp() })
                .height(with(density) { drag.sizePx.height.toDp() })
                .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.04f * lift
                    scaleY = 1f + 0.04f * lift
                    shadowElevation = 8.dp.toPx() * lift
                    alpha = copyAlpha
                    shape = RoundedCornerShape(4.dp)
                    clip = false
                },
        )
    }
}

@Composable
private fun MonthContent(
    state: MonthUiState,
    slideDir: Int,
    showWeekNumbers: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onRetry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()
    val swipeModifier = rememberMonthSwipeModifier(onSwipeNext, onSwipePrev)

    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize().then(swipeModifier),
        contentKey = { s ->
            when (s) {
                is MonthUiState.Success -> "success-${s.month}"
                is MonthUiState.Failure -> "failure-${s.reason}"
                MonthUiState.Loading -> "loading"
            }
        },
        transitionSpec = { calendarSlideTransition(slideDir, slideSpec, fadeSpec, reduceMotion) },
        label = "month-transition",
    ) { s ->
        when (s) {
            MonthUiState.Loading -> MonthGridLoading()
            is MonthUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
            is MonthUiState.Success -> MonthGrid(
                state = s,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onOpenDay,
            )
        }
    }
}

/**
 * Content for both scrolling styles. No swipe detector and no [AnimatedContent]:
 * vertical scrolling owns the gesture, and the list never swaps wholesale — it
 * keeps scrolling while the window behind it reloads.
 */
@Composable
private fun ContinuousMonthContent(
    state: ContinuousMonthUiState,
    listState: LazyListState,
    dense: Boolean,
    showWeekNumbers: Boolean,
    onRetry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    when (state) {
        // The scrolling styles get their own skeleton rather than the paged
        // grid's: same row height, same header, so nothing reflows when the
        // months land on top of it.
        ContinuousMonthUiState.Loading -> ContinuousMonthSkeleton(dense = dense)
        is ContinuousMonthUiState.Failure ->
            CalendarFailure(reason = state.reason, onRetry = onRetry)
        is ContinuousMonthUiState.Success -> if (dense) {
            DenseMonthGrid(
                state = state,
                listState = listState,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onOpenDay,
            )
        } else {
            ContinuousMonthGrid(
                state = state,
                listState = listState,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onOpenDay,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthTopBar(
    title: String,
    titleDate: LocalDate,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            CalendarTitleButton(
                title = title,
                currentDate = titleDate,
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
        scrollBehavior = scrollBehavior,
    )
}

@Composable
internal fun WeekdayHeader(weekStart: DayOfWeek, showWeekNumbers: Boolean) {
    val locale = currentLocale()
    val days = remember(weekStart, locale) {
        (0 until 7).map { offset ->
            DayOfWeek.entries[((weekStart.ordinal + offset) % 7)]
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        // Reserve the gutter so the weekday labels stay over their day columns.
        if (showWeekNumbers) Spacer(Modifier.width(WEEK_NUMBER_GUTTER))
        days.forEach { dow ->
            val isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
            val javaDow = java.time.DayOfWeek.of(dow.ordinal + 1)
            Text(
                text = javaDow.getDisplayName(JavaTextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                color = if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val EVENT_ROW_HEIGHT = 20.dp
private val DAY_NUMBER_HEIGHT = 22.dp
/** Width of the optional left calendar-week gutter (#25); narrow, since it only
 *  seats a one- or two-digit week number in a full-height tonal pill. */
private val WEEK_NUMBER_GUTTER = 40.dp
private val DAY_NUMBER_GAP = 4.dp
private val CELL_TOP_PADDING = 6.dp
private val CELL_GAP = 2.dp
/** Named separately because the split style's selection outline draws its own
 *  rounded rect and has to match this radius exactly. */
private val CELL_CORNER = 12.dp
private val CELL_SHAPE = RoundedCornerShape(CELL_CORNER)

/** Width of the split style's selected-day outline. */
private val SPLIT_SELECTION_STROKE = 1.5.dp
private const val MAX_EVENT_ROWS = 3

/**
 * Row height in the continuous grid. The paged grid divides the viewport between
 * however many rows the month has; a scrolling stream has no such bound, so it
 * fixes a height that seats the day number plus [MAX_EVENT_ROWS] event rows —
 * close to what a five-row month gets on a typical phone.
 */
private val CONTINUOUS_ROW_HEIGHT = 112.dp

/**
 * Whitespace below a month block, before the next month's header. Paired with the
 * header's own top padding it gives the seam between two months roughly a row's
 * worth of air — enough that the blocks read as separate without a rule between
 * them.
 */
private val CONTINUOUS_MONTH_GAP = 20.dp

@Composable
internal fun MonthGrid(
    state: MonthUiState.Success,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    /** See [MonthWeekRow]'s `selected`: an anchor for the morph, never a mark. */
    selected: LocalDate? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Match the weekday header's 8dp inset so day cells sit under their
            // labels, and so the week-number gutter's centre lines up with the
            // top bar's hamburger (4dp bar inset + 24dp half icon button).
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val month = state.month
        state.weeks.forEach { week ->
            MonthWeekRow(
                week = week,
                today = state.today,
                inMonth = { it.month == month.month && it.year == month.year },
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onOpenDay,
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * The continuous grid (#38): months stacked into one vertical scroll, each a
 * self-contained block under its own sticky header.
 *
 * A block shows *only its own days* — the boundary week keeps its seven columns
 * so nothing shifts sideways, but the neighbour month's cells are left blank
 * rather than filled with dimmed duplicates of days shown again a block later.
 * The whitespace either side of a header is what separates one month from the
 * next; scrolling itself never stops at a page seam.
 *
 * The list is indexed by *absolute month index* (two items per month: header,
 * then block), so a block's identity never changes and the week-start preference
 * can't move it. Months the loaded window hasn't reached render as a skeleton of
 * the right height and pull the window along behind them.
 */
@Composable
internal fun ContinuousMonthGrid(
    state: ContinuousMonthUiState.Success,
    listState: LazyListState,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthCount = remember { continuousMonthCount() }
    val todayMonth = remember(state.today) { YearMonth(state.today.year, state.today.month) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        repeat(monthCount) { index ->
            val month = yearMonthForIndex(index)
            stickyHeader(key = "header-$index", contentType = "month-header") {
                ContinuousMonthHeader(
                    month = month,
                    currentYear = state.today.year,
                    isCurrent = month == todayMonth,
                )
            }
            item(key = "month-$index", contentType = "month-block") {
                ContinuousMonthBlock(
                    month = month,
                    weeks = state.monthsByIndex[index],
                    weekStart = state.weekStart,
                    today = state.today,
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                )
            }
        }
    }
}

/**
 * One month's week rows. Sized by [weekRowsInMonth] even before its data has
 * loaded, so the skeleton occupies exactly what the real block will and a scroll
 * across unloaded months doesn't jump when they arrive.
 */
@Composable
private fun ContinuousMonthBlock(
    month: YearMonth,
    weeks: List<MonthWeek>?,
    weekStart: DayOfWeek,
    today: LocalDate,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
) {
    val rowCount = remember(month, weekStart) { weekRowsInMonth(month, weekStart) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(bottom = CONTINUOUS_MONTH_GAP),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (weeks == null) {
            repeat(rowCount) { ContinuousWeekPlaceholder() }
        } else {
            weeks.forEach { week ->
                MonthWeekRow(
                    week = week,
                    today = today,
                    inMonth = { it.month == month.month && it.year == month.year },
                    // The block owns its month alone: a day from either
                    // neighbour is left out entirely rather than dimmed.
                    blankOutside = true,
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CONTINUOUS_ROW_HEIGHT),
                )
            }
        }
    }
}

/**
 * The month label that pins above its block. Opaque `surface` so the rows scroll
 * under it cleanly, and generous vertical padding — the whitespace around the
 * label is what makes each month read as its own section. The rule beneath it
 * closes the header off against the grid; it is the one part of this layout the
 * user is still deciding on.
 */
@Composable
private fun ContinuousMonthHeader(month: YearMonth, currentYear: Int, isCurrent: Boolean) {
    val locale = currentLocale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 20.dp),
    ) {
        Text(
            text = formatMonthTitle(month, locale, currentYear),
            style = MaterialTheme.typography.titleMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
    }
}

/**
 * The Dense style (#38): one uninterrupted vertical stream of weeks, months
 * flowing into each other.
 *
 * Kept alongside the block layout rather than replaced by it — the seams that
 * make months legible are exactly what someone wanting a dense calendar doesn't
 * want. There is no month boundary to dim across here, so every day reads as
 * in-month and the 1st names its month: the only marker of where one ends.
 *
 * Indexed by absolute *week* number, so a row's identity never changes and there
 * is no page seam to scroll across. Weeks the loaded window hasn't reached yet
 * render as a skeleton and pull the window along behind them.
 */
@Composable
internal fun DenseMonthGrid(
    state: ContinuousMonthUiState.Success,
    listState: LazyListState,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekCount = remember(state.weekStart) { continuousWeekCount(state.weekStart) }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        items(count = weekCount, key = { it }) { index ->
            val week = state.weeksByIndex[index]
            if (week == null) {
                ContinuousWeekPlaceholder()
            } else {
                MonthWeekRow(
                    week = week,
                    today = state.today,
                    // Every day in the stream belongs to a month equally — there
                    // is no "other month" to recede here.
                    inMonth = { true },
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    labelMonthOnFirst = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CONTINUOUS_ROW_HEIGHT),
                )
            }
        }
    }
}

/** Which way a drag went, decided once per gesture and then held. */
private enum class DragAxis { Undecided, Horizontal, Vertical }

/**
 * The month grid's drag gesture: horizontal pages the month, vertical expands or
 * collapses the split style (#53). The grid doesn't follow the finger, so there is
 * no distance to rubber-band against — it commits the moment the accumulated drag
 * clears the threshold, once per gesture, exactly as
 * [rememberCalendarPageSwipe][de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSwipe]
 * does for the week and day views.
 *
 * This is the month's own detector rather than that shared one because it needs
 * two axes, and the axis is **locked on the first movement and held for the whole
 * gesture** so a drag can page or expand but never both. Two independent
 * detectors on one surface would each see their own component of a diagonal drag
 * and both fire.
 *
 * The horizontal threshold is the shared one. The vertical is larger — swapping
 * the whole layout out deserves a more deliberate pull than stepping to the next
 * month.
 *
 * [onExpand]/[onCollapse] are null for the paged style, which leaves the vertical
 * axis unclaimed: the lock still happens, so a vertical drag there does nothing
 * rather than being re-read as a page turn.
 */
@Composable
private fun rememberMonthSwipeModifier(
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onExpand: (() -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
): Modifier {
    val density = LocalDensity.current
    val pageThreshold = with(density) { CALENDAR_SWIPE_THRESHOLD.toPx() }
    val expandThreshold = with(density) { MONTH_EXPAND_THRESHOLD.toPx() }
    return Modifier.pointerInput(onSwipeNext, onSwipePrev, onExpand, onCollapse) {
        var accum = Offset.Zero
        var axis = DragAxis.Undecided
        var fired = false
        detectDragGestures(
            onDragStart = {
                accum = Offset.Zero
                axis = DragAxis.Undecided
                fired = false
            },
            onDragEnd = {
                accum = Offset.Zero
                axis = DragAxis.Undecided
                fired = false
            },
            onDragCancel = {
                accum = Offset.Zero
                axis = DragAxis.Undecided
                fired = false
            },
            onDrag = { _, drag ->
                accum += drag
                if (axis == DragAxis.Undecided) {
                    // Ties go horizontal, keeping paging the default reading of an
                    // ambiguous drag as it was before the vertical axis existed.
                    axis = if (abs(accum.x) >= abs(accum.y)) {
                        DragAxis.Horizontal
                    } else {
                        DragAxis.Vertical
                    }
                }
                // Both axes commit the instant the drag clears their threshold,
                // rather than on release — see [rememberCalendarPageSwipe], which
                // does the same for the week and day views.
                if (!fired) {
                    val commit = when (axis) {
                        DragAxis.Horizontal -> when {
                            accum.x < -pageThreshold -> onSwipeNext
                            accum.x > pageThreshold -> onSwipePrev
                            else -> null
                        }
                        DragAxis.Vertical -> when {
                            accum.y > expandThreshold -> onExpand
                            accum.y < -expandThreshold -> onCollapse
                            else -> null
                        }
                        DragAxis.Undecided -> null
                    }
                    if (commit != null) {
                        fired = true
                        commit()
                    }
                }
            },
        )
    }
}

/** Drag distance that commits an expand/collapse — deliberately longer than a page. */
private val MONTH_EXPAND_THRESHOLD = 48.dp

/**
 * Split style content: the compact grid keeps the month swipe, the pane below it
 * lists whatever day is selected — and a downward drag trades the pane away for
 * the full paged grid, an upward one brings it back (#53).
 *
 * The grid slides between months like the paged style, which it can only do
 * because it always reserves [SPLIT_GRID_ROWS] rows. Sized to its own month it
 * stood 4–6 rows tall, so every swipe shunted the pane up or down by a row on
 * top of swapping the grid — the pane now holds still and only the grid moves.
 *
 * Expansion is deliberately **not** a stored preference. It is a way to look at
 * the month you are on, not a fourth style; persisted, someone would expand it
 * once and later find their Split style permanently changed with nothing on
 * screen to explain why. [rememberSaveable] carries it across a rotation, which
 * is as long as it should live.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SplitMonthContent(
    state: MonthUiState,
    selected: LocalDate,
    slideDir: Int,
    showWeekNumbers: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onRetry: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
) {
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Back collapses before it does anything else. Expanding replaces the whole
    // screen, so it is a place you can be, and every other place in the app can
    // be backed out of. Declared deeper than CalendarHost's view-stack handler,
    // which is what makes it win while it is enabled.
    BackHandler(enabled = expanded) { expanded = false }
    // The swipe wraps the grid rather than living inside it: mid-transition
    // there are two grids, and the gesture belongs to neither. The pane is left
    // out of it — it scrolls and is full of tappable rows.
    val swipeModifier = rememberMonthSwipeModifier(
        onSwipeNext = onSwipeNext,
        onSwipePrev = onSwipePrev,
        onExpand = { expanded = true },
        onCollapse = { expanded = false },
    )

    when (state) {
        MonthUiState.Loading -> MonthGridLoading()
        is MonthUiState.Failure -> CalendarFailure(reason = state.reason, onRetry = onRetry)
        is MonthUiState.Success -> SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = expanded,
                modifier = Modifier.fillMaxSize(),
                // Both branches fill the same box — the grid grows into exactly the
                // room the pane gives up — so there is no size change to contain.
                // The travel between them is the shared elements' job, not a slide.
                transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                label = "split-expand-transition",
            ) { isExpanded ->
                CompositionLocalProvider(
                    // Null under reduced motion: nothing is tagged, nothing
                    // travels, and the cross-fade above is the whole transition.
                    LocalMonthMorph provides if (reduceMotion) {
                        null
                    } else {
                        MonthMorphScope(this@SharedTransitionLayout, this@AnimatedContent)
                    },
                ) {
                    SplitMonthBody(
                        expanded = isExpanded,
                        state = state,
                        selected = selected,
                        slideDir = slideDir,
                        showWeekNumbers = showWeekNumbers,
                        swipeModifier = swipeModifier,
                        onSelectDay = onSelectDay,
                        onOpenDay = onOpenDay,
                        onEventClick = onEventClick,
                        onCreateEvent = onCreateEvent,
                        onSetExpanded = { expanded = it },
                    )
                }
            }
        }
    }
}

/** The two faces of the split style, sharing one set of morph tags. */
@Composable
private fun SplitMonthBody(
    expanded: Boolean,
    state: MonthUiState.Success,
    selected: LocalDate,
    slideDir: Int,
    showWeekNumbers: Boolean,
    swipeModifier: Modifier,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onSetExpanded: (Boolean) -> Unit,
) {
    if (expanded) {
        SplitMonthExpanded(
            state = state,
            selected = selected,
            slideDir = slideDir,
            showWeekNumbers = showWeekNumbers,
            swipeModifier = swipeModifier,
            // A tap in the expanded grid picks the day and drops back, which
            // gives the expanded month a job — a chooser you dip into — rather
            // than a mode you can get stranded in. The collapse then runs with
            // the selection already set, so the pane arrives showing the day
            // you picked.
            onPickDay = {
                onSelectDay(it)
                onSetExpanded(false)
            },
            onCollapse = { onSetExpanded(false) },
        )
    } else {
        SplitMonthCollapsed(
            state = state,
            selected = selected,
            slideDir = slideDir,
            showWeekNumbers = showWeekNumbers,
            swipeModifier = swipeModifier,
            onSelectDay = onSelectDay,
            onOpenDay = onOpenDay,
            onEventClick = onEventClick,
            onCreateEvent = onCreateEvent,
            onExpand = { onSetExpanded(true) },
        )
    }
}

/** The split style at rest: compact grid, handle, then the selected day's events. */
@Composable
private fun SplitMonthCollapsed(
    state: MonthUiState.Success,
    selected: LocalDate,
    slideDir: Int,
    showWeekNumbers: Boolean,
    swipeModifier: Modifier,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onExpand: () -> Unit,
) {
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    Column(Modifier.fillMaxSize()) {
        // Grid and handle drag as one surface — the handle is what advertises the
        // gesture, so it has to answer to it as well as to a tap. The pane is
        // outside: it scrolls, and is full of tappable rows.
        Column(swipeModifier) {
            AnimatedContent(
                // The selection travels *with* the state so each page keeps its
                // own. Read from outside, both pages would show the incoming
                // one, and paging visibly threw the marker across the outgoing
                // grid — onto the new month's 1st, which the old grid still
                // shows among its trailing days — before the new page arrived.
                targetState = state to selected,
                // Keyed on the month alone, so a provider notification refreshing
                // the month you are on — or a tap moving the selection within it
                // — updates in place instead of sliding.
                contentKey = { (s, _) -> s.month },
                transitionSpec = {
                    calendarSlideTransition(slideDir, slideSpec, fadeSpec, reduceMotion)
                },
                label = "split-month-transition",
            ) { (s, sel) ->
                SplitMonthGrid(
                    state = s,
                    selected = sel,
                    showWeekNumbers = showWeekNumbers,
                    onSelectDay = onSelectDay,
                )
            }
            SplitExpandHandle(expanded = false, onToggle = onExpand)
        }
        SplitDayPane(
            date = selected,
            today = state.today,
            // Null, not empty: the selection moves to the new month before
            // its data arrives, and a missing key means "not loaded yet".
            // Passing an empty list would claim the day was free.
            events = state.instancesByDay[selected],
            zone = state.zone,
            onOpenDay = onOpenDay,
            onEventClick = onEventClick,
            onCreateEvent = onCreateEvent,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * The split style pulled open: the pane is gone and the month gets the whole
 * screen in the paged style's own vocabulary — real event bars and pills instead
 * of dots. The handle stays, now at the foot of the grid, to pull it back.
 *
 * No selection marker here, deliberately. Once the cells carry real event bars
 * the outline is one mark too many, and it has nothing to say: every day is a tap
 * away from being the selected one, and tapping is what closes this view.
 */
@Composable
private fun SplitMonthExpanded(
    state: MonthUiState.Success,
    /** Marked nowhere here; it only anchors the outline's morph. */
    selected: LocalDate,
    slideDir: Int,
    showWeekNumbers: Boolean,
    swipeModifier: Modifier,
    onPickDay: (LocalDate) -> Unit,
    onCollapse: () -> Unit,
) {
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    // The whole screen drags here, handle included — there is no pane to keep out
    // of it, and the handle is the obvious thing to reach for on the way back.
    Column(Modifier.fillMaxSize().then(swipeModifier)) {
        AnimatedContent(
            targetState = state to selected,
            modifier = Modifier.weight(1f),
            contentKey = { (s, _) -> s.month },
            transitionSpec = {
                calendarSlideTransition(slideDir, slideSpec, fadeSpec, reduceMotion)
            },
            label = "split-expanded-month-transition",
        ) { (s, sel) ->
            MonthGrid(
                state = s,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onPickDay,
                selected = sel,
            )
        }
        SplitExpandHandle(expanded = true, onToggle = onCollapse)
    }
}

/**
 * The grab handle at the seam between grid and pane — the same M3 drag-handle
 * pill a bottom sheet uses, for the same reason: it advertises that the surface
 * moves.
 *
 * The drag itself lives on the grid, not here. This exists so the gesture is
 * findable at all, and it takes taps too — a hidden swipe is no use to someone
 * who never tries it, or who can't make the gesture.
 */
@Composable
private fun SplitExpandHandle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (expanded) R.string.month_split_collapse else R.string.month_split_expand,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SPLIT_HANDLE_ROW_HEIGHT)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = SPLIT_HANDLE_WIDTH, height = SPLIT_HANDLE_HEIGHT)
                // Tagged too, so it slides from the pane's seam down to the foot
                // of the grid rather than blinking out at one and in at the other.
                .morphElement(MonthMorphKey.Handle)
                .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

// --- Split style (#53) ----------------------------------------------------

/**
 * Row height in the split grid. Only a day number and a row of dots to seat, so
 * it's roughly a third of a paged row — which is the point: the space it gives
 * up goes to the day pane below.
 */
private val SPLIT_ROW_HEIGHT = 46.dp
private val SPLIT_DOT_SIZE = 5.dp
// Dots are capped by MAX_EVENT_ROWS, not a constant of their own: they stand for
// the paged grid's lanes, so the two caps have to be the same number or a dot
// would have no bar to become (#53).

/**
 * Rows the split grid always reserves — the most any month needs. A month that
 * fits in fewer pads the remainder with blank rows rather than shrinking, which
 * is what lets the pane below hold still from month to month.
 */
private const val SPLIT_GRID_ROWS = 6

/**
 * The expand handle: M3's drag-handle pill (32×4dp), in a row tall enough to be a
 * comfortable tap target on its own.
 */
private val SPLIT_HANDLE_WIDTH = 32.dp
private val SPLIT_HANDLE_HEIGHT = 4.dp
private val SPLIT_HANDLE_ROW_HEIGHT = 24.dp

/**
 * The split style's grid (#53): the month compressed to day numbers and event
 * dots, with the selected day listed underneath by [SplitDayPane].
 *
 * Tapping selects rather than drilling into the Day view — the pane is the
 * answer to "what's on this day", so opening a whole screen for it would defeat
 * the layout. The full Day view stays one tap away on the pane's date header.
 */
@Composable
internal fun SplitMonthGrid(
    state: MonthUiState.Success,
    selected: LocalDate,
    showWeekNumbers: Boolean,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val month = state.month
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        state.weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().height(SPLIT_ROW_HEIGHT)) {
                if (showWeekNumbers) {
                    WeekNumberGutter(
                        weekStart = week.days.first(),
                        modifier = Modifier
                            .width(WEEK_NUMBER_GUTTER)
                            .fillMaxHeight(),
                    )
                }
                week.days.forEachIndexed { col, day ->
                    val inMonth = day.month == month.month && day.year == month.year
                    // Seated by lane rather than gathered by colour, so each dot
                    // is the event the expanded grid draws in that same lane.
                    val seated = week.laneEvents(col, day, MAX_EVENT_ROWS)
                    SplitDayCell(
                        date = day,
                        events = seated,
                        hidden = week.overflowEvents(col, day, MAX_EVENT_ROWS),
                        isToday = day == state.today,
                        // A page marks only the days its own month owns. Paging
                        // moves the selection before this month's replacement
                        // has loaded, so for a moment the grid is asked to mark
                        // the *next* month's 1st — a date it shows among its
                        // trailing days, where the marker would fade in just
                        // before the page slid away. Selection always lands
                        // inside the month it belongs to, so scoping it here
                        // removes that without timing anything against the
                        // page transition.
                        isSelected = day == selected && inMonth,
                        inMonth = inMonth,
                        dark = dark,
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        // Hold the grid at a constant height whatever shape the month is, so the
        // pane beneath it doesn't move as you page and one month can slide over
        // another without a height change under it.
        repeat(SPLIT_GRID_ROWS - state.weeks.size) {
            Spacer(Modifier.fillMaxWidth().height(SPLIT_ROW_HEIGHT))
        }
    }
}

/**
 * One compact day: its number over up to [MAX_EVENT_ROWS] lane-seated event dots.
 *
 * Selection and today are deliberately different signals — a tinted, outlined
 * cell versus the filled circle the other views already use for today — so the
 * two read at once when they land on the same day.
 */
@Composable
private fun SplitDayCell(
    date: LocalDate,
    events: List<EventInstance>,
    /** Events on this day that didn't fit a lane, shown as a "+N" beside the dots. */
    hidden: List<EventInstance>,
    isToday: Boolean,
    isSelected: Boolean,
    inMonth: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection is the outline's job alone. Tinting the fill as well lightened
    // the whole cell, which read as a second state on top of the day rather than
    // as a mark on it — and fought today's own circle when they coincided.
    val background = when {
        inMonth -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    // Each cell fades its own outline, so moving the selection reads as one
    // mark crossing the grid rather than as an outline blinking out here and
    // reappearing there. Snapped under reduced motion.
    //
    // Held as a State and read *inside the draw lambda below*, never unwrapped
    // here. Read in composition it made every frame of the fade recompose all
    // 42 cells at once, which is what made the outline shiver rather than fade.
    // Kept in the draw phase, the animation touches nothing but the pixels.
    val reduceMotion = rememberReduceMotion()
    // The outline runs on the *fast* effects spec, unlike everything else here.
    // It is a small mark that only ever appears or disappears, and at the shared
    // pace it lingered after the thing it marks had already moved.
    val outlineSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val selection = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else outlineSpec,
        label = "split-day-selection",
    )
    val outlineColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val outlineStroke = with(density) { SPLIT_SELECTION_STROKE.toPx() }
    val outlineRadius = with(density) { CELL_CORNER.toPx() }
    // Background and content are separate layers, mirroring how the paged row is
    // built — and so the pill can be tagged for the morph on its own. Tagged as
    // one piece with its contents inside, the dots would be nested shared
    // elements within a shared element and travel twice.
    Box(
        modifier = modifier
            .padding(horizontal = CELL_GAP, vertical = 1.dp)
            .selectable(selected = isSelected, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .morphElement(MonthMorphKey.Cell(date))
                .clip(CELL_SHAPE)
                .background(background),
        )
        // The outline is a layer of its own, deliberately *untagged*. Drawn on the
        // morphing pill it rode the cell's shared bounds, so collapsing painted it
        // at the expanded cell's size and shrank it down — a full-height outline
        // flashing over a grid that no longer had full-height cells. Untagged it
        // is laid out where the collapsed cell actually is and simply fades in,
        // which is the only size it is ever true at.
        //
        // Tagged only while it is the selected day, and paired with an invisible
        // stand-in over the same cell in the expanded grid, so it travels with its
        // cell instead of fading in place. Tagged unconditionally it would put a
        // key on all 42 cells against the one partner the expanded grid offers,
        // and the other 41 would fly in from the layout origin.
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (isSelected) {
                        Modifier.morphBounds(MonthMorphKey.Selection(date))
                    } else {
                        Modifier
                    },
                )
                .drawBehind {
                    val alpha = selection.value
                    if (alpha <= 0f) return@drawBehind
                    // Inset by half the stroke: a stroke straddles the path it
                    // follows, so drawn on the bounds themselves the outer half
                    // would fall outside the cell and be clipped to a hairline
                    // along the edges.
                    val inset = outlineStroke / 2f
                    drawRoundRect(
                        color = outlineColor.copy(alpha = alpha),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - outlineStroke, size.height - outlineStroke),
                        cornerRadius = CornerRadius(outlineRadius - inset),
                        style = Stroke(width = outlineStroke),
                    )
                },
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DayNumberCell(
                date = date,
                isToday = isToday,
                inMonth = inMonth,
                modifier = Modifier.fillMaxWidth().morphElement(MonthMorphKey.DayNumber(date)),
            )
            Spacer(Modifier.height(2.dp))
            SplitDots(date = date, events = events, hidden = hidden, dark = dark)
        }
    }
}

/**
 * One dot per seated event, in lane order, plus a "+N" for those that didn't fit.
 *
 * Deliberately *not* de-duplicated by colour: [events] arrives lane-seated so the
 * dots line up with the bars the expanded grid draws, and collapsing two events
 * that share a calendar into one dot would both undercount the day and leave a
 * bar with no dot to grow out of.
 */
@Composable
private fun SplitDots(
    date: LocalDate,
    events: List<EventInstance>,
    hidden: List<EventInstance>,
    dark: Boolean,
) {
    if (events.isEmpty()) return
    val soften = LocalSoftenColors.current
    val dimCutoff = LocalDimCutoff.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        events.forEach { event ->
            // The dot keeps its own circle and the bar its 4dp corners; they
            // cross-fade inside shared bounds, and at the dot's size the two
            // radii are a fraction of a pixel apart.
            //
            // morphBounds goes *outside* the size: the shared bounds have to be
            // free to drive the measurement. Behind a fixed .size() the dot stayed
            // 5dp for the whole transition however far its bar had travelled, and
            // only snapped to full width once the transition ended — the growth
            // never happened, it was clamped away.
            //
            // *Every* dot is tagged, including those in the middle of a multi-day
            // run: the expanded grid gives each covered column its own anchor
            // inside the bar, so each one has a real place to come from.
            Box(
                modifier = Modifier
                    .morphBounds(MonthMorphKey.Event(date, event.instanceId))
                    .size(SPLIT_DOT_SIZE)
                    .alpha(if (dimCutoff != null && event.hasEnded(dimCutoff)) EventDimAlpha else 1f)
                    .background(eventAccent(event.color, dark, soften), CircleShape),
            )
        }
        if (hidden.isNotEmpty()) {
            // Tagged, not lifted: this count and the expanded grid's dot row are
            // the same marker on the same day, so it travels with its cell like
            // everything else rather than riding above the grid on its own layer.
            Text(
                text = "+${hidden.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .morphBounds(MonthMorphKey.Overflow(date))
                    .alpha(if (allEnded(hidden, dimCutoff)) EventDimAlpha else 1f),
            )
        }
    }
}

/**
 * The selected day's events, in the agenda's own row vocabulary so the two
 * surfaces read as one app. The date header opens the full Day view, matching
 * the Week and Agenda headers (#37).
 */
@Composable
internal fun SplitDayPane(
    date: LocalDate,
    today: LocalDate,
    /** Null while the selected day's month is still loading; empty means free. */
    events: List<EventInstance>?,
    zone: TimeZone,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimCutoff = LocalDimCutoff.current
    // A day's list used to be replaced outright, so changing day popped one set
    // of rows out and another in. The incoming list rises as it fades, which is
    // the same entrance the rest of the family uses for resolved content.
    val enter = itemEnter()
    val fadeSpec = rememberCalendarFadeSpec()
    Column(modifier = modifier) {
        AgendaDayHeader(date = date, today = today, onOpenDay = onOpenDay)
        AnimatedContent(
            targetState = date to events,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Whether the day is still loading is part of the identity, so the
            // skeleton hands over to the real list with the same transition.
            contentKey = { (d, e) -> d to (e == null) },
            transitionSpec = { enter togetherWith fadeOut(fadeSpec) },
            label = "split-day-pane",
        ) { (day, dayEvents) ->
            when {
                // The day isn't in the loaded grid yet — paging lands the
                // selection on the new month before its events arrive. "Nothing
                // scheduled" here would be a claim about the day rather than
                // about the wait.
                dayEvents == null -> SplitPaneSkeleton()
                dayEvents.isEmpty() -> AgendaEmptyDayRow(
                    text = stringResource(R.string.month_split_no_events),
                    onClick = { onCreateEvent(day) },
                )
                else -> LazyColumn(
                    // Bottom inset clears the FAB stack so the last row stays
                    // tappable.
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    itemsIndexed(
                        items = dayEvents,
                        key = { _, event -> event.instanceId },
                    ) { index, event ->
                        AgendaEventRow(
                            event = event,
                            day = day,
                            zone = zone,
                            position = positionOf(index, dayEvents.size),
                            dimmed = dimCutoff != null && event.hasEnded(dimCutoff),
                            onClick = { onEventClick(event) },
                            // An event added to or removed from the day you are
                            // already looking at moves its neighbours rather
                            // than teleporting them.
                            modifier = animateItemMotion(),
                        )
                    }
                }
            }
        }
    }
}

/** Stand-in rows for a day pane whose month hasn't arrived yet. */
@Composable
private fun SplitPaneSkeleton() {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SPLIT_SKELETON_ROW_HEIGHT)
                    .alpha(pulse)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.shapes.medium,
                    ),
            )
        }
    }
}

private val SPLIT_SKELETON_ROW_HEIGHT = 56.dp

/** A week the sliding window hasn't loaded yet — same height, so nothing jumps. */
@Composable
private fun ContinuousWeekPlaceholder() {
    val pulse = rememberSkeletonPulse()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTINUOUS_ROW_HEIGHT),
    ) {
        repeat(7) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = CELL_GAP, vertical = 1.dp)
                    .alpha(pulse)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CELL_SHAPE),
            )
        }
    }
}

/**
 * The first-frame skeleton for the scrolling styles: the layout they are about
 * to become, at the same measurements, so the arriving months replace it in
 * place instead of shifting everything.
 *
 * In practice the window is small enough that this is rarely on screen for long
 * — it is there for the calendar big enough to make even one month's worth of
 * recurrence expansion take a moment.
 */
@Composable
private fun ContinuousMonthSkeleton(dense: Boolean) {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .clipToBounds(),
    ) {
        if (!dense) {
            // Stand-in for the sticky month header, so the first rows start
            // where they will once the real one is there.
            Box(
                modifier = Modifier
                    .padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
                    .width(SKELETON_HEADER_WIDTH)
                    .height(SKELETON_HEADER_HEIGHT)
                    .alpha(pulse)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.small,
                    ),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
        // More rows than a viewport holds; the clip takes the overflow.
        repeat(6) {
            ContinuousWeekPlaceholder()
            Spacer(Modifier.height(2.dp))
        }
    }
}

private val SKELETON_HEADER_WIDTH = 128.dp
private val SKELETON_HEADER_HEIGHT = 20.dp

/**
 * The slow breath that tells a skeleton from an empty grid. Held at full opacity
 * when the system asks for reduced motion — the placeholders still read as
 * unfilled without it.
 */
@Composable
private fun rememberSkeletonPulse(): Float {
    if (rememberReduceMotion()) return 1f
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )
    return alpha
}

/**
 * One week of the grid. Bars (all-day / multi-day) are positioned absolutely so
 * a multi-day event is one connected bar across the columns; single-day timed
 * events sit beneath them as filled pills in their own cell. The cap is
 * [MAX_EVENT_ROWS] rows of bars+pills, then a "+N" dot indicator per day.
 * A transparent per-day layer on top turns a tap into "open that day".
 */
@Composable
private fun MonthWeekRow(
    week: MonthWeek,
    today: LocalDate,
    inMonth: (LocalDate) -> Boolean,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    blankOutside: Boolean = false,
    labelMonthOnFirst: Boolean = false,
    /**
     * The selected day, when there is one. Draws *nothing* — it only places the
     * invisible counterpart the compact grid's outline morphs against. Null for
     * every style but the split style's expanded form, which composes no extra
     * layer at all.
     */
    selected: LocalDate? = null,
) {
    val dark = isSystemInDarkTheme()
    val laneCount = (week.spans.maxOfOrNull { it.lane } ?: -1) + 1
    val shownLanes = laneCount.coerceAtMost(MAX_EVENT_ROWS)
    val morphing = morphInFlight()

    // Drag to reschedule (#68). The chips can take no pointer input of their own
    // — the full-bleed tap layer sits on top of them — so one detector on the
    // row's day-column box hit-tests them geometrically instead.
    val moveScope = LocalEventMove.current
    val dragController = LocalMonthDrag.current
    val rowToken = remember { Any() }
    val bandCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val cellCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { EVENT_ROW_HEIGHT.toPx() }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val dragging = dragController?.isDragging == true
    DisposableEffect(rowToken, dragController) {
        onDispose { dragController?.removeRow(rowToken) }
    }
    // Republished on layout *and* on every recomposition: the coordinates change
    // only on the former, but what the row draws — which the controller reads to
    // find a moved chip's seat — changes on the latter.
    val publish = {
        val cell = cellCoordinates[0]?.takeIf { it.isAttached }
        if (dragController != null && cell != null) {
            dragController.putRow(
                rowToken,
                MonthRowGeometry(
                    days = week.days,
                    cell = cell,
                    band = bandCoordinates[0],
                    columnWidthPx = cell.size.width / 7f,
                    laneHeightPx = rowHeightPx,
                    laneCount = MAX_EVENT_ROWS,
                    isRtl = isRtl,
                    chipAt = { col, lane -> week.chipAt(col, lane, MAX_EVENT_ROWS) },
                ),
            )
        }
    }
    SideEffect { publish() }
    // Once the grid holds the settled chip, tell the controller where this row
    // has seated it, so the copy in flight can glide onto it. Keyed on the week,
    // which is what changes when the re-read lands.
    LaunchedEffect(week, dragController?.settling) {
        dragController?.noteSettled(rowToken)
    }

    Row(modifier) {
        // Optional calendar-week gutter, sized so the seven day columns below
        // divide the remaining width — the absolute bar offsets stay correct
        // because they're measured inside the grid box, not the whole row.
        if (showWeekNumbers) {
            WeekNumberGutter(
                weekStart = week.days.first(),
                modifier = Modifier
                    .width(WEEK_NUMBER_GUTTER)
                    .fillMaxHeight(),
            )
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { coords ->
                    cellCoordinates[0] = coords
                    publish()
                }
                .then(
                    monthChipDragModifier(
                        week = week,
                        moveScope = moveScope,
                        controller = dragController,
                        band = bandCoordinates,
                        rowHeightPx = rowHeightPx,
                        isRtl = isRtl,
                    ),
                ),
        ) {
            val colW = maxWidth / 7

            // Per-day background pills — same surfaceContainer rounded surface the
            // week/day views use, so the three views share one visual language.
            // Spanning bars draw on top of these, bridging cells, so they still read
            // as one continuous event.
            Row(Modifier.matchParentSize()) {
                week.days.forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = CELL_GAP, vertical = 1.dp)
                            .morphElement(MonthMorphKey.Cell(d))
                            .background(
                                color = when {
                                    inMonth(d) -> MaterialTheme.colorScheme.surfaceContainer
                                    // A blanked cell draws nothing at all: it is
                                    // the gap that shows where the month starts.
                                    blankOutside -> Color.Transparent
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = CELL_SHAPE,
                            ),
                    )
                }
            }

            Column(Modifier.fillMaxSize().padding(top = CELL_TOP_PADDING)) {
                Row(Modifier.fillMaxWidth()) {
                    week.days.forEach { d ->
                        if (blankOutside && !inMonth(d)) {
                            Spacer(Modifier.weight(1f).height(DAY_NUMBER_HEIGHT))
                        } else {
                            DayNumberCell(
                                date = d,
                                isToday = d == today,
                                inMonth = inMonth(d),
                                // Dense has no month boundaries to dim across and
                                // no header to name the month, so the 1st does it.
                                monthLabel = if (labelMonthOnFirst && d.day == 1) {
                                    shortMonthName(d)
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .morphElement(MonthMorphKey.DayNumber(d)),
                            )
                        }
                    }
                }
                // Breathing room between the day number (and today's circle) and the
                // first event row.
                Spacer(Modifier.height(DAY_NUMBER_GAP))
                // Clipped at rest so a bar can't spill into the row below, but not
                // while a morph is in flight: a mark travels between two different
                // rows, and clipping it to the one it is arriving at hides it for
                // the whole first half of the journey. See [morphInFlight].
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onGloballyPositioned {
                            bandCoordinates[0] = it
                            publish()
                        }
                        // A dragged chip travels to another row, so the clip
                        // yields for it as it does for a morph.
                        .then(if (morphing || dragging) Modifier else Modifier.clipToBounds()),
                ) {
                    // Spanning bars on their shared lanes.
                    week.spans.filter { it.lane < shownLanes }.forEach { span ->
                        val cols = span.endCol - span.startCol + 1
                        MonthBar(
                            event = span.event,
                            dark = dark,
                            continuesLeft = span.continuesLeft,
                            continuesRight = span.continuesRight,
                            days = week.days.subList(span.startCol, span.endCol + 1),
                            modifier = Modifier
                                .offset(
                                    x = colW * span.startCol,
                                    y = EVENT_ROW_HEIGHT * span.lane,
                                )
                                // Anchored on the day the bar starts *in this row*,
                                // which is where its dot sat. A bar carried in from
                                // the previous week starts at column 0, and column
                                // 0's dot is the one that grows into it.
                                //
                                // Outside the width/height, so the shared bounds
                                // drive the measurement instead of being pinned to
                                // the bar's final size from the first frame. The
                                // offset stays outside it: that is where the bar
                                // sits, not how big it is.
                                .morphBounds(
                                    MonthMorphKey.Event(
                                        date = week.days[span.startCol],
                                        instanceId = span.event.instanceId,
                                    ),
                                )
                                .width(colW * cols)
                                .height(EVENT_ROW_HEIGHT)
                                .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp),
                        )
                        // One invisible slice of the bar per further column it
                        // covers. A multi-day event has a dot on every day but
                        // only one bar, so those other dots had nothing to travel
                        // to and sat still while the rest of the grid moved.
                        //
                        // These give each of them a real place inside the bar to
                        // come out of and go back into, at its own column — so a
                        // dot drops out of the bar above it rather than appearing
                        // from nowhere, or from the top of the grid, which is what
                        // an untagged dot and a partnerless tag respectively did.
                        for (c in (span.startCol + 1)..span.endCol) {
                            Box(
                                Modifier
                                    .offset(x = colW * c, y = EVENT_ROW_HEIGHT * span.lane)
                                    .morphBounds(
                                        MonthMorphKey.Event(
                                            date = week.days[c],
                                            instanceId = span.event.instanceId,
                                        ),
                                    )
                                    .width(colW)
                                    .height(EVENT_ROW_HEIGHT)
                                    .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp),
                            )
                        }
                    }
                    // Single-day timed pills + overflow, per column. Pills fill the
                    // lane slots no bar occupies on THIS day (top-most first), so a
                    // bar-free day isn't pushed down by a multi-day event that only
                    // sits on other days of the week.
                    week.days.forEachIndexed { col, d ->
                        val timed = week.timedByDay[d].orEmpty()
                        val occupied = week.spans
                            .filter { it.lane < shownLanes && col in it.startCol..it.endCol }
                            .map { it.lane }
                            .toSet()
                        val freeSlots = (0 until MAX_EVENT_ROWS).filter { it !in occupied }
                        val pillsShown = timed.take(freeSlots.size)
                        pillsShown.forEachIndexed { i, ev ->
                            MonthBar(
                                event = ev,
                                dark = dark,
                                continuesLeft = false,
                                continuesRight = false,
                                days = listOf(d),
                                modifier = Modifier
                                    .offset(
                                        x = colW * col,
                                        y = EVENT_ROW_HEIGHT * freeSlots[i],
                                    )
                                    .morphBounds(MonthMorphKey.Event(d, ev.instanceId))
                                    .width(colW)
                                    .height(EVENT_ROW_HEIGHT)
                                    .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp),
                            )
                        }
                        val hidden = (week.countByDay[d] ?: 0) - occupied.size - pillsShown.size
                        if (hidden > 0) {
                            val hiddenEvents = buildList {
                                week.spans
                                    .filter { it.lane >= shownLanes && col in it.startCol..it.endCol }
                                    .forEach { add(it.event) }
                                addAll(timed.drop(pillsShown.size))
                            }
                            OverflowDots(
                                events = hiddenEvents,
                                total = hidden,
                                dark = dark,
                                modifier = Modifier
                                    .offset(x = colW * col, y = EVENT_ROW_HEIGHT * MAX_EVENT_ROWS)
                                    .morphBounds(MonthMorphKey.Overflow(d))
                                    .width(colW)
                                    .padding(horizontal = 3.dp),
                            )
                        }
                    }
                }
            }

            // Invisible stand-in for the compact grid's selection outline, so it
            // has somewhere to travel to and from. Its own layer rather than a
            // child of the cell pill: that pill is itself a tagged piece, and a
            // tag nested inside a tag travels twice. Padded to match the outline's
            // own bounds over there, or it would arrive at the wrong size.
            if (selected != null) {
                Row(Modifier.matchParentSize()) {
                    week.days.forEach { d ->
                        if (d == selected && inMonth(d)) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = CELL_GAP, vertical = 1.dp)
                                    .morphBounds(MonthMorphKey.Selection(d)),
                            )
                        } else {
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }

            // Tap layer: in month view a tap on any day opens that day. Padded and
            // clipped to the background pill so the ripple matches it. A blanked
            // cell isn't part of this month, so it takes no taps either.
            Row(Modifier.matchParentSize()) {
                week.days.forEach { d ->
                    if (blankOutside && !inMonth(d)) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = CELL_GAP, vertical = 1.dp)
                                .clip(CELL_SHAPE)
                                .clickable { onOpenDay(d) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The row-level pickup for month chips: resolves which chip the press landed on
 * from the geometry the row just laid out, and abandons the gesture on empty
 * space (or an unmovable calendar) so tapping a day still opens it.
 */
@Composable
private fun monthChipDragModifier(
    week: MonthWeek,
    moveScope: EventMoveScope?,
    controller: MonthDragController?,
    band: Array<LayoutCoordinates?>,
    rowHeightPx: Float,
    isRtl: Boolean,
): Modifier = rememberDragSurface(
    enabled = moveScope?.dragEnabled == true && controller != null,
    key = week.days.first(),
    onPickUp = { local, pointerInRoot, nodeInRoot, size ->
        val bandTop = band[0]?.takeIf { it.isAttached }?.positionInRoot()?.y
        val columnPx = size.width / 7f
        val bandY = if (bandTop == null) -1f else local.y - (bandTop - nodeInRoot.y)
        val lane = (bandY / rowHeightPx).toInt()
        // The column under the finger on screen, and the day that column shows.
        val column = (local.x / columnPx).toInt()
        val dayIndex = if (isRtl) week.days.lastIndex - column else column
        val event = if (bandY < 0f || columnPx <= 0f) {
            null
        } else {
            week.chipAt(dayIndex, lane, MAX_EVENT_ROWS)
        }
        if (event == null || moveScope?.allows(event) != true) {
            false
        } else {
            controller?.begin(
                event = event,
                grabDate = week.days[dayIndex],
                pointerInRoot = pointerInRoot,
                chipInRoot = Offset(
                    x = nodeInRoot.x + column * columnPx,
                    y = requireNotNull(bandTop) + lane * rowHeightPx,
                ),
                size = IntSize(columnPx.toInt(), rowHeightPx.toInt()),
            )
            true
        }
    },
    onMove = { controller?.move(it) },
    onDrop = {
        controller?.finish()?.let { drop ->
            // How many columns the finger crossed — grabbing the middle of a
            // multi-day bar shifts by what it travelled, not to where it landed.
            val delta = (drop.targetDate.toEpochDays() - drop.grabDate.toEpochDays()).toInt()
            val took = moveScope?.move(
                MoveRequest(
                    eventId = drop.event.eventId,
                    beginMillis = drop.event.start.toEpochMilliseconds(),
                    endMillis = drop.event.end.toEpochMilliseconds(),
                    target = MoveTarget.ByDays(delta),
                ),
            )
            // Refused, so nothing will land: let the copy go now rather than
            // hold the chip out for a settle that can never arrive.
            if (took != true) controller?.release()
        }
    },
    onCancel = { controller?.cancel() },
)

/**
 * Left-gutter calendar-week cell (#25): a full-height tonal pill mirroring the
 * day cells' geometry, set apart by the secondaryContainer tint (matching the
 * Week view's badge), with the ISO week number centred like a day number. The
 * week is computed on the row's first day — the same basis as the Week view — so
 * the two agree.
 */
@Composable
private fun WeekNumberGutter(weekStart: LocalDate, modifier: Modifier = Modifier) {
    val weekNumber = remember(weekStart) { weekStart.toJavaLocalDate().isoWeekNumber() }
    val label = stringResource(R.string.week_number_label)
    Box(
        modifier = modifier
            .padding(horizontal = CELL_GAP, vertical = 1.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CELL_SHAPE)
            .semantics { contentDescription = "$label $weekNumber" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun DayNumberCell(
    date: LocalDate,
    isToday: Boolean,
    inMonth: Boolean,
    modifier: Modifier = Modifier,
    monthLabel: String? = null,
) {
    Box(
        modifier = modifier.height(DAY_NUMBER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            Box(
                modifier = Modifier
                    .size(DAY_NUMBER_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.day.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else if (monthLabel != null) {
            Text(
                text = "$monthLabel ${date.day}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                softWrap = false,
            )
        } else {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/** Locale-short month name ("Jul", "Juli"), for the 1st in the Dense grid. */
@Composable
private fun shortMonthName(date: LocalDate): String {
    val locale = currentLocale()
    return remember(date.month, locale) {
        java.time.Month.of(date.month.ordinal + 1)
            .getDisplayName(JavaTextStyle.SHORT, locale)
    }
}

/** A filled event pill/bar — softened (or raw) fill, title clipped to one line. */
@Composable
private fun MonthBar(
    event: de.jeanlucmakiola.calendula.domain.EventInstance,
    dark: Boolean,
    continuesLeft: Boolean,
    continuesRight: Boolean,
    modifier: Modifier = Modifier,
    /**
     * The days this chip covers in its row, for the drag (#68). A drop's own chip
     * has to ghost until the copy lands on it, and the instance id can't find it:
     * the provider hands the re-read instance a new one.
     */
    days: List<LocalDate>? = null,
) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && event.hasEnded(dimCutoff)
    val soften = LocalSoftenColors.current
    val fill = eventFill(event.color, dark, soften)
    val moveAction = eventMoveAction(event)
    // The source stays put as a ghost while its floating copy travels.
    val monthDrag = LocalMonthDrag.current
    val lifted = monthDrag?.liftedInstanceId == event.instanceId ||
        monthDrag?.isSettledChip(event, days) == true
    val ghost = ghostAlpha(lifted)
    val shape = RoundedCornerShape(
        topStart = if (continuesLeft) 0.dp else 4.dp,
        bottomStart = if (continuesLeft) 0.dp else 4.dp,
        topEnd = if (continuesRight) 0.dp else 4.dp,
        bottomEnd = if (continuesRight) 0.dp else 4.dp,
    )
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .then(if (ghost < 1f) Modifier.alpha(ghost) else Modifier)
            .background(fill, shape)
            .padding(horizontal = 4.dp)
            .semantics {
                contentDescription = title
                if (moveAction != null) customActions = listOf(moveAction)
            },
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

/**
 * Overflow row: a dot per hidden colour (up to three) plus "+N" for the rest. A
 * dot dims once every event sharing its colour has ended, the "+N" once the
 * whole overflow has (#79).
 */
@Composable
private fun OverflowDots(
    events: List<EventInstance>,
    total: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val soften = LocalSoftenColors.current
    val dimCutoff = LocalDimCutoff.current
    val byColor = events.groupBy { it.color }
    val dots = byColor.keys.take(3)
    Row(
        modifier = modifier.height(EVENT_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(if (allEnded(byColor.getValue(argb), dimCutoff)) EventDimAlpha else 1f)
                    .background(eventAccent(argb, dark, soften), CircleShape),
            )
        }
        val extra = total - dots.size
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (allEnded(events, dimCutoff)) EventDimAlpha else 1f),
            )
        }
    }
}

/** True when dimming is on and every one of [events] is already over. */
private fun allEnded(events: List<EventInstance>, dimCutoff: Instant?): Boolean =
    dimCutoff != null && events.isNotEmpty() && events.all { it.hasEnded(dimCutoff) }

@Composable
private fun MonthGridLoading() {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = shape,
                            ),
                    )
                }
            }
        }
    }
}

private fun formatMonthTitle(ym: YearMonth, locale: Locale, currentYear: Int): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(ym.year, ym.month.ordinal + 1, 1),
        locale = locale,
        currentYear = currentYear,
        skeleton = "LLLL",
    )
