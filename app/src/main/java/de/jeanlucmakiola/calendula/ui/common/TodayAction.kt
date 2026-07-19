package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R

/**
 * The top-bar "jump to today" icon button, shared by every calendar view's app
 * bar (#60). Shown in place of the fade-in FAB pill when the user moves the
 * today control into the toolbar; renders nothing when [show] is false, so it
 * drops straight into an app bar's `actions` slot without a wrapping condition.
 */
@Composable
fun TodayAction(show: Boolean, onToday: () -> Unit) {
    if (!show) return
    IconButton(onClick = onToday) {
        Icon(
            imageVector = Icons.Default.Today,
            contentDescription = stringResource(R.string.today_jump_action),
        )
    }
}
