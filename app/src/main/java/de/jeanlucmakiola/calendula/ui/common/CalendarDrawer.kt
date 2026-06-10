package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/**
 * Navigation drawer shared by every top-level calendar screen (M2/M3/M4
 * entry points). Stateless — the host screen owns the drawer state and wires
 * the callbacks.
 */
@Composable
fun CalendarDrawer(
    onToday: () -> Unit,
    onJumpToDate: () -> Unit,
    onFilter: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.month_today_action)) },
            selected = false,
            onClick = onToday,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.month_action_jump_to_date)) },
            selected = false,
            onClick = onJumpToDate,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.month_action_filter)) },
            selected = false,
            onClick = onFilter,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.month_action_settings)) },
            selected = false,
            onClick = onSettings,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
