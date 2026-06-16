package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Soften a raw calendar color toward a pastel that fits the active theme.
 *  - Keeps the hue (so users still recognise their calendars)
 *  - Caps saturation so harsh provider colors stop screaming
 *  - Pins value/brightness to a band that reads on both light and dark surfaces
 */
fun pastelize(rawArgb: Int, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(rawArgb, hsv)
    hsv[1] = (hsv[1] * 0.6f).coerceIn(0.25f, 0.65f)
    hsv[2] = if (dark) 0.82f else 0.72f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Leading avatar for a calendar: a neutral chip holding a calendar glyph tinted
 * in the calendar's (pastelised) colour. Shared by the calendar manager and the
 * visibility filter so they read identically.
 */
@Composable
fun CalendarColorChip(color: Int, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = pastelize(color, dark),
            modifier = Modifier.size(22.dp),
        )
    }
}
