package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.spansMultipleDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class GroupAgendaDaysTest {

    private val zone = TimeZone.UTC
    private val anchor = LocalDate(2026, 7, 1)
    // A wide window so clamping to the window end is out of the way unless tested.
    private val windowEnd = LocalDate(2026, 7, 31)

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, min: Int = 0): Instant =
        LocalDateTime(y, mo, d, h, min).toInstant(zone)

    private fun event(
        id: Long,
        title: String,
        start: Instant,
        end: Instant,
        isAllDay: Boolean = false,
    ) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1,
        title = title,
        start = start,
        end = end,
        isAllDay = isAllDay,
        color = 0,
        location = null,
    )

    private fun days(instances: List<EventInstance>) =
        groupAgendaDays(anchor, windowEnd, instances, zone)

    @Test
    fun `a timed multi-day event lists on every day it spans`() {
        val e = event(1, "trip", at(2026, 7, 1, 14, 0), at(2026, 7, 3, 10, 0))

        val result = days(listOf(e))

        assertThat(result.map { it.date }).containsExactly(
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 2),
            LocalDate(2026, 7, 3),
        ).inOrder()
        result.forEach { assertThat(it.events).containsExactly(e) }
    }

    @Test
    fun `an all-day multi-day event stops before its exclusive next-midnight end`() {
        // 1–3 July inclusive: end is the exclusive midnight opening 4 July.
        val e = event(1, "holiday", at(2026, 7, 1), at(2026, 7, 4), isAllDay = true)

        val result = days(listOf(e))

        assertThat(result.map { it.date }).containsExactly(
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 2),
            LocalDate(2026, 7, 3),
        ).inOrder()
    }

    @Test
    fun `a single-day event lists exactly once`() {
        val e = event(1, "lunch", at(2026, 7, 2, 12, 0), at(2026, 7, 2, 13, 0))

        assertThat(days(listOf(e)).map { it.date })
            .containsExactly(LocalDate(2026, 7, 2))
    }

    @Test
    fun `an event ending exactly at midnight does not reach the next day`() {
        val e = event(1, "late", at(2026, 7, 1, 22, 0), at(2026, 7, 2, 0, 0))

        assertThat(days(listOf(e)).map { it.date })
            .containsExactly(LocalDate(2026, 7, 1))
    }

    @Test
    fun `an event begun before the anchor is clamped to the anchor day`() {
        val e = event(1, "ongoing", at(2026, 6, 29, 9, 0), at(2026, 7, 2, 9, 0))

        assertThat(days(listOf(e)).map { it.date }).containsExactly(
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 2),
        ).inOrder()
    }

    @Test
    fun `an event running past the window is clamped to the last visible day`() {
        val narrowEnd = LocalDate(2026, 7, 2)
        val e = event(1, "long", at(2026, 7, 1, 9, 0), at(2026, 7, 5, 9, 0))

        val result = groupAgendaDays(anchor, narrowEnd, listOf(e), zone)

        assertThat(result.map { it.date }).containsExactly(
            LocalDate(2026, 7, 1),
            LocalDate(2026, 7, 2),
        ).inOrder()
    }

    // All-day events are stored at UTC midnights; resolving them in a device
    // zone east of UTC would leak them onto the following day (see spanLastDay).
    private val berlin = TimeZone.of("Europe/Berlin")

    private fun utcMidnight(y: Int, mo: Int, d: Int): Instant =
        LocalDateTime(y, mo, d, 0, 0).toInstant(TimeZone.UTC)

    @Test
    fun `a single-day all-day event does not leak onto the next day in an eastern zone`() {
        val e = event(1, "birthday", utcMidnight(2026, 7, 2), utcMidnight(2026, 7, 3), isAllDay = true)

        val result = groupAgendaDays(anchor, windowEnd, listOf(e), berlin)

        assertThat(result.map { it.date }).containsExactly(LocalDate(2026, 7, 2))
        assertThat(e.spansMultipleDays(berlin)).isFalse()
    }

    @Test
    fun `an all-day multi-day event spans its true days in an eastern zone`() {
        // 2–4 July inclusive: exclusive end is the UTC midnight opening 5 July.
        val e = event(1, "holiday", utcMidnight(2026, 7, 2), utcMidnight(2026, 7, 5), isAllDay = true)

        val result = groupAgendaDays(anchor, windowEnd, listOf(e), berlin)

        assertThat(result.map { it.date }).containsExactly(
            LocalDate(2026, 7, 2),
            LocalDate(2026, 7, 3),
            LocalDate(2026, 7, 4),
        ).inOrder()
    }

    @Test
    fun `yesterday's all-day event does not surface under the anchor day in an eastern zone`() {
        // The agenda window starts at the anchor's local midnight, which in Berlin is
        // 22:00 UTC the day before — so an all-day event that ended yesterday (whose
        // exclusive UTC-midnight end sits at that instant) is still returned by the
        // provider. It must not be clamped onto the anchor's "today" section (#65).
        val jul19 = LocalDate(2026, 7, 19)
        val e = event(1, "birthday", utcMidnight(2026, 7, 18), utcMidnight(2026, 7, 19), isAllDay = true)

        val result = groupAgendaDays(jul19, LocalDate(2026, 8, 18), listOf(e), berlin)

        assertThat(result).isEmpty()
    }

    @Test
    fun `an all-day event that falls entirely past the window end is dropped`() {
        // The same guard, at the far edge: an instance whose true days are all beyond
        // windowEnd occupies no visible day and must not be clamped onto the last one.
        val e = event(1, "birthday", utcMidnight(2026, 7, 3), utcMidnight(2026, 7, 4), isAllDay = true)

        val result = groupAgendaDays(anchor, LocalDate(2026, 7, 2), listOf(e), berlin)

        assertThat(result).isEmpty()
    }

    @Test
    fun `within a day all-day events sort before timed ones`() {
        val allDay = event(1, "birthday", at(2026, 7, 1), at(2026, 7, 2), isAllDay = true)
        val timed = event(2, "call", at(2026, 7, 1, 9, 0), at(2026, 7, 1, 10, 0))

        val dayOne = days(listOf(timed, allDay)).first { it.date == anchor }

        assertThat(dayOne.events).containsExactly(allDay, timed).inOrder()
    }
}
