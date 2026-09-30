package de.jeanlucmakiola.calendula.ui.month

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import de.jeanlucmakiola.calendula.ui.common.PublishChrome
import de.jeanlucmakiola.calendula.ui.common.ScreenActions
import de.jeanlucmakiola.calendula.ui.common.ScreenBody
import de.jeanlucmakiola.calendula.ui.common.ScreenChrome
import de.jeanlucmakiola.calendula.ui.common.LocalViewMorph
import de.jeanlucmakiola.calendula.ui.common.EnterOnFocus
import de.jeanlucmakiola.calendula.ui.common.LocalViewFocus
import de.jeanlucmakiola.calendula.ui.common.ViewMorphKey
import de.jeanlucmakiola.calendula.ui.common.morphDay
import de.jeanlucmakiola.calendula.ui.common.morphEvent
import de.jeanlucmakiola.calendula.ui.common.morphPageActive
import de.jeanlucmakiola.calendula.ui.common.jumpToPage
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSpec
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import de.jeanlucmakiola.calendula.ui.common.eventPaint
import de.jeanlucmakiola.calendula.ui.common.eventSurface
import de.jeanlucmakiola.calendula.ui.common.monthBarCuts
import kotlin.math.roundToInt
import kotlin.math.abs
import de.jeanlucmakiola.calendula.ui.common.rememberDragSurface
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.eventTitleOverflow
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.moveInFlight
import de.jeanlucmakiola.calendula.ui.common.abandonedMoves
import de.jeanlucmakiola.calendula.ui.common.OnUndoStarted
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.DragSnapHaptics
import de.jeanlucmakiola.calendula.ui.common.SETTLE_FADE_MILLIS
import de.jeanlucmakiola.calendula.ui.common.SETTLE_GRACE_MILLIS
import de.jeanlucmakiola.calendula.ui.common.EventMoveScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDayHeader
import de.jeanlucmakiola.calendula.ui.agenda.AgendaEmptyDayRow
import de.jeanlucmakiola.calendula.ui.agenda.AgendaEventRow
import de.jeanlucmakiola.calendula.ui.agenda.agendaDayLabel
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.AppBarSpacing
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.inlineTimeLabel
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.EventChipShape
import de.jeanlucmakiola.calendula.ui.common.trimmedLines
import de.jeanlucmakiola.calendula.ui.common.monthBarShape
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.animateItemMotion
import de.jeanlucmakiola.floret.identity.itemEnter
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
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
import kotlin.time.Clock
import kotlin.time.Instant
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val LocalTodayPulse = staticCompositionLocalOf { 0 }
private val LocalOpenWeek = staticCompositionLocalOf<((LocalDate) -> Unit)?> { null }

/** A brief push from today's full cell into its immediate neighbours. */
@Composable
private fun rememberTodayRipple(active: Boolean): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val pulse = remember { Animatable(0f) }
    val pulseKey = LocalTodayPulse.current
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(pulseKey, active, reduceMotion) {
        if (active && pulseKey > 0 && !reduceMotion) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 180))
            pulse.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            pulse.snapTo(0f)
        }
    }
    return pulse
}

/** Today's cell: a light primary tint over the ordinary cell, set apart without shouting (#241). */
@Composable
private fun todayCellColor(): Color =
    MaterialTheme.colorScheme.primaryContainer.copy(alpha = TODAY_CELL_TINT)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainer)

private const val TODAY_CELL_TINT = 0.45f

/** Paint-only motion keeps hit targets and the month grid's lane geometry stable. */
private fun Modifier.todayRipple(relativeColumn: Int?, progress: () -> Float): Modifier {
    if (relativeColumn == null || abs(relativeColumn) > 1) return this
    return graphicsLayer {
        val pulse = progress()
        if (relativeColumn == 0) {
            scaleX = 1f + 0.10f * pulse
            scaleY = 1f + 0.06f * pulse
        } else {
            translationX = (if (relativeColumn < 0) -1f else 1f) * 4.dp.toPx() * pulse
            scaleX = 1f - 0.025f * pulse
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    onOpenDay: (LocalDate) -> Unit,
    onOpenWeek: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel = hiltViewModel(),
) {
    // Opens on the focused date's month, and hands its selection back as it moves.
    val focus = LocalViewFocus.current
    EnterOnFocus(viewModel) { viewModel.goToDate(it) }
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

    val scope = rememberCoroutineScope()

    val scrolling = viewStyle.isScrolling
    var pendingScrollMonth by remember { mutableStateOf<YearMonth?>(null) }
    var scrollRequestId by remember { mutableIntStateOf(0) }

    // The paged and split styles' months. Made afresh whenever a paged style
    // comes on screen, opening on the month the scrolling style was showing: a
    // pager that was never laid out can't be scrolled to follow it meanwhile.
    val anchorPage = monthIndexOf(month)
    val pagerState = key(scrolling) {
        rememberPagerState(initialPage = anchorPage) { continuousMonthCount() }
    }
    val pageSpec = rememberCalendarPageSpec()
    val reduceMotion = rememberReduceMotion()
    // A swipe moves the pager and the anchor follows once it settles; anything
    // else that moves the anchor has the pager follow it instead.
    LaunchedEffect(anchorPage, scrolling) {
        if (scrolling || pagerState.currentPage == anchorPage || pagerState.isScrollInProgress) {
            return@LaunchedEffect
        }
        pagerState.jumpToPage(anchorPage, reduceMotion, pageSpec)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect(viewModel::onPageSettled)
    }
    // Keep the shared focus on the visible month or its selected day.
    LaunchedEffect(viewModel) {
        snapshotFlow { month to selectedDate }.drop(1).collect { (visible, selected) ->
            val current = focus?.date
            focus?.date = selected ?: current?.takeIf {
                it.year == visible.year && it.month == visible.month
            } ?: LocalDate(visible.year, visible.month, 1)
        }
    }
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
    // The month under the finger, so the title turns over mid-swipe, not after it.
    val titleMonth = if (scrolling) visibleMonth else yearMonthForIndex(pagerState.currentPage)

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
        snapshotFlow { visibleMonth to pendingScrollMonth }
            .distinctUntilChanged()
            .collect { (visible, pending) ->
                if (pending == null || pending == visible) {
                    viewModel.syncScrollMonth(visible)
                    if (pending == visible) pendingScrollMonth = null
                }
            }
    }

    val isOnCurrentMonth = titleMonth == YearMonth(today.year, today.month)
    var splitExpanded by rememberSaveable(viewStyle) {
        mutableStateOf(viewStyle == MonthViewStyle.Paged)
    }
    var todayPulse by remember { mutableIntStateOf(0) }

    // Continuous names the month on the block's own sticky header, so the bar
    // carries the year instead of repeating it two lines further down. Dense has
    // no header to defer to, so it keeps the full title.
    val locale = currentLocale()
    val (topBarTitle, topBarShortTitle) = remember(viewStyle, titleMonth, locale, today.year) {
        if (viewStyle == MonthViewStyle.Continuous) {
            val year = titleMonth.year.toString()
            year to year
        } else {
            formatMonthTitle(titleMonth, locale, currentYear = today.year) to
                formatMonthTitle(titleMonth, locale, currentYear = today.year, abbreviated = true)
        }
    }

    // Straight to the pager as well as the anchor: a tap mid-fling must still
    // land, and the anchor hasn't caught up with the fling yet to tell the
    // difference. The continuous stream scrolls to the month instead.
    val jumpToMonth: (YearMonth) -> Unit = { ym ->
        scope.launch { pagerState.jumpToPage(monthIndexOf(ym), reduceMotion, pageSpec) }
    }
    val jumpToToday: () -> Unit = {
        todayPulse++
        if (scrolling) {
            val targetMonth = YearMonth(today.year, today.month)
            pendingScrollMonth = targetMonth
            val requestId = ++scrollRequestId
            viewModel.goToToday()
            scope.launch {
                try {
                    listState.animateScrollToItem(
                        if (dense) weekIndexOf(today, weekStart)
                        else itemIndexForMonth(monthIndexOf(targetMonth)),
                    )
                } finally {
                    if (scrollRequestId == requestId) {
                        pendingScrollMonth = null
                    }
                }
            }
        } else {
            viewModel.goToToday()
            jumpToMonth(YearMonth(today.year, today.month))
        }
    }
    // Drawer jump-to-date.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        if (scrolling) {
            val targetMonth = YearMonth(target.year, target.month)
            pendingScrollMonth = targetMonth
            val requestId = ++scrollRequestId
            viewModel.goToDate(target)
            scope.launch {
                try {
                    listState.animateScrollToItem(
                        if (dense) weekIndexOf(LocalDate(target.year, target.month, 1), weekStart)
                        else itemIndexForMonth(monthIndexOf(targetMonth)),
                    )
                } finally {
                    if (scrollRequestId == requestId) {
                        pendingScrollMonth = null
                    }
                }
            }
        } else {
            viewModel.goToDate(target)
            jumpToMonth(YearMonth(target.year, target.month))
        }
    }

    PublishChrome(
        ScreenChrome(
            title = topBarTitle,
            shortTitle = topBarShortTitle,
            titleDate = LocalDate(titleMonth.year, titleMonth.month, 1),
            drawerDate = LocalDate(titleMonth.year, titleMonth.month, 1),
            awayFromToday = !isOnCurrentMonth,
            todayText = stringResource(R.string.month_today_action),
        ),
        ScreenActions(
            onToday = jumpToToday,
            onJumpToDate = jumpToDate,
            onCreate = {
                // Split has a selected day; the other styles anchor on today when
                // its month is shown, else the 1st.
                onCreateEvent(
                    when {
                        (viewStyle == MonthViewStyle.Split ||
                            (viewStyle == MonthViewStyle.Paged && !splitExpanded)) -> selectedDate
                            ?: if (isOnCurrentMonth) today else LocalDate(titleMonth.year, titleMonth.month, 1)
                        isOnCurrentMonth -> today
                        else -> LocalDate(titleMonth.year, titleMonth.month, 1)
                    },
                    null,
                )
            },
        ),
    )

    ScreenBody(modifier = modifier) { innerPadding ->
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
                LocalTodayPulse provides todayPulse,
                LocalOpenWeek provides onOpenWeek,
            ) {
                if (scrolling) {
                    ContinuousMonthContent(
                        state = continuousState,
                        listState = listState,
                        dense = dense,
                        showWeekNumbers = showWeekNumbers,
                        onRetry = jumpToToday,
                        onOpenDay = onOpenDay,
                        onEventClick = onEventClick,
                    )
                } else if (viewStyle == MonthViewStyle.Split || viewStyle == MonthViewStyle.Paged) {
                    SplitMonthContent(
                        state = state,
                        selected = selectedDate,
                        initiallyExpanded = viewStyle == MonthViewStyle.Paged,
                        expanded = splitExpanded,
                        onExpandedChange = { splitExpanded = it },
                        pagerState = pagerState,
                        weekStart = weekStart,
                        monthState = viewModel::month,
                        showWeekNumbers = showWeekNumbers,
                        onPaneSwipeNext = { viewModel.stepSplitPane(1) },
                        onPaneSwipePrev = { viewModel.stepSplitPane(-1) },
                        onClearSelection = viewModel::clearSelection,
                        onRetry = jumpToToday,
                        // A tapped leading/trailing day follows to its own
                        // month, pager and all — straight away, or a swipe
                        // still settling would land and take the tap back.
                        onSelectDay = { date ->
                            viewModel.selectDate(date)
                            jumpToMonth(YearMonth(date.year, date.month))
                        },
                        onOpenDay = onOpenDay,
                        onEventClick = onEventClick,
                        onCreateEvent = { onCreateEvent(it, null) },
                    )
                } else {
                    MonthContent(
                        state = state,
                        pagerState = pagerState,
                        weekStart = weekStart,
                        monthState = viewModel::month,
                        showWeekNumbers = showWeekNumbers,
                        onRetry = jumpToToday,
                        onOpenDay = onOpenDay,
                        onEventClick = onEventClick,
                    )
                }
            }
        }
        MonthDragOverlay(chipDrag)
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
    val timeChipWidth = rememberMonthTimeChipWidth()
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
    val abandoned = abandonedMoves()
    LaunchedEffect(controller.settling, moveInFlight, seat, abandoned) {
        val landed = controller.settling
        if (landed == null || moveInFlight) return@LaunchedEffect
        if (seat == null) {
            // Past the mark this drop was let go on, it was abandoned and no
            // seat is ever coming.
            if (abandoned == controller.settledAbandonedMark) delay(MONTH_SETTLE_TIMEOUT_MILLIS)
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
            // The time the source chip carried, drawn only if this one-column
            // cut-out of it has the room — which a slice of a multi-day bar has
            // not, however wide the bar was.
            time = drag.time,
            showTime = with(density) { drag.sizePx.width.toDp() } >= timeChipWidth,
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
                .padding(horizontal = MONTH_CHIP_INSET, vertical = 1.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.04f * lift
                    scaleY = 1f + 0.04f * lift
                    shadowElevation = 8.dp.toPx() * lift
                    alpha = copyAlpha
                    shape = EventChipShape
                    clip = false
                },
        )
    }
}

@Composable
private fun MonthContent(
    state: MonthUiState,
    pagerState: PagerState,
    weekStart: DayOfWeek,
    monthState: (YearMonth, DayOfWeek) -> StateFlow<MonthUiState>,
    showWeekNumbers: Boolean,
    onRetry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
) {
    val fadeSpec = rememberCalendarFadeSpec()
    // Loading and failure concern the provider as a whole, so they stand in for
    // the pager; a page still loading its own month handles that itself.
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize(),
        contentKey = { it::class },
        transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
        label = "month-state",
    ) { s ->
        when (s) {
            MonthUiState.Loading -> MonthGridLoading()
            is MonthUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
            is MonthUiState.Success -> MonthPager(
                pagerState = pagerState,
                weekStart = weekStart,
                monthState = monthState,
                modifier = Modifier.fillMaxSize(),
            ) { _, page ->
                MonthGrid(
                    state = page,
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

/**
 * The paged and split styles' months as pages that follow the finger and snap
 * once a swipe passes half a page or is flung (#336). Only the page on screen
 * carries the chip drag and the morph tags: its neighbours show some of its
 * dates among their leading and trailing days, and would otherwise claim its
 * drops and its shared elements.
 */
@Composable
private fun MonthPager(
    pagerState: PagerState,
    weekStart: DayOfWeek,
    monthState: (YearMonth, DayOfWeek) -> StateFlow<MonthUiState>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit = { MonthGridLoading() },
    page: @Composable (index: Int, state: MonthUiState.Success) -> Unit,
) {
    val chipDrag = LocalMonthDrag.current
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        // The months either side are composed ahead, so a swipe lands on a loaded
        // month rather than watching it fill in.
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top,
        // A held chip moves within its month; the page stays put under it.
        userScrollEnabled = chipDrag?.isDragging != true,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = rememberCalendarPageSpec(),
        ),
    ) { index ->
        MonthPage(
            index = index,
            active = index == pagerState.currentPage,
            morphActive = morphPageActive(index, pagerState.settledPage),
            weekStart = weekStart,
            monthState = monthState,
            loading = loading,
            page = page,
        )
    }
}

@Composable
private fun MonthPage(
    index: Int,
    active: Boolean,
    morphActive: Boolean,
    weekStart: DayOfWeek,
    monthState: (YearMonth, DayOfWeek) -> StateFlow<MonthUiState>,
    loading: @Composable () -> Unit,
    page: @Composable (index: Int, state: MonthUiState.Success) -> Unit,
) {
    val ym = yearMonthForIndex(index)
    val state by remember(ym, weekStart) { monthState(ym, weekStart) }.collectAsStateWithLifecycle()
    val chipDrag = LocalMonthDrag.current
    val morph = LocalMonthMorph.current
    val viewMorph = LocalViewMorph.current
    CompositionLocalProvider(
        LocalMonthDrag provides chipDrag.takeIf { active },
        LocalMonthMorph provides morph.takeIf { active },
        LocalViewMorph provides viewMorph.takeIf { morphActive },
    ) {
        when (val s = state) {
            is MonthUiState.Success -> page(index, s)
            else -> loading()
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
    onEventClick: (EventInstance) -> Unit,
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
                onEventClick = onEventClick,
            )
        } else {
            ContinuousMonthGrid(
                state = state,
                listState = listState,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onOpenDay,
                onEventClick = onEventClick,
            )
        }
    }
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
            .padding(horizontal = AppBarSpacing.Inset, vertical = 4.dp),
    ) {
        // Reserve the gutter so the weekday labels stay over their day columns.
        if (showWeekNumbers) Spacer(Modifier.width(rememberWeekNumberGutter()))
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
/** Padding between the week-number pill's edge and the number inside it. */
private val WEEK_NUMBER_PADDING = 6.dp

/** The widest week number an ISO year reaches; digits are tabular, so one
 *  measurement of it prices every week in the grid. */
private const val WEEK_NUMBER_SAMPLE = "53"

/**
 * Width of the optional left calendar-week gutter (#25), measured rather than
 * fixed: it is sized to the number it seats at the style the pill draws it in,
 * so it follows the font scale instead of reserving slack for it, and spends
 * nothing more on a column the grid would rather hand to the seven days (#213).
 */
@Composable
private fun rememberWeekNumberGutter(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = weekNumberStyle()
    return remember(style, density, measurer) {
        val text = with(density) { measurer.measure(WEEK_NUMBER_SAMPLE, style).size.width.toDp() }
        (text + (WEEK_NUMBER_PADDING + CELL_GAP) * 2).coerceAtLeast(48.dp)
    }
}

/** The week number's own style — a step down from the day numbers beside it. */
@Composable
private fun weekNumberStyle() =
    MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
private val DAY_NUMBER_GAP = 4.dp
private val CELL_TOP_PADDING = 6.dp
/** Named separately because the split style's selection outline draws its own
 *  rounded rect and has to match this radius exactly. */
private val CELL_CORNER = 12.dp
private val CELL_SHAPE = RoundedCornerShape(CELL_CORNER)

/** Width of the split style's selected-day outline. */
private val SPLIT_SELECTION_STROKE = 1.5.dp
/**
 * Dots a split-style cell draws. Fixed, unlike the paged grid's measured cap:
 * the split rows are a fixed height whatever the screen is.
 *
 * Dot *i* is lane *i*, so a dot morphs into the bar the expanded grid draws
 * there (#53) — [MonthWeek.laneEvents] seats the same events in the same order
 * at any cap, so a larger one only appends. Where the expanded grid seats fewer
 * lanes than this — a six-row month in a short landscape viewport — the dots
 * past its cap have no bar to become and simply fade instead of travelling.
 */
internal const val SPLIT_DOT_LANES = 3

/** Diameter of a single overflow dot. */
private val OVERFLOW_DOT_SIZE = 6.dp

/**
 * Height of the overflow row: the "+N" beside the dots, measured at the style it
 * is drawn in.
 *
 * It was taking a whole event lane, which is more than a dot and a label line
 * need and one lane fewer for the chips. Measured rather than picked, so it
 * still holds the label at a large font scale — a fixed height clipped the "+N"
 * at anything above the default.
 */
@Composable
private fun rememberOverflowRowHeight(): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelSmall
    return remember(style, density, measurer) {
        with(density) { measurer.measure(OVERFLOW_SAMPLE, style).size.height.toDp() }
            .coerceAtLeast(OVERFLOW_DOT_SIZE)
    }
}

/** The tallest the counter gets; digits are tabular, so one is as wide as any. */
private const val OVERFLOW_SAMPLE = "+9"

/**
 * Lanes of chips this week's cells draw in a row [rowHeight] tall, before the
 * rest become dots.
 *
 * The cap used to be a flat three at every size, so a tall five-row month threw
 * away lanes it had the room for while a cramped six-row one drew a third chip
 * its band could not hold and put the dots below the clip, where nothing showed
 * that the day held more at all.
 *
 * Measured instead, with no ceiling: a cell spends whatever height it was given.
 * The overflow row is only charged for when some day in the week actually
 * overflows — a week that fits gets that space as another lane rather than
 * reserving room for a marker it will not draw.
 */
internal fun MonthWeek.laneCapFor(rowHeight: Dp, overflowRow: Dp): Int {
    val band = monthBandHeight(rowHeight)
    val full = (band / EVENT_ROW_HEIGHT).toInt().coerceAtLeast(1)
    if (!overflowsAt(full)) return full
    return ((band - overflowRow) / EVENT_ROW_HEIGHT).toInt().coerceAtLeast(1)
}

/** What a row [rowHeight] tall leaves for chips once the day number is drawn. */
internal fun monthBandHeight(rowHeight: Dp): Dp =
    rowHeight - CELL_TOP_PADDING - DAY_NUMBER_HEIGHT - DAY_NUMBER_GAP

/** Whether any day in this week holds more than [lanes] lanes can seat. */
private fun MonthWeek.overflowsAt(lanes: Int): Boolean =
    days.withIndex().any { (col, day) -> overflowEvents(col, day, lanes).isNotEmpty() }

/**
 * Row height in the continuous grid. The paged grid divides the viewport between
 * however many rows the month has; a scrolling stream has no such bound, so it
 * fixes one — close to what a five-row month gets on a typical phone. How many
 * chips that seats is [MonthWeek.laneCapFor]'s answer like anywhere else, so a
 * week that does not overflow fills the band rather than holding a lane back.
 */
private val CONTINUOUS_ROW_HEIGHT = 112.dp

/**
 * Whitespace below a month block, before the next month's header. Paired with the
 * header's own top padding it gives the seam between two months roughly a row's
 * worth of air — enough that the blocks read as separate without a rule between
 * them.
 */
private val CONTINUOUS_MONTH_GAP = 20.dp

/** The paged grid's own vertical padding, and the gap between its week rows —
 *  named because [monthLaneCap] has to take them off the viewport first. */
private val GRID_VERTICAL_PADDING = 4.dp
private val GRID_ROW_GAP = 2.dp

/** Gap between the weekday header and the seamless stream's first week row. */
private val DENSE_HEADER_GAP = 4.dp

@Composable
internal fun MonthGrid(
    state: MonthUiState.Success,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    /** See [MonthWeekRow]'s `selected`: an anchor for the morph, never a mark. */
    selected: LocalDate? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The rows divide whatever the viewport leaves once the Column's own
        // padding and the gaps between them are paid, so how many chips a cell
        // can seat is only knowable here.
        val rows = state.weeks.size.coerceAtLeast(1)
        val rowHeight =
            (maxHeight - GRID_VERTICAL_PADDING * 2 - GRID_ROW_GAP * (rows - 1)) / rows
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Match the weekday header's inset so day cells sit under their
                // labels, and so the week-number gutter's centre lines up with the
                // top bar's hamburger (4dp bar inset + 24dp half icon button).
                .padding(horizontal = AppBarSpacing.Inset, vertical = GRID_VERTICAL_PADDING),
            verticalArrangement = Arrangement.spacedBy(GRID_ROW_GAP),
        ) {
            val month = state.month
            // Once per grid: the value depends on the typography, the density, the
            // locale and the 24-hour setting, none of which vary by row (#219).
            val timeChipWidth = rememberMonthTimeChipWidth()
            state.weeks.forEach { week ->
                MonthWeekRow(
                    week = week,
                    today = state.today,
                    zone = state.zone,
                    timeChipWidth = timeChipWidth,
                    rowHeight = rowHeight,
                    inMonth = { it.month == month.month && it.year == month.year },
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    selected = selected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
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
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthCount = remember { continuousMonthCount() }
    val todayMonth = remember(state.today) { YearMonth(state.today.year, state.today.month) }
    val timeChipWidth = rememberMonthTimeChipWidth()
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
                    zone = state.zone,
                    timeChipWidth = timeChipWidth,
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
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
    zone: TimeZone,
    timeChipWidth: Dp,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
) {
    val rowCount = remember(month, weekStart) { weekRowsInMonth(month, weekStart) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppBarSpacing.Inset)
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
                    zone = zone,
                    timeChipWidth = timeChipWidth,
                    rowHeight = CONTINUOUS_ROW_HEIGHT,
                    inMonth = { it.month == month.month && it.year == month.year },
                    // The block owns its month alone: a day from either
                    // neighbour is left out entirely rather than dimmed.
                    blankOutside = true,
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
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
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekCount = remember(state.weekStart) { continuousWeekCount(state.weekStart) }
    val timeChipWidth = rememberMonthTimeChipWidth()
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            // The gap under the weekday header is a real margin, not content
            // padding: a lazy list scrolls its rows *through* the before-content
            // padding, so scrolling to a week landed its row that far down with
            // the tail of the previous one showing above it (#191).
            .padding(horizontal = AppBarSpacing.Inset)
            .padding(top = DENSE_HEADER_GAP),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        // Bottom inset clears the FAB stack so the last row stays tappable.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        items(count = weekCount, key = { it }) { index ->
            val week = state.weeksByIndex[index]
            if (week == null) {
                ContinuousWeekPlaceholder()
            } else {
                MonthWeekRow(
                    week = week,
                    today = state.today,
                    zone = state.zone,
                    timeChipWidth = timeChipWidth,
                    rowHeight = CONTINUOUS_ROW_HEIGHT,
                    // Every day in the stream belongs to a month equally — there
                    // is no "other month" to recede here.
                    inMonth = { true },
                    showWeekNumbers = showWeekNumbers,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    labelMonthOnFirst = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CONTINUOUS_ROW_HEIGHT),
                )
            }
        }
    }
}

/**
 * The split style's expand gesture: a downward drag trades the pane away for the
 * full grid, an upward one brings it back (#53). Vertical only — the months page
 * on the pager inside it, and each detector only claims its own axis at the
 * slop, so a drag can page or expand but never both. Commits the moment the drag
 * clears [MONTH_EXPAND_THRESHOLD], once per gesture.
 */
@Composable
private fun rememberSplitExpandDrag(onExpand: () -> Unit, onCollapse: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { MONTH_EXPAND_THRESHOLD.toPx() }
    val currentExpand by rememberUpdatedState(onExpand)
    val currentCollapse by rememberUpdatedState(onCollapse)
    return Modifier.pointerInput(threshold) {
        var accum = 0f
        // One commit per gesture: without this a long drag would keep re-firing
        // every time the accumulator crossed the threshold again.
        var fired = false
        detectVerticalDragGestures(
            onDragStart = {
                accum = 0f
                fired = false
            },
            onVerticalDrag = { _, drag ->
                accum += drag
                if (!fired) {
                    val commit = when {
                        accum > threshold -> currentExpand
                        accum < -threshold -> currentCollapse
                        else -> null
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
 * lists the month's agenda or the selected day — and a downward drag trades the
 * pane away for the full paged grid, an upward one brings it back (#53). The
 * paged style is the same surface opened the other way up (#111).
 *
 * One pager holds whole months — grid, handle and pane — and each page switches
 * between its two faces itself. An expand therefore morphs inside a page that
 * stays put: with a pager per face, every expand swapped one pager for another,
 * and whether the pieces paired up came down to frame timing.
 *
 * The grid stands only as many rows tall as its own month spans (#162).
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
    selected: LocalDate?,
    initiallyExpanded: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    pagerState: PagerState,
    weekStart: DayOfWeek,
    monthState: (YearMonth, DayOfWeek) -> StateFlow<MonthUiState>,
    showWeekNumbers: Boolean,
    onPaneSwipeNext: () -> Unit,
    onPaneSwipePrev: () -> Unit,
    onClearSelection: () -> Unit,
    onRetry: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
) {
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    BackHandler(enabled = expanded != initiallyExpanded) {
        onExpandedChange(initiallyExpanded)
    }
    val expandDrag = rememberSplitExpandDrag(
        onExpand = {
            if (!expanded) {
                onExpandedChange(true)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        },
        onCollapse = {
            if (expanded) {
                onExpandedChange(false)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        },
    )
    // In the split style a tap in the expanded grid picks the day and drops back,
    // which gives the expanded month a job — a chooser you dip into — rather than
    // a mode you can get stranded in. The collapse then runs with the selection
    // already set, so the pane arrives showing the day you picked. The paged
    // style's grid is its home, so a tap there opens the day (#111).
    val onPickDay: (LocalDate) -> Unit = if (initiallyExpanded) {
        onOpenDay
    } else {
        {
            onSelectDay(it)
            onExpandedChange(false)
        }
    }
    // The view switch's own shared scope: a SharedTransitionLayout nested in the
    // host's would be a second lookahead scope. Null under reduced motion.
    val viewMorph = LocalViewMorph.current
    val shared = viewMorph?.shared
    val chipDrag = LocalMonthDrag.current

    when (state) {
        MonthUiState.Loading -> MonthGridLoading()
        is MonthUiState.Failure -> CalendarFailure(reason = state.reason, onRetry = onRetry)
        is MonthUiState.Success -> HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // The months either side are composed ahead, so a swipe lands on a
            // loaded month rather than watching it fill in.
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top,
            // A held chip moves within its month; the page stays put under it.
            userScrollEnabled = chipDrag?.isDragging != true,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = rememberCalendarPageSpec(),
            ),
        ) { index ->
            val ym = yearMonthForIndex(index)
            val pageState by remember(ym, weekStart) { monthState(ym, weekStart) }
                .collectAsStateWithLifecycle()
            val page = pageState as? MonthUiState.Success ?: return@HorizontalPager
            // Only the settled page carries the drag and the morph tags: its
            // neighbours repeat some of its dates and would claim them.
            val active = index == pagerState.settledPage
            // The selection only ever lies in the anchor month.
            val pageSelected = selected?.takeIf { it.year == ym.year && it.month == ym.month }
            AnimatedContent(
                targetState = expanded,
                modifier = Modifier.fillMaxSize(),
                // Both faces fill the same page — the grid grows into exactly the
                // room the pane gives up — so there is no size change to contain.
                // The travel between them is the shared elements' job.
                transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
                label = "split-expand",
            ) { isExpanded ->
                // The view switch's morph stays off a face until it has fully
                // entered (#184): both faces tag the same events for it. Switched
                // off rather than removed — adding and dropping the tags rebuilt
                // every bar's modifier chain around the expand morph, which left
                // bars standing still on later collapses.
                val faceTransition = transition
                val faceViewMorph = remember(viewMorph, active, faceTransition) {
                    viewMorph?.takeIf { active }?.gated {
                        faceTransition.currentState == EnterExitState.Visible
                    }
                }
                val monthMorph = remember(active, reduceMotion, shared, this) {
                    if (active && !reduceMotion && shared != null) {
                        MonthMorphScope(shared, this)
                    } else {
                        null
                    }
                }
                CompositionLocalProvider(
                    LocalMonthDrag provides chipDrag.takeIf { active },
                    LocalViewMorph provides faceViewMorph,
                    LocalMonthMorph provides monthMorph,
                ) {
                    if (isExpanded) {
                        SplitMonthExpanded(
                            state = page,
                            selected = pageSelected,
                            showWeekNumbers = showWeekNumbers,
                            expandDrag = expandDrag,
                            onPickDay = onPickDay,
                            // A tapped chip is asking for that event, not for its
                            // day, so it opens the detail from here too rather
                            // than collapsing (#187).
                            onEventClick = onEventClick,
                            onCollapse = { onExpandedChange(false) },
                        )
                    } else {
                        // The pane is still invisible for the first stretch of a
                        // collapse, so it is built once the grid has mostly
                        // landed, not on the morph's opening frames.
                        var paneReady by remember {
                            mutableStateOf(reduceMotion || transition.currentState == EnterExitState.Visible)
                        }
                        LaunchedEffect(Unit) {
                            if (!paneReady) {
                                delay(SPLIT_PANE_COMPOSE_DELAY_MILLIS)
                                paneReady = true
                            }
                        }
                        SplitMonthCollapsed(
                            state = page,
                            paneReady = paneReady,
                            selected = pageSelected,
                            showWeekNumbers = showWeekNumbers,
                            expandDrag = expandDrag,
                            onSelectDay = onSelectDay,
                            onPaneSwipeNext = onPaneSwipeNext,
                            onPaneSwipePrev = onPaneSwipePrev,
                            onClearSelection = onClearSelection,
                            onOpenDay = onOpenDay,
                            onEventClick = onEventClick,
                            onCreateEvent = onCreateEvent,
                            onExpand = { onExpandedChange(true) },
                            // The pane waits for the grid to settle, and clears
                            // out before it grows, so the two never overlap.
                            paneModifier = if (reduceMotion) {
                                Modifier
                            } else {
                                Modifier.animateEnterExit(
                                    enter = fadeIn(
                                        tween(SPLIT_PANE_FADE_IN_MILLIS, SPLIT_PANE_FADE_IN_DELAY_MILLIS, SPLIT_PANE_EASING),
                                    ),
                                    exit = fadeOut(tween(SPLIT_PANE_FADE_OUT_MILLIS)),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The split style at rest: compact grid, handle, then the agenda or the selected day. */
@Composable
private fun SplitMonthCollapsed(
    state: MonthUiState.Success,
    paneReady: Boolean,
    selected: LocalDate?,
    showWeekNumbers: Boolean,
    expandDrag: Modifier,
    onSelectDay: (LocalDate) -> Unit,
    onPaneSwipeNext: () -> Unit,
    onPaneSwipePrev: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    onExpand: () -> Unit,
    paneModifier: Modifier = Modifier,
) {
    Column(Modifier.fillMaxSize()) {
        // Grid and handle drag as one surface — the handle is what advertises the
        // gesture, so it has to answer to it as well as to a tap. The pane is
        // outside: it scrolls, and is full of tappable rows.
        Column(expandDrag) {
            SplitMonthGrid(
                state = state,
                selected = selected,
                showWeekNumbers = showWeekNumbers,
                onSelectDay = onSelectDay,
                modifier = Modifier.height(splitGridHeight(state.weeks.size)),
            )
            SplitExpandHandle(expanded = false, onToggle = onExpand)
        }
        if (!paneReady) {
            Spacer(Modifier.weight(1f))
        } else if (selected == null) {
            // The agenda pages with the month, on the pager itself.
            Box(Modifier.weight(1f).fillMaxWidth().then(paneModifier)) {
                SplitMonthAgendaPane(
                    state = state,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                )
            }
        } else {
            // A selected day steps a day at a time instead, so the swipe is
            // taken before the pager sees it.
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .then(paneModifier)
                    .then(rememberSplitPaneSwipe(onPaneSwipeNext, onPaneSwipePrev)),
            ) {
                InputChip(
                    selected = true,
                    onClick = onClearSelection,
                    label = { Text(agendaDayLabel(selected, state.today)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.month_split_clear_filter),
                        )
                    },
                    modifier = Modifier.padding(start = 16.dp),
                )
                SplitDayPane(
                    date = selected,
                    today = state.today,
                    events = state.instancesByDay[selected],
                    zone = state.zone,
                    onOpenDay = onOpenDay,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun rememberSplitPaneSwipe(onNext: () -> Unit, onPrev: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    val currentNext by rememberUpdatedState(onNext)
    val currentPrev by rememberUpdatedState(onPrev)
    return Modifier.pointerInput(threshold) {
        var distance = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f; fired = false },
            onHorizontalDrag = { change, drag ->
                change.consume()
                distance += drag
                if (!fired && kotlin.math.abs(distance) >= threshold) {
                    fired = true
                    if (distance < 0f) currentNext() else currentPrev()
                }
            },
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
    selected: LocalDate?,
    showWeekNumbers: Boolean,
    expandDrag: Modifier,
    onPickDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCollapse: () -> Unit,
) {
    // The whole screen drags here, handle included — there is no pane to keep out
    // of it, and the handle is the obvious thing to reach for on the way back.
    Column(Modifier.fillMaxSize().then(expandDrag)) {
        Box(Modifier.weight(1f)) {
            MonthGrid(
                state = state,
                showWeekNumbers = showWeekNumbers,
                onOpenDay = onPickDay,
                onEventClick = onEventClick,
                selected = selected,
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
private val SPLIT_ROW_HEIGHT = 48.dp

/** Vertical padding and row gap of [SplitMonthGrid], which [splitGridHeight] pays for. */
private val SPLIT_GRID_VERTICAL_PADDING = 4.dp
private val SPLIT_ROW_GAP = 2.dp

/** How tall [SplitMonthGrid] stands for a month spanning [rows] weeks. */
private fun splitGridHeight(rows: Int): Dp =
    SPLIT_ROW_HEIGHT * rows + SPLIT_ROW_GAP * (rows - 1).coerceAtLeast(0) +
        SPLIT_GRID_VERTICAL_PADDING * 2
private val SPLIT_DOT_SIZE = 5.dp
// Dots are capped by SPLIT_DOT_LANES, not a constant of their own: they stand for
// the paged grid's lanes, so the two caps have to be the same number or a dot
// would have no bar to become (#53).

/**
 * The expand handle: M3's drag-handle pill (32×4dp), in a row tall enough to be a
 * comfortable tap target on its own.
 */
private val SPLIT_HANDLE_WIDTH = 32.dp
private val SPLIT_HANDLE_HEIGHT = 4.dp
private val SPLIT_HANDLE_ROW_HEIGHT = 24.dp

/** The pane below the split grid fades in once the grid's morph has mostly landed. */
private const val SPLIT_PANE_FADE_IN_DELAY_MILLIS = 300
private const val SPLIT_PANE_COMPOSE_DELAY_MILLIS = 250L
private const val SPLIT_PANE_FADE_IN_MILLIS = 250
private const val SPLIT_PANE_FADE_OUT_MILLIS = 100
private val SPLIT_PANE_EASING = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * The split style's grid (#53): the month compressed to day numbers and event
 * dots, with the selected day listed underneath by [SplitDayPane].
 *
 * Only the rows the month actually spans. It used to pad every month out to six
 * so the pane below held still from page to page, but a row is a sixth of the
 * grid and a third of what the pane gets to show — too much to leave blank on
 * the months that don't need it (#162).
 *
 * Tapping selects rather than drilling into the Day view — the pane is the
 * answer to "what's on this day", so opening a whole screen for it would defeat
 * the layout. The full Day view stays one tap away on the pane's date header.
 */
@Composable
internal fun SplitMonthGrid(
    state: MonthUiState.Success,
    selected: LocalDate?,
    showWeekNumbers: Boolean,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val month = state.month
    val hasToday = state.today.year == month.year && state.today.month == month.month
    val ripple = rememberTodayRipple(active = hasToday)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppBarSpacing.Inset, vertical = SPLIT_GRID_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(SPLIT_ROW_GAP),
    ) {
        state.weeks.forEach { week ->
            val todayCol = week.days.indexOf(state.today).takeIf { hasToday && it >= 0 }
            Row(Modifier.fillMaxWidth().height(SPLIT_ROW_HEIGHT)) {
                if (showWeekNumbers) {
                    WeekNumberGutter(
                        weekStart = week.days.first(),
                        modifier = Modifier
                            .width(rememberWeekNumberGutter())
                            .fillMaxHeight(),
                    )
                }
                week.days.forEachIndexed { col, day ->
                    val inMonth = day.month == month.month && day.year == month.year
                    // Seated by lane rather than gathered by colour, so each dot
                    // is the event the expanded grid draws in that same lane.
                    val seated = week.laneEvents(col, day, SPLIT_DOT_LANES)
                    SplitDayCell(
                        date = day,
                        events = seated,
                        hidden = week.overflowEvents(col, day, SPLIT_DOT_LANES),
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
                        relativeToToday = todayCol?.let { col - it },
                        rippleProgress = { ripple.value },
                        dark = dark,
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One compact day: its number over up to [SPLIT_DOT_LANES] lane-seated event dots.
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
    relativeToToday: Int?,
    rippleProgress: () -> Float,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection is the outline's job alone. Tinting the fill as well lightened
    // the whole cell, which read as a second state on top of the day rather than
    // as a mark on it — and fought today's own circle when they coincided.
    val background = when {
        isToday && inMonth -> todayCellColor()
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
            .todayRipple(relativeToToday, rippleProgress)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitMonthAgendaPane(
    state: MonthUiState.Success,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
) {
    val start = agendaStartForMonth(state.month, state.today)
    val days = state.instancesByDay.keys
        .filter { it >= start && it.year == state.month.year && it.month == state.month.month }
        .filter { state.instancesByDay[it].orEmpty().isNotEmpty() }
        .toMutableList()
        .apply { if (start !in this) add(start) }
        .sorted()
    val dimCutoff = LocalDimCutoff.current
    val listState = key(state.month) { rememberLazyListState() }
    LazyColumn(state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        days.forEach { day ->
            val events = state.instancesByDay[day].orEmpty()
            stickyHeader(key = "header-$day") {
                AgendaDayHeader(date = day, today = state.today, onOpenDay = onOpenDay)
            }
            if (events.isEmpty()) {
                item(key = "empty-$day") {
                    AgendaEmptyDayRow(
                        text = stringResource(R.string.month_split_no_events),
                        onClick = { onCreateEvent(day) },
                    )
                }
            } else {
                itemsIndexed(events, key = { _, event -> "$day-${event.instanceId}" }) { index, event ->
                    AgendaEventRow(
                        event = event,
                        day = day,
                        zone = state.zone,
                        position = positionOf(index, events.size),
                        dimmed = dimCutoff != null && event.hasEnded(dimCutoff),
                        modifier = animateItemMotion().morphEvent(ViewMorphKey.event(event, day)),
                        onClick = { onEventClick(event) },
                    )
                }
            }
            item(key = "gap-$day") { Spacer(Modifier.height(8.dp)) }
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
                            modifier = animateItemMotion().morphEvent(ViewMorphKey.event(event, day)),
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
            .padding(horizontal = AppBarSpacing.Inset)
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
            Spacer(Modifier.height(DENSE_HEADER_GAP))
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
 * [SPLIT_DOT_LANES] rows of bars+pills, then a "+N" dot indicator per day.
 * A transparent per-day layer on top turns a tap into "open that day".
 */
@Composable
private fun MonthWeekRow(
    week: MonthWeek,
    today: LocalDate,
    /** The zone the grid was laid out in — never re-read here (see [MonthUiState.Success.zone]). */
    zone: TimeZone,
    /** The narrowest chip that may carry a start time, measured once per grid (#219). */
    timeChipWidth: Dp,
    /** The height this row was given, which decides its lanes — see [laneCapFor]. */
    rowHeight: Dp,
    inMonth: (LocalDate) -> Boolean,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
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
    val todayCol = week.days.indexOf(today).takeIf { it >= 0 && inMonth(today) }
    val ripple = rememberTodayRipple(active = todayCol != null)
    val overflowRow = rememberOverflowRowHeight()
    val laneCap = week.laneCapFor(rowHeight, overflowRow)
    val laneCount = (week.spans.maxOfOrNull { it.lane } ?: -1) + 1
    val shownLanes = laneCount.coerceAtMost(laneCap)
    val morphing = morphInFlight()
    // Every chip's start time for this row at once, and only when the row's own
    // inputs change: formatting is a parsed pattern per call, and the dim cutoff
    // ticks every minute while a drag recomposes every frame (#219).
    val is24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val chipTimes = remember(week, zone, is24Hour, locale) {
        buildMap {
            week.spans.forEach { span ->
                put(
                    span.event.instanceId,
                    monthChipTime(span.event, span.continuesLeft, zone, is24Hour, locale),
                )
            }
            week.timedByDay.values.flatten().forEach { event ->
                put(
                    event.instanceId,
                    monthChipTime(event, continuesLeft = false, zone, is24Hour, locale),
                )
            }
        }
    }

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
                    laneCount = laneCap,
                    isRtl = isRtl,
                    chipAt = { col, lane -> week.chipAt(col, lane, laneCap) },
                    chipStart = { col, lane -> week.chipStartCol(col, lane) },
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
                    .width(rememberWeekNumberGutter())
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
                        laneCap = laneCap,
                        isRtl = isRtl,
                        chipTimes = chipTimes,
                    ),
                ),
        ) {
            // What a chip has to spend, against the [timeChipWidth] a start time
            // costs it (#219).
            val colW = maxWidth / 7
            // Held here rather than read at the offset: the dots are placed
            // inside a plain lambda, which is no longer in this scope. The box
            // is the whole row, so the day number's share comes off it — the
            // dots are positioned inside the band, not inside this.
            val bandHeight = monthBandHeight(maxHeight)

            // Per-day background pills — same surfaceContainer rounded surface the
            // week/day views use, so the three views share one visual language.
            // Spanning bars draw on top of these, bridging cells, so they still read
            // as one continuous event.
            Row(Modifier.matchParentSize()) {
                week.days.forEachIndexed { col, d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = CELL_GAP, vertical = 1.dp)
                            .morphElement(MonthMorphKey.Cell(d))
                            .todayRipple(todayCol?.let { col - it }) { ripple.value }
                            .background(
                                color = when {
                                    d == today && inMonth(d) -> todayCellColor()
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
                    week.days.forEachIndexed { col, d ->
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
                                    .morphElement(MonthMorphKey.DayNumber(d))
                                    .todayRipple(todayCol?.let { col - it }) { ripple.value },
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
                            time = chipTimes[span.event.instanceId],
                            showTime = colW * cols >= timeChipWidth,
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
                                // A blanked day is drawn again in its own
                                // month's block, which owns the tag.
                                .then(
                                    week.days[span.startCol].let { start ->
                                        if (blankOutside && !inMonth(start)) {
                                            Modifier
                                        } else {
                                            Modifier.morphEvent(ViewMorphKey.event(span.event, start))
                                        }
                                    },
                                )
                                .width(colW * cols)
                                .height(EVENT_ROW_HEIGHT)
                                .padding(horizontal = MONTH_CHIP_INSET, vertical = 1.dp),
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
                                    .padding(horizontal = MONTH_CHIP_INSET, vertical = 1.dp),
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
                        val freeSlots = (0 until laneCap).filter { it !in occupied }
                        val pillsShown = timed.take(freeSlots.size)
                        pillsShown.forEachIndexed { i, ev ->
                            MonthBar(
                                event = ev,
                                dark = dark,
                                continuesLeft = false,
                                continuesRight = false,
                                days = listOf(d),
                                time = chipTimes[ev.instanceId],
                                showTime = colW >= timeChipWidth,
                                modifier = Modifier
                                    .offset(
                                        x = colW * col,
                                        y = EVENT_ROW_HEIGHT * freeSlots[i],
                                    )
                                    .morphBounds(MonthMorphKey.Event(d, ev.instanceId))
                                    .then(
                                        if (blankOutside && !inMonth(d)) {
                                            Modifier
                                        } else {
                                            Modifier.morphEvent(ViewMorphKey.event(ev, d))
                                        },
                                    )
                                    .width(colW)
                                    .height(EVENT_ROW_HEIGHT)
                                    .todayRipple(todayCol?.let { col - it }) { ripple.value }
                                    .padding(horizontal = MONTH_CHIP_INSET, vertical = 1.dp),
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
                                rowHeight = overflowRow,
                                modifier = Modifier
                                    // After the chip lanes, but never past the
                                    // band: on a row too short for the lanes it
                                    // is holding, dots placed below it are
                                    // clipped away entirely and the day looks
                                    // like it has nothing more to show.
                                    .offset(
                                        x = colW * col,
                                        y = minOf(
                                            EVENT_ROW_HEIGHT * laneCap,
                                            bandHeight - overflowRow,
                                        ),
                                    )
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

            // Tap layer: a tap on a chip opens that event, anything else opens the
            // day (#187). The chips take no pointer input of their own — this
            // layer covers them — so the lane under the finger is resolved
            // geometrically, exactly as the drag pickup above does it; the down
            // position is read on the initial pass, which consumes nothing and so
            // leaves both the click and a pickup in flight untouched. Padded and
            // clipped to the background pill so the ripple matches it. A blanked
            // cell isn't part of this month, so it takes no taps either.
            val downY = remember(week.days.size) { FloatArray(week.days.size) { NO_DOWN_Y } }
            Row(Modifier.matchParentSize()) {
                week.days.forEachIndexed { col, d ->
                    if (blankOutside && !inMonth(d)) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .pointerInput(col) {
                                    awaitEachGesture {
                                        downY[col] = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        ).position.y
                                    }
                                }
                                .padding(horizontal = CELL_GAP, vertical = 1.dp)
                                .clip(CELL_SHAPE)
                                .clickable {
                                    // Cleared on read: a click with no fresh down
                                    // (TalkBack, D-pad) would otherwise resolve the
                                    // previous tap's position and reopen its chip.
                                    val cellY = downY[col]
                                    downY[col] = NO_DOWN_Y
                                    val chip = week.chipAtCellY(
                                        col = col,
                                        cellY = cellY,
                                        bandTopInCell = bandTopInCell(
                                            cellCoordinates,
                                            bandCoordinates,
                                        ),
                                        rowHeightPx = rowHeightPx,
                                        laneCap = laneCap,
                                    )
                                    if (chip != null) onEventClick(chip) else onOpenDay(d)
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * How far the event band sits below the top of the row's day-column box, or null
 * while either is unmeasured. Read off the live coordinates rather than summed
 * from the padding constants, so it can't drift from what the row actually drew.
 */
private fun bandTopInCell(
    cell: Array<LayoutCoordinates?>,
    band: Array<LayoutCoordinates?>,
): Float? {
    val cellTop = cell[0]?.takeIf { it.isAttached }?.positionInRoot()?.y ?: return null
    val bandTop = band[0]?.takeIf { it.isAttached }?.positionInRoot()?.y ?: return null
    return bandTop - cellTop
}

/**
 * Stand-in [cellY] for "no touch down recorded", which resolves to no chip.
 */
private const val NO_DOWN_Y = Float.NEGATIVE_INFINITY

/**
 * The chip at [cellY] in column [col], where [cellY] is measured from the top of
 * the row's day-column box. Null for a tap above the band (the day number), on an
 * empty lane, or on the overflow dots — all of which mean "open the day", the
 * dots included: their point is that the day holds more than fits.
 */
internal fun MonthWeek.chipAtCellY(
    col: Int,
    cellY: Float,
    bandTopInCell: Float?,
    rowHeightPx: Float,
    laneCap: Int,
): EventInstance? {
    if (bandTopInCell == null || rowHeightPx <= 0f) return null
    val bandY = cellY - bandTopInCell
    if (bandY < 0f) return null
    return chipAt(col, (bandY / rowHeightPx).toInt(), laneCap)
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
    laneCap: Int,
    isRtl: Boolean,
    /** The row's formatted chip times by instance id, so the copy carries the
     *  one its source chip had rather than deriving another (#219). */
    chipTimes: Map<Long, String?>,
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
            week.chipAt(dayIndex, lane, laneCap)
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
                time = chipTimes[event.instanceId],
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
            moveScope?.move(
                MoveRequest(
                    eventId = drop.event.eventId,
                    beginMillis = drop.event.start.toEpochMilliseconds(),
                    endMillis = drop.event.end.toEpochMilliseconds(),
                    target = MoveTarget.ByDays(delta),
                ),
            )
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
    val openLabel = stringResource(R.string.month_open_week, weekNumber)
    val onOpenWeek = LocalOpenWeek.current
    Box(
        modifier = modifier
            .then(if (onOpenWeek != null) Modifier.clickable(onClickLabel = openLabel) {
                onOpenWeek(weekStart)
            } else Modifier)
            .padding(horizontal = CELL_GAP, vertical = 1.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CELL_SHAPE)
            .semantics { contentDescription = "$label $weekNumber" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekNumber.toString(),
            style = weekNumberStyle(),
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
                    .morphDay(date)
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
                modifier = Modifier.morphDay(date),
            )
        } else {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.morphDay(date),
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

/**
 * A filled event pill/bar — softened (or raw) fill, title clipped to one line,
 * with the start time before it where the chip is wide enough ([showTime], #219).
 */
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
    /** The chip's start time, or null where it has none — see `monthChipTime` (#219). */
    time: String? = null,
    /** Whether this chip has the width to draw [time]; a screen reader gets it either way. */
    showTime: Boolean = false,
) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && event.hasEnded(dimCutoff)
    val paint = eventPaint(event, dark)
    // The same title/secondary ink pairing the week and day blocks use, with
    // the time on the quieter half.
    val label = inlineTimeLabel(
        time = time.takeIf { showTime },
        title = title,
        timeInk = paint.secondaryInk,
    )
    // Announced whether or not it is drawn, and comma-separated as the week and
    // day blocks do it: what a screen reader hears shouldn't turn on how wide
    // the chip happens to be (#219).
    val description = if (time != null) "$title, $time" else title
    val moveAction = eventMoveAction(event)
    // The source stays put as a ghost while its floating copy travels.
    val monthDrag = LocalMonthDrag.current
    val lifted = monthDrag?.liftedInstanceId == event.instanceId ||
        monthDrag?.isSettledChip(event, days) == true
    val ghost = ghostAlpha(lifted)
    val shape = monthBarShape(continuesLeft, continuesRight)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .then(if (ghost < 1f) Modifier.alpha(ghost) else Modifier)
            .eventSurface(paint, shape, monthBarCuts(continuesLeft, continuesRight))
            .padding(horizontal = MONTH_CHIP_TEXT_PADDING)
            .semantics {
                contentDescription = description
                if (moveAction != null) customActions = listOf(moveAction)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val titleOverflow = eventTitleOverflow()
        Text(
            text = label,
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
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val soften = LocalSoftenColors.current
    val dimCutoff = LocalDimCutoff.current
    val byColor = events.groupBy { it.color }
    val dots = byColor.keys.take(3)
    Row(
        modifier = modifier.height(rowHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(OVERFLOW_DOT_SIZE)
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
            .padding(horizontal = AppBarSpacing.Inset, vertical = 4.dp),
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

/** [abbreviated] swaps the full month name for its three-letter form (#165). */
private fun formatMonthTitle(
    ym: YearMonth,
    locale: Locale,
    currentYear: Int,
    abbreviated: Boolean = false,
): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(ym.year, ym.month.ordinal + 1, 1),
        locale = locale,
        currentYear = currentYear,
        skeleton = if (abbreviated) "LLL" else "LLLL",
    )
