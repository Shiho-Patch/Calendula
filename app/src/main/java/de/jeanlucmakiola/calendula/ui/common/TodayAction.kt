package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val GlyphShape = RoundedCornerShape(7.dp)
private val GlyphBorder = 1.5.dp

/**
 * Height of the day number. Expressed in dp and converted to sp at draw time so
 * it does not follow the font scale: this is an icon glyph sharing the bar with
 * stroke icons, and a two-digit day that grew with the text would push the bar
 * past the width #165 measured it to. The button's content description carries
 * the meaning for screen readers, so nothing is lost by holding it still.
 */
private val GlyphTextHeight = 12.dp

/**
 * The top-bar "jump to today" icon button, shared by every calendar view's app
 * bar (#60). Shown in place of the fade-in FAB pill when the user moves the
 * today control into the toolbar; renders nothing when [show] is false, so it
 * drops straight into an app bar's `actions` slot without a wrapping condition.
 *
 * The glyph is the current day number in an outlined box rather than a generic
 * calendar icon (#220), so the bar also says what day it is. Outlined rather
 * than the month grid's filled circle, which reads as "this cell is today" and
 * would sit heavier here than the stroke icons beside it.
 */
@Composable
fun TodayAction(show: Boolean, onToday: () -> Unit) {
    if (!show) return
    val now = rememberCurrentMinute()
    // Only the day number is read, so the bar recomposes at midnight rather
    // than on every minute tick.
    val day by remember {
        derivedStateOf { now.value.toLocalDateTime(TimeZone.currentSystemDefault()).date.day }
    }
    val description = stringResource(R.string.today_jump_action)
    IconButton(
        onClick = onToday,
        modifier = Modifier
            .morphChrome(ChromeSlot.Today)
            .semantics { contentDescription = description },
    ) {
        val textSize = with(LocalDensity.current) { GlyphTextHeight.toSp() }
        Box(
            modifier = Modifier
                .size(AppBarSpacing.IconSize)
                .border(GlyphBorder, LocalContentColor.current, GlyphShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                fontSize = textSize,
                lineHeight = textSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current,
            )
        }
    }
}
