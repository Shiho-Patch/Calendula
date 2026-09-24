package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Search matching and how the results are laid out (#80 follow-up): a calendar
 * of months rather than a ranking, with description-only hits held back.
 */
class EventSearchTest {

    private val zone = TimeZone.UTC

    /** 2027-01-15T08:00Z — mid-month and mid-day, so both sides of each are testable. */
    private val now = Instant.parse("2027-01-15T08:00:00Z")
    private val todayStart = Instant.parse("2027-01-15T00:00:00Z")

    private var nextId = 1L

    private fun candidate(
        title: String,
        start: String = "2027-01-20T09:00:00Z",
        location: String? = null,
        description: String? = null,
    ): SearchCandidate {
        val begin = Instant.parse(start)
        return SearchCandidate(
            event = EventInstance(
                instanceId = nextId,
                eventId = nextId++,
                calendarId = 1L,
                title = title,
                start = begin,
                end = begin.plus(kotlin.time.Duration.parse("1h")),
                isAllDay = false,
                color = 0,
                location = location,
            ),
            description = description,
        )
    }

    /** [day] is the event's own date; all-day events sit at UTC midnight. */
    private fun allDayCandidate(title: String, day: String): SearchCandidate {
        val begin = Instant.parse("${day}T00:00:00Z")
        return SearchCandidate(
            event = EventInstance(
                instanceId = nextId,
                eventId = nextId++,
                calendarId = 1L,
                title = title,
                start = begin,
                end = begin.plus(kotlin.time.Duration.parse("24h")),
                isAllDay = true,
                color = 0,
                location = null,
            ),
        )
    }

    private fun search(vararg candidates: SearchCandidate, query: String): SearchResults =
        EventSearch.search(candidates.toList(), query, now, todayStart, zone)

    private fun datedTitles(results: SearchResults): List<String> =
        results.months.flatMap { month -> month.hits.map { it.event.title } }

    @Test
    fun `hits are grouped into months, this one first, then ahead, then back`() {
        val thisMonth = candidate("Test A", start = "2027-01-20T09:00:00Z")
        val nextMonth = candidate("Test B", start = "2027-02-03T09:00:00Z")
        val lastMonth = candidate("Test C", start = "2026-12-10T09:00:00Z")
        val yearBefore = candidate("Test D", start = "2026-03-10T09:00:00Z")

        val results = search(yearBefore, nextMonth, lastMonth, thisMonth, query = "test")

        assertThat(results.months.map { it.year to it.monthNumber })
            .containsExactly(2027 to 1, 2027 to 2, 2026 to 12, 2026 to 3)
            .inOrder()
        assertThat(datedTitles(results)).containsExactly("Test A", "Test B", "Test C", "Test D")
            .inOrder()
    }

    @Test
    fun `inside a month what is still to come comes before what has passed`() {
        // Both sides of today share one January card rather than two headers.
        val earlierThisMonth = candidate("Test past", start = "2027-01-05T09:00:00Z")
        val laterThisMonth = candidate("Test soon", start = "2027-01-20T09:00:00Z")

        val results = search(earlierThisMonth, laterThisMonth, query = "test")

        assertThat(results.months).hasSize(1)
        assertThat(datedTitles(results)).containsExactly("Test soon", "Test past").inOrder()
    }

    @Test
    fun `an event still running today keeps its place among the upcoming ones`() {
        // Reported: "test" at 19:00 having passed must not fall behind "test2" at
        // 23:00 — the day is the boundary, not the moment.
        val earlierToday = candidate("test", start = "2027-01-15T05:00:00Z")
        val laterToday = candidate("test2", start = "2027-01-15T20:00:00Z")

        val results = search(laterToday, earlierToday, query = "test")

        assertThat(datedTitles(results)).containsExactly("test", "test2").inOrder()
    }

    @Test
    fun `a match tomorrow comes before one from yesterday`() {
        val yesterday = candidate("test", start = "2027-01-14T09:00:00Z")
        val tomorrow = candidate("test2", start = "2027-01-16T09:00:00Z")

        val results = search(yesterday, tomorrow, query = "test")

        assertThat(datedTitles(results)).containsExactly("test2", "test").inOrder()
    }

    @Test
    fun `a description-only hit is held back from the dated months`() {
        // The original complaint: "protestantischer" in a holiday's notes landed
        // among the events actually named Test.
        val holiday = candidate(
            "Reformationstag",
            start = "2027-01-16T09:00:00Z",
            description = "Feiertag der protestantischer Kirchen",
        )
        val real = candidate("Test", start = "2027-06-01T09:00:00Z")

        val results = search(holiday, real, query = "test")

        assertThat(datedTitles(results)).containsExactly("Test")
        assertThat(results.inDescriptions.map { it.event.title }).containsExactly("Reformationstag")
    }

    @Test
    fun `a location match stays in the dated months`() {
        val located = candidate("Frühstück", location = "Büro")

        val results = search(located, query = "büro")

        assertThat(datedTitles(results)).containsExactly("Frühstück")
        assertThat(results.inDescriptions).isEmpty()
    }

    @Test
    fun `an event named for one token stays dated even if another is in the notes`() {
        val mixed = candidate("Zahnarzt", description = "Termin bestätigen")

        val results = search(mixed, query = "zahnarzt termin")

        assertThat(datedTitles(results)).containsExactly("Zahnarzt")
        assertThat(results.inDescriptions).isEmpty()
    }

    @Test
    fun `every token has to match, in any field and any order`() {
        val both = candidate("Zahnarzt", description = "Termin bestätigen")
        val onlyOne = candidate("Zahnarzt")

        val results = search(both, onlyOne, query = "termin zahnarzt")

        assertThat(results.allHits.map { it.event.eventId }).containsExactly(both.event.eventId)
    }

    @Test
    fun `a mid-word match is still found, so compounds keep working`() {
        val compound = candidate("Zahnarzttermin")

        assertThat(datedTitles(search(compound, query = "termin"))).containsExactly("Zahnarzttermin")
    }

    @Test
    fun `case folds beyond ASCII`() {
        assertThat(datedTitles(search(candidate("ÄRZTE Termin"), query = "ärzte")))
            .containsExactly("ÄRZTE Termin")
        assertThat(datedTitles(search(candidate("ärzte"), query = "ÄRZTE")))
            .containsExactly("ärzte")
    }

    @Test
    fun `an all-day event is filed by its own day, not by UTC`() {
        // Behind UTC an all-day event sits at midnight UTC, which is the previous
        // month locally (#82) — the month has to come from the span's first day.
        val newYear = SearchCandidate(
            event = EventInstance(
                instanceId = 99L,
                eventId = 99L,
                calendarId = 1L,
                title = "Test holiday",
                start = Instant.parse("2027-02-01T00:00:00Z"),
                end = Instant.parse("2027-02-02T00:00:00Z"),
                isAllDay = true,
                color = 0,
                location = null,
            ),
        )

        val results = EventSearch.search(
            listOf(newYear),
            "test",
            now,
            todayStart,
            TimeZone.of("America/New_York"),
        )

        assertThat(results.months.single().let { it.year to it.monthNumber }).isEqualTo(2027 to 2)
    }

    @Test
    fun `an all-day event that has passed does not rank as still to come`() {
        // Ahead of UTC its UTC-midnight end lands after the local day started, so
        // comparing raw instants floated yesterday's holiday above today's events.
        val berlin = TimeZone.of("Europe/Berlin")
        val yesterday = allDayCandidate("Test holiday", "2027-01-14")
        val tomorrow = candidate("Test soon", start = "2027-01-16T09:00:00Z")

        val results = EventSearch.search(
            listOf(yesterday, tomorrow),
            "test",
            Instant.parse("2027-01-15T07:00:00Z"),
            Instant.parse("2027-01-14T23:00:00Z"),
            berlin,
        )

        assertThat(datedTitles(results)).containsExactly("Test soon", "Test holiday").inOrder()
        assertThat(results.allHits.last().isPast).isTrue()
    }

    @Test
    fun `an all-day event is not past until its own day is over`() {
        // 21:00 in Los Angeles, where the event's UTC-midnight end is already gone.
        val results = EventSearch.search(
            listOf(allDayCandidate("Test holiday", "2027-01-15")),
            "test",
            Instant.parse("2027-01-16T05:00:00Z"),
            Instant.parse("2027-01-15T08:00:00Z"),
            TimeZone.of("America/Los_Angeles"),
        )

        assertThat(results.allHits.single().isPast).isFalse()
    }

    @Test
    fun `an untitled event's blank title is not something the query can match`() {
        val untitled = candidate("", description = "Titel folgt noch").copy(title = null)

        val results = search(untitled, query = "titel")

        assertThat(datedTitles(results)).isEmpty()
        assertThat(results.inDescriptions.single().titleSpans).isEmpty()
    }

    @Test
    fun `a hit that has already finished is marked past, including earlier today`() {
        val earlierToday = candidate("test", start = "2027-01-15T05:00:00Z")
        val laterToday = candidate("test", start = "2027-01-15T20:00:00Z")

        val hits = search(earlierToday, laterToday, query = "test")
            .allHits
            .associateBy { it.event.eventId }

        assertThat(hits.getValue(earlierToday.event.eventId).isPast).isTrue()
        assertThat(hits.getValue(laterToday.event.eventId).isPast).isFalse()
    }

    @Test
    fun `the title carries the spans to highlight, merged where they overlap`() {
        val hit = search(candidate("Test the tester"), query = "test").allHits.single()

        assertThat(hit.titleSpans).containsExactly(MatchSpan(0, 4), MatchSpan(9, 13)).inOrder()
    }

    @Test
    fun `a description snippet is excerpted around its match`() {
        val long = "x".repeat(300) + " geheimwort " + "y".repeat(300)

        val snippet = search(candidate("Notiz", description = long), query = "geheimwort")
            .allHits
            .single()
            .descriptionSnippet

        requireNotNull(snippet)
        assertThat(snippet.text).contains("geheimwort")
        assertThat(snippet.text.length).isLessThan(long.length)
        assertThat(snippet.text).startsWith("…")
        val span = snippet.spans.single()
        assertThat(snippet.text.substring(span.start, span.end)).isEqualTo("geheimwort")
    }

    @Test
    fun `a title-only match carries no description snippet`() {
        val hit = search(candidate("Test", description = "nothing relevant here"), query = "test")
            .allHits
            .single()

        assertThat(hit.descriptionSnippet).isNull()
    }

    @Test
    fun `a multi-line description matches and snippets as one line`() {
        val snippet = search(
            candidate("Notiz", description = "erste Zeile\n\n  zweite Zeile"),
            query = "zweite",
        ).allHits.single().descriptionSnippet

        requireNotNull(snippet)
        assertThat(snippet.text).isEqualTo("erste Zeile zweite Zeile")
    }

    @Test
    fun `a blank query matches nothing`() {
        assertThat(search(candidate("Test"), query = "   ").isEmpty).isTrue()
    }
}
