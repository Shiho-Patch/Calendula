package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.time.isoWeekNumber
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
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

    val continuous = viewStyle == MonthViewStyle.Continuous

    // Today, from whichever state is driving; the clock only covers the first
    // frame, before either has loaded.
    val today = when {
        continuous -> (continuousState as? ContinuousMonthUiState.Success)?.today
        else -> (state as? MonthUiState.Success)?.today
    } ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    // Keyed on the week start: week indices are anchored on it, so a changed
    // week start would leave the saved scroll offset pointing at a different week.
    val listState = key(weekStart) {
        rememberLazyListState(
            initialFirstVisibleItemIndex =
                weekIndexOf(LocalDate(month.year, month.month, 1), weekStart),
        )
    }

    // The continuous stream has no single "current" month, so the title takes the
    // one the viewport mostly sits in: the midweek day of the row below the top
    // edge, which flips over as that month's first full week comes into view.
    val visibleMonth by remember(weekStart) {
        derivedStateOf {
            val midweek = weekStartForIndex(listState.firstVisibleItemIndex + 1, weekStart)
                .plus(3, DateTimeUnit.DAY)
            YearMonth(midweek.year, midweek.month)
        }
    }
    val titleMonth = if (continuous) visibleMonth else month

    // Feed the visible range back so the sliding data window can follow. The
    // view model ignores everything that doesn't near a loaded edge.
    LaunchedEffect(listState, continuous) {
        if (!continuous) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0) to (visible.lastOrNull()?.index ?: 0)
        }
            .distinctUntilChanged()
            .collect { (first, last) -> viewModel.onVisibleWeeksChanged(first, last) }
    }

    val isOnCurrentMonth = titleMonth == YearMonth(today.year, today.month)

    // Drives whether the title carries the year.
    val currentYear = today.year

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
        if (continuous) {
            scope.launch { listState.animateScrollToItem(weekIndexOf(today, weekStart)) }
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
        if (continuous) {
            scope.launch {
                listState.animateScrollToItem(
                    weekIndexOf(LocalDate(target.year, target.month, 1), weekStart),
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
                    month = titleMonth,
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
                    if (continuous) {
                        ContinuousMonthContent(
                            state = continuousState,
                            listState = listState,
                            showWeekNumbers = showWeekNumbers,
                            onRetry = jumpToToday,
                            onOpenDay = onOpenDay,
                        )
                    } else if (viewStyle == MonthViewStyle.Split) {
                        SplitMonthContent(
                            state = state,
                            selected = selectedDate,
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
 * Continuous style content. No swipe detector and no [AnimatedContent]: vertical
 * scrolling owns the gesture, and the list never swaps wholesale — it keeps
 * scrolling while the window behind it reloads.
 */
@Composable
private fun ContinuousMonthContent(
    state: ContinuousMonthUiState,
    listState: LazyListState,
    showWeekNumbers: Boolean,
    onRetry: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    when (state) {
        ContinuousMonthUiState.Loading -> MonthGridLoading()
        is ContinuousMonthUiState.Failure ->
            CalendarFailure(reason = state.reason, onRetry = onRetry)
        is ContinuousMonthUiState.Success -> ContinuousMonthGrid(
            state = state,
            listState = listState,
            showWeekNumbers = showWeekNumbers,
            onOpenDay = onOpenDay,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthTopBar(
    month: YearMonth,
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
                text = formatMonthTitle(month, locale, currentYear),
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

@Composable
internal fun MonthGrid(
    state: MonthUiState.Success,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/**
 * The continuous grid (#38): one uninterrupted vertical stream of weeks.
 *
 * The list is indexed by *absolute week number*, so a row's identity never
 * changes and there is no page seam to scroll across. Weeks the loaded window
 * hasn't reached yet render as a skeleton and pull the window along behind them.
 *
 * Deliberately not a stack of month grids — that would still repeat a boundary
 * week at the end of one month and the start of the next, which is exactly the
 * duplication #38 asks to be rid of.
 */
@Composable
internal fun ContinuousMonthGrid(
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

/**
 * The month-changing horizontal swipe, shared by the paged and split styles.
 * Accumulates the drag and commits past a small threshold on release — the grid
 * doesn't follow the finger, so there is no distance to rubber-band against.
 */
@Composable
private fun rememberMonthSwipeModifier(
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { 6.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    return Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { dragAccum = 0f },
            onDragEnd = {
                when {
                    dragAccum < -threshold -> onSwipeNext()
                    dragAccum > threshold -> onSwipePrev()
                }
                dragAccum = 0f
            },
            onDragCancel = { dragAccum = 0f },
            onHorizontalDrag = { _, drag -> dragAccum += drag },
        )
    }
}

/**
 * Split style content: the compact grid keeps the month swipe, the pane below it
 * lists whatever day is selected.
 *
 * No [AnimatedContent] here, unlike the paged style. The grid is 4–6 rows tall
 * depending on the month, so sliding one month over another would animate a
 * height change under a pane that is trying to hold still.
 */
@Composable
private fun SplitMonthContent(
    state: MonthUiState,
    selected: LocalDate,
    showWeekNumbers: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onRetry: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
) {
    when (state) {
        MonthUiState.Loading -> MonthGridLoading()
        is MonthUiState.Failure -> CalendarFailure(reason = state.reason, onRetry = onRetry)
        is MonthUiState.Success -> Column(Modifier.fillMaxSize()) {
            SplitMonthGrid(
                state = state,
                selected = selected,
                showWeekNumbers = showWeekNumbers,
                onSelectDay = onSelectDay,
                // The swipe lives on the grid alone — the pane scrolls and is
                // full of tappable rows, so it shouldn't also be a month gesture.
                modifier = rememberMonthSwipeModifier(onSwipeNext, onSwipePrev),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SplitDayPane(
                date = selected,
                today = state.today,
                events = state.instancesByDay[selected].orEmpty(),
                zone = state.zone,
                onOpenDay = onOpenDay,
                onEventClick = onEventClick,
                onCreateEvent = onCreateEvent,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
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
private const val SPLIT_MAX_DOTS = 3

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
                week.days.forEach { day ->
                    SplitDayCell(
                        date = day,
                        events = state.instancesByDay[day].orEmpty(),
                        isToday = day == state.today,
                        isSelected = day == selected,
                        inMonth = day.month == month.month && day.year == month.year,
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
 * One compact day: its number over up to [SPLIT_MAX_DOTS] event dots.
 *
 * Selection and today are deliberately different signals — a tinted, outlined
 * cell versus the filled circle the other views already use for today — so the
 * two read at once when they land on the same day.
 */
@Composable
private fun SplitDayCell(
    date: LocalDate,
    events: List<EventInstance>,
    isToday: Boolean,
    isSelected: Boolean,
    inMonth: Boolean,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        inMonth -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Box(
        modifier = modifier
            .padding(horizontal = CELL_GAP, vertical = 1.dp)
            .clip(CELL_SHAPE)
            .background(background)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CELL_SHAPE)
                } else {
                    Modifier
                },
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
            SplitDots(events = events, dark = dark)
        }
    }
}

/** Up to three colour dots for a day, plus a count when more are hidden. */
@Composable
private fun SplitDots(events: List<EventInstance>, dark: Boolean) {
    if (events.isEmpty()) return
    val soften = LocalSoftenColors.current
    val colors = remember(events) { events.map { it.color }.distinct().take(SPLIT_MAX_DOTS) }
    val extra = events.size - colors.size
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(SPLIT_DOT_SIZE)
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

/**
 * The selected day's events, in the agenda's own row vocabulary so the two
 * surfaces read as one app. The date header opens the full Day view, matching
 * the Week and Agenda headers (#37).
 */
@Composable
internal fun SplitDayPane(
    date: LocalDate,
    today: LocalDate,
    events: List<EventInstance>,
    zone: TimeZone,
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onCreateEvent: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimCutoff = LocalDimCutoff.current
    Column(modifier = modifier) {
        AgendaDayHeader(date = date, today = today, onOpenDay = onOpenDay)
        if (events.isEmpty()) {
            AgendaEmptyDayRow(
                text = stringResource(R.string.month_split_no_events),
                onClick = { onCreateEvent(date) },
            )
        } else {
            LazyColumn(
                // Bottom inset clears the FAB stack so the last row stays tappable.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                itemsIndexed(
                    items = events,
                    key = { _, event -> event.instanceId },
                ) { index, event ->
                    AgendaEventRow(
                        event = event,
                        day = date,
                        zone = zone,
                        position = positionOf(index, events.size),
                        dimmed = dimCutoff != null && event.hasEnded(dimCutoff),
                        onClick = { onEventClick(event) },
                    )
                }
            }
        }
    }
}

/** A week the sliding window hasn't loaded yet — same height, so nothing jumps. */
@Composable
private fun ContinuousWeekPlaceholder() {
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CELL_SHAPE),
            )
        }
    }
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
    labelMonthOnFirst: Boolean = false,
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
                                color = if (inMonth(d)) MaterialTheme.colorScheme.surfaceContainer
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = CELL_SHAPE,
                            ),
                    )
                }
            }

            Column(Modifier.fillMaxSize().padding(top = CELL_TOP_PADDING)) {
                Row(Modifier.fillMaxWidth()) {
                    week.days.forEach { d ->
                        DayNumberCell(
                            date = d,
                            isToday = d == today,
                            inMonth = inMonth(d),
                            // Continuous has no month boundaries to dim across, so
                            // the 1st names its month instead — the only marker
                            // telling one month from the next inside the stream.
                            monthLabel = if (labelMonthOnFirst && d.day == 1) {
                                shortMonthName(d)
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
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
            // clipped to the background pill so the ripple matches it.
            Row(Modifier.matchParentSize()) {
                week.days.forEach { d ->
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

/** Locale-short month name ("Jul", "Juli"), for the 1st in the continuous grid. */
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
