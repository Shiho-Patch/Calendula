package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.calendula.ui.common.pastelize
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    selectedView: CalendarView,
    onSelectView: (CalendarView) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()

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
    val jumpToToday = {
        slideDir = 0
        viewModel.goToToday()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Open only via the menu button — edge-swipe would fight the month swipe.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            CalendarDrawer(
                onToday = {
                    jumpToToday()
                    scope.launch { drawerState.close() }
                },
                onJumpToDate = { scope.launch { drawerState.close() } /* TODO: open date picker */ },
                onFilter = { scope.launch { drawerState.close() } /* TODO: open filter sheet */ },
                onSettings = { scope.launch { drawerState.close() } /* TODO: navigate to settings */ },
            )
        },
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MonthTopBar(
                    month = month,
                    selectedView = selectedView,
                    onCycleView = { onSelectView(selectedView.next()) },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = !isOnCurrentMonth,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = jumpToToday,
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        text = { Text(stringResource(R.string.month_today_action)) },
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                WeekdayHeader(weekStart = DayOfWeek.MONDAY)
                MonthContent(
                    state = state,
                    slideDir = slideDir,
                    onSwipeNext = goNext,
                    onSwipePrev = goPrev,
                    onRetry = jumpToToday,
                )
            }
        }
    }
}

@Composable
private fun MonthContent(
    state: MonthUiState,
    slideDir: Int,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
    onRetry: () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 6.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val slideSpec = rememberCalendarSlideSpec()

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
        transitionSpec = { calendarSlideTransition(slideDir, slideSpec) },
        label = "month-transition",
    ) { s ->
        when (s) {
            MonthUiState.Loading -> MonthGridLoading()
            is MonthUiState.Failure -> CalendarFailure(reason = s.reason, onRetry = onRetry)
            is MonthUiState.Success -> MonthGrid(state = s, weekStart = DayOfWeek.MONDAY)
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
private fun WeekdayHeader(weekStart: DayOfWeek) {
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

@Composable
private fun MonthGrid(
    state: MonthUiState.Success,
    weekStart: DayOfWeek,
) {
    val firstOfMonth = LocalDate(state.month.year, state.month.month, 1)
    val gridStart = firstOfMonth.startOfGridWeek(weekStart)

    // Show only the weeks the current month actually touches; leading/trailing
    // days of neighbouring months are left blank rather than rendered.
    val leadOffset = ((firstOfMonth.dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    val daysInMonth =
        java.time.YearMonth.of(state.month.year, state.month.month.ordinal + 1).lengthOfMonth()
    val weeks = (leadOffset + daysInMonth + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(weeks) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { col ->
                    val date = gridStart.plus(row * 7 + col, DateTimeUnit.DAY)
                    val inMonth =
                        date.month == state.month.month && date.year == state.month.year
                    if (inMonth) {
                        DayCard(
                            date = date,
                            isToday = date == state.today,
                            data = state.cells[date],
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayCard(
    date: LocalDate,
    isToday: Boolean,
    data: DayCellData?,
    modifier: Modifier = Modifier,
) {
    val todayPrefix = stringResource(R.string.month_a11y_today_prefix)
    val cellLabel = buildString {
        if (isToday) append(todayPrefix).append(", ")
        append(date.year).append('-')
        append((date.month.ordinal + 1).toString().padStart(2, '0')).append('-')
        append(date.day.toString().padStart(2, '0'))
        data?.let { append(", ").append(it.count).append(" Events") }
    }

    // M3 Expressive press feedback: a spatial spring from the active motion
    // scheme drives a subtle scale, instead of a fixed easing curve.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "day-card-press",
    )

    Card(
        onClick = { /* TODO: open the day view (S3) for this date */ },
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = cellLabel },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.day.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            EventDotRow(data)
        }
    }
}

@Composable
private fun EventDotRow(data: DayCellData?) {
    if (data == null || data.swatches.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        return
    }
    val dark = isSystemInDarkTheme()
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.swatches.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(pastelize(argb, dark), CircleShape),
            )
        }
        if (data.count > data.swatches.size) {
            Text(
                text = "+${data.count - data.swatches.size}",
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
