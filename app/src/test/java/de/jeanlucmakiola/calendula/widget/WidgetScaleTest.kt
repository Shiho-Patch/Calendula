package de.jeanlucmakiola.calendula.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WidgetScaleTest {

    @Test
    fun `the on-device calibration points map to their tiers`() {
        // The two sizes measured on-device: the compact widget stays COMPACT (the
        // baseline, unchanged), the full-width one steps up to LARGE — not XLARGE,
        // which read as too big on a phone (#51).
        assertThat(scaleFor(DpSize(222.dp, 270.dp))).isEqualTo(WidgetScale.COMPACT)
        assertThat(scaleFor(DpSize(378.dp, 672.dp))).isEqualTo(WidgetScale.LARGE)
    }

    @Test
    fun `a full-width widget at ordinary height still scales up`() {
        // The regression the height cap used to cause: widening the widget without
        // also making it unusually tall is *the* resize #51 reports, and it must
        // reach the tier its width earned. Three cells tall is about 270dp.
        assertThat(scaleFor(DpSize(378.dp, 270.dp))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(300.dp, 270.dp))).isEqualTo(WidgetScale.REGULAR)
        assertThat(scaleFor(DpSize(460.dp, 300.dp))).isEqualTo(WidgetScale.XLARGE)
    }

    @Test
    fun `width buckets into the four tiers`() {
        // Tall enough that the height cap never binds, isolating the width rule.
        val h = 500.dp
        assertThat(scaleFor(DpSize(180.dp, h))).isEqualTo(WidgetScale.COMPACT)
        assertThat(scaleFor(DpSize(259.dp, h))).isEqualTo(WidgetScale.COMPACT)
        assertThat(scaleFor(DpSize(260.dp, h))).isEqualTo(WidgetScale.REGULAR)
        assertThat(scaleFor(DpSize(329.dp, h))).isEqualTo(WidgetScale.REGULAR)
        assertThat(scaleFor(DpSize(330.dp, h))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(419.dp, h))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(420.dp, h))).isEqualTo(WidgetScale.XLARGE)
        assertThat(scaleFor(DpSize(900.dp, h))).isEqualTo(WidgetScale.XLARGE)
    }

    @Test
    fun `extra height never raises the tier`() {
        // Height decides how many rows are visible, not how big they are: a tall,
        // narrow widget wants more events, not bigger text.
        assertThat(scaleFor(DpSize(222.dp, 200.dp))).isEqualTo(WidgetScale.COMPACT)
        assertThat(scaleFor(DpSize(222.dp, 900.dp))).isEqualTo(WidgetScale.COMPACT)
        assertThat(scaleFor(DpSize(300.dp, 900.dp))).isEqualTo(WidgetScale.REGULAR)
    }

    @Test
    fun `only a genuinely squashed widget is stepped back down`() {
        // Same (wide) width, shrinking height. The cap exists to stop oversized type
        // surviving in a one- or two-row sliver — it must not fire at normal heights.
        // Heights are gross; the cap works on height minus 60dp of chrome, so the
        // LARGE floor is 190dp (130dp usable) and the REGULAR floor 130dp (70dp).
        val wide = 378.dp
        assertThat(scaleFor(DpSize(wide, 400.dp))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(wide, 260.dp))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(wide, 190.dp))).isEqualTo(WidgetScale.LARGE)
        assertThat(scaleFor(DpSize(wide, 189.dp))).isEqualTo(WidgetScale.REGULAR)
        assertThat(scaleFor(DpSize(wide, 130.dp))).isEqualTo(WidgetScale.REGULAR)
        assertThat(scaleFor(DpSize(wide, 129.dp))).isEqualTo(WidgetScale.COMPACT)
        // The provider's declared floor (minResizeWidth/Height = 110dp) is COMPACT.
        assertThat(scaleFor(DpSize(110.dp, 110.dp))).isEqualTo(WidgetScale.COMPACT)
    }

    @Test
    fun `the tier is monotonic in both axes`() {
        // Growing a widget must never make its type smaller. Sweeps the whole
        // plausible range rather than spot-checking, so a future threshold edit
        // can't accidentally invert a step.
        val widths = (110..900 step 7).map { it.dp }
        val heights = (110..900 step 7).map { it.dp }
        widths.forEach { w ->
            heights.zipWithNext { shorter, taller ->
                assertThat(scaleFor(DpSize(w, taller)))
                    .isAtLeast(scaleFor(DpSize(w, shorter)))
            }
        }
        heights.forEach { h ->
            widths.zipWithNext { narrower, wider ->
                assertThat(scaleFor(DpSize(wider, h)))
                    .isAtLeast(scaleFor(DpSize(narrower, h)))
            }
        }
    }
}
