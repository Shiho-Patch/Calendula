package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
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
            .isEqualTo(CornerSize(TIMED_BLOCK_CORNER))
        assertThat(timedBlockShape(continuesBefore = false, continuesAfter = true).topStart)
            .isEqualTo(CornerSize(TIMED_BLOCK_CORNER))
        assertThat(timedBlockShape(continuesBefore = false, continuesAfter = true).bottomEnd)
            .isEqualTo(CornerSize(square))
    }

    @Test
    fun `a dragged piece is sized by what its day can still show`() {
        val fourHours = 4 * 60
        // Held at 22:00 only two of its four hours fit before midnight...
        assertThat(visibleSpanMinutes(22 * 60, fourHours)).isEqualTo(2 * 60)
        // ...carried up to 21:00 it grows to three...
        assertThat(visibleSpanMinutes(21 * 60, fourHours)).isEqualTo(3 * 60)
        // ...and anywhere it fits outright it is simply itself.
        assertThat(visibleSpanMinutes(10 * 60, fourHours)).isEqualTo(fourHours)
        assertThat(visibleSpanMinutes(20 * 60, fourHours)).isEqualTo(fourHours)
    }
}
