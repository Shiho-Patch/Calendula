package de.jeanlucmakiola.calendula.ui.week

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class WeekLayoutTest {

    private val zone = TimeZone.UTC

    // 2026-06-10 is a Wednesday; its Monday-anchored week starts 2026-06-08.
    private val wed = LocalDate(2026, 6, 10)
    private val mon = LocalDate(2026, 6, 8)
    private val weekDays = (0..6).map { mon.plusDays(it) }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant =
        date.atTime(h, m).toInstant(zone)

    private fun event(
        start: Instant,
        end: Instant,
        allDay: Boolean = false,
        id: Long = 1L,
        title: String = "E",
    ) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        start = start,
        end = end,
        isAllDay = allDay,
        color = 0xFF112233.toInt(),
        location = null,
    )

    @Test
    fun `startOfWeek snaps to monday`() {
        assertThat(wed.startOfWeek(DayOfWeek.MONDAY)).isEqualTo(mon)
        assertThat(mon.startOfWeek(DayOfWeek.MONDAY)).isEqualTo(mon)
    }

    @Test
    fun `spanRange spans seven days`() {
        val range = spanRange(mon, 7, zone)
        assertThat(range.start).isEqualTo(at(mon, 0, 0))
        // endInclusive is the last second of day 7 (Sunday 2026-06-14)
        assertThat(range.endInclusive).isEqualTo(LocalDate(2026, 6, 14).atTime(23, 59, 59).toInstant(zone))
    }

    @Test
    fun `coversDay is true for any overlap and false otherwise`() {
        val ev = event(at(wed, 9), at(wed, 10))
        assertThat(ev.coversDay(wed, zone)).isTrue()
        assertThat(ev.coversDay(mon, zone)).isFalse()

        // All-day: UTC midnights, end exclusive. Mon..Tue covers Mon and Tue
        // but not Wed (the Wed-midnight end is exclusive).
        val multiDay = event(at(mon, 0), at(wed, 0), allDay = true)
        assertThat(multiDay.coversDay(mon, zone)).isTrue()
        assertThat(multiDay.coversDay(LocalDate(2026, 6, 9), zone)).isTrue()
        assertThat(multiDay.coversDay(wed, zone)).isFalse()
        assertThat(multiDay.coversDay(LocalDate(2026, 6, 12), zone)).isFalse()
    }

    @Test
    fun `single-day all-day event does not leak into the next day east of UTC`() {
        // A birthday on Wed: the provider stores UTC midnights with an exclusive
        // end (Thu 00:00 UTC). In a zone east of UTC the device-local day must
        // still resolve to Wed only — never Thu. Regression for the all-day
        // event appearing on two days in the views.
        val berlin = TimeZone.of("Europe/Berlin") // UTC+2 in June
        val ev = event(at(wed, 0), at(wed.plusDays(1), 0), allDay = true)
        assertThat(ev.coversDay(wed, berlin)).isTrue()
        assertThat(ev.coversDay(wed.plusDays(1), berlin)).isFalse()
        assertThat(ev.coversDay(wed.plusDays(-1), berlin)).isFalse()
    }

    @Test
    fun `single timed event gets one lane`() {
        val blocks = layoutDay(listOf(event(at(wed, 9), at(wed, 10, 30))), wed, zone)
        assertThat(blocks).hasSize(1)
        val b = blocks.single()
        assertThat(b.startMin).isEqualTo(9 * 60)
        assertThat(b.endMin).isEqualTo(10 * 60 + 30)
        assertThat(b.lane).isEqualTo(0)
        assertThat(b.laneCount).isEqualTo(1)
    }

    @Test
    fun `overlapping events resolve to side-by-side lanes`() {
        val a = event(at(wed, 9), at(wed, 11), id = 1L)
        val b = event(at(wed, 10), at(wed, 12), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone).sortedBy { it.lane }
        assertThat(blocks.map { it.lane }).containsExactly(0, 1)
        assertThat(blocks.all { it.laneCount == 2 }).isTrue()
    }

    @Test
    fun `back-to-back events reuse one lane`() {
        val a = event(at(wed, 9), at(wed, 10), id = 1L)
        val b = event(at(wed, 10), at(wed, 11), id = 2L)
        val blocks = layoutDay(listOf(a, b), wed, zone)
        assertThat(blocks).hasSize(2)
        assertThat(blocks.all { it.lane == 0 && it.laneCount == 1 }).isTrue()
    }

    @Test
    fun `event spanning midnight is clipped to the day`() {
        // Starts the previous evening, ends 02:00 on wed.
        val ev = event(at(mon.plusDays(1), 22), at(wed, 2))
        val blocks = layoutDay(listOf(ev), wed, zone)
        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().startMin).isEqualTo(0)
        assertThat(blocks.single().endMin).isEqualTo(2 * 60)
    }

    @Test
    fun `instant event is kept with zero-length`() {
        val ev = event(at(wed, 12), at(wed, 12))
        val blocks = layoutDay(listOf(ev), wed, zone)
        assertThat(blocks).hasSize(1)
        assertThat(blocks.single().startMin).isEqualTo(12 * 60)
        assertThat(blocks.single().endMin).isEqualTo(12 * 60)
    }

    @Test
    fun `all-day events are excluded from the timed layout`() {
        val ev = event(at(wed, 0), at(wed.plusDays(1), 0), allDay = true)
        assertThat(layoutDay(listOf(ev), wed, zone)).isEmpty()
    }

    @Test
    fun `events on other days are dropped`() {
        val ev = event(at(mon, 9), at(mon, 10))
        assertThat(layoutDay(listOf(ev), wed, zone)).isEmpty()
    }

    @Test
    fun `single-day all-day event is a one-column span`() {
        // Wed only: start Wed 00:00, end Thu 00:00.
        val ev = event(at(weekDays[2], 0), at(weekDays[3], 0), allDay = true)
        val spans = layoutAllDay(listOf(ev), weekDays, zone)
        assertThat(spans).hasSize(1)
        val s = spans.single()
        assertThat(s.startCol).isEqualTo(2)
        assertThat(s.endCol).isEqualTo(2)
        assertThat(s.lane).isEqualTo(0)
    }

    @Test
    fun `multi-day all-day event becomes one span across columns`() {
        // Tue..Thu: end Fri 00:00 is exclusive, so Fri is not covered.
        val ev = event(at(weekDays[1], 0), at(weekDays[4], 0), allDay = true)
        val s = layoutAllDay(listOf(ev), weekDays, zone).single()
        assertThat(s.startCol).isEqualTo(1)
        assertThat(s.endCol).isEqualTo(3)
    }

    @Test
    fun `span reaching outside the week is clamped to visible columns`() {
        // Starts two days before Monday, ends Wed 00:00 → covers Mon..Tue.
        val ev = event(at(mon.plusDays(-2), 0), at(weekDays[2], 0), allDay = true)
        val s = layoutAllDay(listOf(ev), weekDays, zone).single()
        assertThat(s.startCol).isEqualTo(0)
        assertThat(s.endCol).isEqualTo(1)
    }

    @Test
    fun `overlapping all-day spans get separate lanes`() {
        val a = event(at(weekDays[0], 0), at(weekDays[3], 0), allDay = true, id = 1L) // Mon..Wed
        val b = event(at(weekDays[1], 0), at(weekDays[4], 0), allDay = true, id = 2L) // Tue..Thu
        val spans = layoutAllDay(listOf(a, b), weekDays, zone)
        assertThat(spans.map { it.lane }.toSet()).isEqualTo(setOf(0, 1))
    }

    @Test
    fun `disjoint all-day spans reuse one lane`() {
        val a = event(at(weekDays[0], 0), at(weekDays[1], 0), allDay = true, id = 1L) // Mon
        val b = event(at(weekDays[3], 0), at(weekDays[4], 0), allDay = true, id = 2L) // Thu
        val spans = layoutAllDay(listOf(a, b), weekDays, zone)
        assertThat(spans.all { it.lane == 0 }).isTrue()
    }

    private fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
}
