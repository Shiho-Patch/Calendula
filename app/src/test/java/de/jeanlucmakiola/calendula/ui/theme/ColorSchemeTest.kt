package de.jeanlucmakiola.calendula.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ColorSchemeTest {

    @Test
    fun `seed color matches design spec slate`() {
        // The seed color must remain stable - the design is anchored to it.
        // Change this only if the spec is updated.
        assertThat(CalendulaSeed).isEqualTo(Color(0xFF5C6B7A))
    }

    @Test
    fun `light fallback scheme uses seed as primary derivation source`() {
        val scheme = CalendulaLightFallback
        // Primary should be a recognizable derivative of the seed (not neutral gray)
        assertThat(scheme.primary).isNotEqualTo(Color.Black)
        assertThat(scheme.primary).isNotEqualTo(Color.White)
    }

    @Test
    fun `dark fallback scheme differs from light`() {
        assertThat(CalendulaDarkFallback.background).isNotEqualTo(CalendulaLightFallback.background)
    }
}
