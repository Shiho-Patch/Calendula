package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalendarTitleButtonTest {

    @Test
    fun `keeps the full title when it fits`() {
        assertThat(titleFor("September", "Sep", titleWidth = 200, availableWidth = 300))
            .isEqualTo("September")
    }

    @Test
    fun `keeps the full title when it fills the slot exactly`() {
        assertThat(titleFor("September", "Sep", titleWidth = 300, availableWidth = 300))
            .isEqualTo("September")
    }

    @Test
    fun `falls back to the short title when the full one overflows`() {
        assertThat(titleFor("September", "Sep", titleWidth = 400, availableWidth = 300))
            .isEqualTo("Sep")
    }

    @Test
    fun `a caller with nothing to shorten still gets its own title back`() {
        assertThat(titleFor("Wed, 2 Sep", "Wed, 2 Sep", titleWidth = 400, availableWidth = 300))
            .isEqualTo("Wed, 2 Sep")
    }
}
