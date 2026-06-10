package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.pastelize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val HOUR_HEIGHT = 56.dp
private val GUTTER_WIDTH = 48.dp
private val MIN_EVENT_HEIGHT = 24.dp
private val ALL_DAY_ROW_HEIGHT = 24.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
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
        is WeekUiState.Success -> s.weekStart == s.today.startOfWeek(DayOfWeek.MONDAY)
        else -> true
    }

    // Slide direction for the week transition: +1 = next, -1 = prev, 0 = jump.
    var slideDir by remember { mutableIntStateOf(0) }
    val goNext = { slideDir = 1; viewModel.goToNext() }
    val goPrev = { slideDir = -1; viewModel.goToPrev() }
    val jumpToToday = { slideDir = 0; viewModel.goToToday() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the week swipe.
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
                WeekTopBar(
                    weekStart = weekStart,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next()) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isOnCurrentWeek,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = jumpToToday,
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        text = { Text(stringResource(R.string.week_today_action)) },
                    )
                }
            },
        ) { innerPadding ->
            WeekContent(
                state = state,
                slideDir = slideDir,
                topSectionColor = topSectionColor,
                onSwipeNext = goNext,
                onSwipePrev = goPrev,
                onRetry = jumpToToday,
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberCalendarSlideSpec()

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
            is WeekUiState.Success -> WeekSuccess(state = s, topSectionColor = topSectionColor)
        }
    }
}

@Composable
private fun WeekSuccess(state: WeekUiState.Success, topSectionColor: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(topSectionColor)
                .animateContentSize(),
        ) {
            WeekDayHeader(days = state.days, today = state.today)
            AllDayStrip(state = state)
        }
        // Breathing room between the (colour-shifting) top section and the
        // scrolling timeline below.
        Spacer(Modifier.height(8.dp))
        Timeline(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTopBar(
    weekStart: LocalDate,
    selectedView: CalendarView,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Spacer(Modifier.width(GUTTER_WIDTH))
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

@Composable
private fun AllDayStrip(state: WeekUiState.Success) {
    if (state.allDaySpans.isEmpty()) return
    val dark = isSystemInDarkTheme()
    val lanes = state.allDaySpans.maxOf { it.lane } + 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier.width(GUTTER_WIDTH),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                text = stringResource(R.string.week_all_day),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            )
        }
        // Span bars are positioned absolutely so a multi-day event is one
        // connected bar across columns rather than a chip per day.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(ALL_DAY_ROW_HEIGHT * lanes),
        ) {
            val colWidth = maxWidth / 7
            state.allDaySpans.forEach { span ->
                val spanCols = span.endCol - span.startCol + 1
                AllDayBar(
                    event = span.event,
                    dark = dark,
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
private fun AllDayBar(event: EventInstance, dark: Boolean, modifier: Modifier = Modifier) {
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    Box(
        modifier = modifier
            .background(pastelize(event.color, dark), RoundedCornerShape(4.dp))
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
private fun Timeline(state: WeekUiState.Success) {
    val totalHeight = HOUR_HEIGHT * 24
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current

    Box(modifier = Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()

        // Center the timeline on noon once the content is measured. Deriving the
        // target from maxValue (known after layout) is reliable, unlike reading
        // the viewport height during the first composition.
        LaunchedEffect(scrollState) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            val maxV = scrollState.maxValue
            val target = with(density) {
                (HOUR_HEIGHT.toPx() * 12 - (HOUR_HEIGHT.toPx() * 24 - maxV) / 2f).roundToInt()
            }.coerceIn(0, maxV)
            scrollState.scrollTo(target)
        }

        // One scroll container for the whole timeline. Gutter and day columns
        // are siblings inside it, so they share the exact same scroll position
        // and can never drift apart.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // Hour gutter
            Column(
                modifier = Modifier
                    .width(GUTTER_WIDTH)
                    .height(totalHeight),
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
            // Day columns bundled in a rounded container so the whole block has
            // soft corners (rounded at the day's start and end).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(totalHeight)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.days.forEach { day ->
                        DayColumnCard(
                            blocks = state.timedByDay[day].orEmpty(),
                            dark = dark,
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
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.small,
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
    modifier: Modifier = Modifier,
) {
    val title = block.event.title.ifBlank { stringResource(R.string.event_untitled) }
    val timeLabel = "${minToHm(block.startMin)}–${minToHm(block.endMin)}"
    val showTime = block.endMin - block.startMin >= 45
    Box(
        modifier = modifier
            .background(pastelize(block.event.color, dark), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = "$title, $timeLabel" },
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = if (showTime) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.Black.copy(alpha = 0.85f),
            )
            if (showTime) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
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
