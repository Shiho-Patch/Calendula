package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import java.util.Locale

/** Which month chips carry a start time, and how it reads (#219).
 *  How it is *set* against the title lives in `EventTimeStyleTest`. */
class MonthChipTimeTest {

    private val zone = TimeZone.UTC
    private val locale = Locale.UK
    private val day = LocalDate(2026, Month.SEPTEMBER, 2)

    private fun timed(hour: Int, minute: Int) = EventInstance(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "Standup",
        start = day.atTime(hour, minute).toInstant(zone),
        end = day.atTime(hour + 1, minute).toInstant(zone),
        isAllDay = false,
        color = 0,
        location = null,
    )

    private fun allDay() = EventInstance(
        instanceId = 2L,
        eventId = 2L,
        calendarId = 1L,
        title = "Holiday",
        start = day.atTime(0, 0).toInstant(zone),
        end = day.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(zone),
        isAllDay = true,
        color = 0,
        location = null,
    )

    @Test
    fun `a timed chip shows its start in the 24-hour convention`() {
        val time = monthChipTime(timed(9, 5), continuesLeft = false, zone, is24Hour = true, locale)
        assertThat(time).isEqualTo("09:05")
    }

    @Test
    fun `the 12-hour setting is honoured`() {
        val time = monthChipTime(timed(14, 30), continuesLeft = false, zone, is24Hour = false, locale)
        assertThat(time).isEqualTo("2:30 pm")
    }

    @Test
    fun `an all-day chip never shows a time`() {
        val time = monthChipTime(allDay(), continuesLeft = false, zone, is24Hour = true, locale)
        assertThat(time).isNull()
    }

    @Test
    fun `a bar carried in from the previous week shows none either`() {
        // Its start is not in this segment, so printing it would put a time on a
        // row the event does not begin on.
        val time = monthChipTime(timed(9, 5), continuesLeft = true, zone, is24Hour = true, locale)
        assertThat(time).isNull()
    }
}
