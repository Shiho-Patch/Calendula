package de.jeanlucmakiola.calendula.ui.day

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
import de.jeanlucmakiola.calendula.ui.week.layoutAllDay
import de.jeanlucmakiola.calendula.ui.week.layoutDay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A live, scaled-down Day view for the onboarding view chooser. Today's date is
 * real; only the events are stand-ins.
 */
@Composable
internal fun DayViewPreview(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val state = remember(today, zone) { sampleDayState(today, zone) }
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        // Half the scroll range is noon — the same centring the live view does.
        scrollState.scrollTo(scrollState.maxValue / 2)
    }

    ScaledViewPreview(height = height, modifier = modifier) {
        DaySuccess(
            state = state,
            topSectionColor = MaterialTheme.colorScheme.surface,
            scrollState = scrollState,
            allDayHeight = state.allDayStripHeight(),
            dragController = rememberTimelineDragController(),
            onEventClick = {},
            onCreateAt = { _, _ -> },
            onDrop = {},
        )
    }
}

/** Sample day state, laid out through the same helpers the live view uses. */
private fun sampleDayState(today: LocalDate, zone: TimeZone): DayUiState.Success {
    val days = listOf(today)
    val events = sampleTimelineEvents(days, today, zone)
    return DayUiState.Success(
        date = today,
        today = today,
        allDay = layoutAllDay(events.filter { it.isAllDay }, days, zone),
        timed = layoutDay(events, today, zone),
    )
}
