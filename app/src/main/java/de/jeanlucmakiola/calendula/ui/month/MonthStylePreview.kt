package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.math.roundToInt

/**
 * A live, scaled-down Month view in a given [MonthViewStyle], for the settings
 * chooser.
 *
 * It renders the *real* grid composables rather than a drawing of them, so the
 * preview cannot drift from the thing it depicts: change a cell's shape or an
 * event bar's colour and every preview follows automatically. The trick is
 * [requiredSize] — it ignores the incoming constraints, so the grid lays itself
 * out at a full phone width and a plausible viewport height, and a
 * [graphicsLayer] scale shrinks the finished layout into the card.
 *
 * The month, today's position and the week start are all real; only the events
 * are stand-ins, since the settings screen has no business querying the
 * provider for a thumbnail.
 */
@Composable
internal fun MonthStylePreview(
    style: MonthViewStyle,
    weekStart: DayOfWeek,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val scale = height.value / VIRTUAL_HEIGHT.value
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val sample = remember(today, weekStart, zone) { sampleMonthState(today, weekStart, zone) }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            // A preview is a picture, not a control: swallow touches before the
            // grid's own clickables see them, and give screen readers one label
            // instead of six weeks of day cells.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes
                            .forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { }
            // Measure the grid at a full phone viewport but report the scaled
            // size, so the node occupies exactly what it draws.
            //
            // Modifier.requiredSize would be the obvious way to force the larger
            // measurement, but it *centres* content that overflows the incoming
            // constraints — which pushed the grid to a negative offset and left
            // only its bottom-right corner inside the clip.
            .layout { measurable, _ ->
                val fullWidth = screenWidth.roundToPx()
                val fullHeight = VIRTUAL_HEIGHT.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(fullWidth, fullHeight))
                layout((fullWidth * scale).roundToInt(), (fullHeight * scale).roundToInt()) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            WeekdayHeader(weekStart = weekStart, showWeekNumbers = false)
                when (style) {
                    MonthViewStyle.Paged -> MonthGrid(
                        state = sample.month,
                        showWeekNumbers = false,
                        onOpenDay = {},
                    )
                    MonthViewStyle.Continuous -> ContinuousMonthGrid(
                        state = sample.continuous,
                        listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = itemIndexForMonth(
                                monthIndexOf(YearMonth(today.year, today.month)),
                            ),
                        ),
                        showWeekNumbers = false,
                        onOpenDay = {},
                    )
                    MonthViewStyle.Dense -> DenseMonthGrid(
                        state = sample.continuous,
                        listState = rememberLazyListState(
                            // Start a week above today's, so the preview shows a
                            // stream running past the viewport rather than one
                            // beginning at its top edge.
                            initialFirstVisibleItemIndex =
                                weekIndexOf(today, weekStart) - 1,
                        ),
                        showWeekNumbers = false,
                        onOpenDay = {},
                    )
                    MonthViewStyle.Split -> {
                        SplitMonthGrid(
                            state = sample.month,
                            selected = today,
                            showWeekNumbers = false,
                            onSelectDay = {},
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SplitDayPane(
                            date = today,
                            today = today,
                            events = sample.month.instancesByDay[today].orEmpty(),
                            zone = zone,
                            onOpenDay = {},
                            onEventClick = {},
                            onCreateEvent = {},
                            modifier = Modifier.fillMaxWidth().height(SPLIT_PANE_HEIGHT),
                        )
                    }
                }
            }
    }
}

/**
 * The viewport the preview pretends to be: a phone's width by the height a
 * calendar view gets under the top bar. Scaling from a fixed height keeps the
 * three styles comparable — each shows the same slice of screen.
 */
private val VIRTUAL_HEIGHT = 440.dp
private val SPLIT_PANE_HEIGHT = 170.dp

private class SampleMonth(
    val month: MonthUiState.Success,
    val continuous: ContinuousMonthUiState.Success,
)

/**
 * A month's worth of stand-in events, laid out through the same
 * [layoutMonthWeeks] / [clipWeekToMonth] the live views use — so the preview
 * exercises the real span, lane and overflow logic rather than approximating it.
 *
 * Colours are raw ARGB on purpose: that is what the provider hands out for an
 * event, so a token here would misrepresent what the grid actually renders.
 */
private fun sampleMonthState(
    today: LocalDate,
    weekStart: DayOfWeek,
    zone: TimeZone,
): SampleMonth {
    val ym = YearMonth(today.year, today.month)
    val first = LocalDate(ym.year, ym.month, 1)
    val events = sampleEvents(first, today, zone)

    val weeks = layoutMonthWeeks(ym, weekStart, events, zone)
    val month = MonthUiState.Success(
        month = ym,
        today = today,
        weeks = weeks,
        instancesByDay = instancesByDay(weeks.flatMap { it.days }, events, zone),
        zone = zone,
    )

    // The month either side of this one: enough for Continuous to show a block,
    // the whitespace under it and the next month's header coming up, and for
    // Dense to have somewhere to scroll in both directions.
    val centre = monthIndexOf(ym)
    val window = (centre - 1)..(centre + 1)
    val continuous = ContinuousMonthUiState.Success(
        today = today,
        monthsByIndex = window.associateWith { index ->
            val m = yearMonthForIndex(index)
            layoutMonthWeeks(m, weekStart, events, zone).map { clipWeekToMonth(it, m) }
        },
        weeksByIndex = weekWindowFor(window, weekStart).associateWith { index ->
            val days = (0 until 7).map {
                weekStartForIndex(index, weekStart).plus(it, DateTimeUnit.DAY)
            }
            layoutCalendarWeek(days, events, zone)
        },
        weekStart = weekStart,
    )
    return SampleMonth(month, continuous)
}

private val SAMPLE_COLORS = listOf(
    0xFF3F7BD4.toInt(),
    0xFFCE5B4C.toInt(),
    0xFF4E9A6A.toInt(),
    0xFF8A63C7.toInt(),
)

private fun sampleEvents(
    firstOfMonth: LocalDate,
    today: LocalDate,
    zone: TimeZone,
): List<EventInstance> {
    var id = 0L
    fun next() = ++id

    fun timed(day: LocalDate, hour: Int, length: Int, colorIndex: Int) = EventInstance(
        instanceId = next(),
        eventId = id,
        calendarId = 1L,
        title = SAMPLE_TITLES[(id.toInt() - 1) % SAMPLE_TITLES.size],
        start = day.atTime(hour, 0).toInstant(zone),
        end = day.atTime(hour + length, 0).toInstant(zone),
        isAllDay = false,
        color = SAMPLE_COLORS[colorIndex % SAMPLE_COLORS.size],
        location = null,
    )

    fun allDay(from: LocalDate, days: Int, colorIndex: Int) = EventInstance(
        instanceId = next(),
        eventId = id,
        calendarId = 1L,
        title = SAMPLE_TITLES[(id.toInt() - 1) % SAMPLE_TITLES.size],
        // All-day events sit at UTC midnights with an exclusive end.
        start = from.atTime(0, 0).toInstant(TimeZone.UTC),
        end = from.plus(days, DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC),
        isAllDay = true,
        color = SAMPLE_COLORS[colorIndex % SAMPLE_COLORS.size],
        location = null,
    )

    // Spread across the month so most weeks carry something, with one multi-day
    // bar to show a span bridging cells and a busy today for the split pane.
    return buildList {
        add(allDay(firstOfMonth.plus(9, DateTimeUnit.DAY), days = 3, colorIndex = 2))
        add(timed(firstOfMonth.plus(1, DateTimeUnit.DAY), 9, 1, 0))
        add(timed(firstOfMonth.plus(4, DateTimeUnit.DAY), 14, 2, 1))
        add(timed(firstOfMonth.plus(7, DateTimeUnit.DAY), 11, 1, 3))
        add(timed(firstOfMonth.plus(15, DateTimeUnit.DAY), 10, 1, 0))
        add(timed(firstOfMonth.plus(18, DateTimeUnit.DAY), 16, 1, 2))
        add(timed(firstOfMonth.plus(22, DateTimeUnit.DAY), 8, 2, 1))
        add(timed(firstOfMonth.plus(25, DateTimeUnit.DAY), 13, 1, 3))
        // Today, so the split pane's list and the grid's dots both have content.
        add(timed(today, 9, 1, 0))
        add(timed(today, 12, 1, 1))
        add(timed(today, 15, 2, 3))
    }
}

private val SAMPLE_TITLES = listOf(
    "Standup",
    "Lunch",
    "Review",
    "Gym",
    "Call",
    "Workshop",
    "Dentist",
    "Trip",
)
