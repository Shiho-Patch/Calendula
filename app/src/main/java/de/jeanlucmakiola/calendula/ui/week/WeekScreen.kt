package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.common.CalendarDrawer
import de.jeanlucmakiola.calendula.ui.common.CalendarTitleButton
import de.jeanlucmakiola.calendula.ui.common.CalendarFabColumn
import de.jeanlucmakiola.calendula.ui.common.TodayAction
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.ghostAlpha
import de.jeanlucmakiola.calendula.ui.common.LocalEventMove
import de.jeanlucmakiola.calendula.ui.common.MoveRequest
import de.jeanlucmakiola.calendula.ui.common.MoveTarget
import de.jeanlucmakiola.calendula.ui.common.TimelineDragController
import de.jeanlucmakiola.calendula.ui.common.TimelineDragOverlay
import de.jeanlucmakiola.calendula.ui.common.TimelineDrop
import de.jeanlucmakiola.calendula.ui.common.beginsOn
import de.jeanlucmakiola.calendula.ui.common.eventMoveAction
import de.jeanlucmakiola.calendula.ui.common.rememberEventDragSource
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineDragController
import de.jeanlucmakiola.calendula.ui.common.startInstant
import de.jeanlucmakiola.calendula.ui.common.LocalDimCutoff
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.calendula.ui.common.NowLine
import de.jeanlucmakiola.calendula.ui.common.rememberCurrentMinute
import de.jeanlucmakiola.calendula.ui.common.ViewSwitcherPill
import de.jeanlucmakiola.calendula.ui.common.calendarSlideTransition
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarPageSwipe
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourLines
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.MIN_EVENT_FRACTION
import de.jeanlucmakiola.calendula.ui.common.MIN_TITLE_WRAP_WIDTH
import de.jeanlucmakiola.calendula.ui.common.SECONDARY_INK_ALPHA
import de.jeanlucmakiola.calendula.ui.common.hourHeight
import de.jeanlucmakiola.calendula.ui.common.rememberTimelinePinchZoom
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.GUTTER_CONTENT_START_INSET
import de.jeanlucmakiola.calendula.ui.common.GUTTER_WIDTH
import de.jeanlucmakiola.calendula.ui.common.HourGutter
import de.jeanlucmakiola.calendula.ui.common.hourSeparatorLines
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.next
import de.jeanlucmakiola.floret.time.isoWeekNumber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val ALL_DAY_ROW_HEIGHT = 24.dp
private val ALL_DAY_VERTICAL_PADDING = 6.dp
/** Gap between day columns; part of the column pitch a drag maps positions through. */
private val COLUMN_GAP = 2.dp

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
    onOpenDay: (LocalDate) -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateEvent: (LocalDate, Int?) -> Unit,
    quickSwitchViews: List<CalendarView> = IMPLEMENTED_VIEWS,
    drawerViewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
    todayInToolbar: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStartDate.collectAsStateWithLifecycle()
    val dimCompleted by viewModel.dimCompletedEvents.collectAsStateWithLifecycle()
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

    // Drives whether the title carries the year. Falls back to the clock only
    // while the first load is in flight, when there is no state to read today from.
    val currentYear = when (val s = state) {
        is WeekUiState.Success -> s.today.year
        else -> Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.year
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
                WeekTopBar(
                    weekStart = weekStart,
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
                    todayVisible = !isOnCurrentWeek && !todayInToolbar,
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
            CompositionLocalProvider(LocalDimCutoff provides dimCutoff) {
                WeekContent(
                    state = state,
                    slideDir = slideDir,
                    topSectionColor = topSectionColor,
                    onSwipeNext = goNext,
                    onSwipePrev = goPrev,
                    onRetry = jumpToToday,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onCreateAt = { d, minutes -> onCreateEvent(d, minutes) },
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                )
            }
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
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slideSpec = rememberCalendarSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()

    // Hoisted above the per-week AnimatedContent so the vertical scroll position
    // survives week-to-week swipes (e.g. 18:00 stays centred). We only centre on
    // noon once, on first entry into the week view (i.e. when arriving from the
    // month/day view), not on every swipe.
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        // Half the scroll range *is* noon: the content spans a full 24 hours, so
        // centring the range centres midday at whatever hour height is in force.
        scrollState.scrollTo(scrollState.maxValue / 2)
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
    val swipeModifier = rememberCalendarPageSwipe(onSwipeNext, onSwipePrev)

    // Above the AnimatedContent on purpose: a page change mid-drag would strand
    // the floating block inside the outgoing page.
    val dragController = rememberTimelineDragController()
    val move = LocalEventMove.current
    val zone = remember { TimeZone.currentSystemDefault() }

    Box(modifier = modifier) {
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize().then(swipeModifier),
            contentKey = { s ->
                when (s) {
                    is WeekUiState.Success -> "success-${s.weekStart}"
                    is WeekUiState.Failure -> "failure-${s.reason}"
                    WeekUiState.Loading -> "loading"
                }
            },
            transitionSpec = { calendarSlideTransition(slideDir, slideSpec, fadeSpec, reduceMotion) },
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
                    dragController = dragController,
                    onEventClick = onEventClick,
                    onOpenDay = onOpenDay,
                    onCreateAt = onCreateAt,
                    onDrop = { drop ->
                        move?.move(
                            MoveRequest(
                                eventId = drop.event.eventId,
                                beginMillis = drop.event.start.toEpochMilliseconds(),
                                endMillis = drop.event.end.toEpochMilliseconds(),
                                target = MoveTarget.Start(drop.startInstant(zone)),
                            ),
                        )
                    },
                )
            }
        }
        TimelineDragOverlay(dragController)
    }
}

@Composable
private fun WeekSuccess(
    state: WeekUiState.Success,
    topSectionColor: Color,
    scrollState: ScrollState,
    allDayHeight: Dp,
    dragController: TimelineDragController,
    onEventClick: (EventInstance) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onDrop: (TimelineDrop) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(topSectionColor),
        ) {
            WeekDayHeader(days = state.days, today = state.today, onOpenDay = onOpenDay)
            AllDayStrip(state = state, height = allDayHeight, onEventClick = onEventClick)
        }
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
private fun WeekTopBar(
    weekStart: LocalDate,
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
                title = formatWeekTitle(weekStart, locale, currentYear),
                currentDate = weekStart,
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
private fun WeekDayHeader(
    days: List<LocalDate>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = currentLocale()
    val weekStart = days.first()
    val weekNumber = remember(weekStart) { weekStart.toJavaLocalDate().isoWeekNumber() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        // Mirror the day-column layout (empty weekday line + spacer) so the
        // badge lines up vertically with the date numbers. The start inset centres
        // the badge on the top bar's hamburger (see GUTTER_CONTENT_START_INSET).
        Column(
            modifier = Modifier.width(GUTTER_WIDTH).padding(start = GUTTER_CONTENT_START_INSET),
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
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenDay(date) },
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
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && event.hasEnded(dimCutoff)
    val soften = LocalSoftenColors.current
    val fill = eventFill(event.color, dark, soften)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
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
    state: WeekUiState.Success,
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
    // bar, day header and all-day strip.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hourHeight = zoom.scale.hourHeight(maxHeight)
        val totalHeight = hourHeight * 24
        // The pinch sits on the Row, above both scroll viewports: it has to
        // outrank the vertical scroll, and it does that by watching the initial
        // pass, which only reaches it if it is their ancestor.
        val pinch = rememberTimelinePinchZoom(scrollState, maxHeight, hourHeight, zoom)
        // Gutter and day columns are two scroll viewports that SHARE one scroll
        // state, so they stay perfectly aligned. The day-column viewport is a
        // static, rounded-clipped window — the content scrolls inside it, so the
        // soft corners are permanent at any scroll position (not just at the
        // day's start/end).
        Row(modifier = Modifier.fillMaxSize().then(pinch)) {
            // Hour gutter (scrolls in sync with the day columns). Same start inset
            // as the header badge so the labels sit under it and on the hamburger.
            HourGutter(
                scrollState = scrollState,
                hourHeight = hourHeight,
                dragController = dragController,
            )
            // Day columns: rounded, clipped scroll viewport (permanent corners).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { dragController.geometry.viewport = it },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeight)
                        // The scrolling content itself, so its root position
                        // already folds in the scroll offset — a drag maps
                        // through it without reading the scroll state.
                        .onGloballyPositioned { coords ->
                            val gap = with(density) { COLUMN_GAP.toPx() }
                            dragController.geometry.let {
                                it.grid = coords
                                it.scroll = scrollState
                                it.hourPx = with(density) { hourHeight.toPx() }
                                it.columnGapPx = gap
                                it.columnWidthPx = (coords.size.width + gap) / state.days.size
                                it.days = state.days
                                it.isRtl = isRtl
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
                ) {
                    state.days.forEach { day ->
                        DayColumnCard(
                            blocks = state.timedByDay[day].orEmpty(),
                            dark = dark,
                            date = day,
                            today = state.today,
                            hourHeight = hourHeight,
                            dragController = dragController,
                            onEventClick = onEventClick,
                            onCreateAt = onCreateAt,
                            onDrop = onDrop,
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
                // Faint hour separators sit over the column background but under
                // the event blocks (drawBehind paints before the children).
                .hourSeparatorLines(showHourLines, hourPx, hourLineColor)
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
            val minEventHeight = hourHeight * MIN_EVENT_FRACTION
            blocks.forEach { block ->
                val laneWidth = colWidth / block.laneCount
                val top = hourHeight * (block.startMin / 60f)
                val rawHeight = hourHeight * ((block.endMin - block.startMin) / 60f)
                val height = if (rawHeight < minEventHeight) minEventHeight else rawHeight
                EventBlock(
                    block = block,
                    dark = dark,
                    height = height,
                    width = laneWidth,
                    date = date,
                    dragController = dragController,
                    onClick = { onEventClick(block.event) },
                    onDrop = onDrop,
                    modifier = Modifier
                        .offset(x = laneWidth * block.lane, y = top)
                        .width(laneWidth)
                        .height(height)
                        .padding(horizontal = 1.dp),
                )
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
    width: Dp,
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
    // What's left for text once the 2.dp top/bottom padding is paid for.
    val available = height - 4.dp
    // Only full-width (non-overlapping) blocks that are tall enough show the
    // time. On narrow overlapping columns we drop it so the title can wrap to
    // fill the whole block, mirroring Google Calendar — and a block that cannot
    // afford both lines spends its space on the title.
    val showTime = block.endMin - block.startMin >= 45 &&
        block.laneCount == 1 &&
        available >= titleLineHeight + timeLineHeight
    // A short block drops the title rather than serving a horizontally sliced
    // one: half a letter reads as a rendering fault, while a bare colour chip
    // reads as what it is — an event too brief to label. Tap still opens it, and
    // the semantics description carries the full title either way.
    val showTitle = available >= titleLineHeight
    // Wrap the title across as many lines as the block can fit — but only once a
    // line is wide enough to hold more than a syllable. Below that the extra
    // lines just stack fragments of the word, and one ellipsised line reads
    // better.
    val contentHeight = available - if (showTime) timeLineHeight else 0.dp
    val titleMaxLines = if (width < MIN_TITLE_WRAP_WIDTH) {
        1
    } else {
        (contentHeight / titleLineHeight).toInt().coerceAtLeast(1)
    }
    val dimCutoff = LocalDimCutoff.current
    val dimmed = dimCutoff != null && block.event.hasEnded(dimCutoff)
    val soften = LocalSoftenColors.current
    val fill = eventFill(block.event.color, dark, soften)
    val zone = remember { TimeZone.currentSystemDefault() }
    val moveAction = eventMoveAction(block.event)
    // A block clipped at the top continues from the previous day: its top edge is
    // midnight, not the event's start, so dragging it would invent a time.
    val draggable = moveAction != null && block.beginsOn(date, zone)
    val dragModifier = rememberEventDragSource(
        enabled = draggable,
        key = block.event.instanceId,
        onPickUp = { pointer, blockRoot, _ -> dragController.begin(block, pointer, blockRoot) },
        onMove = dragController::move,
        onDrop = { dragController.finish()?.let(onDrop) },
        onCancel = dragController::cancel,
    )
    val lifted = draggable && dragController.liftedInstanceId == block.event.instanceId
    val ghost = ghostAlpha(lifted, landed = dragController.settling != null)
    Box(
        modifier = (if (dimmed) modifier.alpha(EventDimAlpha) else modifier)
            // The source stays put as a ghost while its floating copy travels,
            // then fades out as the copy settles on its new slot.
            .then(if (lifted) Modifier.alpha(ghost) else Modifier)
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
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = 0.85f),
                )
            }
            if (showTime) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun WeekLoading() {
    val scale = LocalTimelineZoom.current.scale
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Same scale resolution as the loaded timeline, so the skeleton's
            // columns don't resize the moment the real week arrives.
            val totalHeight = scale.hourHeight(maxHeight) * 24
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
}

private fun minToHm(min: Int, is24Hour: Boolean, locale: java.util.Locale): String =
    formatMinuteOfDay(min, is24Hour, locale)

/**
 * The week's title: just the month [weekStart] falls in.
 *
 * The day numbers are already in the column headers directly below, so spelling
 * out "13.–19. Jul" restates them in the widest string in the bar. Naming the
 * month of [weekStart] means a week straddling a boundary keeps the outgoing
 * month until it is fully gone — a week is seven contiguous days, so the earlier
 * month has a day in it exactly while [weekStart] is still inside it. That also
 * makes the *month* depend on nothing but [weekStart], so it cannot drift with
 * the direction you paged in from.
 *
 * The *year* is decided from the whole week, not just its start: a week running
 * Dec 28 – Jan 3 has four of its seven visible columns in the new year, so
 * "December" with no year would be actively misleading while the reader is
 * looking straight at January dates.
 */
private fun formatWeekTitle(weekStart: LocalDate, locale: Locale, currentYear: Int): String {
    val weekEnd = weekStart.plus(6, kotlinx.datetime.DateTimeUnit.DAY)
    return formatCalendarTitle(
        date = java.time.LocalDate.of(weekStart.year, weekStart.month.ordinal + 1, 1),
        locale = locale,
        currentYear = currentYear,
        skeleton = "LLLL",
        forceYear = weekEnd.year != weekStart.year,
    )
}
