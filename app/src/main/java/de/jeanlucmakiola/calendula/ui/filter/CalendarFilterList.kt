package de.jeanlucmakiola.calendula.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.sourceAppName
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.positionOf

/**
 * Calendar-visibility filter (M3), rendered inline in the navigation drawer.
 * Every calendar grouped by account, each with a colour swatch and a visibility
 * switch; toggling writes straight to DataStore and every calendar view
 * re-filters live. Three states (Loading / Failure / Success).
 */
@Composable
fun CalendarFilterList(
    modifier: Modifier = Modifier,
    viewModel: FilterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        FilterUiState.Loading -> FilterLoading(modifier)
        is FilterUiState.Failure -> FilterMessage(s.reason, modifier)
        is FilterUiState.Success -> FilterList(
            groups = s.groups,
            onSetVisible = viewModel::setVisible,
            modifier = modifier,
        )
    }
}

/**
 * Renders as a plain (non-scrolling) [Column] so it flows inside the drawer's
 * single scroll container — the whole sidebar scrolls as one. Calendar counts
 * are small, so a lazy list isn't needed.
 */
@Composable
private fun FilterList(
    groups: List<AccountGroup>,
    onSetVisible: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        groups.forEach { group ->
            Text(
                text = if (group.ambiguous) {
                    stringResource(
                        R.string.calendars_account_from_source,
                        group.account,
                        sourceAppName(group.accountType),
                    )
                } else {
                    group.account
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 4.dp),
            )
            group.calendars.forEachIndexed { index, cal ->
                GroupedRow(
                    title = cal.displayName,
                    position = positionOf(index, group.calendars.size),
                    minHeight = 56.dp,
                    leading = { CalendarColorChip(cal.color) },
                    trailing = {
                        Checkbox(
                            checked = cal.visible,
                            onCheckedChange = { onSetVisible(cal.id, it) },
                        )
                    },
                    onClick = { onSetVisible(cal.id, !cal.visible) },
                )
            }
        }
    }
}

@Composable
private fun FilterLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    ),
            )
        }
    }
}

@Composable
private fun FilterMessage(reason: FailureReason, modifier: Modifier = Modifier) {
    val msg = when (reason) {
        FailureReason.NoCalendarsConfigured -> R.string.state_failure_no_calendars
        FailureReason.PermissionRevoked -> R.string.state_failure_permission
        else -> R.string.state_failure_provider
    }
    Text(
        text = stringResource(msg),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    )
}
