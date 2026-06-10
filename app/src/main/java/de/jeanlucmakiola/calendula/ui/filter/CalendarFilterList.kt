package de.jeanlucmakiola.calendula.ui.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.ui.common.pastelize

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

@Composable
private fun FilterList(
    groups: List<AccountGroup>,
    onSetVisible: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.account}") {
                Text(
                    text = group.account,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(group.calendars, key = { it.id }) { cal ->
                CalendarToggleRow(
                    row = cal,
                    dark = dark,
                    onCheckedChange = { onSetVisible(cal.id, it) },
                )
            }
        }
    }
}

@Composable
private fun CalendarToggleRow(
    row: CalendarRow,
    dark: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(pastelize(row.color, dark), CircleShape),
        )
        Text(
            text = row.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = row.visible,
            onCheckedChange = onCheckedChange,
        )
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
