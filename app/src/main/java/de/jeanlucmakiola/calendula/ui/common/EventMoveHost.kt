package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.SnackChip
import de.jeanlucmakiola.floret.components.SnackChipHeight
import de.jeanlucmakiola.floret.components.SnackChipMargin
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** How long the confirmation chip stays up, matching a short snackbar. */
private const val CHIP_MILLIS = 4_000L

/** What the chip currently reads, kept past the outcome it was built from. */
private data class ChipContent(val message: String, val undo: MoveUndo?)

/** The FAB's own band at the bottom end, which the chip must not run into. */
private val FAB_BAND = 88.dp

/**
 * The two surfaces a drag-and-drop reschedule needs on top of the calendar: the
 * recurring-scope prompt, and the confirmation chip carrying Undo.
 *
 * None of the four calendar screens sets a `snackbarHost` on its Scaffold, so
 * this hosts its own confirmation — a pill on the FAB's own band at the bottom
 * start rather than a full-width bar, so the calendar it confirms a change to
 * stays visible behind it.
 */
@Composable
fun EventMoveHost(viewModel: RescheduleViewModel, modifier: Modifier = Modifier) {
    val prompt by viewModel.scopePrompt.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val locale = currentLocale()
    val use24Hour = LocalUse24HourFormat.current

    prompt?.let { pending ->
        RecurringScopeDialog(
            title = stringResource(R.string.event_move_recurring_title),
            onSelect = viewModel::moveWithScope,
            onDismiss = viewModel::cancelScope,
            allowSeries = !pending.occurrenceOnly,
            reason = stringResource(R.string.event_move_occurrence_only)
                .takeIf { pending.occurrenceOnly },
        )
    }

    val moved = outcome as? MoveOutcome.Moved
    val movedLabel = moved?.let { formatMovedTo(it.startMillis, it.isAllDay, use24Hour, locale) }
    val message = when (outcome) {
        null -> null
        is MoveOutcome.Moved -> stringResource(R.string.event_move_done, movedLabel.orEmpty())
        MoveOutcome.Undone -> stringResource(R.string.event_move_undone)
        MoveOutcome.WriteDenied -> stringResource(R.string.event_move_write_denied)
        MoveOutcome.Gone -> stringResource(R.string.event_move_gone)
        MoveOutcome.BlockedSeriesEnd -> stringResource(R.string.event_move_blocked_series_end)
        MoveOutcome.Failed -> stringResource(R.string.event_move_failed)
    }

    // Held past the outcome being consumed so the chip has something to draw
    // while it springs back out. Updated *in composition* rather than from an
    // effect: an effect lands a frame late, so the chip would open carrying the
    // previous message and grow into this one while it was still animating in.
    val shown = remember { mutableStateOf(ChipContent("", null)) }
    if (message != null && (shown.value.message != message || shown.value.undo != moved?.undo)) {
        shown.value = ChipContent(message, moved?.undo)
    }
    val content = shown.value
    LaunchedEffect(outcome) {
        if (outcome == null) return@LaunchedEffect
        delay(CHIP_MILLIS)
        viewModel.consumeOutcome()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val chipMaxWidth = maxWidth - FAB_BAND
        // A FAB-height band anchored at the bottom start with the FAB's own
        // margin; centring the chip in it lines it up beside the bottom-end FAB
        // at exactly its height, rather than sitting a touch above it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = SnackChipMargin, bottom = SnackChipMargin)
                .height(SnackChipHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            SnackChip(
                visible = outcome != null,
                message = content.message,
                maxWidth = chipMaxWidth,
                actionLabel = stringResource(R.string.event_move_undo)
                    .takeIf { content.undo != null },
                onAction = content.undo?.let { undo -> { viewModel.undo(undo) } },
            )
        }
    }
}

/**
 * "Fri, 7 Aug, 09:00" — the day, plus the time for a timed event. All-day events
 * are read back on the UTC calendar day they are anchored to (#65, #82).
 */
private fun formatMovedTo(
    startMillis: Long,
    isAllDay: Boolean,
    use24Hour: Boolean,
    locale: Locale,
): String {
    val zone: ZoneId = if (isAllDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val skeleton = when {
        isAllDay -> "EEEdMMM"
        use24Hour -> "EEEdMMMHm"
        else -> "EEEdMMMhm"
    }
    return localizedDateFormatter(locale, skeleton)
        .format(Instant.ofEpochMilli(startMillis).atZone(zone))
}
