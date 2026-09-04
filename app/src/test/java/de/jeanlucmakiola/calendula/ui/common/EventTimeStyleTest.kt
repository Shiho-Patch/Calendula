package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** How an event's time is set against its title (#219). */
class EventTimeStyleTest {

    @Test
    fun `a time steps down to regular and closes its tracking up`() {
        val label = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        val time = label.asEventTime()
        assertThat(time.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(time.letterSpacing).isEqualTo(0.sp)
    }

    @Test
    fun `nothing else of the style is touched`() {
        val label = TextStyle(
            fontSize = 11.sp,
            color = Color.Red,
            textDecoration = TextDecoration.LineThrough,
        )
        val time = label.asEventTime()
        assertThat(time.fontSize).isEqualTo(11.sp)
        assertThat(time.color).isEqualTo(Color.Red)
        assertThat(time.textDecoration).isEqualTo(TextDecoration.LineThrough)
    }

    @Test
    fun `the title is inked above the time beside it`() {
        assertThat(TITLE_INK_ALPHA).isGreaterThan(SECONDARY_INK_ALPHA)
    }

    @Test
    fun `the time is set apart from the title it precedes`() {
        val label = inlineTimeLabel("09:05", "Standup", Color.Black)
        assertThat(label.text).isEqualTo("09:05 Standup")
        // Only the time is restyled: the title keeps the surface's own label
        // style, so the two read as separate things on one line (#219).
        val spans = label.spanStyles
        assertThat(spans).hasSize(1)
        assertThat(spans.single().start).isEqualTo(0)
        assertThat(spans.single().end).isEqualTo("09:05".length)
        assertThat(spans.single().item.fontWeight).isEqualTo(FontWeight.Normal)
        assertThat(spans.single().item.letterSpacing).isEqualTo(0.sp)
        assertThat(spans.single().item.color).isEqualTo(Color.Black)
    }

    @Test
    fun `a chip without a time is styled title and nothing else`() {
        val label = inlineTimeLabel(null, "Standup", Color.Black)
        assertThat(label.text).isEqualTo("Standup")
        assertThat(label.spanStyles).isEmpty()
    }
}
