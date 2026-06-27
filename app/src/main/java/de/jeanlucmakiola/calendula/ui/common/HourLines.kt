package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Whether the week/day timeline draws an hour separator line, from the
 * `showHourLines` preference. Provided once at the app root (like
 * [LocalUse24HourFormat]) so the timeline reads it without ViewModel plumbing.
 * Defaults to off — the historical clean look.
 */
val LocalShowHourLines = staticCompositionLocalOf { false }

/**
 * Draw a faint separator line at the top of each hour (1..23) when [show] is
 * true. Applied to a day column's content so each line sits over the column's
 * background but beneath the event blocks. [hourHeightPx] is one hour's pixel
 * height; [color] is resolved by the caller from the theme.
 */
fun Modifier.hourSeparatorLines(show: Boolean, hourHeightPx: Float, color: Color): Modifier =
    if (!show) {
        this
    } else {
        drawBehind {
            for (hour in 1 until 24) {
                val y = hour * hourHeightPx
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }
    }
