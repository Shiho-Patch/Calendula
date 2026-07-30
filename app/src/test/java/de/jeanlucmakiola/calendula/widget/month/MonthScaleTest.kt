package de.jeanlucmakiola.calendula.widget.month

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.widget.WidgetSize
import org.junit.jupiter.api.Test

class MonthScaleTest {

    /**
     * `appwidget_info_month.xml`'s `minResizeWidth` — the narrowest the host will
     * let a month widget be resized to. Mirrored here because the smallest size
     * step has to fit inside it; the two move together or the grid clips again.
     */
    private val MIN_RESIZE_WIDTH = 250.dp

    // --- the #103 guarantee: seven columns always fit -------------------------

    @Test
    fun `the smallest step fits the narrowest placement the provider allows`() {
        // This is the whole fix. The grid is laid out at a fixed width, so the
        // default step must fit the narrowest widget a user can drag it down to —
        // otherwise columns fall off the edge exactly as they did when the width
        // came from the launcher (#103). The slack covers host chrome, which is
        // not part of the declared size.
        val small = monthMetricsFor(WidgetSize.SMALL).gridWidth
        assertThat(small.value).isLessThan(MIN_RESIZE_WIDTH.value)
        assertThat((MIN_RESIZE_WIDTH - small).value).isAtLeast(24f)
    }

    @Test
    fun `every step lays out exactly seven columns plus its padding`() {
        WidgetSize.entries.map(::monthMetricsFor).forEach { m ->
            assertThat(m.gridWidth).isEqualTo(m.columnWidth * 7 + m.gridPadding * 2)
        }
    }

    @Test
    fun `the largest step still fits a full-width phone widget`() {
        // EXTRA_LARGE is opt-in, but it should be reachable on a normal handset
        // rather than being a tablet-only trap.
        assertThat(monthMetricsFor(WidgetSize.EXTRA_LARGE).gridWidth.value).isAtMost(400f)
    }

    // --- the ramp ------------------------------------------------------------

    @Test
    fun `sizes are non-decreasing across the steps`() {
        monthMetricsFor(WidgetSize.SMALL)
        WidgetSize.entries.map(::monthMetricsFor).zipWithNext { small, big ->
            assertThat(big.columnWidth.value).isAtLeast(small.columnWidth.value)
            assertThat(big.laneHeight.value).isAtLeast(small.laneHeight.value)
            assertThat(big.dayNumberHeight.value).isAtLeast(small.dayNumberHeight.value)
            assertThat(big.headerTitle.value).isAtLeast(small.headerTitle.value)
            assertThat(big.weekday.value).isAtLeast(small.weekday.value)
            assertThat(big.dayNumber.value).isAtLeast(small.dayNumber.value)
            assertThat(big.eventTitle.value).isAtLeast(small.eventTitle.value)
            assertThat(big.overflow.value).isAtLeast(small.overflow.value)
            assertThat(big.iconImage.value).isAtLeast(small.iconImage.value)
            assertThat(big.iconBox.value).isAtLeast(small.iconBox.value)
        }
    }

    @Test
    fun `the today circle always fits inside its column`() {
        // The day number sits in a circle dayNumberHeight across, centred in a
        // column. If it ever outgrew the column it would collide with its
        // neighbours.
        WidgetSize.entries.map(::monthMetricsFor).forEach { m ->
            assertThat(m.dayNumberHeight.value).isLessThan(m.columnWidth.value)
        }
    }

    @Test
    fun `SMALL keeps the widget's original row metrics`() {
        // SMALL is the default, so a month widget whose owner never touched the
        // setting must keep the metrics it shipped with.
        val m = monthMetricsFor(WidgetSize.SMALL)
        assertThat(m.laneHeight).isEqualTo(14.dp)
        assertThat(m.dayNumberHeight).isEqualTo(18.dp)
        assertThat(m.headerTitle.value).isEqualTo(15f)
        assertThat(m.weekday.value).isEqualTo(11f)
        assertThat(m.dayNumber.value).isEqualTo(11f)
        assertThat(m.eventTitle.value).isEqualTo(9f)
        assertThat(m.overflow.value).isEqualTo(9f)
    }

    @Test
    fun `three lanes plus the day number stay inside a compact week row`() {
        // A week row gets roughly a sixth of the grid's height. If the metrics
        // outgrew that, lanes would be clipped rather than merely tight.
        val m = monthMetricsFor(WidgetSize.SMALL)
        val weekRow = m.dayNumberHeight + 2.dp + (m.laneHeight + 1.dp) * MAX_LANES + m.laneHeight
        assertThat(weekRow.value).isLessThan(90f)
    }
}
