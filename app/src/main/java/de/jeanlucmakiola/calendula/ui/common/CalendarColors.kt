package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.graphics.Color

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
