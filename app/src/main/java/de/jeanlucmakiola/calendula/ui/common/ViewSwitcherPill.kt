package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Top-bar pill that shows the current view and cycles to the next one on tap
 * (spec M1: Month → Week → Day → Month, restricted to [IMPLEMENTED_VIEWS]).
 *
 * Renders nothing when [cycle] holds fewer than [QuickSwitchConfig.MIN_CYCLE]
 * views (#150), so it drops straight into an app bar's `actions` slot without a
 * wrapping condition — the same way [TodayAction] handles being turned off.
 */
@Composable
fun ViewSwitcherPill(
    current: CalendarView,
    cycle: List<CalendarView>,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cycle.size < QuickSwitchConfig.MIN_CYCLE) return
    FilledTonalButton(
        onClick = onCycle,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Text(stringResource(current.labelRes))
    }
}
