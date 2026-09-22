package de.jeanlucmakiola.calendula.domain.ics

import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A component whose `END:` line never arrives (a download cut short, a producer
 * that dropped a line). `indexOfEnd` falls back to the end of the file, so the
 * block is read as if it had been closed.
 */
class IcsUnterminatedComponentTest {

    private val parser = IcsParser(TimeZone.of("Europe/Berlin"))

    @Test
    fun `file truncated mid-property keeps the event it had started`() {
        val result = parser.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:truncated-1@fixture
            SUMMARY:Cut off mid-file
            DTSTART:20260924T100000Z
            DTEND:20260924T1
            """.trimIndent(),
        )
        assertEquals(1, result.events.size)
        val event = result.events.single()
        assertEquals("Cut off mid-file", event.summary)
        // The truncated DTEND does not parse, so the event is zero length.
        assertEquals(event.start, event.end)
        assertEquals(emptySet<IcsParseWarning>(), result.warnings)
    }

    @Test
    fun `a missing END between two events does not merge them`() {
        val result = parser.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:first@fixture
            SUMMARY:First event
            DTSTART:20260924T100000Z
            DTEND:20260924T110000Z
            BEGIN:VEVENT
            UID:second@fixture
            SUMMARY:Second event
            DTSTART:20260925T100000Z
            DTEND:20260925T110000Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )
        assertEquals(
            listOf("First event", "Second event"),
            result.events.map { it.summary },
        )
        assertEquals(
            listOf("first@fixture", "second@fixture"),
            result.events.map { it.uid },
        )
    }
}
