package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Test
import java.time.ZoneId

class EventWriteMapperTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private fun form(
        isAllDay: Boolean = false,
        start: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 30)),
    ): EventForm = EventForm(calendarId = 1L, isAllDay = isAllDay, start = start, end = end)

    @Test
    fun `timed event resolves wall clock in the given zone`() {
        val times = form().toWriteTimes(berlin)
        // 2026-06-11 is CEST (UTC+2): 10:00 local == 08:00Z.
        assertThat(times.dtStartMillis).isEqualTo(1_781_164_800_000L)
        assertThat(times.dtEndMillis - times.dtStartMillis).isEqualTo(90L * 60L * 1000L)
        assertThat(times.timezone).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `all-day event lives at UTC midnights with exclusive end`() {
        val times = form(isAllDay = true).toWriteTimes(berlin)
        assertThat(times.timezone).isEqualTo("UTC")
        assertThat(times.dtStartMillis % 86_400_000L).isEqualTo(0L)
        // Single-day all-day event: DTEND is the NEXT UTC midnight.
        assertThat(times.dtEndMillis - times.dtStartMillis).isEqualTo(86_400_000L)
    }

    @Test
    fun `availability maps to the provider constants`() {
        assertThat(Availability.Busy.toProviderValue())
            .isEqualTo(CalendarContract.Events.AVAILABILITY_BUSY)
        assertThat(Availability.Free.toProviderValue())
            .isEqualTo(CalendarContract.Events.AVAILABILITY_FREE)
        assertThat(Availability.Tentative.toProviderValue())
            .isEqualTo(CalendarContract.Events.AVAILABILITY_TENTATIVE)
    }

    @Test
    fun `access level maps to the provider constants`() {
        assertThat(AccessLevel.Default.toProviderValue())
            .isEqualTo(CalendarContract.Events.ACCESS_DEFAULT)
        assertThat(AccessLevel.Private.toProviderValue())
            .isEqualTo(CalendarContract.Events.ACCESS_PRIVATE)
        assertThat(AccessLevel.Confidential.toProviderValue())
            .isEqualTo(CalendarContract.Events.ACCESS_CONFIDENTIAL)
        assertThat(AccessLevel.Public.toProviderValue())
            .isEqualTo(CalendarContract.Events.ACCESS_PUBLIC)
    }

    @Test
    fun `multi-day all-day event spans every covered day`() {
        val times = form(
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 13), LocalTime(0, 0)),
        ).toWriteTimes(berlin)
        // 11th, 12th, 13th inclusive = 3 days.
        assertThat(times.dtEndMillis - times.dtStartMillis).isEqualTo(3L * 86_400_000L)
    }
}
