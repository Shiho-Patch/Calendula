package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TimelineHoursTest {

    private val viewport = 600.dp

    @Test
    fun `the chosen range exactly fills the viewport`() {
        // 07–22 is 15 hours: 600dp / 15 = 40dp an hour.
        assertThat(TimelineScale.FitHours(7, 22).hourHeight(viewport)).isEqualTo(40.dp)
    }

    @Test
    fun `the whole day fits like FitDay`() {
        assertThat(TimelineScale.FitHours(0, 24).hourHeight(viewport))
            .isEqualTo(TimelineScale.FitDay.hourHeight(viewport))
    }

    @Test
    fun `a short range on a tall screen stops at the pinch ceiling`() {
        assertThat(TimelineScale.FitHours(9, 11).hourHeight(2000.dp)).isEqualTo(MAX_PINCH_HOUR_HEIGHT)
    }

    @Test
    fun `ranges are held to the day and the minimum span`() {
        assertThat(TimelineHours.of(true, -3, 30)).isEqualTo(TimelineHours(true, 0, 24))
        assertThat(TimelineHours.of(true, 23, 22)).isEqualTo(TimelineHours(true, 20, 22))
        assertThat(TimelineHours.of(true, 5, 1)).isEqualTo(TimelineHours(true, 0, 2))
    }

    @Test
    fun `the pickers only offer valid pairings`() {
        assertThat(TimelineHours.startOptions(22)).isEqualTo((0..20).toList())
        assertThat(TimelineHours.endOptions(7)).isEqualTo((9..24).toList())
    }

    @Test
    fun `opening scrolls to the start hour once the layout is at the fitted height`() {
        // 40px an hour over a 600px viewport: 960px of content, 360px of scroll.
        assertThat(startHourScroll(maxValue = 360, viewportPx = 600, startHour = 7, hourPx = 40f))
            .isEqualTo(280)
    }

    @Test
    fun `a layout still at the previous scale is waited out`() {
        // Still 56px an hour: 1344 - 600 = 744 of scroll.
        assertThat(startHourScroll(maxValue = 744, viewportPx = 600, startHour = 7, hourPx = 40f))
            .isNull()
        // Not laid out at all yet.
        assertThat(startHourScroll(maxValue = Int.MAX_VALUE, viewportPx = 0, startHour = 7, hourPx = 40f))
            .isNull()
    }

    @Test
    fun `the whole day needs no scroll`() {
        assertThat(startHourScroll(maxValue = 0, viewportPx = 600, startHour = 0, hourPx = 25f))
            .isEqualTo(0)
    }

    @Test
    fun `a fitted range round-trips through storage`() {
        val scale = TimelineScale.FitHours(6, 20)
        assertThat(parseTimelineScale(scale.storageValue())).isEqualTo(scale)
        assertThat(parseTimelineScale("hours:x-2")).isEqualTo(TimelineScale.Regular)
    }

    @Test
    fun `opening with visible hours on replaces a pinched scale`() {
        val persisted = mutableListOf<TimelineScale>()
        val zoom = TimelineZoom(TimelineScale.custom(90.dp)) { persisted += it }
        zoom.adoptHours(TimelineHours(true, 7, 22))

        assertThat(zoom.open()).isEqualTo(7)
        assertThat(zoom.scale).isEqualTo(TimelineScale.FitHours(7, 22))

        // A pinch overrides it for the visit but is not saved over the preference.
        zoom.beginPinch()
        zoom.pinchTo(100.dp)
        zoom.endPinch()
        assertThat(zoom.scale).isEqualTo(TimelineScale.custom(100.dp))
        assertThat(persisted).isEmpty()

        // Opening again fits the range again.
        zoom.open()
        assertThat(zoom.scale).isEqualTo(TimelineScale.FitHours(7, 22))
    }

    @Test
    fun `a late stored scale does not undo the fit, and switching off restores it`() {
        val zoom = TimelineZoom(TimelineScale.Regular) {}
        zoom.adoptHours(TimelineHours(true, 7, 22))
        zoom.open()
        zoom.adopt(TimelineScale.Comfortable)
        assertThat(zoom.scale).isEqualTo(TimelineScale.FitHours(7, 22))

        zoom.adoptHours(TimelineHours(false, 7, 22))
        assertThat(zoom.open()).isNull()
        assertThat(zoom.scale).isEqualTo(TimelineScale.Comfortable)
    }

    @Test
    fun `with visible hours off a pinch is still saved`() {
        val persisted = mutableListOf<TimelineScale>()
        val zoom = TimelineZoom(TimelineScale.Regular) { persisted += it }
        assertThat(zoom.open()).isNull()
        zoom.beginPinch()
        zoom.pinchTo(70.dp)
        zoom.endPinch()
        assertThat(persisted).containsExactly(TimelineScale.custom(70.dp))
    }
}
