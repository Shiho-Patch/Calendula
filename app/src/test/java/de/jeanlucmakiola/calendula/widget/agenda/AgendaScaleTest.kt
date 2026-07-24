package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.widget.WidgetScale
import de.jeanlucmakiola.calendula.widget.scaleFor
import org.junit.jupiter.api.Test

class AgendaScaleTest {

    // --- the "default size is unchanged" regression guard ---------------------

    @Test
    fun `every default placement width stays COMPACT`() {
        // Ties the guarantee to the provider XML's declared 3-cell default rather
        // than to one measured launcher: the whole band a default placement can
        // land in must bucket to COMPACT, or a freshly placed widget silently
        // changes appearance (#51). See AGENDA_DEFAULT_WIDTH_BAND.
        val band = AGENDA_DEFAULT_WIDTH_BAND
        var w = band.start
        while (w <= band.endInclusive) {
            assertThat(scaleFor(DpSize(w, 270.dp))).isEqualTo(WidgetScale.COMPACT)
            w += 1.dp
        }
    }

    @Test
    fun `COMPACT metrics equal the widget's original constants`() {
        // If this fails, a default-sized agenda widget no longer looks as it did.
        val m = metricsFor(WidgetScale.COMPACT)
        assertThat(m.title).isEqualTo(16.sp)
        assertThat(m.dayHeader).isEqualTo(13.sp)
        assertThat(m.eventTitle).isEqualTo(14.sp)
        assertThat(m.eventTime).isEqualTo(12.sp)
        assertThat(m.placeholder).isEqualTo(14.sp)
        assertThat(m.message).isEqualTo(14.sp)
        assertThat(m.stripeH).isEqualTo(36.dp)
        assertThat(m.iconImage).isEqualTo(22.dp)
        assertThat(m.iconBox).isEqualTo(40.dp)
        assertThat(m.rowVPad).isEqualTo(4.dp)
        assertThat(m.dayHeaderTopPad).isEqualTo(10.dp)
    }

    @Test
    fun `the derived text indent matches the original hardcoded inset`() {
        // TEXT_INDENT replaced a hardcoded 19dp; it must still resolve to 19dp or
        // day headers and the placeholder line stop aligning with event titles.
        assertThat(TEXT_INDENT).isEqualTo(19.dp)
        assertThat(TEXT_INDENT).isEqualTo(ROW_H_PAD + STRIPE_WIDTH + STRIPE_GAP)
    }

    // --- the ramp ------------------------------------------------------------

    @Test
    fun `sizes are non-decreasing across the tiers`() {
        val tiers = WidgetScale.entries.map(::metricsFor)

        tiers.zipWithNext { small, big ->
            assertThat(big.title.value).isAtLeast(small.title.value)
            assertThat(big.dayHeader.value).isAtLeast(small.dayHeader.value)
            assertThat(big.eventTitle.value).isAtLeast(small.eventTitle.value)
            assertThat(big.eventTime.value).isAtLeast(small.eventTime.value)
            assertThat(big.placeholder.value).isAtLeast(small.placeholder.value)
            assertThat(big.message.value).isAtLeast(small.message.value)
            assertThat(big.stripeH.value).isAtLeast(small.stripeH.value)
            assertThat(big.iconImage.value).isAtLeast(small.iconImage.value)
            assertThat(big.iconBox.value).isAtLeast(small.iconBox.value)
            assertThat(big.rowVPad.value).isAtLeast(small.rowVPad.value)
            assertThat(big.dayHeaderTopPad.value).isAtLeast(small.dayHeaderTopPad.value)
        }
    }

    @Test
    fun `the event title keeps its lead over the time line`() {
        // The secondary line steps more slowly on purpose; if it ever caught up the
        // row would lose its hierarchy.
        WidgetScale.entries.map(::metricsFor).forEach { m ->
            assertThat(m.eventTitle.value).isGreaterThan(m.eventTime.value)
        }
    }

    @Test
    fun `no tier grows type more than half again over the baseline`() {
        // Guards against a future edit turning "more readable" into "absurd".
        val base = metricsFor(WidgetScale.COMPACT)
        val top = metricsFor(WidgetScale.XLARGE)
        assertThat(top.title.value / base.title.value).isLessThan(1.5f)
        assertThat(top.eventTitle.value / base.eventTitle.value).isLessThan(1.5f)
    }

    // --- font scale ----------------------------------------------------------

    @Test
    fun `the stripe tracks the system font scale`() {
        // The stripe is Dp, the text beside it is sp: without this the two diverge
        // at large accessibility font settings and the stripe under-runs the row.
        val m = metricsFor(WidgetScale.COMPACT)
        assertThat(m.scaledForFont(1f).stripeH).isEqualTo(36.dp)
        assertThat(m.scaledForFont(1.3f).stripeH.value).isWithin(0.01f).of(46.8f)
        assertThat(m.scaledForFont(0.85f).stripeH.value).isWithin(0.01f).of(30.6f)
    }

    @Test
    fun `scaling for the default font scale changes nothing`() {
        val m = metricsFor(WidgetScale.LARGE)
        assertThat(m.scaledForFont(1f)).isSameInstanceAs(m)
    }

    @Test
    fun `font scaling leaves the sp sizes alone`() {
        // Glance already applies the font scale to sp; scaling them here too would
        // double-count it.
        val m = metricsFor(WidgetScale.REGULAR)
        val scaled = m.scaledForFont(1.3f)
        assertThat(scaled.title).isEqualTo(m.title)
        assertThat(scaled.eventTitle).isEqualTo(m.eventTitle)
        assertThat(scaled.rowVPad).isEqualTo(m.rowVPad)
    }
}
