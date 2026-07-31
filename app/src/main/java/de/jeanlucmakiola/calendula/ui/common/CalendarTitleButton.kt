package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.LocalDate

/**
 * Top-bar title that doubles as the jump-to-date entry point: the formatted
 * [title] with a drop-down caret, opening the same [CalendarDatePickerDialog] the
 * drawer uses and reporting the chosen day through [onJumpToDate].
 *
 * [currentDate] seeds the picker with whatever the bar is currently naming (the
 * visible day, week start or month anchor).
 *
 * The row keeps its own 8.dp inset rather than shifting back onto the app bar's
 * start alignment: M3 places the title flush against the navigation icon's
 * trailing edge, and the title is hit-tested on top of it, so a negative offset
 * would swallow taps meant for the menu button.
 */
@Composable
fun CalendarTitleButton(
    title: String,
    currentDate: LocalDate,
    onJumpToDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(
                onClickLabel = stringResource(R.string.drawer_jump_to_date),
                role = Role.Button,
            ) { showDatePicker = true }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
