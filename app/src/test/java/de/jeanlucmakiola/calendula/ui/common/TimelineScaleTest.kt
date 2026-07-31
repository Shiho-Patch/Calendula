package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TimelineScaleTest {

    /** A Pixel-7-ish timeline viewport: what the issue reporter is looking at. */
    private val phoneViewport = 670.dp

    @Test
    fun `Regular is the timeline's original 56dp constant`() {
        // The default must not move: an install that never opens the setting has
        // to keep the week and day views it already had (#56).
        assertThat(TimelineScale.Regular.hourHeight(phoneViewport)).isEqualTo(56.dp)
    }

    @Test
    fun `the fixed scales ignore the viewport`() {
        for (scale in listOf(TimelineScale.Compact, TimelineScale.Regular, TimelineScale.Comfortable)) {
            assertThat(scale.hourHeight(200.dp)).isEqualTo(scale.hourHeight(2000.dp))
        }
    }

    @Test
    fun `the fixed scales get taller in listed order`() {
        assertThat(TimelineScale.Compact.hourHeight(phoneViewport))
            .isLessThan(TimelineScale.Regular.hourHeight(phoneViewport))
        assertThat(TimelineScale.Regular.hourHeight(phoneViewport))
            .isLessThan(TimelineScale.Comfortable.hourHeight(phoneViewport))
    }

    @Test
    fun `fit-day puts all 24 hours inside a phone viewport`() {
        // The whole point of the issue: no vertical scrolling to see the day.
        val h = TimelineScale.FitDay.hourHeight(phoneViewport)
        assertThat(h * 24).isAtMost(phoneViewport)
        // …and it uses the space, rather than leaving most of it empty.
        assertThat(h * 24).isGreaterThan(phoneViewport * 0.9f)
    }

    @Test
    fun `fit-day clamps instead of shrinking hours past legibility`() {
        // A very short viewport (split screen, tiny device) would otherwise give
        // hour rows too small for the gutter's 24 labels; the clamp wins and the
        // timeline keeps a little scroll.
        assertThat(TimelineScale.FitDay.hourHeight(120.dp)).isEqualTo(FIT_DAY_MIN)
    }

    @Test
    fun `fit-day clamps instead of stretching hours on a very tall viewport`() {
        assertThat(TimelineScale.FitDay.hourHeight(4000.dp)).isEqualTo(FIT_DAY_MAX)
    }

    @Test
    fun `the minimum event height matches the old 24dp floor at the default scale`() {
        // MIN_EVENT_FRACTION replaced a hardcoded 24dp; at Regular it must still
        // land there, or short events change size for everyone who never touched
        // the setting.
        val floor = TimelineScale.Regular.hourHeight(phoneViewport) * MIN_EVENT_FRACTION
        assertThat(floor.value).isWithin(0.5f).of(24f)
    }

    @Test
    fun `the minimum event height stays a fixed share of an hour`() {
        // A fixed dp floor would swallow ever more of the day as the scale drops;
        // as a fraction it always means the same duration.
        for (scale in TimelineScale.entries) {
            val hour = scale.hourHeight(phoneViewport)
            assertThat((hour * MIN_EVENT_FRACTION) / hour).isWithin(0.001f).of(MIN_EVENT_FRACTION)
        }
    }
}
