package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class AgendaTimeLabelTest {

    private val zone = TimeZone.UTC

    private fun at(y: Int, mo: Int, d: Int, h: Int = 0, min: Int = 0): Instant =
        LocalDateTime(y, mo, d, h, min).toInstant(zone)

    private fun event(start: Instant, end: Instant, isAllDay: Boolean = false) = EventInstance(
        instanceId = 1,
        eventId = 1,
        calendarId = 1,
        title = "e",
        start = start,
        end = end,
        isAllDay = isAllDay,
        color = 0,
        location = null,
    )

    private fun labelOn(y: Int, mo: Int, d: Int, event: EventInstance) =
        agendaTimeLabel(event, LocalDate(y, mo, d), zone)

    @Test
    fun `a single-day timed event is a start-end range`() {
        val e = event(at(2026, 7, 2, 12, 0), at(2026, 7, 2, 13, 0))

        assertThat(labelOn(2026, 7, 2, e))
            .isEqualTo(AgendaTimeLabel.Range(e.start, e.end))
    }

    @Test
    fun `a single-day all-day event is all-day`() {
        val e = event(at(2026, 7, 2), at(2026, 7, 3), isAllDay = true)

        assertThat(labelOn(2026, 7, 2, e)).isEqualTo(AgendaTimeLabel.AllDay)
    }

    @Test
    fun `a multi-day timed event names the start, middle, and end days`() {
        val e = event(at(2026, 7, 1, 14, 0), at(2026, 7, 4, 10, 0))

        assertThat(labelOn(2026, 7, 1, e)).isEqualTo(AgendaTimeLabel.Starts(e.start))
        assertThat(labelOn(2026, 7, 2, e)).isEqualTo(AgendaTimeLabel.AllDay)
        assertThat(labelOn(2026, 7, 3, e)).isEqualTo(AgendaTimeLabel.AllDay)
        assertThat(labelOn(2026, 7, 4, e)).isEqualTo(AgendaTimeLabel.Ends(e.end))
    }

    @Test
    fun `a multi-day all-day event is all-day on every day`() {
        val e = event(at(2026, 7, 2), at(2026, 7, 5), isAllDay = true)

        assertThat(labelOn(2026, 7, 2, e)).isEqualTo(AgendaTimeLabel.AllDay)
        assertThat(labelOn(2026, 7, 3, e)).isEqualTo(AgendaTimeLabel.AllDay)
        assertThat(labelOn(2026, 7, 4, e)).isEqualTo(AgendaTimeLabel.AllDay)
    }

    @Test
    fun `an event begun before the shown day is not labelled as starting`() {
        // Runs 29 Jun 09:00 → 2 Jul 09:00; on 1 Jul it is mid-span, on 2 Jul it ends.
        val e = event(at(2026, 6, 29, 9, 0), at(2026, 7, 2, 9, 0))

        assertThat(labelOn(2026, 7, 1, e)).isEqualTo(AgendaTimeLabel.AllDay)
        assertThat(labelOn(2026, 7, 2, e)).isEqualTo(AgendaTimeLabel.Ends(e.end))
    }
}
