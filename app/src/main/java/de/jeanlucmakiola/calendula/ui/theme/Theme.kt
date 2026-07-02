package de.jeanlucmakiola.calendula.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme. Honors:
 *   - System light/dark.
 *   - Dynamic Color on API 31+, else falls back to the hand-tuned scheme
 *     derived from [CalendulaSeed].
 *   - A user-chosen [typography] (custom fonts, issue #19), defaulting to the
 *     Material scale on the system typeface.
 *
 * The Settings screen overrides darkTheme, dynamicColor, and typography; the
 * bare defaults (used by the crash screen) just follow the system.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CalendulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    typography: Typography = CalendulaTypography,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> CalendulaDarkFallback
        else -> CalendulaLightFallback
    }

    // MaterialExpressiveTheme routes all component + custom motion through
    // MaterialTheme.motionScheme (switches, chips, pickers, calendar slide,
    // FAB, field reveal). The STANDARD scheme is a deliberate choice over
    // expressive(): same spring choreography, but without the overshoot —
    // the bouncy variant felt overdone in review (2026-06-11).
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        motionScheme = MotionScheme.standard(),
        content = content,
    )
}
