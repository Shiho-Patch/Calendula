package de.jeanlucmakiola.calendula.widget

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.material3.ColorProviders
import de.jeanlucmakiola.calendula.ui.theme.CalendulaDarkFallback
import de.jeanlucmakiola.calendula.ui.theme.CalendulaLightFallback

/**
 * Brand fallback for devices without Material You dynamic colour (API < 31).
 * Reuses the exact same hand-tuned schemes as the in-app theme
 * ([CalendulaLightFallback] / [CalendulaDarkFallback]) so a widget on an older
 * device matches the app surface-for-surface.
 */
private val CalendulaGlanceColors = ColorProviders(
    light = CalendulaLightFallback,
    dark = CalendulaDarkFallback,
)

/**
 * Glance equivalent of `CalendulaTheme`. On API 31+ it follows the system's
 * Material You palette (so the widget matches the home screen / the app's
 * dynamic colour); below that it falls back to the brand scheme. Either way the
 * widget draws only from M3 colour-role tokens (`GlanceTheme.colors.*`) — never
 * a hardcoded colour — so it tracks light/dark automatically.
 */
@Composable
fun CalendulaGlanceTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        CalendulaGlanceColors
    }
    GlanceTheme(colors = colors, content = content)
}
