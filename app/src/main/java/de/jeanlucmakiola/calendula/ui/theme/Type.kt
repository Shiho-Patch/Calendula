package de.jeanlucmakiola.calendula.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

/**
 * Tracking the two label roles the calendar grids are set in. Material gives
 * both 0.5sp, tuned for isolated UI labels with room around them; a month chip
 * is a text box some 37dp wide, where 0.5sp on an 11sp glyph spends most of a
 * character on spacing alone. 0.1sp is what Material itself sets labelLarge to,
 * so the label family stays coherent (#190).
 */
private val LabelTracking = 0.1.sp

/**
 * Material 3 Expressive typography with the label roles' tracking tightened.
 * Everything else is the default scale.
 */
val CalendulaTypography: Typography = Typography().let { base ->
    base.copy(
        labelMedium = base.labelMedium.copy(letterSpacing = LabelTracking),
        labelSmall = base.labelSmall.copy(letterSpacing = LabelTracking),
    )
}
