package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.week.MINUTES_PER_DAY
import de.jeanlucmakiola.calendula.ui.week.layoutDay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test

/**
 * Which edges a timed block is cut on, and how much of a dragged piece its day
 * can show (#253).
 */
class TimedBlockShapeTest {

    private val zone = TimeZone.UTC
    private val day1 = LocalDate(2026, 9, 5)
    private val day2 = day1.plus(1, DateTimeUnit.DAY)
    private val day3 = day2.plus(1, DateTimeUnit.DAY)

    private fun event(from: LocalDateTimeIsh, to: LocalDateTimeIsh) = EventInstance(
        instanceId = 1L, eventId = 1L, calendarId = 1L, title = "E",
        start = from.date.atTime(from.hour, 0).toInstant(zone),
        end = to.date.atTime(to.hour, 0).toInstant(zone),
        isAllDay = false, color = 0xFF112233.toInt(), location = null,
    )

    data class LocalDateTimeIsh(val date: LocalDate, val hour: Int)

    private fun at(date: LocalDate, hour: Int) = LocalDateTimeIsh(date, hour)

    @Test
    fun `a same-day block is cut on neither edge`() {
        val ev = event(at(day1, 9), at(day1, 10))
        val block = layoutDay(listOf(ev), day1, zone).single()

        assertThat(block.continuesBefore(day1, zone)).isFalse()
        assertThat(block.continuesAfter(day1, zone)).isFalse()
    }

    @Test
    fun `an overnight event is cut at the foot of its first day and the head of its second`() {
        val ev = event(at(day1, 20), at(day2, 8))

        val head = layoutDay(listOf(ev), day1, zone).single()
        assertThat(head.continuesBefore(day1, zone)).isFalse()
        assertThat(head.continuesAfter(day1, zone)).isTrue()

        val tail = layoutDay(listOf(ev), day2, zone).single()
        assertThat(tail.continuesBefore(day2, zone)).isTrue()
        assertThat(tail.continuesAfter(day2, zone)).isFalse()
    }

    @Test
    fun `a day wholly inside a long event is cut on both edges`() {
        val ev = event(at(day1, 20), at(day3, 8))
        val middle = layoutDay(listOf(ev), day2, zone).single()

        assertThat(middle.continuesBefore(day2, zone)).isTrue()
        assertThat(middle.continuesAfter(day2, zone)).isTrue()
    }

    @Test
    fun `an event ending exactly at midnight is not cut`() {
        val ev = event(at(day1, 20), at(day2, 0))
        val block = layoutDay(listOf(ev), day1, zone).single()

        assertThat(block.continuesAfter(day1, zone)).isFalse()
    }

    @Test
    fun `only the cut edges lose their corners`() {
        val square = 0.dp
        assertThat(timedBlockShape(continuesBefore = true, continuesAfter = false).topStart)
            .isEqualTo(CornerSize(square))
        assertThat(timedBlockShape(continuesBefore = true, continuesAfter = false).bottomStart)
            .isEqualTo(CornerSize(EVENT_CHIP_CORNER))
        assertThat(timedBlockShape(continuesBefore = false, continuesAfter = true).topStart)
            .isEqualTo(CornerSize(EVENT_CHIP_CORNER))
        assertThat(timedBlockShape(continuesBefore = false, continuesAfter = true).bottomEnd)
            .isEqualTo(CornerSize(square))
    }

    private val fourHours = 4 * 60

    @Test
    fun `an event that fits its day is drawn as one uncut piece`() {
        val pieces = dragSlices(eventStartMin = 10 * 60, spanMin = fourHours)

        assertThat(pieces).hasSize(1)
        assertThat(pieces.single().dayOffset).isEqualTo(0)
        assertThat(pieces.single().spanMin).isEqualTo(fourHours)
        assertThat(pieces.single().continuesBefore).isFalse()
        assertThat(pieces.single().continuesAfter).isFalse()
    }

    @Test
    fun `dragging the start half re-splits both days as it moves`() {
        // Held at 22:00: two hours on this day, two on the next.
        assertThat(dragSlices(22 * 60, fourHours).map { it.spanMin })
            .containsExactly(2 * 60, 2 * 60).inOrder()
        // Carried up to 21:00 the first day takes three, so the second day's end
        // comes up by exactly the hour the first day gained.
        assertThat(dragSlices(21 * 60, fourHours).map { it.spanMin })
            .containsExactly(3 * 60, 60).inOrder()
    }

    @Test
    fun `dragging the end half splits the same way, one day back`() {
        // The tail's top edge is at midnight and the event began two hours
        // earlier, so it starts at -120 relative to the day the finger is on.
        val pieces = dragSlices(eventStartMin = -2 * 60, spanMin = fourHours)

        assertThat(pieces.map { it.dayOffset }).containsExactly(-1, 0).inOrder()
        assertThat(pieces.map { it.spanMin }).containsExactly(2 * 60, 2 * 60).inOrder()
        // The piece on the earlier day ends at midnight; the held one starts there.
        assertThat(pieces.first().startMin).isEqualTo(22 * 60)
        assertThat(pieces.first().continuesAfter).isTrue()
        assertThat(pieces.last().startMin).isEqualTo(0)
        assertThat(pieces.last().continuesBefore).isTrue()
    }

    @Test
    fun `the earlier half shrinks away as the end half is pulled down its own day`() {
        // Tail top dragged from midnight down to 01:00: one hour left behind.
        assertThat(dragSlices(-2 * 60 + 60, fourHours).map { it.spanMin })
            .containsExactly(60, 3 * 60).inOrder()
        // At 02:00 the event no longer crosses midnight at all.
        val whole = dragSlices(0, fourHours)
        assertThat(whole).hasSize(1)
        assertThat(whole.single().continuesBefore).isFalse()
    }

    @Test
    fun `a day wholly inside a long event is cut on both sides`() {
        val pieces = dragSlices(eventStartMin = 22 * 60, spanMin = 30 * 60)

        assertThat(pieces.map { it.dayOffset }).containsExactly(0, 1, 2).inOrder()
        assertThat(pieces[1].spanMin).isEqualTo(MINUTES_PER_DAY)
        assertThat(pieces[1].continuesBefore).isTrue()
        assertThat(pieces[1].continuesAfter).isTrue()
    }

    @Test
    fun `an event ending exactly at midnight takes no piece of the next day`() {
        val pieces = dragSlices(eventStartMin = 22 * 60, spanMin = 2 * 60)

        assertThat(pieces).hasSize(1)
        assertThat(pieces.single().continuesAfter).isFalse()
    }

    @Test
    fun `a zero-length event still has a piece to draw`() {
        val pieces = dragSlices(eventStartMin = 9 * 60, spanMin = 0)

        assertThat(pieces).hasSize(1)
        assertThat(pieces.single().dayOffset).isEqualTo(0)
        assertThat(pieces.single().startMin).isEqualTo(9 * 60)
        assertThat(pieces.single().spanMin).isEqualTo(0)
    }

    @Test
    fun `only the days there are columns for are sliced`() {
        // A week's worth of event, previewed on a timeline showing three days
        // from the one the finger is on.
        val pieces = dragSlices(0, 7 * MINUTES_PER_DAY, within = 0..2)

        assertThat(pieces.map { it.dayOffset }).containsExactly(0, 1, 2).inOrder()
        assertThat(pieces.last().continuesAfter).isTrue()
    }

    @Test
    fun `an event outside the columns shown is sliced into nothing`() {
        assertThat(dragSlices(-3 * MINUTES_PER_DAY, 60, within = 0..2)).isEmpty()
    }
}
