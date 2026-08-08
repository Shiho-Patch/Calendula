package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Ranking and matching for search (#80 follow-up): where a query matched decides
 * the order, and matching folds case and splits on whitespace.
 */
class EventSearchRankerTest {

    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000L)

    private fun candidate(
        id: Long,
        title: String,
        location: String? = null,
        description: String? = null,
        startMillis: Long = now.toEpochMilliseconds() + 86_400_000L,
    ) = SearchCandidate(
        event = EventInstance(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = title,
            start = Instant.fromEpochMilliseconds(startMillis),
            end = Instant.fromEpochMilliseconds(startMillis + 3_600_000L),
            isAllDay = false,
            color = 0,
            location = location,
        ),
        description = description,
    )

    private fun titlesFor(vararg candidates: SearchCandidate, query: String): List<String> =
        EventSearchRanker.rank(candidates.toList(), query, now).map { it.event.title }

    @Test
    fun `a title match outranks a description match even when it is further off`() {
        // The reported case: "protestantischer" in a holiday's description put it
        // above the events actually called Test, because it was sooner.
        val holiday = candidate(
            id = 1L,
            title = "Reformationstag",
            description = "Feiertag der protestantischer Kirchen",
            startMillis = now.toEpochMilliseconds() + 86_400_000L,
        )
        val real = candidate(
            id = 2L,
            title = "Test",
            startMillis = now.toEpochMilliseconds() + 30 * 86_400_000L,
        )

        assertThat(titlesFor(holiday, real, query = "test"))
            .containsExactly("Test", "Reformationstag")
            .inOrder()
    }

    @Test
    fun `a word start outranks a mid-word match inside the same field`() {
        val midWord = candidate(1L, "Zahnarzttermin")
        val wordStart = candidate(2L, "Neuer Termin beim Amt")

        assertThat(titlesFor(midWord, wordStart, query = "termin"))
            .containsExactly("Neuer Termin beim Amt", "Zahnarzttermin")
            .inOrder()
    }

    @Test
    fun `a mid-word match is still found, so compounds keep working`() {
        val compound = candidate(1L, "Zahnarzttermin")

        assertThat(titlesFor(compound, query = "termin")).containsExactly("Zahnarzttermin")
    }

    @Test
    fun `the field ranking runs title over location over description`() {
        val inDescription = candidate(1L, "Frühstück", description = "im Büro besprechen")
        val inLocation = candidate(2L, "Frühstück", location = "Büro")
        val inTitle = candidate(3L, "Büro aufräumen")

        assertThat(titlesFor(inDescription, inLocation, inTitle, query = "büro"))
            .containsExactly("Büro aufräumen", "Frühstück", "Frühstück")
            .inOrder()
        // The location hit ranks above the description one.
        val ranked = EventSearchRanker.rank(
            listOf(inDescription, inLocation, inTitle),
            "büro",
            now,
        )
        assertThat(ranked.map { it.event.eventId }).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test
    fun `case folds beyond ASCII`() {
        val upper = candidate(1L, "ÄRZTE Termin")

        assertThat(titlesFor(upper, query = "ärzte")).containsExactly("ÄRZTE Termin")
        assertThat(titlesFor(candidate(2L, "ärzte"), query = "ÄRZTE")).containsExactly("ärzte")
    }

    @Test
    fun `every token has to match, in any field and any order`() {
        val both = candidate(1L, "Zahnarzt", description = "Termin bestätigen")
        val onlyOne = candidate(2L, "Zahnarzt")

        val ranked = EventSearchRanker.rank(listOf(both, onlyOne), "termin zahnarzt", now)

        assertThat(ranked.map { it.event.eventId }).containsExactly(1L)
    }

    @Test
    fun `an event ranks by the weakest field the query needed`() {
        // Both tokens in the title beats one token stranded in the description.
        val allInTitle = candidate(1L, "Zahnarzt Termin")
        val split = candidate(2L, "Zahnarzt", description = "Termin bestätigen")

        assertThat(titlesFor(split, allInTitle, query = "zahnarzt termin"))
            .containsExactly("Zahnarzt Termin", "Zahnarzt")
            .inOrder()
    }

    @Test
    fun `ties keep the nearest-first order, upcoming before past`() {
        val soon = candidate(1L, "Test", startMillis = now.toEpochMilliseconds() + 86_400_000L)
        val later = candidate(2L, "Test", startMillis = now.toEpochMilliseconds() + 5 * 86_400_000L)
        val recentPast =
            candidate(3L, "Test", startMillis = now.toEpochMilliseconds() - 86_400_000L)
        val longAgo =
            candidate(4L, "Test", startMillis = now.toEpochMilliseconds() - 90 * 86_400_000L)

        val ranked = EventSearchRanker.rank(listOf(longAgo, later, recentPast, soon), "test", now)

        assertThat(ranked.map { it.event.eventId }).containsExactly(1L, 2L, 3L, 4L).inOrder()
    }

    @Test
    fun `the title carries the spans to highlight, merged where they overlap`() {
        val event = candidate(1L, "Test the tester")

        val hit = EventSearchRanker.rank(listOf(event), "test", now).single()

        assertThat(hit.titleSpans).containsExactly(MatchSpan(0, 4), MatchSpan(9, 13)).inOrder()
    }

    @Test
    fun `a description snippet is excerpted around its match`() {
        val long = "x".repeat(300) + " geheimwort " + "y".repeat(300)
        val event = candidate(1L, "Notiz", description = long)

        val snippet = EventSearchRanker.rank(listOf(event), "geheimwort", now)
            .single()
            .descriptionSnippet

        requireNotNull(snippet)
        assertThat(snippet.text).contains("geheimwort")
        assertThat(snippet.text.length).isLessThan(long.length)
        assertThat(snippet.text).startsWith("…")
        // The span points at the match inside the excerpt, not the full text.
        val span = snippet.spans.single()
        assertThat(snippet.text.substring(span.start, span.end)).isEqualTo("geheimwort")
    }

    @Test
    fun `a title-only match carries no description snippet`() {
        val event = candidate(1L, "Test", description = "nothing relevant here")

        assertThat(EventSearchRanker.rank(listOf(event), "test", now).single().descriptionSnippet)
            .isNull()
    }

    @Test
    fun `a multi-line description matches and snippets as one line`() {
        val event = candidate(1L, "Notiz", description = "erste Zeile\n\n  zweite Zeile")

        val snippet = EventSearchRanker.rank(listOf(event), "zweite", now).single().descriptionSnippet

        requireNotNull(snippet)
        assertThat(snippet.text).isEqualTo("erste Zeile zweite Zeile")
    }

    @Test
    fun `a blank query matches nothing`() {
        assertThat(EventSearchRanker.rank(listOf(candidate(1L, "Test")), "   ", now)).isEmpty()
    }
}
