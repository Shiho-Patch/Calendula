package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val DotSize = 8.dp
private val LineThickness = 2.dp

/**
 * Current wall-clock instant that updates once a minute, re-aligning to each
 * minute boundary (rather than ticking a fixed 60 s) so it never drifts toward
 * the middle of a minute. Drives the "now" line in the day/week grids.
 */
@Composable
private fun rememberCurrentMinute(): State<Instant> {
    val instant = remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Clock.System.now()
            instant.value = now
            delay(60_000L - now.toEpochMilliseconds() % 60_000L)
        }
    }
    return instant
}

/**
 * A thin "current time" indicator — a leading dot plus a line — drawn across a
 * day column. Positioned on the same [hourHeight] scale the event blocks use so
 * it lines up with the grid, and refreshed each minute. Renders nothing unless
 * the wall clock is on [date]; callers mount it only for the column showing
 * today, so the per-minute tick runs on a single column.
 */
@Composable
fun NowLine(
    date: LocalDate,
    hourHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val now by rememberCurrentMinute()
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    if (local.date != date) return

    val minutes = local.hour * 60 + local.minute
    val top = hourHeight * (minutes / 60f)
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DotSize)
            .offset(y = top - DotSize / 2),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LineThickness)
                .background(color),
        )
        // The dot anchors the line to the gutter edge, mirroring the standard
        // calendar "now" marker; drawn after the line so it sits on top.
        Box(
            modifier = Modifier
                .size(DotSize)
                .background(color, CircleShape),
        )
    }
}
