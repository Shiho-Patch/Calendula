package de.jeanlucmakiola.calendula.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import de.jeanlucmakiola.floret.identity.FloretExpressiveTheme

/**
 * Calendula's theme: the family's [FloretExpressiveTheme] machinery — dynamic
 * colour on API 31+, system light/dark, and the **standard** motion scheme
 * (spring choreography without the overshoot) — fed with Calendula's own
 * identity: its seed-derived fallback schemes ([CalendulaLightFallback] /
 * [CalendulaDarkFallback]) and a [typography] that defaults to
 * [CalendulaTypography] but is overridable for the user's custom-font choice
 * (issue #19).
 *
 * The mechanics live in floret-kit's identity module; the look stays here, so a
 * sibling app reuses the same theming without inheriting Calendula's palette.
 * The Settings screen overrides darkTheme, dynamicColor and typography; the bare
 * defaults (used by the crash screen) just follow the system.
 */
@Composable
fun CalendulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    typography: Typography = CalendulaTypography,
    content: @Composable () -> Unit,
) {
    FloretExpressiveTheme(
        lightScheme = CalendulaLightFallback,
        darkScheme = CalendulaDarkFallback,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        typography = typography,
        content = content,
    )
}
