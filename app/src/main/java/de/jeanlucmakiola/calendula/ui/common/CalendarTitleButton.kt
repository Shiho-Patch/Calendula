package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.LocalDate

/**
 * Top-bar title that doubles as the jump-to-date entry point: the formatted
 * [title] with a drop-down caret, opening the same [CalendarDatePickerDialog] the
 * drawer uses and reporting the chosen day through [onJumpToDate].
 *
 * [currentDate] seeds the picker with whatever the bar is currently naming (the
 * visible day, week start or month anchor); null makes the title a plain label,
 * as the agenda's is, and the caret folds away.
 *
 * [shortTitle] replaces [title] when the full one does not fit the width the app
 * bar hands the title slot (#165). Either way the line clamps to one and
 * ellipsises, so pass a [shortTitle] wherever there is something left to drop.
 */
@Composable
fun CalendarTitleButton(
    title: String,
    currentDate: LocalDate?,
    onJumpToDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    shortTitle: String = title,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(
                enabled = currentDate != null,
                onClickLabel = stringResource(R.string.drawer_jump_to_date),
                role = Role.Button,
            ) { showDatePicker = true }
            .padding(horizontal = AppBarSpacing.TitleInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.titleLarge
        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
            val measurer = rememberTextMeasurer()
            val shown = remember(title, shortTitle, style, constraints.maxWidth, measurer) {
                titleFor(
                    title = title,
                    shortTitle = shortTitle,
                    titleWidth = measurer.measure(title, style).size.width,
                    availableWidth = constraints.maxWidth,
                )
            }
            // Cross-fades, so switching views or paging turns the title over
            // rather than snapping it (#184).
            val fadeSpec = rememberCalendarFadeSpec()
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    (fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec)).using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.CenterStart,
            ) { text ->
                Text(
                    text = text,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = currentDate != null,
            enter = fadeIn(rememberCalendarFadeSpec()) + expandHorizontally(),
            exit = fadeOut(rememberCalendarFadeSpec()) + shrinkHorizontally(),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker && currentDate != null) {
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

/** Falls back to [shortTitle] when [title] is wider than [availableWidth] (#165). */
internal fun titleFor(
    title: String,
    shortTitle: String,
    titleWidth: Int,
    availableWidth: Int,
): String = if (titleWidth <= availableWidth) title else shortTitle
