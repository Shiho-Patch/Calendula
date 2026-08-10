package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.calendula.ui.common.ScaledViewPreview
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineDragController
import de.jeanlucmakiola.calendula.ui.common.sampleTimelineEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A live, scaled-down Week view for the onboarding view chooser. The week and
 * today's column are real; only the events are stand-ins.
 *
 * The timeline is scrolled to the working day rather than to midnight — the
 * live view centres on noon once it knows its own scroll range, which a preview
 * that is never interacted with would otherwise never do.
 */
@Composable
internal fun WeekViewPreview(
    weekStart: DayOfWeek,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val state = remember(today, weekStart, zone) { sampleWeekState(today, weekStart, zone) }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        // Half the scroll range is noon — the same centring the live view does.
        scrollState.scrollTo(scrollState.maxValue / 2)
    }

    ScaledViewPreview(height = height, modifier = modifier) {
        WeekSuccess(
            state = state,
            topSectionColor = MaterialTheme.colorScheme.surface,
            scrollState = scrollState,
            allDayHeight = state.allDayStripHeight(),
            dragController = rememberTimelineDragController(),
            onEventClick = {},
            onOpenDay = {},
            onCreateAt = { _, _ -> },
            onDrop = {},
        )
    }
}

/** Sample week state, laid out through the same helpers the live view uses. */
private fun sampleWeekState(
    today: LocalDate,
    weekStart: DayOfWeek,
    zone: TimeZone,
): WeekUiState.Success {
    val start = today.startOfWeek(weekStart)
    val days = (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
    val events = sampleTimelineEvents(days, today, zone)
    return WeekUiState.Success(
        weekStart = start,
        today = today,
        days = days,
        allDaySpans = layoutAllDay(events.filter { it.isAllDay }, days, zone),
        timedByDay = days.associateWith { layoutDay(events, it, zone) },
    )
}
