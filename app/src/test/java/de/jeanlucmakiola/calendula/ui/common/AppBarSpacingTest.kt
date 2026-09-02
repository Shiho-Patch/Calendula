package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AppBarSpacingTest {

    @Test
    fun `the pill's background ends one inset from the screen edge`() {
        // The padding is applied inside M3's actions row, so it carries that
        // row's own inset on top of whatever the pill asks for (#165).
        val rendered = AppBarSpacing.BarPadding + AppBarSpacing.ContainerTrailingInset
        assertThat(rendered).isEqualTo(AppBarSpacing.Inset)
    }

    @Test
    fun `trailing insets stay non-negative`() {
        // Modifier.padding throws on a negative value.
        assertThat(AppBarSpacing.ContainerTrailingInset.value).isAtLeast(0f)
        assertThat(AppBarSpacing.IconTrailingInset.value).isAtLeast(0f)
    }

    @Test
    fun `an icon button's glyph ends further in than a container's edge`() {
        assertThat(AppBarSpacing.IconTrailingInset.value)
            .isGreaterThan(AppBarSpacing.Inset.value)
    }
}
