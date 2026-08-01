package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * The two surfaces a drag-and-drop reschedule needs on top of the calendar: the
 * recurring-scope prompt, and the confirmation snackbar carrying Undo.
 *
 * None of the four calendar screens sets a `snackbarHost` on its Scaffold, so
 * this hosts its own — bottom-centre inside the host's root Box, above every
 * view. It forgoes the Scaffold's FAB avoidance in exchange for not threading a
 * `SnackbarHostState` through all four screens.
 */
@Composable
fun EventMoveHost(viewModel: RescheduleViewModel, modifier: Modifier = Modifier) {
    val prompt by viewModel.scopePrompt.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
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
    val undoLabel = stringResource(R.string.event_move_undo)

    LaunchedEffect(outcome) {
        val text = message ?: return@LaunchedEffect
        val undo = moved?.undo
        val result = snackbars.showSnackbar(
            message = text,
            actionLabel = undoLabel.takeIf { undo != null },
            withDismissAction = undo == null,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && undo != null) {
            viewModel.undo(undo)
        } else {
            viewModel.consumeOutcome()
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(
            hostState = snackbars,
            modifier = Modifier.navigationBarsPadding().padding(16.dp),
        )
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
