package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.pastelize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 48.dp
private val MIN_EVENT_HEIGHT = 24.dp
private val ALL_DAY_ROW_HEIGHT = 24.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp

/** Total all-day strip height for a week (0 when there are no all-day events). */
private fun WeekUiState.Success.allDayStripHeight(): Dp {
    if (allDaySpans.isEmpty()) return 0.dp
    val lanes = allDaySpans.maxOf { it.lane } + 1
    return ALL_DAY_ROW_HEIGHT * lanes + ALL_DAY_VERTICAL_PADDING * 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStartDate.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The static header + all-day strip share the app bar's scrolled colour so
    // the whole top region elevates together once the timeline scrolls under it.
    val topSectionColor by animateColorAsState(
        targetValue = if (scrollBehavior.state.overlappedFraction > 0.01f) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "week-top-section-color",
    )

    val isOnCurrentWeek = when (val s = state) {
        // True when today falls inside the displayed week — independent of which
        // weekday the user picked as the first day.
        is WeekUiState.Success ->
            s.today >= s.weekStart && s.today <= s.weekStart.plus(6, kotlinx.datetime.DateTimeUnit.DAY)
        else -> true
    }

    // Slide direction for the week transition: +1 = next, -1 = prev, 0 = jump.
    var slideDir by remember { mutableIntStateOf(0) }
    val goNext = { slideDir = 1; viewModel.goToNext() }
    val goPrev = { slideDir = -1; viewModel.goToPrev() }
    // Slide toward today: viewing the future → today comes in from the left
    // (back), viewing the past → from the right (forward).
    val jumpToToday = {
        slideDir = when (val s = state) {
            is WeekUiState.Success -> if (s.today < s.weekStart) -1 else 1
            else -> 0
        }
        viewModel.goToToday()
    }
    // Drawer jump-to-date: slide from the side the target week lies on.
    val jumpToDate: (LocalDate) -> Unit = { target ->
        slideDir = if (target < weekStart) -1 else 1
        viewModel.goToDate(target)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the week swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                currentView = selectedView,
                currentDate = weekStart,
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
                WeekTopBar(
                    weekStart = weekStart,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next()) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenSearch = onOpenSearch,
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                CalendarFabColumn(
                    todayVisible = !isOnCurrentWeek,
                    todayText = stringResource(R.string.week_today_action),
                    onToday = jumpToToday,
                    onCreate = {
                        // Anchor on today when it's in view, else the week's first day.
                        val today = Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                        onCreateEvent(if (isOnCurrentWeek) today else weekStart, null)
                    },
                )
            },
        ) { innerPadding ->
            WeekContent(
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
private fun WeekContent(
    state: WeekUiState,
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
    val threshold = with(density) { 24.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberCalendarSlideSpec()

    // Hoisted above the per-week AnimatedContent so the vertical scroll position
    // survives week-to-week swipes (e.g. 18:00 stays centred). We only centre on
    // noon once, on first entry into the week view (i.e. when arriving from the
    // month/day view), not on every swipe.
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
    // week during a swipe, so the strip slides along but never jumps in height;
    // it just springs smoothly from the old to the new size.
    val targetAllDayHeight = (state as? WeekUiState.Success)?.allDayStripHeight() ?: 0.dp
    val allDayHeight by animateDpAsState(
        targetValue = targetAllDayHeight,
        label = "all-day-strip-height",
    )

    // Whole-page horizontal swipe. It sits one level above the timeline's
    // vertical scroll: a horizontal drag only crosses *this* detector's slop,
    // while a vertical drag is consumed by the inner scroll first — so the two
    // gestures coexist without fighting.
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
        modifier = modifier.then(swipeModifier),
        contentKey = { s ->
            when (s) {
                is WeekUiState.Success -> "success-${s.weekStart}"
                is WeekUiState.Failure -> "failure-${s.reason}"
                WeekUiState.Loading -> "loading"
            }
        },
        transitionSpec = { calendarSlideTransition(slideDir, slideSpec) },
        label = "week-transition",
    ) { s ->
        when (s) {
            WeekUiState.Loading -> WeekLoading()
            is WeekUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
            is WeekUiState.Success -> WeekSuccess(
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
private fun WeekSuccess(
    state: WeekUiState.Success,
    topSectionColor: Color,
    scrollState: ScrollState,
    allDayHeight: Dp,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(topSectionColor),
        ) {
            WeekDayHeader(days = state.days, today = state.today)
            AllDayStrip(state = state, height = allDayHeight, onEventClick = onEventClick)
        }
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
private fun WeekTopBar(
    weekStart: LocalDate,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = formatWeekRange(weekStart),
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
        // Match the static top section exactly: plain surface, lifting to
        // surfaceContainer once content scrolls under the bar.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun WeekDayHeader(days: List<LocalDate>, today: LocalDate) {
    val locale = currentLocale()
    val weekStart = days.first()
    val weekNumber = remember(weekStart) {
        java.time.LocalDate.of(weekStart.year, weekStart.month.ordinal + 1, weekStart.day)
            .get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        // Mirror the day-column layout (empty weekday line + spacer) so the
        // badge lines up vertically with the date numbers.
        Column(
            modifier = Modifier.width(GUTTER_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = " ", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(2.dp))
            WeekNumberBadge(weekNumber = weekNumber)
        }
        days.forEach { date ->
            val javaDow = java.time.DayOfWeek.of(date.dayOfWeek.ordinal + 1)
            val isToday = date == today
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = javaDow.getDisplayName(JavaTextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                // Always reserve the 28dp circle slot so the header height is
                // identical whether or not the week contains today.
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isToday) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxSize(),
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
                        )
                    }
                }
            }
        }
    }
}

/** Calendar-week badge shown in the header gutter, deliberately set apart with a
 *  filled box and bold number. */
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
            .padding(vertical = ALL_DAY_VERTICAL_PADDING),
    ) {
        // Keep the gutter-width offset so the bars line up with the day columns.
        Spacer(Modifier.width(GUTTER_WIDTH))
        // Span bars are positioned absolutely so a multi-day event is one
        // connected bar across columns rather than a chip per day. clipToBounds
        // keeps bars from spilling out while the height animates.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            val colWidth = maxWidth / 7
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
    Box(
        modifier = modifier
            .background(pastelize(event.color, dark), RoundedCornerShape(4.dp))
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
            color = Color.Black.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun Timeline(
    state: WeekUiState.Success,
    scrollState: ScrollState,
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
) {
    val totalHeight = HOUR_HEIGHT * 24
    val dark = isSystemInDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        // Gutter and day columns are two scroll viewports that SHARE one scroll
        // state, so they stay perfectly aligned. The day-column viewport is a
        // static, rounded-clipped window — the content scrolls inside it, so the
        // soft corners are permanent at any scroll position (not just at the
        // day's start/end).
        Row(modifier = Modifier.fillMaxSize()) {
            // Hour gutter (scrolls in sync with the day columns)
            Column(
                modifier = Modifier
                    .width(GUTTER_WIDTH)
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
                                text = "%02d".format(h),
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
            // Day columns: rounded, clipped scroll viewport (permanent corners).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .verticalScroll(scrollState),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.days.forEach { day ->
                        DayColumnCard(
                            blocks = state.timedByDay[day].orEmpty(),
                            dark = dark,
                            date = day,
                            today = state.today,
                            onEventClick = onEventClick,
                            onCreateAt = onCreateAt,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
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
    onEventClick: (EventInstance) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    Card(
        // Plain rectangular columns — the soft corners come from the outer
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
                // Tap an empty slot to create an event there; taps on event
                // blocks are consumed by their own handler first. Snaps to hour.
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
    val timeLabel = "${minToHm(block.startMin)}–${minToHm(block.endMin)}"
    val showTime = block.endMin - block.startMin >= 45
    Box(
        modifier = modifier
            .background(pastelize(block.event.color, dark), RoundedCornerShape(4.dp))
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
                color = Color.Black.copy(alpha = 0.85f),
            )
            if (showTime) {
                // Narrow columns can't fit "13:00–14:00" on one line, so let it
                // wrap to a second line (after the dash) instead of clipping the
                // end time.
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun WeekLoading() {
    val totalHeight = HOUR_HEIGHT * 24
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        // Header skeleton
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            repeat(7) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(36.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(8.dp),
                        ),
                )
            }
        }
        Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            repeat(7) {
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
}

private fun minToHm(min: Int): String =
    if (min >= MINUTES_PER_DAY) "24:00" else "%02d:%02d".format(min / 60, min % 60)

private fun formatWeekRange(weekStart: LocalDate): String {
    val locale = Locale.getDefault()
    val end = weekStart.plus(6, kotlinx.datetime.DateTimeUnit.DAY)
    val monthName = { d: LocalDate ->
        java.time.Month.of(d.month.ordinal + 1).getDisplayName(JavaTextStyle.SHORT, locale)
    }
    return if (weekStart.month == end.month && weekStart.year == end.year) {
        "${weekStart.day}.–${end.day}. ${monthName(weekStart)} ${weekStart.year}"
    } else if (weekStart.year == end.year) {
        "${weekStart.day}. ${monthName(weekStart)} – ${end.day}. ${monthName(end)} ${end.year}"
    } else {
        "${weekStart.day}. ${monthName(weekStart)} ${weekStart.year} – " +
            "${end.day}. ${monthName(end)} ${end.year}"
    }
}
