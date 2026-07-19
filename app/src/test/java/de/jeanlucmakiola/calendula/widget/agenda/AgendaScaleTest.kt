package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AgendaScaleTest {

    // --- scaleFor: bucketing -------------------------------------------------

    @Test
    fun `the on-device calibration points map to their tiers`() {
        // The two sizes measured on-device: the compact widget stays COMPACT (the
        // baseline, unchanged), the large one steps up to LARGE — not XLARGE,
        // which read as too big on a phone (#51).
        assertThat(scaleFor(DpSize(222.dp, 270.dp))).isEqualTo(AgendaScale.COMPACT)
        assertThat(scaleFor(DpSize(378.dp, 672.dp))).isEqualTo(AgendaScale.LARGE)
    }

    @Test
    fun `width buckets into the four tiers`() {
        // Tall enough that the height cap never binds, isolating the width rule.
        val h = 500.dp
        assertThat(scaleFor(DpSize(180.dp, h))).isEqualTo(AgendaScale.COMPACT)
        assertThat(scaleFor(DpSize(259.dp, h))).isEqualTo(AgendaScale.COMPACT)
        assertThat(scaleFor(DpSize(260.dp, h))).isEqualTo(AgendaScale.REGULAR)
        assertThat(scaleFor(DpSize(329.dp, h))).isEqualTo(AgendaScale.REGULAR)
        assertThat(scaleFor(DpSize(330.dp, h))).isEqualTo(AgendaScale.LARGE)
        assertThat(scaleFor(DpSize(419.dp, h))).isEqualTo(AgendaScale.LARGE)
        assertThat(scaleFor(DpSize(420.dp, h))).isEqualTo(AgendaScale.XLARGE)
        assertThat(scaleFor(DpSize(900.dp, h))).isEqualTo(AgendaScale.XLARGE)
    }

    @Test
    fun `extra height never raises the tier`() {
        // Height decides how many rows are visible, not how big they are: a tall,
        // narrow widget wants more events, not bigger text.
        val short = scaleFor(DpSize(222.dp, 200.dp))
        val tall = scaleFor(DpSize(222.dp, 900.dp))
        assertThat(short).isEqualTo(AgendaScale.COMPACT)
        assertThat(tall).isEqualTo(AgendaScale.COMPACT)
    }

    @Test
    fun `a squashed widget is stepped back down`() {
        // Same (wide) width, shrinking height: the tier must walk down rather than
        // keep big type in a sliver of space.
        val wide = 378.dp
        assertThat(scaleFor(DpSize(wide, 672.dp))).isEqualTo(AgendaScale.LARGE)
        assertThat(scaleFor(DpSize(wide, 400.dp))).isEqualTo(AgendaScale.LARGE)
        assertThat(scaleFor(DpSize(wide, 300.dp))).isEqualTo(AgendaScale.REGULAR)
        assertThat(scaleFor(DpSize(wide, 200.dp))).isEqualTo(AgendaScale.COMPACT)
    }

    @Test
    fun `the height cap never exceeds what the width earned`() {
        // A tall but narrow widget stays at its width's tier — the cap only ever
        // lowers, so a huge height can't promote a 222dp-wide widget.
        assertThat(scaleFor(DpSize(222.dp, 900.dp))).isEqualTo(AgendaScale.COMPACT)
        assertThat(scaleFor(DpSize(300.dp, 900.dp))).isEqualTo(AgendaScale.REGULAR)
    }

    // --- metricsFor: the "default unchanged" regression guard -----------------

    @Test
    fun `COMPACT metrics equal the widget's original constants`() {
        // If this fails, a default-sized agenda widget no longer looks as it did.
        val m = metricsFor(AgendaScale.COMPACT)
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
    }

    @Test
    fun `type sizes are non-decreasing across the tiers`() {
        val tiers = listOf(
            AgendaScale.COMPACT,
            AgendaScale.REGULAR,
            AgendaScale.LARGE,
            AgendaScale.XLARGE,
        ).map(::metricsFor)

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
        }
    }
}
