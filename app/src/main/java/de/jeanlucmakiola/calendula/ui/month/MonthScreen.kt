package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.components.pastelize
import de.jeanlucmakiola.floret.time.isoWeekNumber
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
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
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView> = IMPLEMENTED_VIEWS,
    drawerViewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
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

    val isOnCurrentMonth = when (val s = state) {
        is MonthUiState.Success -> s.month == YearMonth(s.today.year, s.today.month)
        else -> true
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
    // (back), viewing the past → from the right (forward).
    val jumpToToday = {
        slideDir = when (val s = state) {
            is MonthUiState.Success ->
                if (YearMonth(s.today.year, s.today.month) < s.month) -1 else 1
            else -> 0
        }
        viewModel.goToToday()
    }
    // Drawer jump-to-date: slide from the side the target month lies on.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        slideDir = if (YearMonth(target.year, target.month) < month) -1 else 1
        viewModel.goToDate(target)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the month swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = LocalDate(month.year, month.month, 1),
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
                    month = month,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next(quickSwitchViews)) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnCurrentMonth,
                    todayText = stringResource(R.string.month_today_action),
                    onToday = jumpToToday,
                    onCreate = {
                        // Anchor on today when its month is shown, else the 1st.
                        val today = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        onCreateEvent(
                            if (isOnCurrentMonth) today
                            else LocalDate(month.year, month.month, 1),
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
    val density = LocalDensity.current
    val threshold = with(density) { 6.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    val swipeModifier = Modifier.pointerInput(Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthTopBar(
    month: YearMonth,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = formatMonthYear(month),
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
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek, showWeekNumbers: Boolean) {
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

@Composable
private fun MonthGrid(
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
        state.weeks.forEach { week ->
            MonthWeekRow(
                week = week,
                today = state.today,
                month = state.month,
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
    month: YearMonth,
    showWeekNumbers: Boolean,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
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
                    val inMonth = d.month == month.month && d.year == month.year
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = CELL_GAP, vertical = 1.dp)
                            .background(
                                color = if (inMonth) MaterialTheme.colorScheme.surfaceContainer
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
                            inMonth = d.month == month.month && d.year == month.year,
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

/** A filled event pill/bar — pastelized fill, title clipped to one line. */
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
    val shape = RoundedCornerShape(
        topStart = if (continuesLeft) 0.dp else 4.dp,
        bottomStart = if (continuesLeft) 0.dp else 4.dp,
        topEnd = if (continuesRight) 0.dp else 4.dp,
        bottomEnd = if (continuesRight) 0.dp else 4.dp,
    )
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            .background(pastelize(event.color, dark), shape)
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.Black.copy(alpha = 0.8f),
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
    Row(
        modifier = modifier.height(EVENT_ROW_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(pastelize(argb, dark), CircleShape),
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

private fun formatMonthYear(ym: YearMonth): String {
    val locale = Locale.getDefault()
    val name = java.time.Month.of(ym.month.ordinal + 1)
        .getDisplayName(JavaTextStyle.FULL, locale)
    return "$name ${ym.year}"
}
