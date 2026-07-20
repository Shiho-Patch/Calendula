package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the month swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = LocalDate(titleMonth.year, titleMonth.month, 1),
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
                    todayVisible = !isOnCurrentMonth && !todayInToolbar,
                    todayText = stringResource(R.string.month_today_action),
                    onToday = jumpToToday,
                    onCreate = {
                        // Anchor on today when its month is shown, else the 1st.
                        onCreateEvent(
                            if (isOnCurrentMonth) today
                            else LocalDate(titleMonth.year, titleMonth.month, 1),
                            null,
                        )
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                WeekdayHeader(weekStart = weekStart, showWeekNumbers = showWeekNumbers)
                CompositionLocalProvider(LocalDimCutoff provides dimCutoff) {
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
                            onSelectDay = viewModel::selectDate,
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
        }
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
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    showTodayButton: Boolean,
    onToday: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
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
private val CELL_SHAPE = RoundedCornerShape(12.dp)
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
    /**
     * Outlined when set, matching the compact grid's marker. Only the split
     * style's expanded form passes one — the paged style has no selection, and
     * marking a day there would invent a state it doesn't have (#53).
     */
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
 * collapses the split style (#53). Accumulates and commits past a threshold on
 * release — the grid doesn't follow the finger, so there is no distance to
 * rubber-band against.
 *
 * The axis is **locked on the first movement and held for the whole gesture**, so
 * a drag can page or expand but never both. Two independent detectors on one
 * surface would each see their own component of a diagonal drag and both fire.
 *
 * The horizontal threshold matches the week and day views'. It used to be 6dp,
 * which is inside the distance a tap wanders: brushing the grid changed the
 * month, and a page that turns on an unintended gesture reads as the animation
 * misfiring rather than as the gesture being over-eager. The vertical one is
 * larger — swapping the whole layout out deserves a more deliberate pull than
 * stepping to the next month.
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
    val pageThreshold = with(density) { MONTH_SWIPE_THRESHOLD.toPx() }
    val expandThreshold = with(density) { MONTH_EXPAND_THRESHOLD.toPx() }
    return Modifier.pointerInput(onSwipeNext, onSwipePrev, onExpand, onCollapse) {
        var accum = Offset.Zero
        var axis = DragAxis.Undecided
        detectDragGestures(
            onDragStart = {
                accum = Offset.Zero
                axis = DragAxis.Undecided
            },
            onDragEnd = {
                when (axis) {
                    DragAxis.Horizontal -> when {
                        accum.x < -pageThreshold -> onSwipeNext()
                        accum.x > pageThreshold -> onSwipePrev()
                    }
                    DragAxis.Vertical -> when {
                        accum.y > expandThreshold -> onExpand?.invoke()
                        accum.y < -expandThreshold -> onCollapse?.invoke()
                    }
                    DragAxis.Undecided -> Unit
                }
                accum = Offset.Zero
                axis = DragAxis.Undecided
            },
            onDragCancel = {
                accum = Offset.Zero
                axis = DragAxis.Undecided
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
            },
        )
    }
}

/** Drag distance that commits a month change, matching the week and day views. */
private val MONTH_SWIPE_THRESHOLD = 24.dp

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
    var expanded by rememberSaveable { mutableStateOf(false) }
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
        is MonthUiState.Success -> AnimatedContent(
            targetState = expanded,
            modifier = Modifier.fillMaxSize(),
            // Both branches fill the same box — the grid grows into exactly the
            // room the pane gives up — so there is no size change to contain.
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "split-expand-transition",
        ) { isExpanded ->
            if (isExpanded) {
                SplitMonthExpanded(
                    state = state,
                    selected = selected,
                    slideDir = slideDir,
                    showWeekNumbers = showWeekNumbers,
                    swipeModifier = swipeModifier,
                    // A tap in the expanded grid picks the day and drops back,
                    // which gives the expanded month a job — a chooser you dip
                    // into — rather than a mode you can get stranded in. The
                    // collapse then runs with the selection already set, so the
                    // pane arrives showing the day you picked.
                    onPickDay = {
                        onSelectDay(it)
                        expanded = false
                    },
                    onCollapse = { expanded = false },
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
                    onExpand = { expanded = true },
                )
            }
        }
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
        AnimatedContent(
            // The selection travels *with* the state so each page keeps its
            // own. Read from outside, both pages would show the incoming
            // one, and paging visibly threw the marker across the outgoing
            // grid — onto the new month's 1st, which the old grid still
            // shows among its trailing days — before the new page arrived.
            targetState = state to selected,
            modifier = swipeModifier,
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
 */
@Composable
private fun SplitMonthExpanded(
    state: MonthUiState.Success,
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

    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state to selected,
            modifier = Modifier.weight(1f).then(swipeModifier),
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
                        hidden = (week.countByDay[day] ?: 0) - seated.size,
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
    hidden: Int,
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
    val reduceMotion = rememberReduceMotion()
    val fadeSpec = rememberCalendarFadeSpec()
    val selection by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else fadeSpec,
        label = "split-day-selection",
    )
    Box(
        modifier = modifier
            .padding(horizontal = CELL_GAP, vertical = 1.dp)
            .clip(CELL_SHAPE)
            .background(background)
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = selection),
                shape = CELL_SHAPE,
            )
            .selectable(selected = isSelected, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DayNumberCell(
                date = date,
                isToday = isToday,
                inMonth = inMonth,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(2.dp))
            SplitDots(events = events, hidden = hidden, dark = dark)
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
private fun SplitDots(events: List<EventInstance>, hidden: Int, dark: Boolean) {
    if (events.isEmpty()) return
    val soften = LocalSoftenColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        events.forEach { event ->
            Box(
                modifier = Modifier
                    .size(SPLIT_DOT_SIZE)
                    .background(eventFill(event.color, dark, soften), CircleShape),
            )
        }
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    /** See [MonthGrid]'s `selected`; null for every style but expanded split. */
    selected: LocalDate? = null,
) {
    val dark = isSystemInDarkTheme()
    val laneCount = (week.spans.maxOfOrNull { it.lane } ?: -1) + 1
    val shownLanes = laneCount.coerceAtMost(MAX_EVENT_ROWS)

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
                .fillMaxHeight(),
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
                            .background(
                                color = when {
                                    inMonth(d) -> MaterialTheme.colorScheme.surfaceContainer
                                    // A blanked cell draws nothing at all: it is
                                    // the gap that shows where the month starts.
                                    blankOutside -> Color.Transparent
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = CELL_SHAPE,
                            )
                            // Scoped to the row's own month for the same reason the
                            // compact grid scopes it: a boundary week shows the
                            // neighbour month's dates too, and the marker belongs to
                            // exactly one of them.
                            .then(
                                if (d == selected && inMonth(d)) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CELL_SHAPE,
                                    )
                                } else {
                                    Modifier
                                },
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
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Breathing room between the day number (and today's circle) and the
                // first event row.
                Spacer(Modifier.height(DAY_NUMBER_GAP))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                ) {
                    // Spanning bars on their shared lanes.
                    week.spans.filter { it.lane < shownLanes }.forEach { span ->
                        val cols = span.endCol - span.startCol + 1
                        MonthBar(
                            event = span.event,
                            dark = dark,
                            continuesLeft = span.continuesLeft,
                            continuesRight = span.continuesRight,
                            modifier = Modifier
                                .offset(
                                    x = colW * span.startCol,
                                    y = EVENT_ROW_HEIGHT * span.lane,
                                )
                                .width(colW * cols)
                                .height(EVENT_ROW_HEIGHT)
                                .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp),
                        )
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
                                modifier = Modifier
                                    .offset(
                                        x = colW * col,
                                        y = EVENT_ROW_HEIGHT * freeSlots[i],
                                    )
                                    .width(colW)
                                    .height(EVENT_ROW_HEIGHT)
                                    .padding(horizontal = CELL_GAP + 1.dp, vertical = 1.dp),
                            )
                        }
                        val hidden = (week.countByDay[d] ?: 0) - occupied.size - pillsShown.size
                        if (hidden > 0) {
                            val hiddenColors = buildList {
                                week.spans
                                    .filter { it.lane >= shownLanes && col in it.startCol..it.endCol }
                                    .forEach { add(it.event.color) }
                                timed.drop(pillsShown.size).forEach { add(it.color) }
                            }.distinct().take(3)
                            OverflowDots(
                                colors = hiddenColors,
                                extra = hidden - hiddenColors.size,
                                dark = dark,
                                modifier = Modifier
                                    .offset(x = colW * col, y = EVENT_ROW_HEIGHT * MAX_EVENT_ROWS)
                                    .width(colW)
                                    .padding(horizontal = 3.dp),
                            )
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
) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && event.hasEnded(dimCutoff)
    val soften = LocalSoftenColors.current
    val fill = eventFill(event.color, dark, soften)
    val shape = RoundedCornerShape(
        topStart = if (continuesLeft) 0.dp else 4.dp,
        bottomStart = if (continuesLeft) 0.dp else 4.dp,
        topEnd = if (continuesRight) 0.dp else 4.dp,
        bottomEnd = if (continuesRight) 0.dp else 4.dp,
    )
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .background(fill, shape)
            .padding(horizontal = 4.dp)
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

/** Overflow row: a dot per hidden event (up to three) plus "+N" for the rest. */
@Composable
private fun OverflowDots(
    colors: List<Int>,
    extra: Int,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val soften = LocalSoftenColors.current
    Row(
        modifier = modifier.height(EVENT_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(eventFill(argb, dark, soften), CircleShape),
            )
        }
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
