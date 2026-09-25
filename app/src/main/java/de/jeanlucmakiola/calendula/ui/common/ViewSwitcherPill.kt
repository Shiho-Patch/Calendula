package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp

/**
 * Top-bar pill that shows the current view and cycles to the next one on tap
 * (spec M1: Month → Week → Day → Month, restricted to [IMPLEMENTED_VIEWS]).
 *
 * Renders nothing when [cycle] holds fewer than [QuickSwitchConfig.MIN_CYCLE]
 * views (#150), so it drops straight into an app bar's `actions` slot without a
 * wrapping condition — the same way [TodayAction] handles being turned off.
 *
 * [trailingInset] lands the pill's background on the bar's own rhythm; pass
 * `0.dp` outside a top app bar, where M3's actions padding is not there to
 * cancel (#165).
 */
@Composable
fun ViewSwitcherPill(
    current: CalendarView,
    cycle: List<CalendarView>,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
    trailingInset: Dp = AppBarSpacing.ContainerTrailingInset,
) {
    if (cycle.size < QuickSwitchConfig.MIN_CYCLE) return
    FilledTonalButton(
        onClick = onCycle,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.padding(end = trailingInset),
    ) {
        Text(current.label())
    }
}
