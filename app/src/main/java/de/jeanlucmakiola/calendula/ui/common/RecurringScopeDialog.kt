package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.floret.components.OptionCard

/**
 * How far a write to a recurring event should reach: this occurrence, it and
 * everything after (a series split), or the whole series. Shared by the edit
 * screen's save and a drag-and-drop reschedule; one of the two carve-outs from
 * the full-screen picker rule.
 *
 * [allowOccurrence] drops "only this event" (an exception row can't carry its own
 * rule); [allowSeries] drops the two wider options, for a rule whose days can't
 * be recalculated from one moved occurrence — [reason] then says why.
 */
@Composable
fun RecurringScopeDialog(
    title: String,
    onSelect: (RecurringWriteScope) -> Unit,
    onDismiss: () -> Unit,
    allowOccurrence: Boolean = true,
    allowSeries: Boolean = true,
    reason: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (reason != null) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allowOccurrence) {
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_occurrence),
                        onClick = { onSelect(RecurringWriteScope.ThisEvent) },
                    )
                }
                if (allowSeries) {
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_following),
                        onClick = { onSelect(RecurringWriteScope.ThisAndFollowing) },
                    )
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_series),
                        onClick = { onSelect(RecurringWriteScope.AllEvents) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}
