package de.jeanlucmakiola.calendula.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FontsTest {

    @Test
    fun `fromToken round-trips every bundled font`() {
        BundledFont.entries.forEach { font ->
            assertThat(BundledFont.fromToken(font.token)).isEqualTo(font)
        }
    }

    @Test
    fun `fromToken is null for system, custom, and unknown tokens`() {
        assertThat(BundledFont.fromToken(FONT_SYSTEM_TOKEN)).isNull()
        assertThat(BundledFont.fromToken(FONT_CUSTOM_TOKEN)).isNull()
        assertThat(BundledFont.fromToken("not-a-font")).isNull()
        assertThat(BundledFont.fromToken(null)).isNull()
    }

    @Test
    fun `a bumped custom-font stamp breaks font-settings equality`() {
        // Re-importing keeps the same "custom" token, so only the stamp can make
        // the settings re-emit and invalidate remember() keys downstream.
        val before = AppFontSettings(brand = FONT_CUSTOM_TOKEN, brandStamp = 1)
        assertThat(before.copy(brandStamp = 2)).isNotEqualTo(before)
        assertThat(before.copy(plainStamp = 1)).isNotEqualTo(before)
    }

    @Test
    fun `system for both roles returns the untouched default typography`() {
        assertThat(calendulaTypography(brand = null, plain = null))
            .isSameInstanceAs(CalendulaTypography)
    }

    @Test
    fun `brand drives display and headline, plain drives title, body, and label`() {
        val brand = BundledFont.Lora.family
        val plain = BundledFont.JetBrainsMono.family
        val typography = calendulaTypography(brand = brand, plain = plain)

        assertThat(typography.displayLarge.fontFamily).isEqualTo(brand)
        assertThat(typography.headlineSmall.fontFamily).isEqualTo(brand)
        assertThat(typography.titleLarge.fontFamily).isEqualTo(plain)
        assertThat(typography.bodyMedium.fontFamily).isEqualTo(plain)
        assertThat(typography.labelSmall.fontFamily).isEqualTo(plain)
    }

    @Test
    fun `a null role keeps that role's default family while the other is applied`() {
        val plain = BundledFont.Lora.family
        val typography = calendulaTypography(brand = null, plain = plain)

        // Brand styles keep the Material default; plain styles switch.
        assertThat(typography.displayLarge.fontFamily)
            .isEqualTo(CalendulaTypography.displayLarge.fontFamily)
        assertThat(typography.bodyLarge.fontFamily).isEqualTo(plain)
    }
}
