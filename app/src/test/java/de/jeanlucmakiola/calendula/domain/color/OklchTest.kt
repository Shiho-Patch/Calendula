package de.jeanlucmakiola.calendula.domain.color

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OklchTest {

    @Test
    fun `matches Oklab's published anchors`() {
        // Guards the conversion matrices against a transcription slip: these are
        // Ottosson's own reference values for the space.
        assertThat(oklchOf(0xFFFFFFFF.toInt()).lightness).isWithin(0.001f).of(1f)
        assertThat(oklchOf(0xFF000000.toInt()).lightness).isWithin(0.001f).of(0f)
        assertThat(oklchOf(0xFFFF0000.toInt()).lightness).isWithin(0.001f).of(0.6280f)
    }

    @Test
    fun `white and black have no chroma`() {
        assertThat(oklchOf(0xFFFFFFFF.toInt()).chroma).isWithin(0.001f).of(0f)
        assertThat(oklchOf(0xFF000000.toInt()).chroma).isWithin(0.001f).of(0f)
        assertThat(oklchOf(0xFF808080.toInt()).chroma).isWithin(0.001f).of(0f)
    }

    @Test
    fun `converting to sRGB and back round-trips`() {
        for (argb in listOf(0xFFF4511E, 0xFF039BE5, 0xFF0B8043, 0xFF8E24AA, 0xFFFBD75B)) {
            val original = oklchOf(argb.toInt())
            val roundTripped = oklchOf(original.toArgb())
            assertThat(roundTripped.lightness).isWithin(0.01f).of(original.lightness)
            assertThat(roundTripped.chroma).isWithin(0.01f).of(original.chroma)
            assertThat(roundTripped.hue).isWithin(1f).of(original.hue)
        }
    }

    @Test
    fun `an out-of-gamut request keeps its lightness and gives up chroma`() {
        // Most of the Oklch cylinder is outside sRGB. Lightness is the axis the
        // contrast guarantees rest on, so it is the one that must survive.
        val requested = Oklch(0.45f, 0.35f, 150f)
        val actual = oklchOf(requested.toArgb())
        assertThat(actual.lightness).isWithin(0.02f).of(requested.lightness)
        assertThat(actual.chroma).isLessThan(requested.chroma)
    }

    @Test
    fun `pinning lightness holds luminance far tighter than pinning HSV value did`() {
        // The whole reason for the space swap. At a pinned HSV value the hues
        // spread across relative luminance 0.10..0.45; pinned Oklch lightness
        // must keep them in a narrow band, or one ink cannot serve them all.
        val luminances = (0 until 360 step 15).map { hue ->
            val argb = Oklch(0.45f, 0.16f, hue.toFloat()).toArgb()
            relativeLuminance(argb)
        }
        assertThat(luminances.max() / luminances.min()).isLessThan(2.5)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
