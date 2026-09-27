package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/**
 * The FAB stack shared by the three calendar views: a persistent "+" to
 * create an event, with the jump-to-today pill appearing above it whenever
 * the view isn't anchored on today.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendarFabColumn(
    todayVisible: Boolean,
    todayText: String,
    onToday: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(
            visible = todayVisible,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()),
        ) {
            ExtendedFloatingActionButton(
                onClick = onToday,
                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                text = { Text(todayText) },
            )
        }
        FloatingActionButton(onClick = onCreate) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.event_edit_new_title),
            )
        }
    }
}
