package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The arithmetic behind the pinch's focal anchor (#56) — the one part of the
 * gesture that is not a pointer-event concern and can be pinned down here.
 */
class TimelineZoomTest {

    @Test
    fun `the moment under the fingers stays under them`() {
        // Scrolled to 09:00 at 56dp/h, pinching around a point 100px down the
        // viewport — that point is 10:47-ish, and it has to still be there after.
        val old = 56f
        val new = 84f
        val scroll = (9 * old).toInt()
        val centroidY = 100f
        val before = (scroll + centroidY) / old

        val after = anchoredScroll(scroll, centroidY, old, new)

        assertThat((after + centroidY) / new).isWithin(0.001f).of(before)
    }

    @Test
    fun `zooming in scrolls down and zooming out scrolls back up`() {
        val scroll = 500
        val centroidY = 200f
        assertThat(anchoredScroll(scroll, centroidY, 56f, 84f)).isGreaterThan(scroll.toFloat())
        assertThat(anchoredScroll(scroll, centroidY, 56f, 32f)).isLessThan(scroll.toFloat())
    }

    @Test
    fun `an unchanged scale asks for no scroll`() {
        assertThat(anchoredScroll(500, 200f, 56f, 56f)).isWithin(0.001f).of(500f)
    }

    @Test
    fun `pinching at the very top of a day held at the top keeps it there`() {
        // Midnight is at offset 0 whatever the scale, so there is nothing to
        // correct — a pinch here must not push the day off its own start.
        assertThat(anchoredScroll(0, 0f, 56f, 84f)).isWithin(0.001f).of(0f)
    }

    @Test
    fun `a pinch lands on whole pixels`() {
        // The gutter is 24 stacked hour-tall boxes, each rounding its own height,
        // while the lines and blocks are drawn at the fractional one — a
        // fractional hour puts the two on different grids and jumps the labels
        // about as the pinch drifts across each half pixel.
        for (target in listOf(56.4f, 56.6f, 83.5f, 120.01f)) {
            val landed = pinchedHourHeightPx(target, fillPx = 28f, maxPx = 240f)
            assertThat(landed).isEqualTo(landed.toInt().toFloat())
        }
    }

    @Test
    fun `a pinch stops where the day fills the screen`() {
        assertThat(pinchedHourHeightPx(target = 5f, fillPx = 28f, maxPx = 240f)).isEqualTo(28f)
    }

    @Test
    fun `filling the screen outranks the ceiling`() {
        // On a viewport tall enough for the two to disagree, dead space under
        // midnight is the worse of the two failures.
        assertThat(pinchedHourHeightPx(target = 10f, fillPx = 300f, maxPx = 240f)).isEqualTo(300f)
    }

    @Test
    fun `a pinch stops at the ceiling`() {
        assertThat(pinchedHourHeightPx(target = 9_000f, fillPx = 28f, maxPx = 240f)).isEqualTo(240f)
    }

    @Test
    fun `a settled pinch is what gets persisted`() {
        var persisted: TimelineScale? = null
        val zoom = TimelineZoom(TimelineScale.Regular) { persisted = it }

        zoom.beginPinch()
        zoom.pinchTo(70.dp)
        assertThat(zoom.scale).isEqualTo(TimelineScale.custom(70.dp))
        // Nothing is written until the fingers lift — a DataStore write per
        // pointer frame is what this state holder exists to avoid.
        assertThat(persisted).isNull()

        zoom.endPinch()
        assertThat(persisted).isEqualTo(TimelineScale.custom(70.dp))
    }

    @Test
    fun `the stored value cannot snap the timeline back mid-pinch`() {
        val zoom = TimelineZoom(TimelineScale.Regular) {}
        zoom.beginPinch()
        zoom.pinchTo(70.dp)

        // The preference echoing its old value back (it is a frame or two behind
        // the fingers) must not land while the gesture is still running.
        zoom.adopt(TimelineScale.Regular)
        assertThat(zoom.scale).isEqualTo(TimelineScale.custom(70.dp))

        // Once it has settled, Settings can still move it.
        zoom.endPinch()
        zoom.adopt(TimelineScale.Compact)
        assertThat(zoom.scale).isEqualTo(TimelineScale.Compact)
    }
}
