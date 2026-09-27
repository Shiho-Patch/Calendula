package de.jeanlucmakiola.calendula.domain.color

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EventToneTest {

    /** Hues at full chroma, standing in for the provider colours in the wild. */
    private val hues = (0 until 360 step 15).map { Oklch(0.65f, 0.2f, it.toFloat()).toArgb() }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun contrast(a: Int, b: Int): Double {
        val hi = maxOf(relativeLuminance(a), relativeLuminance(b))
        val lo = minOf(relativeLuminance(a), relativeLuminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    /** The other ink, for asserting the chosen one is the better of the two. */
    private fun flip(ink: Int) =
        if (ink == 0xFFFFFFFF.toInt()) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    /** [ink] over [fill] at [alpha], the way a title actually renders. */
    private fun composite(ink: Int, fill: Int, alpha: Double): Int {
        fun mix(shift: Int): Int {
            val i = (ink shr shift) and 0xFF
            val f = (fill shr shift) and 0xFF
            return (alpha * i + (1 - alpha) * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    @Test
    fun `hues of one lightness all get the same ink`() {
        // The bug this replaced: at a pinned HSV value the hues straddled the ink
        // crossover, so red took white while orange beside it took black. Ink may
        // still differ by *pole*, but never by hue within a pole.
        for (dark in listOf(false, true)) {
            val inks = hues.map { eventTone(it, dark, harmonise = true).onContainer }.toSet()
            assertThat(inks).containsExactly(0xFFFFFFFF.toInt())
        }
    }

    @Test
    fun `naturally light colours keep their character instead of turning muddy`() {
        // Forcing every hue deep made a yellow into olive and a cream into brown.
        // Pale sources stay pale — and then take dark ink.
        for (pale in listOf(0xFFF6BF26, 0xFFFFF8DC, 0xFFF0E68C, 0xFFFFFFFF)) {
            val tone = eventTone(pale.toInt(), dark = false, harmonise = true)
            assertThat(oklchOf(tone.container).lightness).isGreaterThan(0.8f)
            assertThat(tone.onContainer).isEqualTo(0xFF000000.toInt())
        }
    }

    @Test
    fun `warm mid colours still go deep, so orange reads as orange and not as cream`() {
        // The threshold has to sit above orange: a burnt orange still says
        // "orange", which is why it belongs on the deep pole with white ink.
        // Note the ceiling this implies: a *bright* orange (#FF8C00, #FFA500) is
        // genuinely a light colour and takes the light pole instead — forcing it
        // deep is exactly what produced brown.
        for (warm in listOf(0xFFF4511E, 0xFFE67C73, 0xFFD50000, 0xFFE65100)) {
            val tone = eventTone(warm.toInt(), dark = false, harmonise = true)
            assertThat(oklchOf(tone.container).lightness).isLessThan(0.6f)
            assertThat(tone.onContainer).isEqualTo(0xFFFFFFFF.toInt())
        }
    }

    @Test
    fun `title ink clears WCAG AA on every hue`() {
        for (dark in listOf(false, true)) {
            for (hue in hues) {
                val tone = eventTone(hue, dark, harmonise = true)
                val ink = composite(tone.onContainer, tone.container, 0.85)
                assertThat(contrast(tone.container, ink)).isGreaterThan(4.5)
                assertThat(contrast(tone.container, ink))
                    .isGreaterThan(contrast(tone.container, composite(flip(tone.onContainer), tone.container, 0.85)))
            }
        }
    }

    @Test
    fun `the secondary line also clears WCAG AA on every hue`() {
        for (dark in listOf(false, true)) {
            for (hue in hues) {
                val tone = eventTone(hue, dark, harmonise = true)
                val ink = composite(tone.onContainer, tone.container, 0.8)
                assertThat(contrast(tone.container, ink)).isGreaterThan(4.5)
            }
        }
    }

    @Test
    fun `an accent stays visible against its own theme's surface`() {
        // Accents carry no text, so the bar is WCAG's 3:1 for non-text contrast.
        val lightSurface = 0xFFFEF7FF.toInt()
        val darkSurface = 0xFF141218.toInt()
        for ((dark, surface) in listOf(false to lightSurface, true to darkSurface)) {
            for (hue in hues) {
                val accent = eventTone(hue, dark, harmonise = true).accent
                assertThat(contrast(accent, surface)).isGreaterThan(3.0)
            }
        }
    }

    @Test
    fun `container and accent keep the same hue, so one calendar reads as one colour`() {
        for (hue in hues) {
            val tone = eventTone(hue, dark = false, harmonise = true)
            assertThat(oklchOf(tone.accent).hue).isWithin(2f).of(oklchOf(tone.container).hue)
        }
    }

    @Test
    fun `orange no longer gets dark ink`() {
        // The reported case: a plain orange calendar used to fall just above the
        // crossover and take black text while its neighbours took white.
        val tone = eventTone(0xFFF4511E.toInt(), dark = false, harmonise = true)
        assertThat(tone.onContainer).isEqualTo(0xFFFFFFFF.toInt())
    }

    @Test
    fun `different hues stay distinguishable`() {
        val containers = hues.map { eventTone(it, dark = false, harmonise = true).container }
        assertThat(containers.toSet()).hasSize(hues.size)
    }

    @Test
    fun `a grey source stays grey rather than gaining an invented hue`() {
        for (grey in listOf(0xFF000000, 0xFF808080, 0xFFFFFFFF, 0xFF9E9E9E)) {
            val tone = eventTone(grey.toInt(), dark = false, harmonise = true)
            assertThat(oklchOf(tone.container).chroma).isLessThan(0.01f)
        }
    }

    @Test
    fun `raw colours are painted verbatim when harmonising is off`() {
        val raw = 0xFFF4511E.toInt()
        val tone = eventTone(raw, dark = false, harmonise = false)
        assertThat(tone.container).isEqualTo(raw)
        assertThat(tone.accent).isEqualTo(raw)
    }

    @Test
    fun `raw mode still picks a readable ink per colour`() {
        // Nothing constrains what a provider sends, so the ink cannot be constant
        // on this path the way it is for harmonised containers.
        assertThat(eventTone(0xFF101010.toInt(), dark = false, harmonise = false).onContainer)
            .isEqualTo(0xFFFFFFFF.toInt())
        assertThat(eventTone(0xFFFFF6C0.toInt(), dark = false, harmonise = false).onContainer)
            .isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `the edge bar steps clear of its block's container on every hue`() {
        for (dark in listOf(false, true)) {
            for (harmonise in listOf(false, true)) {
                (hues + 0xFFFFF6C0.toInt() + 0xFF000000.toInt() + 0xFFFFFFFF.toInt()).forEach { raw ->
                    val container = oklchOf(eventTone(raw, dark, harmonise).container)
                    val bar = oklchOf(edgeHintTone(raw, dark, harmonise))
                    assertThat(kotlin.math.abs(bar.lightness - container.lightness))
                        .isAtLeast(EDGE_HINT_STEP - 0.02f)
                }
            }
        }
    }

    @Test
    fun `the edge bar goes darker on a light scheme and lighter on a dark one`() {
        hues.forEach { raw ->
            val light = eventTone(raw, dark = false, harmonise = true).container
            val dark = eventTone(raw, dark = true, harmonise = true).container
            assertThat(oklchOf(edgeHintTone(raw, dark = false, harmonise = true)).lightness)
                .isLessThan(oklchOf(light).lightness)
            assertThat(oklchOf(edgeHintTone(raw, dark = true, harmonise = true)).lightness)
                .isGreaterThan(oklchOf(dark).lightness)
        }
    }

    @Test
    fun `a pale block on a dark scheme gets a deeper bar rather than a white one`() {
        val pale = 0xFFFFF6C0.toInt()
        val container = oklchOf(eventTone(pale, dark = true, harmonise = true).container)
        assertThat(oklchOf(edgeHintTone(pale, dark = true, harmonise = true)).lightness)
            .isLessThan(container.lightness)
    }
}
