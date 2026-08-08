package de.jeanlucmakiola.calendula.ui.agenda

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.ui.common.ScaledViewPreview
import de.jeanlucmakiola.calendula.ui.common.sampleAgendaEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** How far ahead the agenda preview's stand-in window runs. */
private const val PREVIEW_WINDOW_DAYS = 14

/**
 * A live, scaled-down Agenda view for the onboarding view chooser. Today and
 * the day headers are real; only the events are stand-ins.
 */
@Composable
internal fun AgendaViewPreview(
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val state = remember(today, zone) { sampleAgendaState(today, zone) }

    ScaledViewPreview(height = height, modifier = modifier) {
        AgendaContent(
            state = state,
            // Stand-in events are all upcoming, so the past-event preference
            // cannot change what the preview shows — keep it at the default.
            pastDisplay = PastEventDisplay.SHOW,
            showToday = true,
            onRetry = {},
            onEventClick = {},
            onOpenDay = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Sample agenda state, grouped through the same helper the live view uses. */
private fun sampleAgendaState(today: LocalDate, zone: TimeZone): AgendaUiState.Success {
    val windowEnd = today.plus(PREVIEW_WINDOW_DAYS, DateTimeUnit.DAY)
    return AgendaUiState.Success(
        anchor = today,
        today = today,
        days = groupAgendaDays(today, windowEnd, sampleAgendaEvents(today, zone), zone),
        range = AgendaRange.Custom(PREVIEW_WINDOW_DAYS),
        rangeIsOverride = false,
        rangeEnd = windowEnd,
        // The range bar belongs to the screen, not the list — the preview shows
        // the list only.
        showRangeBar = false,
        zone = zone,
    )
}
