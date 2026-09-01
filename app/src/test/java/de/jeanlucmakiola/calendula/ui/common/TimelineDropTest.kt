package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import de.jeanlucmakiola.calendula.ui.week.layoutDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * What a timeline drop asks for, for the head and the tail of an event that
 * crosses midnight (#253). The tail's top edge is midnight rather than the
 * event's start, so the drop has to take that offset back off.
 */
class TimelineDropTest {

    private val zone = TimeZone.UTC
    private val sat = LocalDate(2026, 9, 5)
    private val sun = sat.plus(1, DateTimeUnit.DAY)

    // Sat 5 Sep 20:00 to Sun 6 Sep 08:00.
    private val overnight = EventInstance(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "Night shift",
        start = sat.atTime(20, 0).toInstant(zone),
        end = sun.atTime(8, 0).toInstant(zone),
        isAllDay = false,
        color = 0xFF112233.toInt(),
        location = null,
    )

    private fun blockOn(day: LocalDate): TimedBlock =
        layoutDay(listOf(overnight), day, zone).single()

    @Test
    fun `the head block carries no clip offset`() {
        val head = blockOn(sat)
        assertThat(head.startMin).isEqualTo(20 * 60)
        assertThat(head.clipOffsetMinutes(sat, zone)).isEqualTo(0)
    }

    @Test
    fun `the tail block is offset by the part that already ran`() {
        val tail = blockOn(sun)
        assertThat(tail.startMin).isEqualTo(0)
        // 20:00 to midnight is four hours of the event already gone.
        assertThat(tail.clipOffsetMinutes(sun, zone)).isEqualTo(4 * 60)
    }

    @Test
    fun `dropping the head asks for exactly where it landed`() {
        val target = LocalDate(2026, 9, 2)
        val drop = TimelineDrop(overnight, target, 20 * 60, clipOffsetMin = 0)

        assertThat(drop.startInstant(zone)).isEqualTo(target.atTime(20, 0).toInstant(zone))
    }

    @Test
    fun `dropping the tail moves the event by the days the tail travelled`() {
        val tail = blockOn(sun)
        val offset = tail.clipOffsetMinutes(sun, zone)
        // Dragged three days back, same height in the column: Sun 6 -> Thu 3.
        val drop = TimelineDrop(overnight, LocalDate(2026, 9, 3), 0, offset)

        // The event began Sat 5 Sep 20:00, so it now begins Wed 2 Sep 20:00 —
        // the whole event moved three days, and no time of day was invented.
        assertThat(drop.startInstant(zone))
            .isEqualTo(LocalDate(2026, 9, 2).atTime(20, 0).toInstant(zone))
    }

    @Test
    fun `dragging the tail down the column re-times the event across midnight`() {
        val offset = blockOn(sun).clipOffsetMinutes(sun, zone)
        // Tail top pulled from 00:00 to 03:00 on its own day.
        val drop = TimelineDrop(overnight, sun, 3 * 60, offset)

        assertThat(drop.startInstant(zone)).isEqualTo(sat.atTime(23, 0).toInstant(zone))
    }

    @Test
    fun `pulling the tail above its own midnight moves the event earlier`() {
        val offset = blockOn(sun).clipOffsetMinutes(sun, zone)
        // The tail's top edge dragged an hour above the midnight it rests on.
        val drop = TimelineDrop(overnight, sun, -60, offset)

        // Event was Sat 20:00 to Sun 08:00; it now begins an hour earlier.
        assertThat(drop.startInstant(zone)).isEqualTo(sat.atTime(19, 0).toInstant(zone))
    }

    @Test
    fun `a top edge dragged past the next midnight resolves onto the day after`() {
        val drop = TimelineDrop(overnight, sat, 25 * 60, clipOffsetMin = 0)

        assertThat(drop.startInstant(zone)).isEqualTo(sun.atTime(1, 0).toInstant(zone))
    }

    @Test
    fun `a short tail can still be dragged a step earlier`() {
        // 23:50 to 00:05: five minutes of tail, less than one snap step, which
        // a floor pinned at midnight left with nowhere to go (#253).
        assertThat(dragFloorMin(clipOffsetMin = 10, eventSpanMin = 15))
            .isEqualTo(-DRAG_SNAP_MINUTES)
    }

    @Test
    fun `a long tail may rise until its event ends just inside the day`() {
        // Sat 20:00 to Sun 08:00, held by the Sunday half.
        assertThat(dragFloorMin(clipOffsetMin = 4 * 60, eventSpanMin = 12 * 60))
            .isEqualTo(DRAG_SNAP_MINUTES - 8 * 60)
    }

    @Test
    fun `an unclipped block cannot be dragged above its own midnight`() {
        assertThat(dragFloorMin(clipOffsetMin = 0, eventSpanMin = 60)).isEqualTo(0)
    }

    @Test
    fun `a drop across a DST boundary keeps the time of day it was let go at`() {
        // Europe/Berlin springs forward at 02:00 on 29 March 2026, between the
        // event's start and the midnight its tail is cut at.
        val berlin = TimeZone.of("Europe/Berlin")
        val sun = LocalDate(2026, 3, 29)
        val overnightIntoDst = EventInstance(
            instanceId = 3L, eventId = 3L, calendarId = 1L, title = "Night shift",
            start = LocalDate(2026, 3, 28).atTime(22, 0).toInstant(berlin),
            end = sun.atTime(6, 0).toInstant(berlin),
            isAllDay = false, color = 0xFF112233.toInt(), location = null,
        )
        val tail = layoutDay(listOf(overnightIntoDst), sun, berlin).single()
        val offset = tail.clipOffsetMinutes(sun, berlin)
        // Dropped back onto its own slot a week earlier, clear of the boundary.
        val drop = TimelineDrop(overnightIntoDst, LocalDate(2026, 3, 22), 0, offset)

        assertThat(drop.startInstant(berlin))
            .isEqualTo(LocalDate(2026, 3, 21).atTime(22, 0).toInstant(berlin))
    }

    @Test
    fun `a block that is not clipped is unaffected wherever it sits`() {
        val plain = EventInstance(
            instanceId = 2L, eventId = 2L, calendarId = 1L, title = "Standup",
            start = sat.atTime(9, 0).toInstant(zone),
            end = sat.atTime(10, 0).toInstant(zone),
            isAllDay = false, color = 0xFF112233.toInt(), location = null,
        )
        val block = layoutDay(listOf(plain), sat, zone).single()
        assertThat(block.clipOffsetMinutes(sat, zone)).isEqualTo(0)
    }
}
