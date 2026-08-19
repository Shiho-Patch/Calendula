package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

/**
 * Reading the Simple Calendar / Fossify export dialect (Codeberg #225).
 *
 * Every fixture is shaped as `IcsExporter.writeEvent` emits it — property order,
 * the `X-FOSSIFY-*` extensions and the `P0DT1H5M0S` duration spelling included.
 *
 * Their all-day `DTEND` is *usually* RFC-correct and must not be "corrected":
 * `Event.endTS` anchors a UI-created all-day event at noon of its last day
 * (`EventActivity.getStartEndTimes`), or at the exclusive midnight for a
 * CalDAV-sourced one, and the exporter's `+ TWELVE_HOURS` rounds either to the
 * following midnight. Shifting those by a day would corrupt them.
 *
 * The exception — and the whole of #225 — is events mirrored from Contacts.
 * `MainActivity` builds those with `startTS = endTS = timestamp`, so `+ 12h`
 * lands back on the starting day and they export zero length.
 */
class IcsFossifyImportTest {

    private val parser = IcsParser(TimeZone.of("Europe/Berlin"))

    private fun fossify(vararg body: String) = (
        listOf(
            "BEGIN:VCALENDAR",
            "PRODID:-//Fossify//NONSGML Event Calendar//EN",
            "VERSION:2.0",
        ) + body + listOf("END:VCALENDAR")
        ).joinToString("\r\n")

    private fun days(event: ParsedIcsEvent): Long =
        (event.end - event.start).inWholeMilliseconds / 86_400_000L

    @Test
    fun `an all-day event keeps the exact span the file gives it`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Anna Birthday",
                "UID:abc123",
                "X-FOSSIFY-CATEGORY-COLOR:-1155931",
                "DTSTART;VALUE=DATE:19900412",
                "DTEND;VALUE=DATE:19900413",
                "X-FOSSIFY-MISSING-YEAR:0",
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYMONTH=4",
                "END:VEVENT",
            ),
        )

        val event = result.events.single()
        assertThat(event.isAllDay).isTrue()
        assertThat(days(event)).isEqualTo(1)
    }

    @Test
    fun `contact-mirrored birthdays export zero length and must not vanish`() {
        // The reported failure (#225). Fossify mirrors Contacts birthdays and
        // anniversaries with startTS == endTS, so the exporter's +12h rounds
        // back to the starting day: DTSTART == DTEND. Read literally these are
        // yearly series of zero-length occurrences, which the provider expands
        // into nothing at all — the birthdays import "successfully" and are
        // then nowhere to be seen.
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("ics/fossify-contact-birthdays.ics"),
        ).use { it.readBytes().toString(Charsets.UTF_8) }

        val result = parser.parse(text)

        assertThat(result.events).hasSize(3)
        result.events.forEach {
            assertThat(it.isAllDay).isTrue()
            assertThat(days(it)).isEqualTo(1)
            assertThat(it.recurrenceRule).startsWith("FREQ=YEARLY")
            // Their all-day reminder encoding: "on the day", not midnight UTC.
            assertThat(it.semanticReminderMinutes()).containsExactly(0)
        }
    }

    @Test
    fun `a real Fossify holiday file imports unchanged`() {
        // Shipped inside Fossify Calendar (assets/holidays/AT/public.ics). Its
        // PRODID names Fossify, so any producer-sniffing shortcut would corrupt
        // it — the file is plain, conformant iCalendar.
        val text = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("ics/fossify-holidays-at.ics"),
        ).use { it.readBytes().toString(Charsets.UTF_8) }

        val result = parser.parse(text)

        assertThat(result.events.map { it.summary })
            .containsExactly("Neujahr", "Heilige Drei Könige").inOrder()
        assertThat(result.events.map { days(it) }).containsExactly(1L, 1L)
        assertThat(result.events.map { it.recurrenceRule }).containsExactly("FREQ=YEARLY", "FREQ=YEARLY")
    }

    @Test
    fun `an all-day event with no DTEND lasts one day`() {
        // RFC 5545 3.6.1, and the span the provider needs to expand a series.
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "DTSTART;VALUE=DATE:20260819",
                "END:VEVENT",
            ),
        )

        assertThat(days(result.events.single())).isEqualTo(1)
    }

    @Test
    fun `a zero-length all-day event is widened rather than left to vanish`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "DTSTART;VALUE=DATE:20260819",
                "DTEND;VALUE=DATE:20260819",
                "END:VEVENT",
            ),
        )

        assertThat(days(result.events.single())).isEqualTo(1)
    }

    @Test
    fun `a malformed repeat rule is repaired rather than passed to the provider`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T081500Z",
                "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().recurrenceRule).isEqualTo("FREQ=WEEKLY;INTERVAL=1")
        assertThat(result.warnings).contains(IcsParseWarning.RecurrenceRuleRepaired)
    }

    @Test
    fun `a task is imported as an event and reported`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VTODO",
                "SUMMARY:Pay rent",
                "DTSTART;VALUE=DATE:20260901",
                "END:VTODO",
            ),
        )

        val task = result.events.single()
        assertThat(task.isTask).isTrue()
        assertThat(task.summary).isEqualTo("Pay rent")
        assertThat(days(task)).isEqualTo(1)
        assertThat(result.warnings).contains(IcsParseWarning.TasksImportedAsEvents)
    }

    @Test
    fun `a task dated only by DUE still imports`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VTODO",
                "SUMMARY:File taxes",
                "DUE;VALUE=DATE:20260901",
                "END:VTODO",
            ),
        )

        assertThat(result.events.single().summary).isEqualTo("File taxes")
    }

    @Test
    fun `the per-event colour wins over the calendar colour`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "X-FOSSIFY-CATEGORY-COLOR:-1155931",
                "COLOR:tomato",
                "X-FOSSIFY-EVENT-COLOR:-2818048",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T090000Z",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().color).isEqualTo(-2818048)
    }

    @Test
    fun `the calendar colour stands in when the event has none of its own`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "X-FOSSIFY-CATEGORY-COLOR:-1155931",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T090000Z",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().color).isEqualTo(-1155931)
    }

    @Test
    fun `the legacy Simple Calendar colour spellings are read too`() {
        val result = parser.parse(
            listOf(
                "BEGIN:VCALENDAR",
                "PRODID:-//Simple Mobile Tools//NONSGML Event Calendar//EN",
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "X-SMT-CATEGORY-COLOR:-1155931",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T090000Z",
                "END:VEVENT",
                "END:VCALENDAR",
            ).joinToString("\r\n"),
        )

        assertThat(result.events.single().color).isEqualTo(-1155931)
    }

    @Test
    fun `the literal null Fossify writes for a missing calendar is not a colour`() {
        // Both properties interpolate a nullable calendar straight into the value.
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Holiday",
                "X-FOSSIFY-CATEGORY-COLOR:null",
                "CATEGORIES:null",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T090000Z",
                "END:VEVENT",
            ),
        )

        val event = result.events.single()
        assertThat(event.color).isNull()
        assertThat(event.calendarName).isNull()
    }

    @Test
    fun `CATEGORIES names the source calendar`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Anna Birthday",
                "CATEGORIES:Birthdays",
                "DTSTART;VALUE=DATE:20260819",
                "DTEND;VALUE=DATE:20260820",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().calendarName).isEqualTo("Birthdays")
    }

    @Test
    fun `a reminder written as a positive trigger is not read as a lead time`() {
        // The exporter flips the sign for reminders stored below -1
        // (`sign = if (reminder.minutes < -1) "" else "-"`), so a trigger can
        // point after the start. On an all-day event that means a time of day.
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Anna Birthday",
                "DTSTART;VALUE=DATE:20260819",
                "DTEND;VALUE=DATE:20260820",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "TRIGGER:P0DT9H0M0S",
                "END:VALARM",
                "END:VEVENT",
            ),
        )

        val event = result.events.single()
        assertThat(event.reminderMinutes).containsExactly(-540)
        assertThat(event.semanticReminderMinutes()).containsExactly(0)
    }

    @Test
    fun `an all-day reminder rounds to whole days before`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Anna Birthday",
                "DTSTART;VALUE=DATE:20260819",
                "DTEND;VALUE=DATE:20260820",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                // 15h before midnight == "1 day before at 09:00".
                "TRIGGER:-P0DT15H0M0S",
                "END:VALARM",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().semanticReminderMinutes()).containsExactly(1440)
    }

    @Test
    fun `a timed reminder keeps its exact lead time`() {
        // Their P0DT1H5M0S duration spelling, from Parser.getDurationCode.
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T081500Z",
                "BEGIN:VALARM",
                "ACTION:DISPLAY",
                "TRIGGER:-P0DT0H10M0S",
                "END:VALARM",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().semanticReminderMinutes()).containsExactly(10)
    }

    @Test
    fun `an all-day series' EXDATE day codes carry over`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "DTSTART;VALUE=DATE:20260819",
                "DTEND;VALUE=DATE:20260820",
                "RRULE:FREQ=DAILY;INTERVAL=1",
                "EXDATE:20260820",
                "EXDATE:20260822",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().exDates).containsExactly("20260820", "20260822").inOrder()
    }

    @Test
    fun `a timed series' bare EXDATE day code is resolved against the series time`() {
        // They store excluded occurrences as day codes and write them out that
        // way even for a timed series, where the property alone is ambiguous.
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Gym",
                "DTSTART:20260819T170000Z",
                "DTEND:20260819T180000Z",
                "RRULE:FREQ=DAILY;INTERVAL=1",
                "EXDATE:20260820",
                "END:VEVENT",
            ),
        )

        assertThat(result.events.single().exDates).containsExactly("20260820T170000Z")
    }

    @Test
    fun `EXDATE is dropped along with an unusable recurrence rule`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Gym",
                "DTSTART:20260819T170000Z",
                "DTEND:20260819T180000Z",
                "RRULE:INTERVAL=1",
                "EXDATE:20260820",
                "END:VEVENT",
            ),
        )

        val event = result.events.single()
        assertThat(event.recurrenceRule).isNull()
        assertThat(event.exDates).isEmpty()
    }

    @Test
    fun `a whole export imports every component`() {
        val result = parser.parse(
            fossify(
                "BEGIN:VEVENT",
                "SUMMARY:Anna Birthday",
                "UID:abc123",
                "X-FOSSIFY-CATEGORY-COLOR:-1155931",
                "CATEGORIES:Birthdays",
                "LAST-MODIFIED:20260101T120000Z",
                "TRANSP:TRANSPARENT",
                "DTSTART;VALUE=DATE:19900412",
                "DTEND;VALUE=DATE:19900413",
                "X-FOSSIFY-MISSING-YEAR:0",
                "DTSTAMP:20260819T100000Z",
                "CLASS:PUBLIC",
                "STATUS:CONFIRMED",
                "RRULE:FREQ=YEARLY;INTERVAL=1;BYMONTH=4",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "SUMMARY:Standup",
                "UID:def456",
                "DTSTART:20260819T080000Z",
                "DTEND:20260819T081500Z",
                "RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=TU",
                "END:VEVENT",
                "BEGIN:VTODO",
                "SUMMARY:Pay rent",
                "UID:ghi789",
                "DTSTART;VALUE=DATE:20260901",
                "END:VTODO",
            ),
        )

        assertThat(result.events.map { it.summary })
            .containsExactly("Anna Birthday", "Standup", "Pay rent").inOrder()
        // Nothing may reach the provider as a zero-length all-day row.
        assertThat(result.events.none { it.isAllDay && days(it) == 0L }).isTrue()
    }
}
