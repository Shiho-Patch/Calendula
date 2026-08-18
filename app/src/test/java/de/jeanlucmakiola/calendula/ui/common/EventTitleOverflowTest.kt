package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.text.style.TextOverflow
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EventTitleOverflowTest {

    @Test
    fun `a single LTR line clips, so the title runs to the chip's edge`() {
        val result = eventTitleOverflowFor(rtl = false, singleLine = true)
        assertThat(result.overflow).isEqualTo(TextOverflow.Clip)
        // Load-bearing: with softWrap on, a clipped line breaks at the last
        // whole word and shows less title than the ellipsis did (#164).
        assertThat(result.softWrap).isFalse()
    }

    @Test
    fun `RTL keeps the ellipsis, which truncates at the logical end`() {
        // Clipping with softWrap off cuts at the node's left edge, which in RTL
        // is the end of the string — the title would lose its beginning.
        val result = eventTitleOverflowFor(rtl = true, singleLine = true)
        assertThat(result.overflow).isEqualTo(TextOverflow.Ellipsis)
        assertThat(result.softWrap).isTrue()
    }

    @Test
    fun `a multi-line block keeps the ellipsis, since wrapping needs softWrap`() {
        val result = eventTitleOverflowFor(rtl = false, singleLine = false)
        assertThat(result.overflow).isEqualTo(TextOverflow.Ellipsis)
        assertThat(result.softWrap).isTrue()
    }

    @Test
    fun `multi-line in RTL keeps the ellipsis too`() {
        val result = eventTitleOverflowFor(rtl = true, singleLine = false)
        assertThat(result.overflow).isEqualTo(TextOverflow.Ellipsis)
        assertThat(result.softWrap).isTrue()
    }
}
