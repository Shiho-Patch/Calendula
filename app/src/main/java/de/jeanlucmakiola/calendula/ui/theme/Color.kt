package de.jeanlucmakiola.calendula.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Seed color anchoring the entire palette. See spec section 12.
 * Desaturated slate blue-gray; distinct from HouseHoldKeaper's sage.
 */
val CalendulaSeed: Color = Color(0xFF5C6B7A)

/**
 * Fallback light scheme used on devices that don't support dynamic color
 * (API < 31) or when the user disables it. The real palette is derived by
 * Material's tonal-palette generator from CalendulaSeed at runtime; these
 * constants are a hand-picked subset that approximates that result.
 */
val CalendulaLightFallback = lightColorScheme(
    primary = Color(0xFF3B5364),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBE6FA),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1F),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1F),
)

val CalendulaDarkFallback = darkColorScheme(
    primary = Color(0xFFA3CBE2),
    onPrimary = Color(0xFF003348),
    primaryContainer = Color(0xFF21495F),
    onPrimaryContainer = Color(0xFFCBE6FA),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    background = Color(0xFF101316),
    onBackground = Color(0xFFE1E3E6),
    surface = Color(0xFF101316),
    onSurface = Color(0xFFE1E3E6),
)
