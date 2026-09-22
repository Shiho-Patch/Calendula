package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.Availability
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

/**
 * Reading a Google Calendar export (Codeberg #304), whose dialect the parser had
 * never been held to: `VTIMEZONE` blocks with `X-LIC-LOCATION`, `TZID`-qualified
 * `DTSTART`/`EXDATE`, folded `DESCRIPTION` and `ATTENDEE` lines, `RECURRENCE-ID`
 * overrides written as separate `VEVENT`s, and `@google.com` UIDs.
 *
 * The fixture is a trimmed copy of what "Settings → Import & export → Export"
 * produces, CRLF and all. Note that Google hands that export out as a **zip**
 * containing one `.ics` per calendar — the file here is one member of it.
 */
class IcsGoogleImportTest {

    private val parser = IcsParser(TimeZone.of("Europe/Berlin"))

    private val result: IcsParseResult = parser.parse(
        checkNotNull(
            javaClass.classLoader?.getResourceAsStream("ics/google-calendar-export.ics"),
        ).use { it.readBytes().toString(Charsets.UTF_8) },
    )

    private fun event(summary: String) = result.events.single { it.summary == summary }

    @Test
    fun `every master event imports and the VTIMEZONE block is not mistaken for one`() {
        assertThat(result.events.map { it.summary })
            .containsExactly("Team standup", "Day off", "Christmas break")
    }

    @Test
    fun `X-WR-CALNAME names the calendar for all of them`() {
        assertThat(result.events.map { it.calendarName }.distinct())
            .containsExactly("jane@example.com")
    }

    @Test
    fun `a TZID-qualified start resolves against the device tz database`() {
        val standup = event("Team standup")
        assertThat(standup.isAllDay).isFalse()
        assertThat(standup.zoneId).isEqualTo("Europe/Berlin")
        // 10:00 CEST is 08:00 UTC.
        assertThat(standup.start.toString()).isEqualTo("2026-09-15T08:00:00Z")
        assertThat((standup.end - standup.start).inWholeMinutes).isEqualTo(90)
    }

    @Test
    fun `the recurrence rule and its TZID-qualified EXDATE survive`() {
        val standup = event("Team standup")
        assertThat(standup.recurrenceRule).isEqualTo("FREQ=WEEKLY;BYDAY=TU")
        assertThat(standup.exDates).containsExactly("20260929T080000Z")
    }

    @Test
    fun `the moved occurrence is skipped rather than imported as a duplicate`() {
        // Google writes a RECURRENCE-ID override as its own VEVENT carrying the
        // master's UID. Importing it would put a second "standup" in the
        // calendar; Calendula models no overrides, so it is reported instead.
        assertThat(result.events.map { it.summary }).doesNotContain("Team standup (moved)")
        assertThat(result.warnings).contains(IcsParseWarning.ModifiedOccurrenceSkipped)
    }

    @Test
    fun `a folded DESCRIPTION is unfolded and unescaped`() {
        assertThat(event("Team standup").description).isEqualTo(
            "Weekly sync with the team.\nAgenda lives in the shared doc, see the link below.",
        )
    }

    @Test
    fun `an escaped comma in LOCATION comes back as a comma`() {
        assertThat(event("Team standup").location).isEqualTo("Meeting room 2, 3rd floor")
    }

    @Test
    fun `attendees are reported rather than silently dropped`() {
        assertThat(result.warnings).contains(IcsParseWarning.AttendeesIgnored)
    }

    @Test
    fun `a timed VALARM becomes its lead time in minutes`() {
        assertThat(event("Team standup").semanticReminderMinutes()).containsExactly(30)
    }

    @Test
    fun `an all-day event keeps the single day the file gives it`() {
        val dayOff = event("Day off")
        assertThat(dayOff.isAllDay).isTrue()
        assertThat((dayOff.end - dayOff.start).inWholeDays).isEqualTo(1)
        assertThat(dayOff.availability).isEqualTo(Availability.Free)
    }

    @Test
    fun `an all-day alarm comes back as whole days before`() {
        // Google writes an all-day reminder as a whole-day offset from the
        // event's UTC midnight; a day has to survive as a day.
        assertThat(event("Day off").semanticReminderMinutes()).containsExactly(1440)
    }

    @Test
    fun `a multi-day all-day event keeps its exclusive DTEND span`() {
        val christmas = event("Christmas break")
        assertThat(christmas.isAllDay).isTrue()
        assertThat((christmas.end - christmas.start).inWholeDays).isEqualTo(3)
    }

    @Test
    fun `no recurrence rule is repaired - Google writes them well-formed`() {
        assertThat(result.warnings).doesNotContain(IcsParseWarning.RecurrenceRuleRepaired)
    }
}
