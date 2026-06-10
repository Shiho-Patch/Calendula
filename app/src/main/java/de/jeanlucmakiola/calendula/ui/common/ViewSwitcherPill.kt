package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R

/**
 * Top-bar pill that shows the current view and cycles to the next one on tap
 * (spec M1: Month → Week → Day → Month, restricted to [IMPLEMENTED_VIEWS]).
 */
@Composable
fun ViewSwitcherPill(
    current: CalendarView,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (current) {
        CalendarView.Month -> R.string.view_month
        CalendarView.Week -> R.string.view_week
        CalendarView.Day -> R.string.view_day
    }
    FilledTonalButton(
        onClick = onCycle,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Text(stringResource(labelRes))
    }
}
