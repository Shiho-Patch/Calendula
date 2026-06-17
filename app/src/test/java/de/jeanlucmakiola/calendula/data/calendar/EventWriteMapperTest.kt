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

    @Test
    fun `truncation cutoff is the end of the previous local day`() {
        // June 9 2026 15:30Z == 17:30 in Berlin. The cutoff must be
        // June 8 23:59:59 Berlin == June 8 21:59:59Z.
        assertThat(previousLocalDayEndUtcMillis(1_781_019_000_000L, berlin))
            .isEqualTo(1_780_955_999_000L)
        // All-day series live in UTC: the cutoff for a June 9 UTC-midnight
        // occurrence is June 8 23:59:59Z.
        assertThat(previousLocalDayEndUtcMillis(1_780_963_200_000L, ZoneId.of("UTC")))
            .isEqualTo(1_780_963_199_000L)
    }

    @Test
    fun `duration renders seconds for timed and days for all-day events`() {
        assertThat(form().toWriteTimes(berlin).toRfc2445Duration(isAllDay = false))
            .isEqualTo("P5400S")
        assertThat(form(isAllDay = true).toWriteTimes(berlin).toRfc2445Duration(isAllDay = true))
            .isEqualTo("P1D")
    }

    // --- buildEventUpdateValues (dirty-checked partial update) ---

    private val seriesStart = 1_700_000_000_000L

    private fun update(original: EventForm, updated: EventForm): Map<String, Any?> =
        buildEventUpdateValues(original, updated, seriesStart, berlin)

    @Test
    fun `pristine form produces no values`() {
        val original = form()
        assertThat(update(original, original.copy())).isEmpty()
    }

    @Test
    fun `text-only edit writes just the changed columns`() {
        val original = form()
        val values = update(original, original.copy(title = "New", description = "Body"))
        assertThat(values).containsExactly(
            CalendarContract.Events.TITLE, "New",
            CalendarContract.Events.DESCRIPTION, "Body",
        )
    }

    @Test
    fun `clearing location writes an explicit null`() {
        val original = form().copy(location = "Berlin")
        val values = update(original, original.copy(location = " "))
        assertThat(values).containsExactly(CalendarContract.Events.EVENT_LOCATION, null)
    }

    @Test
    fun `time edit on a one-off event writes absolute times and clears recurrence columns`() {
        val original = form()
        val updated = original.copy(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)),
        )
        val values = update(original, updated)
        // 2026-06-11 11:00 CEST == 09:00Z.
        assertThat(values[CalendarContract.Events.DTSTART])
            .isEqualTo(1_781_164_800_000L + 3_600_000L)
        assertThat(values[CalendarContract.Events.DTEND])
            .isEqualTo(1_781_164_800_000L + 2L * 3_600_000L)
        assertThat(values).containsEntry(CalendarContract.Events.RRULE, null)
        assertThat(values).containsEntry(CalendarContract.Events.DURATION, null)
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Europe/Berlin")
        assertThat(values[CalendarContract.Events.ALL_DAY]).isEqualTo(0)
    }

    @Test
    fun `time edit on a recurring event moves the series start by the same delta`() {
        val original = form().copy(rrule = "FREQ=WEEKLY")
        val updated = original.copy(
            // Pushed one hour later than the displayed occurrence.
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 30)),
        )
        val values = update(original, updated)
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(seriesStart + 3_600_000L)
        assertThat(values).containsEntry(CalendarContract.Events.DTEND, null)
        assertThat(values[CalendarContract.Events.RRULE]).isEqualTo("FREQ=WEEKLY")
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P5400S")
    }

    @Test
    fun `adding a recurrence keeps the times and writes rule plus duration`() {
        val original = form()
        val values = update(original, original.copy(rrule = "FREQ=DAILY;COUNT=5"))
        // The event was one-off, so the row's DTSTART is the occurrence start
        // and a zero delta keeps it in place.
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(seriesStart)
        assertThat(values[CalendarContract.Events.RRULE]).isEqualTo("FREQ=DAILY;COUNT=5")
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P5400S")
        assertThat(values).containsEntry(CalendarContract.Events.DTEND, null)
    }

    @Test
    fun `removing the recurrence writes absolute occurrence times and clears the rule`() {
        val original = form().copy(rrule = "FREQ=WEEKLY")
        val values = update(original, original.copy(rrule = null))
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_164_800_000L)
        assertThat(values[CalendarContract.Events.DTEND])
            .isEqualTo(1_781_164_800_000L + 5_400_000L)
        assertThat(values).containsEntry(CalendarContract.Events.RRULE, null)
        assertThat(values).containsEntry(CalendarContract.Events.DURATION, null)
    }

    @Test
    fun `reminder-only changes touch no event columns`() {
        val original = form()
        assertThat(update(original, original.copy(reminders = listOf(10)))).isEmpty()
    }

    // --- buildOccurrenceExceptionValues ("edit only this event") ---

    @Test
    fun `occurrence exception carries absolute times and the original instance`() {
        val values = buildOccurrenceExceptionValues(
            form = form().copy(title = "Moved", location = "Berlin"),
            originalInstanceMillis = 1_700_000_000_000L,
            zone = berlin,
        )
        assertThat(values[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
            .isEqualTo(1_700_000_000_000L)
        assertThat(values[CalendarContract.Events.TITLE]).isEqualTo("Moved")
        assertThat(values[CalendarContract.Events.EVENT_LOCATION]).isEqualTo("Berlin")
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_164_800_000L)
        assertThat(values[CalendarContract.Events.DTEND])
            .isEqualTo(1_781_164_800_000L + 5_400_000L)
        // A single occurrence never carries its own rule.
        assertThat(values).doesNotContainKey(CalendarContract.Events.RRULE)
        assertThat(values).doesNotContainKey(CalendarContract.Events.DURATION)
    }

    @Test
    fun `occurrence exception clears empty optionals explicitly`() {
        // The provider clones the parent row, so a blank field must be an
        // explicit NULL or the parent's value would survive.
        val values = buildOccurrenceExceptionValues(
            form = form(),
            originalInstanceMillis = 0L,
            zone = berlin,
        )
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_LOCATION, null)
        assertThat(values).containsEntry(CalendarContract.Events.DESCRIPTION, null)
    }

    // --- per-event colour ---

    @Test
    fun `palette colour writes only the key, never a raw colour`() {
        assertThat(eventColorColumns(colorKey = "5", color = 0xFF33B679.toInt()))
            .containsExactly(CalendarContract.Events.EVENT_COLOR_KEY, "5")
    }

    @Test
    fun `raw colour writes the colour and clears any key`() {
        assertThat(eventColorColumns(colorKey = null, color = 0xFF8E24AA.toInt()))
            .containsExactly(
                CalendarContract.Events.EVENT_COLOR_KEY, null,
                CalendarContract.Events.EVENT_COLOR, 0xFF8E24AA.toInt(),
            )
    }

    @Test
    fun `no colour clears both columns so the event inherits its calendar`() {
        assertThat(eventColorColumns(colorKey = null, color = null))
            .containsExactly(
                CalendarContract.Events.EVENT_COLOR_KEY, null,
                CalendarContract.Events.EVENT_COLOR, null,
            )
    }

    @Test
    fun `setting a palette colour on update writes just the key`() {
        val original = form()
        val values = update(original, original.copy(colorKey = "3", color = 0xFFF6BF26.toInt()))
        assertThat(values).containsExactly(CalendarContract.Events.EVENT_COLOR_KEY, "3")
    }

    @Test
    fun `setting a raw colour on update writes the colour and a null key`() {
        val original = form()
        val values = update(original, original.copy(color = 0xFF039BE5.toInt()))
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR, 0xFF039BE5.toInt())
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR_KEY, null)
    }

    @Test
    fun `clearing a colour on update writes explicit nulls`() {
        val original = form().copy(color = 0xFFD50000.toInt())
        val values = update(original, original.copy(color = null))
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR, null)
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR_KEY, null)
    }

    @Test
    fun `unchanged colour writes no colour columns`() {
        val original = form().copy(colorKey = "7", color = 0xFF3F51B5.toInt())
        val values = update(original, original.copy(title = "Renamed"))
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_COLOR_KEY)
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_COLOR)
    }

    @Test
    fun `occurrence exception carries the palette key`() {
        val values = buildOccurrenceExceptionValues(
            form = form().copy(colorKey = "2", color = 0xFFE67C00.toInt()),
            originalInstanceMillis = 0L,
            zone = berlin,
        )
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR_KEY, "2")
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_COLOR)
    }

    @Test
    fun `occurrence exception with no colour clears both columns`() {
        val values = buildOccurrenceExceptionValues(
            form = form(),
            originalInstanceMillis = 0L,
            zone = berlin,
        )
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR, null)
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_COLOR_KEY, null)
    }
}
