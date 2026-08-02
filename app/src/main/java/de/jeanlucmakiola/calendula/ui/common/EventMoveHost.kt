package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** How long the confirmation chip stays up, matching a short snackbar. */
private const val CHIP_MILLIS = 4_000L

/** The FAB's own band at the bottom end, which the chip must not run into. */
private val FAB_BAND = 88.dp

private val CHIP_HEIGHT = 56.dp

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
    // while it slides back out.
    var shownMessage by remember { mutableStateOf("") }
    var shownUndo by remember { mutableStateOf<MoveUndo?>(null) }
    LaunchedEffect(outcome) {
        val text = message ?: return@LaunchedEffect
        shownMessage = text
        shownUndo = moved?.undo
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
                .padding(start = 16.dp, bottom = 16.dp)
                .height(CHIP_HEIGHT),
            contentAlignment = Alignment.CenterStart,
        ) {
            MoveChip(
                visible = outcome != null,
                message = shownMessage,
                maxWidth = chipMaxWidth,
                onUndo = shownUndo?.let { undo -> { viewModel.undo(undo) } },
            )
        }
    }
}

/**
 * The confirmation pill: sized to its content, sliding up from the bottom, with
 * Undo where the write has a clean inverse.
 */
@Composable
private fun MoveChip(
    visible: Boolean,
    message: String,
    maxWidth: Dp,
    onUndo: (() -> Unit)?,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(50),
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = maxWidth),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = if (onUndo != null) 8.dp else 20.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (onUndo != null) {
                    TextButton(
                        onClick = onUndo,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.event_move_undo),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
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
