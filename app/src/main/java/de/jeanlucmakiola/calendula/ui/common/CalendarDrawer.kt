package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.filter.CalendarFilterList

/**
 * Navigation drawer shared by every top-level calendar screen.
 *
 * Visual language (kept deliberately small so sizes don't drift):
 *  - Drawer title — `titleLarge`
 *  - Section headers (e.g. "Calendars") — `titleSmall`, primary, text only
 *  - Nav items (Today / Settings) — Material `NavigationDrawerItem`
 *    (`labelLarge` label + a single 24dp leading icon)
 *
 * Hosts the per-calendar visibility filter (M3) inline — the calendar list with
 * its checkboxes lives here rather than in a separate sheet — plus the "today"
 * jump and a Settings entry (M4). The host screen owns the drawer state.
 */
@Composable
fun CalendarDrawer(
    onToday: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight()) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Today, contentDescription = null) },
                label = { Text(stringResource(R.string.month_today_action)) },
                selected = false,
                onClick = onToday,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            // Calendars (M3) — visibility checkboxes, scrollable, takes the slack
            // between the top actions and the pinned Settings entry.
            DrawerSectionHeader(stringResource(R.string.filter_title))
            CalendarFilterList(modifier = Modifier.weight(1f))

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.month_action_settings)) },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Top-level grouping label in the drawer. Text only, so it never reads as a
 *  tappable nav item. */
@Composable
private fun DrawerSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
    )
}
