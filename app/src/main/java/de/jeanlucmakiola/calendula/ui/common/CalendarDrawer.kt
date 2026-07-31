package de.jeanlucmakiola.calendula.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.filter.CalendarFilterList
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Navigation drawer shared by every top-level calendar screen.
 *
 * Uses the app's grouped-card design system (see [GroupedRow]): a branded
 * header, the View switcher as a grouped card (the active view highlighted),
 * a jump-to-date action, the per-calendar visibility filter (M3) inline, and a
 * pinned Settings row. The "View" section mirrors the top-bar switcher pill —
 * tapping a view here selects it (and closes the drawer) rather than cycling.
 * The host screen owns the drawer state; the sheet reads it only to dismiss
 * itself on back.
 *
 * [currentDate] seeds the jump-to-date picker (the visible day/week-start/month
 * anchor); [onJumpToDate] navigates the active view to the chosen day.
 */
@Composable
fun CalendarDrawer(
    currentView: CalendarView,
    currentDate: LocalDate,
    drawerState: DrawerState,
    onSelectView: (CalendarView) -> Unit,
    onJumpToDate: (LocalDate) -> Unit,
    onSettings: () -> Unit,
    viewOrder: List<CalendarView> = IMPLEMENTED_VIEWS,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Registered in the sheet so it takes precedence over the host's back handler.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalDrawerSheet {
        // The whole sidebar scrolls as one — header, views, the calendar filter
        // and Settings all flow in a single scroll container.
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            DrawerHeader()

            DrawerSectionHeader(stringResource(R.string.view_section))
            viewOrder.forEachIndexed { index, view ->
                GroupedRow(
                    title = stringResource(view.labelRes),
                    position = positionOf(index, viewOrder.size),
                    selected = view == currentView,
                    minHeight = 56.dp,
                    leading = { DrawerLeadingIcon(view.icon) },
                    onClick = { onSelectView(view) },
                )
            }

            Spacer(Modifier.height(16.dp))
            GroupedRow(
                title = stringResource(R.string.drawer_jump_to_date),
                position = Position.Alone,
                minHeight = 56.dp,
                leading = { DrawerLeadingIcon(Icons.Filled.DateRange) },
                onClick = { showDatePicker = true },
            )

            Spacer(Modifier.height(16.dp))

            DrawerSectionHeader(stringResource(R.string.filter_title))
            CalendarFilterList()

            Spacer(Modifier.height(16.dp))
            GroupedRow(
                title = stringResource(R.string.month_action_settings),
                position = Position.Alone,
                minHeight = 56.dp,
                leading = { DrawerLeadingIcon(Icons.Filled.Settings) },
                onClick = onSettings,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        CalendarDatePickerDialog(
            initial = currentDate,
            onConfirm = {
                showDatePicker = false
                onJumpToDate(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** Leading slot for the drawer's plain icons: the same 40.dp footprint
 *  [CalendarColorChip] takes, so every leading glyph shares one vertical axis. */
@Composable
private fun DrawerLeadingIcon(icon: ImageVector) {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null)
    }
}

/** Branded header: the app-icon chip beside the app name. */
@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = GroupedListInset,
                end = GroupedListInset,
                top = 24.dp,
                bottom = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(66.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
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
        modifier = Modifier.padding(
            start = GroupedListInset,
            end = GroupedListInset,
            top = 16.dp,
            bottom = 8.dp,
        ),
    )
}
