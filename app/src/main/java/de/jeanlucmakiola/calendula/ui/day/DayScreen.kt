package de.jeanlucmakiola.calendula.ui.day

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.pastelize
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.week.MINUTES_PER_DAY
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 48.dp
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

    // Slide direction for the day transition: +1 = next, -1 = prev, 0 = jump.
    var slideDir by remember { mutableIntStateOf(0) }
    val goNext = { slideDir = 1; viewModel.goToNext() }
    val goPrev = { slideDir = -1; viewModel.goToPrev() }
    val jumpToToday = { slideDir = 0; viewModel.goToToday() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the day swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                onToday = { jumpToToday(); scope.launch { drawerState.close() } },
                onJumpToDate = { scope.launch { drawerState.close() } /* TODO: date picker */ },
                onFilter = { scope.launch { drawerState.close() } /* TODO: filter sheet */ },
                onSettings = { scope.launch { drawerState.close() } /* TODO: settings */ },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                DayTopBar(
                    date = date,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next()) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isOnToday,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = jumpToToday,
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        text = { Text(stringResource(R.string.day_today_action)) },
                    )
                }
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberCalendarSlideSpec()

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
                is DayUiState.Success -> "success-${s.date}"
                is DayUiState.Failure -> "failure-${s.reason}"
                DayUiState.Loading -> "loading"
            }
        },
        transitionSpec = { calendarSlideTransition(slideDir, slideSpec) },
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
        Timeline(state = state, scrollState = scrollState, onEventClick = onEventClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTopBar(
    date: LocalDate,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Text(
                text = formatDayTitle(date),
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
    state: DayUiState.Success,
    scrollState: ScrollState,
    onEventClick: (EventInstance) -> Unit,
) {
    val totalHeight = HOUR_HEIGHT * 24
    val dark = isSystemInDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        // Gutter and day column are two scroll viewports that SHARE one scroll
        // state, so they stay perfectly aligned. The day-column viewport is a
        // static, rounded-clipped window — the content scrolls inside it, so the
        // soft corners are permanent at any scroll position.
        Row(modifier = Modifier.fillMaxSize()) {
            // Hour gutter (scrolls in sync with the day column)
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
                    onEventClick = onEventClick,
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
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        // Plain rectangular column — the soft corners come from the outer
        // rounded scroll viewport, so inner rounding would look odd at the edges.
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Black.copy(alpha = 0.6f),
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

private fun minToHm(min: Int): String =
    if (min >= MINUTES_PER_DAY) "24:00" else "%02d:%02d".format(min / 60, min % 60)

private fun formatDayTitle(date: LocalDate): String {
    val locale = Locale.getDefault()
    val java = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
    val weekday = java.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val monthName = java.month.getDisplayName(JavaTextStyle.SHORT, locale)
    return "$weekday, ${date.day}. $monthName ${date.year}"
}
