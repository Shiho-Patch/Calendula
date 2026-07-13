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
        // The occurrence's length travels as DURATION, never DTEND — the provider
        // rejects DTEND on an exception ("Exceptions can't overwrite dtend") and
        // derives the end from DTSTART + DURATION, clearing the inherited RRULE.
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P5400S")
        assertThat(values).doesNotContainKey(CalendarContract.Events.DTEND)
        assertThat(values).doesNotContainKey(CalendarContract.Events.RRULE)
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

    // --- buildMovedMasterValues (calendar move: verbatim series copy) ---

    private fun masterSnapshot(
        isAllDay: Boolean = false,
        dtStartMillis: Long = 1_781_164_800_000L,
        dtEndMillis: Long? = 1_781_164_800_000L + 5_400_000L,
        duration: String? = null,
        rrule: String? = null,
        rdate: String? = null,
        exdate: String? = null,
    ) = MasterEventSnapshot(
        title = "Standup",
        isAllDay = isAllDay,
        dtStartMillis = dtStartMillis,
        dtEndMillis = dtEndMillis,
        duration = duration,
        rrule = rrule,
        rdate = rdate,
        exdate = exdate,
        timezone = "Europe/Berlin",
        availability = CalendarContract.Events.AVAILABILITY_BUSY,
        accessLevel = CalendarContract.Events.ACCESS_DEFAULT,
        status = CalendarContract.Events.STATUS_CONFIRMED,
        location = "Room 1",
        description = "",
    )

    @Test
    fun `moved one-off carries target calendar, uid and DTEND but no recurrence`() {
        val values = buildMovedMasterValues(masterSnapshot(), targetCalendarId = 9L, uid = "u@calendula")
        assertThat(values[CalendarContract.Events.CALENDAR_ID]).isEqualTo(9L)
        assertThat(values[CalendarContract.Events.UID_2445]).isEqualTo("u@calendula")
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_164_800_000L)
        assertThat(values[CalendarContract.Events.DTEND]).isEqualTo(1_781_164_800_000L + 5_400_000L)
        assertThat(values).doesNotContainKey(CalendarContract.Events.RRULE)
        assertThat(values).doesNotContainKey(CalendarContract.Events.DURATION)
        // Empty description clears explicitly; a raw/keyed colour is never copied
        // (may be invalid on the target account — the copy inherits its colour).
        assertThat(values).containsEntry(CalendarContract.Events.DESCRIPTION, null)
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_COLOR)
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_COLOR_KEY)
    }

    @Test
    fun `moved series preserves the recurrence skeleton as RRULE plus DURATION`() {
        val values = buildMovedMasterValues(
            masterSnapshot(dtEndMillis = null, duration = "P5400S", rrule = "FREQ=WEEKLY", exdate = "20260618T080000Z"),
            targetCalendarId = 9L,
            uid = "u@calendula",
        )
        assertThat(values[CalendarContract.Events.RRULE]).isEqualTo("FREQ=WEEKLY")
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P5400S")
        assertThat(values[CalendarContract.Events.EXDATE]).isEqualTo("20260618T080000Z")
        // Recurring rows never carry DTEND (the provider's invariant).
        assertThat(values).doesNotContainKey(CalendarContract.Events.DTEND)
    }

    @Test
    fun `moved series without a stored duration derives it from DTEND`() {
        val values = buildMovedMasterValues(
            masterSnapshot(duration = null, rrule = "FREQ=DAILY"),
            targetCalendarId = 9L,
            uid = "u@calendula",
        )
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P5400S")
    }

    // --- buildCopiedExceptionValues (calendar move: exception replay) ---

    private fun exceptionSnapshot(
        isCancelled: Boolean = false,
        duration: String? = null,
        // Occurrence starts one hour into the day and runs 30 minutes.
        dtEndMillis: Long? = 1_781_164_800_000L + 3_600_000L + 1_800_000L,
    ) = ExceptionRowSnapshot(
        exceptionEventId = 42L,
        originalInstanceMillis = 1_781_164_800_000L,
        isCancelled = isCancelled,
        status = if (isCancelled) CalendarContract.Events.STATUS_CANCELED else CalendarContract.Events.STATUS_CONFIRMED,
        title = "Moved occurrence",
        isAllDay = false,
        dtStartMillis = 1_781_164_800_000L + 3_600_000L,
        dtEndMillis = dtEndMillis,
        duration = duration,
        timezone = "Europe/Berlin",
        availability = CalendarContract.Events.AVAILABILITY_BUSY,
        accessLevel = CalendarContract.Events.ACCESS_DEFAULT,
        location = "",
        description = "Notes",
    )

    @Test
    fun `copied modified occurrence carries the original instance and DURATION not DTEND`() {
        val values = buildCopiedExceptionValues(exceptionSnapshot())
        assertThat(values[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
            .isEqualTo(1_781_164_800_000L)
        assertThat(values[CalendarContract.Events.TITLE]).isEqualTo("Moved occurrence")
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_164_800_000L + 3_600_000L)
        // 30-minute occurrence derived from DTEND, written as DURATION only.
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P1800S")
        assertThat(values).doesNotContainKey(CalendarContract.Events.DTEND)
        assertThat(values).containsEntry(CalendarContract.Events.EVENT_LOCATION, null)
    }

    @Test
    fun `copied modified occurrence keeps a stored duration verbatim`() {
        val values = buildCopiedExceptionValues(exceptionSnapshot(duration = "P900S", dtEndMillis = null))
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P900S")
    }
}
