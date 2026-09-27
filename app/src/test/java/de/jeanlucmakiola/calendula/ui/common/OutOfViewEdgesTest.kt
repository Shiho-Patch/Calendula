package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/** Which events a column's edge bars point at, and where a tap scrolls (#188). */
class OutOfViewEdgesTest {

    private fun block(id: Long, startMin: Int, endMin: Int) = TimedBlock(
        event = EventInstance(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "Event $id",
            start = Instant.fromEpochMilliseconds(0),
            end = Instant.fromEpochMilliseconds(0),
            isAllDay = false,
            color = 0xFF112233.toInt(),
            location = null,
        ),
        startMin = startMin,
        endMin = endMin,
        lane = 0,
        laneCount = 1,
    )

    private val early = block(1, 7 * 60, 8 * 60)
    private val breakfast = block(2, 8 * 60, 9 * 60)
    private val lunch = block(3, 12 * 60, 13 * 60)
    private val dinner = block(4, 19 * 60, 20 * 60)
    private val late = block(5, 21 * 60, 22 * 60)

    // Viewport 10:00–17:00.
    private fun outOf(vararg blocks: TimedBlock, slackMin: Float = 0f) =
        outOfViewEvents(blocks.toList(), topMin = 600f, bottomMin = 1020f, slackMin = slackMin)

    @Test
    fun `the nearest hidden event on each side is picked`() {
        val events = outOf(early, breakfast, lunch, dinner, late)
        assertThat(events.above).isEqualTo(breakfast)
        assertThat(events.below).isEqualTo(dinner)
    }

    @Test
    fun `an event in view points nowhere`() {
        assertThat(outOf(lunch)).isEqualTo(OutOfViewEvents.None)
    }

    @Test
    fun `an event cut by the edge is not out of view`() {
        val straddling = block(6, 9 * 60, 11 * 60)
        assertThat(outOf(straddling).above).isNull()
    }

    @Test
    fun `a sliver within the slack still counts as out of view`() {
        val sliver = block(6, 9 * 60, 10 * 60 + 5)
        assertThat(outOf(sliver).above).isNull()
        assertThat(outOf(sliver, slackMin = 10f).above).isEqualTo(sliver)
        val tip = block(7, 17 * 60 - 5, 18 * 60)
        assertThat(outOf(tip).below).isNull()
        assertThat(outOf(tip, slackMin = 10f).below).isEqualTo(tip)
    }

    @Test
    fun `a column scrolled to its top points at nothing above it`() {
        val first = block(6, 0, 30)
        val events = outOfViewEvents(listOf(first), topMin = 0f, bottomMin = 420f, slackMin = 30f)
        assertThat(events.above).isNull()
    }

    @Test
    fun `a short event is judged by the height it renders at`() {
        // Five minutes ending at 09:58 still draws 26 minutes tall, past 10:00.
        val blip = block(6, 9 * 60 + 53, 9 * 60 + 58)
        assertThat(outOf(blip).above).isNull()
    }

    @Test
    fun `an event above scrolls to its start less the margin`() {
        val target = revealScrollTarget(breakfast, ViewportEdge.Top, hourPx = 100f, viewportPx = 700, marginPx = 20f, maxScroll = 1700)
        assertThat(target).isEqualTo(780)
    }

    @Test
    fun `an event below that fits lands whole at the bottom`() {
        val target = revealScrollTarget(dinner, ViewportEdge.Bottom, hourPx = 100f, viewportPx = 700, marginPx = 20f, maxScroll = 1700)
        // Its end, 2000, plus the margin, sits at the viewport's bottom.
        assertThat(target).isEqualTo(1320)
    }

    @Test
    fun `an event below taller than the viewport lands with its start at the top`() {
        val long = block(6, 12 * 60, 22 * 60)
        val target = revealScrollTarget(long, ViewportEdge.Bottom, hourPx = 100f, viewportPx = 700, marginPx = 20f, maxScroll = 1700)
        assertThat(target).isEqualTo(1180)
    }

    @Test
    fun `the target stays within the scroll range`() {
        val first = block(6, 0, 30)
        assertThat(
            revealScrollTarget(first, ViewportEdge.Top, hourPx = 100f, viewportPx = 700, marginPx = 20f, maxScroll = 1700),
        ).isEqualTo(0)
        val last = block(7, 23 * 60, 24 * 60)
        assertThat(
            revealScrollTarget(last, ViewportEdge.Bottom, hourPx = 100f, viewportPx = 700, marginPx = 20f, maxScroll = 1700),
        ).isEqualTo(1700)
    }
}
