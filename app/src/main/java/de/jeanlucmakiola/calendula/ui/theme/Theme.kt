package de.jeanlucmakiola.calendula.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme. Honors:
 *   - System light/dark.
 *   - Dynamic Color on API 31+, else falls back to the hand-tuned scheme
 *     derived from [CalendulaSeed].
 *
 * The Settings screen (later) can override useDynamicColor and themePreference,
 * but the V1 foundation just follows the system.
 */
@Composable
fun CalendulaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CalendulaTypography,
        content = content,
    )
}
