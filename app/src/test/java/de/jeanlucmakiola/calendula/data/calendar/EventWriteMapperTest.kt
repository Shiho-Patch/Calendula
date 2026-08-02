package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.realignRecurrence
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
        timezone: String? = null,
    ): EventForm = EventForm(
        calendarId = 1L,
        isAllDay = isAllDay,
        start = start,
        end = end,
        timezone = timezone,
    )

    @Test
    fun `timed event resolves wall clock in the given zone`() {
        val times = form().toWriteTimes(berlin)
        // 2026-06-11 is CEST (UTC+2): 10:00 local == 08:00Z.
        assertThat(times.dtStartMillis).isEqualTo(1_781_164_800_000L)
        assertThat(times.dtEndMillis - times.dtStartMillis).isEqualTo(90L * 60L * 1000L)
        assertThat(times.timezone).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `pinned zone wins over the device zone`() {
        val times = form(timezone = "America/New_York").toWriteTimes(berlin)
        // 10:00 in New York (EDT, UTC-4) == 14:00Z, not 08:00Z as Berlin would give.
        assertThat(times.timezone).isEqualTo("America/New_York")
        assertThat(times.dtStartMillis).isEqualTo(1_781_186_400_000L)
    }

    @Test
    fun `an unparseable pinned zone falls back to the device zone`() {
        val times = form(timezone = "Mars/Olympus_Mons").toWriteTimes(berlin)
        assertThat(times.timezone).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `all-day ignores a pinned zone and stays UTC`() {
        val times = form(isAllDay = true, timezone = "America/New_York").toWriteTimes(berlin)
        assertThat(times.timezone).isEqualTo("UTC")
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

    private fun update(
        original: EventForm,
        updated: EventForm,
        series: Long = seriesStart,
    ): Map<String, Any?> = buildEventUpdateValues(original, updated, series, berlin)

    /** The instant [local] names in [zoneId], as the provider would store it. */
    private fun instantAt(local: String, zoneId: String): Long =
        java.time.LocalDateTime.parse(local)
            .atZone(java.time.ZoneId.of(zoneId))
            .toInstant()
            .toEpochMilli()

    @Test
    fun `pristine form produces no values`() {
        val original = form()
        assertThat(update(original, original.copy())).isEmpty()
    }

    @Test
    fun `editing the time of a pinned event keeps its zone`() {
        // The regression this guards: the update used to stamp the device zone
        // over the event's own, silently un-anchoring a foreign-zone event so it
        // stopped tracking that zone across DST.
        val original = form(timezone = "America/New_York")
        val values = update(original, original.copy(title = "Standup", start = original.start))
        assertThat(values).doesNotContainKey(CalendarContract.Events.EVENT_TIMEZONE)

        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 30)),
        )
        assertThat(update(original, moved)[CalendarContract.Events.EVENT_TIMEZONE])
            .isEqualTo("America/New_York")
    }

    @Test
    fun `changing only the zone still moves the event`() {
        // Same wall-clock, different zone: the instant moves, so DTSTART must be
        // rewritten even though start/end compare equal.
        val original = form()
        val values = update(original, original.copy(timezone = "America/New_York"))
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("America/New_York")
        // 10:00 Berlin (08:00Z) -> 10:00 New York (14:00Z): six hours later.
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_186_400_000L)
    }

    @Test
    fun `unpinning back to the device zone rewrites the times`() {
        val original = form(timezone = "America/New_York")
        val values = update(original, original.copy(timezone = null))
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Europe/Berlin")
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_781_164_800_000L)
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
    fun `pinning a recurring event to another zone keeps the series wall clock`() {
        // The regression this guards: the series DTSTART used to move by a
        // millisecond delta measured at the *edited occurrence*. Here the series
        // anchor sits in January (Berlin CET, +1) and the edited occurrence in
        // July (Berlin CEST, +2), so the July delta is an hour off for January —
        // the whole series would have drifted to 10:00 Tokyo.
        val series = instantAt("2026-01-07T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY")

        val values = update(original, original.copy(timezone = "Asia/Tokyo"), series)

        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Asia/Tokyo")
        // The anchor still reads 09:00 — now 09:00 in Tokyo, not 10:00.
        assertThat(values[CalendarContract.Events.DTSTART])
            .isEqualTo(instantAt("2026-01-07T09:00", "Asia/Tokyo"))
    }

    @Test
    fun `a time edit moves the series anchor in wall clock across a DST boundary`() {
        // Anchor in winter, edited occurrence in summer: pushing the occurrence
        // one hour later must leave the anchor at 10:00 winter time, not at an
        // instant that re-reads as 11:00 once the offset differs.
        val series = instantAt("2026-01-07T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY")
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(10, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(11, 0)),
        )

        assertThat(update(original, moved, series)[CalendarContract.Events.DTSTART])
            .isEqualTo(instantAt("2026-01-07T10:00", "Europe/Berlin"))
    }

    @Test
    fun `moving a recurring occurrence to another day shifts the anchor by whole days`() {
        val series = instantAt("2026-01-07T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 1, 7), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 1, 7), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY")
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 1, 9), LocalTime(14, 30)),
            end = LocalDateTime(LocalDate(2026, 1, 9), LocalTime(15, 30)),
        )

        assertThat(update(original, moved, series)[CalendarContract.Events.DTSTART])
            .isEqualTo(instantAt("2026-01-09T14:30", "Europe/Berlin"))
    }

    @Test
    fun `a weekday move must carry the rule with the anchor, or the series stays put`() {
        // The anchor moves by the same shift as the occurrence, while RRULE is
        // written verbatim: without realignRecurrence nothing moves.
        val series = instantAt("2026-01-05T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 6, 8), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 8), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY;BYDAY=MO")
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 6, 10), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 10), LocalTime(10, 0)),
            rrule = realignRecurrence("FREQ=WEEKLY;BYDAY=MO", LocalDate(2026, 6, 8), LocalDate(2026, 6, 10)),
        )

        val values = update(original, moved, series)

        val anchor = java.time.Instant.ofEpochMilli(values[CalendarContract.Events.DTSTART] as Long)
            .atZone(java.time.ZoneId.of("Europe/Berlin"))
        assertThat(anchor.dayOfWeek).isEqualTo(java.time.DayOfWeek.WEDNESDAY)
        assertThat(values[CalendarContract.Events.RRULE]).isEqualTo("FREQ=WEEKLY;BYDAY=WE")
    }

    @Test
    fun `a recurring move keeps the series' length across a DST boundary`() {
        // The occurrence being dragged is in CET; the series anchor is in CEST.
        val series = instantAt("2026-07-15T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 1, 7), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 1, 7), LocalTime(10, 30)),
        ).copy(rrule = "FREQ=WEEKLY")
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 1, 8), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 1, 8), LocalTime(10, 30)),
        )

        assertThat(update(original, moved, series)[CalendarContract.Events.DURATION])
            .isEqualTo("P5400S")
    }

    @Test
    fun `shifting a one-off event and back again restores the original values`() {
        val original = form()
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(14, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(15, 0)),
        )

        val forward = update(original, moved)
        val back = update(moved, original)

        assertThat(back[CalendarContract.Events.DTSTART])
            .isEqualTo(original.toWriteTimes(berlin).dtStartMillis)
        assertThat(back[CalendarContract.Events.DTEND])
            .isEqualTo(original.toWriteTimes(berlin).dtEndMillis)
        assertThat(forward[CalendarContract.Events.DTSTART])
            .isNotEqualTo(back[CalendarContract.Events.DTSTART])
    }

    @Test
    fun `undoing a whole-series move lands the anchor back where it started`() {
        // What makes undo safe for "all events": the shift is applied to the
        // anchor the provider currently holds, so re-issuing it with the two
        // forms swapped applies the exact inverse to the moved anchor.
        val series = instantAt("2026-01-07T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY")
        val moved = original.copy(
            start = LocalDateTime(LocalDate(2026, 7, 17), LocalTime(14, 30)),
            end = LocalDateTime(LocalDate(2026, 7, 17), LocalTime(15, 30)),
        )

        val movedAnchor = update(original, moved, series)[CalendarContract.Events.DTSTART] as Long
        assertThat(movedAnchor).isNotEqualTo(series)

        val restored = update(moved, original, movedAnchor)
        assertThat(restored[CalendarContract.Events.DTSTART]).isEqualTo(series)
        assertThat(restored[CalendarContract.Events.DURATION]).isEqualTo("P3600S")
    }

    @Test
    fun `switching a recurring event to all-day anchors the series on a UTC midnight`() {
        val series = instantAt("2026-01-07T09:00", "Europe/Berlin")
        val original = form(
            start = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 7, 15), LocalTime(10, 0)),
        ).copy(rrule = "FREQ=WEEKLY")

        val values = update(original, original.copy(isAllDay = true), series)

        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("UTC")
        val dtStart = values[CalendarContract.Events.DTSTART] as Long
        assertThat(dtStart % (24L * 60 * 60 * 1000)).isEqualTo(0L)
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

    // --- buildOccurrenceCancelValues ("delete only this event") ---

    @Test
    fun `occurrence cancel anchors a single instance and cancels only it`() {
        val values = buildOccurrenceCancelValues(
            originalInstanceMillis = 1_700_000_000_000L,
            dtStartMillis = 1_700_000_000_000L,
            duration = "P3600S",
            timezone = "Europe/Berlin",
            allDay = 0,
        )
        assertThat(values[CalendarContract.Events.ORIGINAL_INSTANCE_TIME])
            .isEqualTo(1_700_000_000_000L)
        // DTSTART + DURATION make the provider derive a single instance and drop
        // the inherited RRULE, so only this occurrence is cancelled — not the
        // whole series (#47). DTEND is never sent (the provider rejects it).
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_700_000_000_000L)
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("P3600S")
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Europe/Berlin")
        assertThat(values[CalendarContract.Events.STATUS])
            .isEqualTo(CalendarContract.Events.STATUS_CANCELED)
        assertThat(values).doesNotContainKey(CalendarContract.Events.DTEND)
        assertThat(values).doesNotContainKey(CalendarContract.Events.RRULE)
    }

    @Test
    fun `all-day occurrence cancel keeps the all-day flag and utc zone`() {
        val values = buildOccurrenceCancelValues(
            originalInstanceMillis = 1_700_000_000_000L,
            dtStartMillis = 1_700_000_000_000L,
            duration = "P1D",
            timezone = "UTC",
            allDay = 1,
        )
        assertThat(values[CalendarContract.Events.ALL_DAY]).isEqualTo(1)
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("UTC")
        assertThat(values[CalendarContract.Events.STATUS])
            .isEqualTo(CalendarContract.Events.STATUS_CANCELED)
    }

    // --- buildOccurrenceExdateValues ("delete only this event", no _sync_id) ---

    @Test
    fun `exdate drop excludes the occurrence and rewrites the recurrence set`() {
        // 2026-07-15T08:00:00Z.
        val values = buildOccurrenceExdateValues(
            existingExdate = null,
            occurrenceMillis = 1_784_102_400_000L,
            dtStartMillis = 1_783_929_600_000L,
            rrule = "FREQ=DAILY;COUNT=5",
            duration = "PT1H",
            timezone = "Europe/Berlin",
            allDay = 0,
        )
        assertThat(values[CalendarContract.Events.EXDATE]).isEqualTo("20260715T080000Z")
        // The whole time/recurrence set rides along: an EXDATE-only update is not
        // treated as a recurrence change, so the provider would leave the expanded
        // instances (and the occurrence) in place. DTSTART alone is worse — it
        // makes the provider recompute lastDate as a single instance and collapse
        // the series to its first occurrence.
        assertThat(values[CalendarContract.Events.DTSTART]).isEqualTo(1_783_929_600_000L)
        assertThat(values[CalendarContract.Events.RRULE]).isEqualTo("FREQ=DAILY;COUNT=5")
        assertThat(values[CalendarContract.Events.DURATION]).isEqualTo("PT1H")
        assertThat(values[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `exdate drop appends to an existing exdate list`() {
        val values = buildOccurrenceExdateValues(
            existingExdate = "20260714T080000Z",
            occurrenceMillis = 1_784_102_400_000L,
            dtStartMillis = 1_783_929_600_000L,
            rrule = "FREQ=DAILY;COUNT=5",
            duration = "PT1H",
            timezone = "Europe/Berlin",
            allDay = 0,
        )
        assertThat(values[CalendarContract.Events.EXDATE])
            .isEqualTo("20260714T080000Z,20260715T080000Z")
    }

    @Test
    fun `exdate drop folds away a repeated occurrence`() {
        val values = buildOccurrenceExdateValues(
            existingExdate = "20260715T080000Z",
            occurrenceMillis = 1_784_102_400_000L,
            dtStartMillis = 1_783_929_600_000L,
            rrule = "FREQ=DAILY;COUNT=5",
            duration = "PT1H",
            timezone = "Europe/Berlin",
            allDay = 0,
        )
        assertThat(values[CalendarContract.Events.EXDATE]).isEqualTo("20260715T080000Z")
    }

    @Test
    fun `all-day exdate drop uses the date-only form`() {
        // An all-day DTSTART sits at UTC midnight, so the date reads off UTC.
        val values = buildOccurrenceExdateValues(
            existingExdate = null,
            occurrenceMillis = 1_784_073_600_000L, // 2026-07-15T00:00:00Z
            dtStartMillis = 1_783_900_800_000L,
            rrule = "FREQ=YEARLY",
            duration = "P1D",
            timezone = "UTC",
            allDay = 1,
        )
        assertThat(values[CalendarContract.Events.EXDATE]).isEqualTo("20260715")
        assertThat(values[CalendarContract.Events.ALL_DAY]).isEqualTo(1)
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
